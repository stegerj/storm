"""Storm analysis service - ported from original Python script"""
import math
import numpy as np
from typing import List, Tuple, Dict, Any, Optional
from PIL import Image
from io import BytesIO
import structlog

from app.config import settings
from app.models import (
    StormCentroid, MovementVector, ForecastData, 
    RiskAnalysis, MultiRadiusAnalysis, RiskLevel
)

logger = structlog.get_logger()


class StormMovementAnalyzer:
    """Analyze storm movement from historical radar frames"""
    
    @staticmethod
    def calculate_movement(historical_frames: List[Tuple[int, Image.Image]]) -> Dict[str, Any]:
        """Calculate storm movement vectors using advanced methods"""
        logger.info("calculating_storm_movement", frames=len(historical_frames))
        
        if len(historical_frames) < 2:
            logger.warning("insufficient_frames_for_analysis")
            return {}
        
        try:
            # Extract storm centroids from each frame
            storm_centroids = StormMovementAnalyzer._extract_centroids(historical_frames)
            
            if len(storm_centroids) < 2:
                logger.warning("insufficient_storm_data")
                return {}
            
            # Calculate movement vectors
            movements = StormMovementAnalyzer._calculate_movements(storm_centroids)
            
            if not movements:
                logger.warning("no_movement_detected")
                return {}
            
            # Weighted averaging
            weighted_speed = StormMovementAnalyzer._weighted_average(movements)
            
            # Polynomial regression with numpy
            regression_result = StormMovementAnalyzer._polynomial_regression(storm_centroids)
            
            # Directional trend analysis
            directional_correction = StormMovementAnalyzer._directional_trend(movements)
            
            # Select final forecast velocity
            final_speed = StormMovementAnalyzer._select_forecast_velocity(
                weighted_speed, regression_result, directional_correction
            )
            
            # Calculate forecasts
            forecast_1h = (final_speed[0] * 60, final_speed[1] * 60)
            forecast_5h = StormMovementAnalyzer._calculate_5h_forecast(
                regression_result, storm_centroids, final_speed
            )
            
            logger.info("movement_analysis_complete", 
                       forecast_1h=forecast_1h, 
                       forecast_5h=forecast_5h)
            
            return {
                'avg_speed_x': final_speed[0],
                'avg_speed_y': final_speed[1],
                'forecast_1h': forecast_1h,
                'forecast_5h': forecast_5h,
                'storm_centroids': [
                    StormCentroid(timestamp=t, x=x, y=y, pixel_count=count)
                    for t, x, y, count in storm_centroids
                ],
                'movements': [
                    MovementVector(speed_x=sx, speed_y=sy, time_diff=td)
                    for sx, sy, td in movements
                ],
                'acceleration': regression_result['acceleration'] if regression_result else (0.0, 0.0)
            }
            
        except Exception as e:
            logger.error("movement_analysis_error", error=str(e))
            return {}
    
    @staticmethod
    def _extract_centroids(frames: List[Tuple[int, Image.Image]]) -> List[Tuple]:
        """Extract storm centroids from radar frames"""
        centroids = []
        
        for timestamp, frame_img in frames:
            # Convert to RGBA if needed
            if frame_img.mode != 'RGBA':
                frame_img = frame_img.convert('RGBA')
            
            # Find high-intensity pixels (red pixels)
            pixels = np.array(frame_img)
            high_intensity_mask = (
                (pixels[:, :, 0] > 200) &  # Red channel
                (pixels[:, :, 1] < 150) &  # Green channel
                (pixels[:, :, 2] < 150)     # Blue channel
            )
            
            if np.any(high_intensity_mask):
                # Calculate centroid
                y_coords, x_coords = np.where(high_intensity_mask)
                avg_x = float(np.mean(x_coords))
                avg_y = float(np.mean(y_coords))
                pixel_count = int(np.sum(high_intensity_mask))
                centroids.append((timestamp, avg_x, avg_y, pixel_count))
        
        return centroids
    
    @staticmethod
    def _calculate_movements(centroids: List[Tuple]) -> List[Tuple]:
        """Calculate movement vectors between consecutive centroids"""
        movements = []
        
        for i in range(1, len(centroids)):
            prev_time, prev_x, prev_y, _ = centroids[i-1]
            curr_time, curr_x, curr_y, _ = centroids[i]
            
            time_diff = (curr_time - prev_time) / 60.0  # Convert to minutes
            dx = curr_x - prev_x
            dy = curr_y - prev_y
            
            if time_diff > 0:
                speed_x = dx / time_diff
                speed_y = dy / time_diff
                movements.append((speed_x, speed_y, time_diff))
        
        return movements
    
    @staticmethod
    def _weighted_average(movements: List[Tuple]) -> Tuple[float, float]:
        """Calculate weighted average of movements"""
        weights = np.arange(1, len(movements) + 1)
        total_weight = np.sum(weights)
        
        weighted_speed_x = np.sum([m[0] * w for m, w in zip(movements, weights)]) / total_weight
        weighted_speed_y = np.sum([m[1] * w for m, w in zip(movements, weights)]) / total_weight
        
        return (weighted_speed_x, weighted_speed_y)
    
    @staticmethod
    def _polynomial_regression(centroids: List[Tuple]) -> Optional[Dict]:
        """Perform polynomial regression using numpy"""
        if len(centroids) < 3:
            return None
        
        try:
            times = np.array([(c[0] - centroids[0][0]) / 60.0 for c in centroids])
            x_positions = np.array([c[1] for c in centroids])
            y_positions = np.array([c[2] for c in centroids])
            
            # Fit quadratic polynomial
            x_coeffs = np.polyfit(times, x_positions, 2)
            y_coeffs = np.polyfit(times, y_positions, 2)
            
            # Calculate acceleration (2*a)
            accel_x = 2 * x_coeffs[0]
            accel_y = 2 * y_coeffs[0]
            
            # Calculate velocity at current time
            current_time = times[-1]
            velocity_x = 2 * x_coeffs[0] * current_time + x_coeffs[1]
            velocity_y = 2 * y_coeffs[0] * current_time + y_coeffs[1]
            
            # Sanity checks
            if (abs(accel_x) > settings.max_reasonable_accel or 
                abs(accel_y) > settings.max_reasonable_accel):
                logger.info("acceleration_too_high", using="weighted_average")
                return None
            
            if (abs(velocity_x) > settings.max_reasonable_velocity or 
                abs(velocity_y) > settings.max_reasonable_velocity):
                logger.info("velocity_too_high", using="weighted_average")
                return None
            
            return {
                'x_coeffs': x_coeffs,
                'y_coeffs': y_coeffs,
                'acceleration': (accel_x, accel_y),
                'velocity': (velocity_x, velocity_y),
                'current_time': current_time
            }
            
        except Exception as e:
            logger.error("regression_error", error=str(e))
            return None
    
    @staticmethod
    def _directional_trend(movements: List[Tuple]) -> float:
        """Calculate directional trend from movement vectors"""
        if len(movements) < 2:
            return 0.0
        
        angles = [math.atan2(m[1], m[0]) for m in movements]
        angle_changes = []
        
        for i in range(1, len(angles)):
            angle_diff = angles[i] - angles[i-1]
            # Normalize to [-pi, pi]
            while angle_diff > math.pi:
                angle_diff -= 2 * math.pi
            while angle_diff < -math.pi:
                angle_diff += 2 * math.pi
            angle_changes.append(angle_diff)
        
        return np.mean(angle_changes) if angle_changes else 0.0
    
    @staticmethod
    def _select_forecast_velocity(
        weighted_speed: Tuple, 
        regression_result: Optional[Dict], 
        directional_correction: float
    ) -> Tuple[float, float]:
        """Select final forecast velocity"""
        speed_x, speed_y = weighted_speed
        
        if regression_result:
            accel_significant = (
                abs(regression_result['acceleration'][0]) > 0.1 or 
                abs(regression_result['acceleration'][1]) > 0.1
            )
            if accel_significant:
                speed_x = regression_result['velocity'][0]
                speed_y = regression_result['velocity'][1]
        
        # Apply directional correction
        if abs(directional_correction) > 0.1:
            forecast_angle = math.atan2(speed_y, speed_x)
            forecast_speed = math.sqrt(speed_x**2 + speed_y**2)
            corrected_angle = forecast_angle + directional_correction * 6
            speed_x = forecast_speed * math.cos(corrected_angle)
            speed_y = forecast_speed * math.sin(corrected_angle)
        
        return (speed_x, speed_y)
    
    @staticmethod
    def _calculate_5h_forecast(
        regression_result: Optional[Dict], 
        centroids: List[Tuple], 
        current_speed: Tuple
    ) -> Tuple[float, float]:
        """Calculate 5-hour forecast"""
        if regression_result:
            try:
                forecast_time = regression_result['current_time'] + 300  # 5 hours
                forecast_x = np.polyval(regression_result['x_coeffs'], forecast_time)
                forecast_y = np.polyval(regression_result['y_coeffs'], forecast_time)
                
                # Convert to displacement
                current_x = centroids[-1][1]
                current_y = centroids[-1][2]
                return (forecast_x - current_x, forecast_y - current_y)
            except:
                return (current_speed[0] * 300, current_speed[1] * 300)
        else:
            return (current_speed[0] * 300, current_speed[1] * 300)


class StormRiskAnalyzer:
    """Analyze storm risk at multiple radii"""
    
    @staticmethod
    def analyze_at_radii(
        latitude: float, 
        longitude: float, 
        movement_data: Dict[str, Any]
    ) -> Dict[str, Any]:
        """Analyze storm risk at multiple radii"""
        logger.info("analyzing_storm_risk", lat=latitude, lon=longitude)
        
        if not movement_data:
            return {}
        
        try:
            storm_centroids = movement_data.get('storm_centroids', [])
            if not storm_centroids:
                return {}
            
            # Get latest centroid
            latest = storm_centroids[-1]
            storm_x = latest.x
            storm_y = latest.y
            
            # User position (center of 512x512 image)
            user_x = 256.0
            user_y = 256.0
            
            # Calculate distance and movement
            storm_to_user_x = user_x - storm_x
            storm_to_user_y = user_y - storm_y
            distance_pixels = math.sqrt(storm_to_user_x**2 + storm_to_user_y**2)
            
            avg_speed_x = movement_data['avg_speed_x']
            avg_speed_y = movement_data['avg_speed_y']
            storm_speed = math.sqrt(avg_speed_x**2 + avg_speed_y**2)
            
            # Calculate alignment
            dot_product = avg_speed_x * storm_to_user_x + avg_speed_y * storm_to_user_y
            alignment = dot_product / (distance_pixels * storm_speed) if storm_speed > 0 else 0
            
            # Convert to km
            distance_km = distance_pixels / settings.pixels_per_km
            storm_speed_km_per_min = storm_speed / settings.pixels_per_km
            
            # Time to impact
            time_to_impact = (
                distance_km / storm_speed_km_per_min 
                if storm_speed_km_per_min > 0 and alignment > 0 
                else float('inf')
            )
            
            # Determine risk levels
            is_approaching = alignment > settings.approach_alignment_threshold
            is_imminent = time_to_impact < 60
            
            current_risk = StormRiskAnalyzer._determine_risk_level(
                is_imminent, is_approaching, time_to_impact, 0
            )
            radius_20km_risk = StormRiskAnalyzer._determine_risk_level(
                False, is_approaching and distance_km < 20, time_to_impact, 20
            )
            radius_100km_risk = StormRiskAnalyzer._determine_risk_level(
                False, is_approaching and distance_km < 100, time_to_impact, 100
            )
            
            # Forecast positions
            forecast_1h = movement_data.get('forecast_1h', (0, 0))
            forecast_5h = movement_data.get('forecast_5h', (0, 0))
            
            return {
                'current': RiskAnalysis(
                    radius_km=0,
                    distance_to_user_km=distance_km,
                    storm_speed_km_per_min=storm_speed_km_per_min,
                    alignment=alignment,
                    is_approaching=is_approaching,
                    time_to_impact=time_to_impact,
                    risk_level=current_risk,
                    forecast_1h_px=forecast_1h,
                    forecast_5h_px=forecast_5h
                ),
                'radius_20km': RiskAnalysis(
                    radius_km=20,
                    distance_to_user_km=distance_km,
                    storm_speed_km_per_min=storm_speed_km_per_min,
                    alignment=alignment,
                    is_approaching=is_approaching and distance_km < 20,
                    time_to_impact=time_to_impact,
                    risk_level=radius_20km_risk
                ),
                'radius_100km': RiskAnalysis(
                    radius_km=100,
                    distance_to_user_km=distance_km,
                    storm_speed_km_per_min=storm_speed_km_per_min,
                    alignment=alignment,
                    is_approaching=is_approaching and distance_km < 100,
                    time_to_impact=time_to_impact,
                    risk_level=radius_100km_risk
                )
            }
            
        except Exception as e:
            logger.error("risk_analysis_error", error=str(e))
            return {}
    
    @staticmethod
    def _determine_risk_level(
        is_imminent: bool, 
        is_approaching: bool, 
        time_to_impact: float, 
        radius_km: float
    ) -> RiskLevel:
        """Determine risk level based on parameters"""
        if is_imminent and is_approaching:
            return RiskLevel.CRITICAL
        elif is_approaching and time_to_impact < 120:
            return RiskLevel.HIGH
        elif is_approaching:
            return RiskLevel.MEDIUM
        elif radius_km < 20:
            return RiskLevel.MEDIUM
        else:
            return RiskLevel.LOW
