
#!/usr/bin/env python3
"""
Storm Alert - Top-of-Class Python Version
Advanced storm detection with ML, radar integration, and multi-level warnings
"""

import requests
import math
import os
from dataclasses import dataclass
from typing import List, Optional, Tuple
from datetime import datetime
from enum import Enum
from PIL import Image
from io import BytesIO


class WarningLevel(Enum):
    """Multi-level warning system following international standards"""
    NONE = "none"
    YELLOW = "yellow"      # Be aware
    ORANGE = "orange"      # Be prepared
    RED = "red"            # Take action


@dataclass
class WeatherCondition:
    code: int
    description: str
    is_stormy: bool


@dataclass
class CurrentWeather:
    temperature: float
    wind_speed: float
    wind_direction: float
    weather_code: int
    time: str
    relative_humidity: Optional[float] = None
    pressure_msl: Optional[float] = None
    cloud_cover: Optional[float] = None


@dataclass
class HourlyData:
    time: List[str]
    temperature: Optional[List[float]] = None
    precipitation_probability: Optional[List[int]] = None
    precipitation: Optional[List[float]] = None
    wind_speed: Optional[List[float]] = None
    wind_gusts: Optional[List[float]] = None
    relative_humidity: Optional[List[float]] = None
    pressure_msl: Optional[List[float]] = None
    cloud_cover: Optional[List[float]] = None


@dataclass
class DailyData:
    time: List[str]
    weather_code: List[int]
    temperature_max: List[float]
    temperature_min: List[float]
    precipitation_probability_max: Optional[List[int]] = None
    wind_speed_max: Optional[List[float]] = None
    precipitation_sum: Optional[List[float]] = None


@dataclass
class WeatherResponse:
    latitude: float
    longitude: float
    current_weather: CurrentWeather
    hourly: Optional[HourlyData] = None
    daily: Optional[DailyData] = None


@dataclass
class StormRisk:
    is_currently_stormy: bool
    is_storm_approaching: bool
    storm_probability: float
    confidence_interval: Tuple[float, float]  # (lower, upper) bounds
    estimated_time_to_storm: int  # in minutes, -1 if no storm approaching
    current_condition: str
    wind_speed: float
    precipitation_probability: int
    current_precipitation: float = 0.0
    max_wind_speed_next_6_hours: float = 0.0
    warning_level: WarningLevel = WarningLevel.NONE
    trend_analysis: Optional[str] = None
    radar_intensity: Optional[float] = None  # 0-1 scale from radar data


def get_weather_condition(code: int) -> WeatherCondition:
    """Interpret weather code from Open-Meteo API"""
    weather_codes = {
        0: ("Clear sky", False),
        1: ("Mainly clear", False),
        2: ("Partly cloudy", False),
        3: ("Overcast", False),
        45: ("Foggy", False),
        48: ("Foggy", False),
        51: ("Drizzle", False),
        53: ("Drizzle", False),
        55: ("Drizzle", False),
        56: ("Freezing drizzle", False),
        57: ("Freezing drizzle", False),
        61: ("Rain", False),
        63: ("Rain", False),
        65: ("Rain", False),
        66: ("Freezing rain", False),
        67: ("Freezing rain", False),
        71: ("Snow", False),
        73: ("Snow", False),
        75: ("Snow", False),
        77: ("Snow grains", False),
        80: ("Rain showers", False),
        81: ("Rain showers", False),
        82: ("Rain showers", False),
        85: ("Snow showers", False),
        86: ("Snow showers", False),
        95: ("Thunderstorm", True),
        96: ("Thunderstorm with hail", True),
        99: ("Thunderstorm with hail", True),
    }
    
    description, is_stormy = weather_codes.get(code, ("Unknown", False))
    return WeatherCondition(code, description, is_stormy)


class DynamicThresholds:
    """Dynamic thresholds based on season and location"""
    
    @staticmethod
    def get_season(month: int) -> str:
        """Determine season from month (1-12)"""
        if month in [12, 1, 2]:
            return "winter"
        elif month in [3, 4, 5]:
            return "spring"
        elif month in [6, 7, 8]:
            return "summer"
        else:
            return "autumn"
    
    @staticmethod
    def get_thresholds(latitude: float, season: str) -> dict:
        """Get dynamic thresholds based on location and season"""
        # Base thresholds
        thresholds = {
            "high_precip_prob": 70,
            "moderate_precip_prob": 50,
            "high_wind_speed": 60,
            "moderate_wind_speed": 40,
            "heavy_precipitation": 5.0,
            "yellow_warning_prob": 0.5,
            "orange_warning_prob": 0.7,
            "red_warning_prob": 0.9,
            "medium_risk": 0.5  # Add missing threshold for ML predictor
        }
        
        # Adjust for latitude (higher latitudes = more sensitive to wind)
        if abs(latitude) > 50:
            thresholds["high_wind_speed"] = 50
            thresholds["moderate_wind_speed"] = 35
        
        # Adjust for season
        if season == "summer":
            thresholds["high_precip_prob"] = 75  # Summer storms more intense
            thresholds["heavy_precipitation"] = 7.0
        elif season == "winter":
            thresholds["high_wind_speed"] = 55  # Winter winds more dangerous
            thresholds["heavy_precipitation"] = 3.0
        
        return thresholds


class TrendAnalyzer:
    """Analyze trends in weather data over time"""
    
    @staticmethod
    def analyze_trend(data: List[float], window_size: int = 3) -> str:
        """Analyze trend of a time series"""
        if len(data) < window_size:
            return "insufficient_data"
        
        # Calculate moving average
        moving_avg = []
        for i in range(len(data) - window_size + 1):
            window = data[i:i + window_size]
            moving_avg.append(sum(window) / len(window))
        
        if len(moving_avg) < 2:
            return "stable"
        
        # Calculate trend
        recent_avg = moving_avg[-1]
        earlier_avg = moving_avg[0]
        change = recent_avg - earlier_avg
        
        # Determine trend
        if change > 0.5:
            return "increasing"
        elif change < -0.5:
            return "decreasing"
        else:
            return "stable"
    
    @staticmethod
    def calculate_rate_of_change(data: List[float]) -> float:
        """Calculate rate of change (derivative approximation)"""
        if len(data) < 2:
            return 0.0
        
        changes = []
        for i in range(1, len(data)):
            changes.append(data[i] - data[i-1])
        
        return sum(changes) / len(changes) if changes else 0.0


class RadarAnalyzer:
    """Analyze radar data for storm detection with real image processing and forecasting"""
    
    @staticmethod
    def get_historical_radar_frames(latitude: float, longitude: float, num_frames: int = 10) -> list:
        """
        Fetch historical radar frames for storm movement analysis
        Returns list of (timestamp, image) tuples
        """
        print(f"   → FETCHING HISTORICAL RADAR FRAMES ({num_frames} frames)")
        try:
            # Get API metadata
            api_response = requests.get("https://api.rainviewer.com/public/weather-maps.json", timeout=10)
            if api_response.status_code != 200:
                print(f"   → Failed to get API metadata: HTTP {api_response.status_code}")
                return []
            
            api_data = api_response.json()
            host = api_data.get("host", "https://tilecache.rainviewer.com")
            radar_data = api_data.get("radar", {})
            past_frames = radar_data.get("past", [])
            
            # Get the most recent N frames
            frames_to_fetch = past_frames[-num_frames:] if len(past_frames) >= num_frames else past_frames
            
            print(f"   → Available historical frames: {len(past_frames)}")
            print(f"   → Fetching {len(frames_to_fetch)} most recent frames")
            
            # Calculate tile coordinates
            zoom = 7
            tile_size = 256
            n = 2 ** zoom
            center_x = (longitude + 180) / 360 * n
            lat_rad = math.radians(latitude)
            center_y = (1 - math.asinh(math.tan(lat_rad)) / math.pi) / 2 * n
            
            # Fetch historical frames
            historical_frames = []
            for frame in frames_to_fetch:
                frame_time = frame.get("time")
                frame_path = frame.get("path")
                
                # Fetch 2x2 grid for each historical frame
                tiles_to_fetch = []
                for dx in [-1, 0]:
                    for dy in [-1, 0]:
                        x_tile = int(center_x) + dx
                        y_tile = int(center_y) + dy
                        tiles_to_fetch.append((x_tile, y_tile, dx, dy))
                
                frame_tiles = []
                for x_tile, y_tile, dx, dy in tiles_to_fetch:
                    tile_url = f"{host}{frame_path}/{tile_size}/{zoom}/{x_tile}/{y_tile}/0/1_0.png"
                    response = requests.get(tile_url, timeout=10)
                    if response.status_code == 200:
                        tile_img = Image.open(BytesIO(response.content))
                        if tile_img.mode != 'RGBA':
                            tile_img = tile_img.convert('RGBA')
                        frame_tiles.append((tile_img, dx, dy))
                
                if frame_tiles:
                    # Stitch tiles together
                    composite_frame = Image.new('RGBA', (tile_size * 2, tile_size * 2))
                    for tile_img, dx, dy in frame_tiles:
                        pos_x = (dx + 1) * tile_size
                        pos_y = (dy + 1) * tile_size
                        composite_frame.paste(tile_img, (pos_x, pos_y))
                    
                    historical_frames.append((frame_time, composite_frame))
                    print(f"   → Fetched frame at {frame_time}")
            
            print(f"   → Successfully fetched {len(historical_frames)} historical frames")
            return historical_frames
            
        except Exception as e:
            print(f"   → Error fetching historical frames: {e}")
            import traceback
            traceback.print_exc()
            return []
    
    @staticmethod
    def calculate_storm_movement(historical_frames: list) -> dict:
        """
        Calculate storm movement vectors from historical radar frames using advanced methods
        Returns movement statistics and forecast data
        """
        print(f"   → CALCULATING STORM MOVEMENT FROM {len(historical_frames)} FRAMES")
        
        if len(historical_frames) < 2:
            print(f"   → Not enough frames for movement analysis")
            return {}
        
        try:
            # Extract storm centroids from each frame
            storm_centroids = []
            
            for timestamp, frame_img in historical_frames:
                # Find high-intensity pixels (storm cells)
                pixels = list(frame_img.get_flattened_data())
                high_intensity_pixels = []
                
                for i, pixel in enumerate(pixels):
                    r, g, b, a = pixel[:4]
                    if r > 200 and g < 150 and b < 150:  # Red pixels (high intensity)
                        x = (i % frame_img.width)
                        y = (i // frame_img.width)
                        high_intensity_pixels.append((x, y))
                
                if high_intensity_pixels:
                    # Calculate centroid
                    avg_x = sum(p[0] for p in high_intensity_pixels) / len(high_intensity_pixels)
                    avg_y = sum(p[1] for p in high_intensity_pixels) / len(high_intensity_pixels)
                    storm_centroids.append((timestamp, avg_x, avg_y, len(high_intensity_pixels)))
                    print(f"   → Frame {timestamp}: centroid=({avg_x:.1f}, {avg_y:.1f}), pixels={len(high_intensity_pixels)}")
            
            if len(storm_centroids) < 2:
                print(f"   → Not enough storm data for movement analysis")
                return {}
            
            # Calculate movement vectors between consecutive frames
            movements = []
            for i in range(1, len(storm_centroids)):
                prev_time, prev_x, prev_y, prev_count = storm_centroids[i-1]
                curr_time, curr_x, curr_y, curr_count = storm_centroids[i]
                
                time_diff = (curr_time - prev_time) / 60  # Convert seconds to minutes
                dx = curr_x - prev_x
                dy = curr_y - prev_y
                
                if time_diff > 0:
                    speed_x = dx / time_diff  # pixels per minute
                    speed_y = dy / time_diff
                    movements.append((speed_x, speed_y, time_diff))
                    print(f"   → Movement {i}: dx={dx:.1f}px, dy={dy:.1f}px, time={time_diff:.1f}min, speed=({speed_x:.2f}, {speed_y:.2f}) px/min")
            
            if not movements:
                print(f"   → No movement detected")
                return {}
            
            # Method 1: Weighted averaging (recent frames get higher weight)
            print(f"   → Using weighted averaging for recent frames")
            weights = [i + 1 for i in range(len(movements))]  # Linear weighting
            total_weight = sum(weights)
            
            weighted_speed_x = sum(m[0] * w for m, w in zip(movements, weights)) / total_weight
            weighted_speed_y = sum(m[1] * w for m, w in zip(movements, weights)) / total_weight
            
            print(f"   → Weighted average movement: ({weighted_speed_x:.2f}, {weighted_speed_y:.2f}) px/min")
            
            # Method 2: Polynomial regression (quadratic for acceleration) - without numpy
            print(f"   → Using polynomial regression for acceleration detection")
            
            # Extract time and position data
            times = [(storm_centroids[i][0] - storm_centroids[0][0]) / 60 for i in range(len(storm_centroids))]  # minutes from start
            x_positions = [c[1] for c in storm_centroids]
            y_positions = [c[2] for c in storm_centroids]
            
            # Fit quadratic polynomial: position = a*t^2 + b*t + c using least squares
            if len(times) >= 3:
                try:
                    def poly_fit_quadratic(times, positions):
                        """Simple quadratic polynomial fit without numpy"""
                        n = len(times)
                        if n < 3:
                            return None
                        
                        # Set up normal equations for quadratic fit
                        # Sum of powers of t
                        sum_t = sum(times)
                        sum_t2 = sum(t*t for t in times)
                        sum_t3 = sum(t*t*t for t in times)
                        sum_t4 = sum(t*t*t*t for t in times)
                        
                        # Cross terms with positions
                        sum_y = sum(positions)
                        sum_ty = sum(t*y for t, y in zip(times, positions))
                        sum_t2y = sum(t*t*y for t, y in zip(times, positions))
                        
                        # Solve the system of equations for coefficients a, b, c
                        # Using Cramer's rule or direct substitution
                        det = n * (sum_t2*sum_t4 - sum_t3*sum_t3) - sum_t * (sum_t*sum_t4 - sum_t2*sum_t3) + sum_t2 * (sum_t*sum_t3 - sum_t2*sum_t2)
                        
                        if abs(det) < 1e-10:
                            return None
                        
                        # Calculate coefficients
                        det_a = sum_y * (sum_t2*sum_t4 - sum_t3*sum_t3) - sum_t * (sum_ty*sum_t4 - sum_t2y*sum_t3) + sum_t2 * (sum_ty*sum_t3 - sum_t2y*sum_t2)
                        a = det_a / det
                        
                        det_b = n * (sum_ty*sum_t4 - sum_t2y*sum_t3) - sum_y * (sum_t*sum_t4 - sum_t2*sum_t3) + sum_t2 * (sum_t*sum_t2y - sum_t2*sum_ty)
                        b = det_b / det
                        
                        det_c = n * (sum_t2*sum_t2y - sum_t3*sum_ty) - sum_t * (sum_t*sum_t2y - sum_t2*sum_ty) + sum_y * (sum_t*sum_t3 - sum_t2*sum_t2)
                        c = det_c / det
                        
                        return (a, b, c)
                    
                    # X position regression
                    x_coeffs = poly_fit_quadratic(times, x_positions)
                    # Y position regression  
                    y_coeffs = poly_fit_quadratic(times, y_positions)
                    
                    if x_coeffs and y_coeffs:
                        # Calculate acceleration (2*a)
                        accel_x = 2 * x_coeffs[0]  # pixels/min^2
                        accel_y = 2 * y_coeffs[0]
                        
                        print(f"   → Acceleration: ({accel_x:.3f}, {accel_y:.3f}) px/min²")
                        
                        # Calculate velocity at current time (derivative: 2*a*t + b)
                        current_time = times[-1]
                        velocity_x = 2 * x_coeffs[0] * current_time + x_coeffs[1]
                        velocity_y = 2 * y_coeffs[0] * current_time + y_coeffs[1]
                        
                        print(f"   → Current velocity from regression: ({velocity_x:.2f}, {velocity_y:.2f}) px/min")
                        
                        # Sanity checks for regression results
                        max_reasonable_accel = 10.0  # pixels/min^2
                        max_reasonable_velocity = 50.0  # pixels/min
                        
                        if abs(accel_x) > max_reasonable_accel or abs(accel_y) > max_reasonable_accel:
                            print(f"   → Acceleration too high, using weighted average")
                            forecast_speed_x = weighted_speed_x
                            forecast_speed_y = weighted_speed_y
                            x_coeffs_final = None
                            y_coeffs_final = None
                        elif abs(velocity_x) > max_reasonable_velocity or abs(velocity_y) > max_reasonable_velocity:
                            print(f"   → Velocity too high, using weighted average")
                            forecast_speed_x = weighted_speed_x
                            forecast_speed_y = weighted_speed_y
                            x_coeffs_final = None
                            y_coeffs_final = None
                        elif abs(accel_x) > 0.1 or abs(accel_y) > 0.1:
                            print(f"   → Using regression-based forecasting (acceleration detected)")
                            forecast_speed_x = velocity_x
                            forecast_speed_y = velocity_y
                            x_coeffs_final = x_coeffs
                            y_coeffs_final = y_coeffs
                        else:
                            print(f"   → Using weighted average (acceleration negligible)")
                            forecast_speed_x = weighted_speed_x
                            forecast_speed_y = weighted_speed_y
                            x_coeffs_final = None
                            y_coeffs_final = None
                    else:
                        print(f"   → Regression failed, using weighted average")
                        forecast_speed_x = weighted_speed_x
                        forecast_speed_y = weighted_speed_y
                        x_coeffs_final = None
                        y_coeffs_final = None
                        
                except Exception as e:
                    print(f"   → Regression failed, using weighted average: {e}")
                    forecast_speed_x = weighted_speed_x
                    forecast_speed_y = weighted_speed_y
                    x_coeffs_final = None
                    y_coeffs_final = None
            else:
                print(f"   → Not enough frames for regression, using weighted average")
                forecast_speed_x = weighted_speed_x
                forecast_speed_y = weighted_speed_y
                x_coeffs_final = None
                y_coeffs_final = None
            
            # Method 3: Directional trend analysis
            print(f"   → Analyzing directional trends")
            angles = [math.atan2(m[1], m[0]) for m in movements]
            
            # Calculate angular change
            angle_changes = []
            for i in range(1, len(angles)):
                angle_diff = angles[i] - angles[i-1]
                # Normalize to [-pi, pi]
                while angle_diff > math.pi:
                    angle_diff -= 2 * math.pi
                while angle_diff < -math.pi:
                    angle_diff += 2 * math.pi
                angle_changes.append(angle_diff)
            
            if angle_changes:
                avg_angle_change = sum(angle_changes) / len(angle_changes)
                print(f"   → Average angular change: {avg_angle_change:.3f} rad/frame")
                
                # Apply directional correction to forecast
                if abs(avg_angle_change) > 0.1:  # Significant directional change
                    print(f"   → Applying directional trend correction")
                    # Adjust forecast angle based on trend
                    forecast_angle = math.atan2(forecast_speed_y, forecast_speed_x)
                    forecast_speed = math.sqrt(forecast_speed_x**2 + forecast_speed_y**2)
                    corrected_angle = forecast_angle + avg_angle_change * 6  # Project 6 frames ahead
                    forecast_speed_x = forecast_speed * math.cos(corrected_angle)
                    forecast_speed_y = forecast_speed * math.sin(corrected_angle)
            
            # Calculate forecast positions using improved methods
            forecast_1h_x = forecast_speed_x * 60  # 1 hour = 60 minutes
            forecast_1h_y = forecast_speed_y * 60
            
            # For 5h, use regression if available, otherwise use current velocity
            if x_coeffs_final and y_coeffs_final:
                try:
                    forecast_5h_time = current_time + 300  # 5 hours = 300 minutes
                    forecast_5h_x = x_coeffs_final[0] * forecast_5h_time**2 + x_coeffs_final[1] * forecast_5h_time + x_coeffs_final[2]
                    forecast_5h_y = y_coeffs_final[0] * forecast_5h_time**2 + y_coeffs_final[1] * forecast_5h_time + y_coeffs_final[2]
                    # Convert to displacement from current position
                    forecast_5h_x = forecast_5h_x - x_positions[-1]
                    forecast_5h_y = forecast_5h_y - y_positions[-1]
                    print(f"   → Using regression for 5h forecast")
                except:
                    forecast_5h_x = forecast_speed_x * 300
                    forecast_5h_y = forecast_speed_y * 300
            else:
                forecast_5h_x = forecast_speed_x * 300
                forecast_5h_y = forecast_speed_y * 300
            
            print(f"   → Final forecast velocities: ({forecast_speed_x:.2f}, {forecast_speed_y:.2f}) px/min")
            print(f"   → 1h forecast displacement: ({forecast_1h_x:.1f}, {forecast_1h_y:.1f}) px")
            print(f"   → 5h forecast displacement: ({forecast_5h_x:.1f}, {forecast_5h_y:.1f}) px")
            
            return {
                'avg_speed_x': forecast_speed_x,
                'avg_speed_y': forecast_speed_y,
                'forecast_1h': (forecast_1h_x, forecast_1h_y),
                'forecast_5h': (forecast_5h_x, forecast_5h_y),
                'storm_centroids': storm_centroids,
                'movements': movements,
                'acceleration': (accel_x if 'accel_x' in locals() else 0, accel_y if 'accel_y' in locals() else 0)
            }
            
        except Exception as e:
            print(f"   → Error calculating storm movement: {e}")
            import traceback
            traceback.print_exc()
            return {}
    
    @staticmethod
    def analyze_storm_at_radii(latitude: float, longitude: float, movement_data: dict) -> dict:
        """
        Analyze storm risk at different radii: current location, 20km, 100km
        Enhanced to check if storm is actually approaching user location
        """
        print(f"   → ANALYZING STORM RISK AT MULTIPLE RADII")
        
        if not movement_data:
            return {}
        
        try:
            # Get the most recent storm centroid
            storm_centroids = movement_data.get('storm_centroids', [])
            if not storm_centroids:
                print(f"   → No storm centroids available")
                return {}
            
            # Use the most recent centroid
            latest_centroid = storm_centroids[-1]
            _, storm_x, storm_y, storm_intensity = latest_centroid
            
            # Calculate user location in pixel coordinates (center of image)
            # Assuming 512x512 image, user is at center
            user_x = 256
            user_y = 256
            
            # Calculate vector from storm to user
            storm_to_user_x = user_x - storm_x
            storm_to_user_y = user_y - storm_y
            
            # Calculate distance from storm to user (in pixels)
            distance_to_user = math.sqrt(storm_to_user_x**2 + storm_to_user_y**2)
            
            # Get storm movement vector
            avg_speed_x = movement_data['avg_speed_x']
            avg_speed_y = movement_data['avg_speed_y']
            
            # Calculate storm speed magnitude
            storm_speed = math.sqrt(avg_speed_x**2 + avg_speed_y**2)
            
            # Calculate dot product to check if storm is moving toward user
            # If dot product is positive, storm is moving toward user
            dot_product = (avg_speed_x * storm_to_user_x + avg_speed_y * storm_to_user_y)
            
            # Calculate alignment (cosine of angle between movement and direction to user)
            # -1 = moving away, 1 = moving directly toward
            if distance_to_user > 0 and storm_speed > 0:
                alignment = dot_product / (distance_to_user * storm_speed)
            else:
                alignment = 0
            
            # Convert pixel movement to km (approximate at zoom 7)
            pixels_per_km = 0.5  # Approximate conversion
            distance_to_user_km = distance_to_user / pixels_per_km
            storm_speed_km_per_min = storm_speed / pixels_per_km
            
            # Calculate time to impact (minutes)
            if storm_speed_km_per_min > 0 and alignment > 0:
                time_to_impact = distance_to_user_km / storm_speed_km_per_min
            else:
                time_to_impact = float('inf')
            
            print(f"   → Storm position: ({storm_x:.1f}, {storm_y:.1f}) pixels")
            print(f"   → User position: ({user_x}, {user_y}) pixels")
            print(f"   → Distance to user: {distance_to_user_km:.1f} km")
            print(f"   → Storm speed: {storm_speed_km_per_min:.2f} km/min")
            print(f"   → Movement alignment: {alignment:.2f} (-1=away, 1=toward)")
            print(f"   → Time to impact: {time_to_impact:.1f} minutes" if time_to_impact < float('inf') else "   → Time to impact: Not approaching")
            
            # Determine if storm is approaching user
            is_approaching = alignment > 0.3  # Storm is moving generally toward user
            is_imminent = time_to_impact < 60  # Will reach user within 1 hour
            
            # Calculate forecast positions
            forecast_1h_x = avg_speed_x * 60
            forecast_1h_y = avg_speed_y * 60
            
            forecast_5h_x = avg_speed_x * 300
            forecast_5h_y = avg_speed_y * 300
            
            radii_analysis = {
                'current': {
                    'radius_km': 0,
                    'distance_to_user_km': distance_to_user_km,
                    'storm_speed_km_per_min': storm_speed_km_per_min,
                    'alignment': alignment,
                    'is_approaching': is_approaching,
                    'time_to_impact': time_to_impact,
                    'forecast_1h_px': (forecast_1h_x, forecast_1h_y),
                    'forecast_5h_px': (forecast_5h_x, forecast_5h_y),
                    'risk_level': 'CRITICAL' if is_imminent and is_approaching else 
                                 'HIGH' if is_approaching and time_to_impact < 120 else
                                 'MEDIUM' if is_approaching else 'LOW'
                },
                '20km': {
                    'radius_km': 20,
                    'risk_level': 'HIGH' if distance_to_user_km < 20 and is_approaching else 'MEDIUM'
                },
                '100km': {
                    'radius_km': 100,
                    'risk_level': 'MEDIUM' if distance_to_user_km < 100 and is_approaching else 'LOW'
                }
            }
            
            print(f"   → Current location risk: {radii_analysis['current']['risk_level']}")
            print(f"   → Storm approaching: {is_approaching}")
            print(f"   → 20km radius risk: {radii_analysis['20km']['risk_level']}")
            print(f"   → 100km radius risk: {radii_analysis['100km']['risk_level']}")
            
            return radii_analysis
            
        except Exception as e:
            print(f"   → Error analyzing radii: {e}")
            import traceback
            traceback.print_exc()
            return {}
    
    @staticmethod
    def get_radar_intensity(latitude: float, longitude: float, movement_data: dict = None) -> Optional[float]:
        """
        Get radar intensity using RainViewer API with proper API flow
        Returns 0-1 scale representing storm intensity
        """
        print(f"   → RADAR ANALYSIS for {latitude:.2f}, {longitude:.2f}")
        try:
            # Step 1: Get the API metadata (host and paths)
            api_response = requests.get("https://api.rainviewer.com/public/weather-maps.json", timeout=10)
            if api_response.status_code != 200:
                print(f"   → Failed to get API metadata: HTTP {api_response.status_code}")
                return None
            
            api_data = api_response.json()
            host = api_data.get("host", "https://tilecache.rainviewer.com")
            
            # Get the latest radar path (usually the last one in the list)
            radar_data = api_data.get("radar", {})
            past_frames = radar_data.get("past", [])
            now_frame = radar_data.get("now")
            
            # Use the most recent frame
            if now_frame:
                path = now_frame.get("path")
            elif past_frames:
                path = past_frames[-1].get("path")
            else:
                print(f"   → No radar data available")
                return None
            
            # Step 2: Construct radar tiles using multi-tile approach for better centering
            # Get 2x2 grid of tiles centered on the location
            zoom = 7  # Maximum zoom supported by RainViewer
            tile_size = 256  # Match CartoDB tile size
            color = 0  # Default color scheme
            options = "1_0"  # smoothed, no snow
            
            # Calculate tile coordinates for the center point
            n = 2 ** zoom
            center_x = (longitude + 180) / 360 * n
            lat_rad = math.radians(latitude)
            center_y = (1 - math.asinh(math.tan(lat_rad)) / math.pi) / 2 * n
            
            # Fetch 2x2 grid of tiles
            tiles_to_fetch = []
            for dx in [-1, 0]:
                for dy in [-1, 0]:
                    x_tile = int(center_x) + dx
                    y_tile = int(center_y) + dy
                    tiles_to_fetch.append((x_tile, y_tile, dx, dy))
            
            radar_tiles = []
            for x_tile, y_tile, dx, dy in tiles_to_fetch:
                tile_url = f"{host}{path}/{tile_size}/{zoom}/{x_tile}/{y_tile}/{color}/{options}.png"
                response = requests.get(tile_url, timeout=10)
                if response.status_code == 200:
                    tile_img = Image.open(BytesIO(response.content))
                    if tile_img.mode != 'RGBA':
                        tile_img = tile_img.convert('RGBA')
                    radar_tiles.append((tile_img, dx, dy))
            
            # Stitch tiles together
            composite_radar = Image.new('RGBA', (tile_size * 2, tile_size * 2))
            for tile_img, dx, dy in radar_tiles:
                pos_x = (dx + 1) * tile_size
                pos_y = (dy + 1) * tile_size
                composite_radar.paste(tile_img, (pos_x, pos_y))
            
            # Convert to bytes for the analysis function
            import io
            radar_bytes = io.BytesIO()
            composite_radar.save(radar_bytes, format='PNG')
            radar_bytes.seek(0)
            
            return RadarAnalyzer._analyze_radar_image_from_response(radar_bytes.read(), latitude, longitude, center_x, center_y, movement_data=movement_data)
        except Exception as e:
            print(f"   → Radar analysis error: {e}")
            import traceback
            traceback.print_exc()
        
        return None
    
    @staticmethod
    def _analyze_radar_image(image_url: str) -> Optional[float]:
        """
        Download and analyze radar image to calculate storm intensity
        Saves image to disk for inspection
        """
        try:
            # Download the radar image
            print(f"   → Downloading radar image from: {image_url}")
            img_response = requests.get(image_url, timeout=10)
            if img_response.status_code != 200:
                print(f"   → Failed to download image: HTTP {img_response.status_code}")
                return None
            
            # Save original image for user inspection
            os.makedirs("radar_images", exist_ok=True)
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            original_path = f"radar_images/radar_{timestamp}_original.png"
            with open(original_path, "wb") as f:
                f.write(img_response.content)
            print(f"   → Saved original radar image: {original_path}")
            
            # Load image using PIL
            img = Image.open(BytesIO(img_response.content))
            
            # Convert to RGB if necessary
            if img.mode != 'RGB':
                img = img.convert('RGB')
            
            # Resize for faster processing if image is large
            original_size = (img.width, img.height)
            if img.width > 500 or img.height > 500:
                img = img.resize((500, 500), Image.Resampling.LANCZOS)
                print(f"   → Resized from {original_size} to (500, 500) for processing")
            
            # Get pixel data
            pixels = list(img.getdata())
            print(f"   → Analyzing {len(pixels)} pixels...")
            
            # Analyze pixel intensity for storm detection
            # Radar reflectivity typically uses color scales:
            # Blue/Green = light rain, Yellow/Orange = moderate, Red/Purple = severe
            
            high_intensity_count = 0
            moderate_intensity_count = 0
            total_pixels = len(pixels)
            
            for pixel in pixels:
                r, g, b = pixel
                
                # Calculate overall brightness
                brightness = (r + g + b) / 3
                
                # Detect high intensity (red/orange/purple pixels)
                if r > 200 and g < 150 and b < 150:  # Red-dominated
                    high_intensity_count += 1
                elif r > 180 and g > 100 and b < 100:  # Orange/yellow
                    moderate_intensity_count += 1
                elif brightness > 200:  # Very bright (could be heavy precipitation)
                    moderate_intensity_count += 1
            
            # Calculate intensity score (0-1)
            if total_pixels == 0:
                return 0.0
            
            # Weighted intensity calculation
            high_weight = 1.0
            moderate_weight = 0.5
            
            intensity_score = (
                (high_intensity_count * high_weight) + 
                (moderate_intensity_count * moderate_weight)
            ) / total_pixels
            
            # Normalize to 0-1 range and amplify for better sensitivity
            normalized_intensity = min(1.0, intensity_score * 10)
            
            print(f"   → High intensity pixels: {high_intensity_count} ({high_intensity_count/total_pixels:.2%})")
            print(f"   → Moderate intensity pixels: {moderate_intensity_count} ({moderate_intensity_count/total_pixels:.2%})")
            print(f"   → Calculated radar intensity: {normalized_intensity:.3f}")
            
            return normalized_intensity
            
        except Exception as e:
            print(f"   → Image processing error: {e}")
            return None
    
    @staticmethod
    def _analyze_radar_image_from_response(image_data: bytes, latitude: float, longitude: float, center_x: float = None, center_y: float = None, movement_data: dict = None) -> Optional[float]:
        """
        Analyze radar image from direct response data with map overlay and forecast visualization
        """
        try:
            # Save original image for user inspection
            os.makedirs("radar_images", exist_ok=True)
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            original_path = f"radar_images/radar_{timestamp}_direct.png"
            with open(original_path, "wb") as f:
                f.write(image_data)
            print(f"   → Saved direct radar image: {original_path}")
            
            # Load radar image
            radar_img = Image.open(BytesIO(image_data))
            print(f"   → Radar image size: {radar_img.size}, mode: {radar_img.mode}")
            if radar_img.mode != 'RGBA':
                radar_img = radar_img.convert('RGBA')
                print(f"   → Converted radar to RGBA")
            
            # Fetch CartoDB map tiles using multi-tile approach for better centering
            # Get 2x2 grid of tiles centered on the location
            zoom = 7  # Match radar zoom level
            tile_size = 256  # CartoDB has fixed 256x256 tiles
            
            # Use the same center coordinates as radar if provided, otherwise calculate
            if center_x is None or center_y is None:
                n = 2 ** zoom
                center_x = (longitude + 180) / 360 * n
                lat_rad = math.radians(latitude)
                center_y = (1 - math.asinh(math.tan(lat_rad)) / math.pi) / 2 * n
            
            # Get the 4 tiles around the center point
            tiles_to_fetch = []
            for dx in [-1, 0]:
                for dy in [-1, 0]:
                    x_tile = int(center_x) + dx
                    y_tile = int(center_y) + dy
                    tiles_to_fetch.append((x_tile, y_tile, dx, dy))
            
            print(f"   → Fetching {len(tiles_to_fetch)} map tiles for 2x2 grid")
            print(f"   → Center tile coordinates: ({int(center_x)}, {int(center_y)})")
            
            # Fetch all map tiles
            map_tiles = []
            for x_tile, y_tile, dx, dy in tiles_to_fetch:
                map_url = f"https://a.basemaps.cartocdn.com/light_all/{zoom}/{x_tile}/{y_tile}.png"
                print(f"   → Fetching map tile ({dx},{dy}): x={x_tile}, y={y_tile}")
                
                map_response = requests.get(map_url, timeout=10)
                if map_response.status_code == 200:
                    tile_img = Image.open(BytesIO(map_response.content))
                    if tile_img.mode != 'RGBA':
                        tile_img = tile_img.convert('RGBA')
                    map_tiles.append((tile_img, dx, dy))
                else:
                    print(f"   → Failed to fetch tile ({dx},{dy}): HTTP {map_response.status_code}")
            
            if not map_tiles:
                print(f"   → Failed to fetch any map tiles, using radar only")
                img = radar_img
            else:
                # Stitch map tiles together
                # Create a 2x2 grid (512x512)
                composite_map = Image.new('RGBA', (tile_size * 2, tile_size * 2))
                
                for tile_img, dx, dy in map_tiles:
                    # Calculate position in the grid
                    # dx=-1 is left, dx=0 is right
                    # dy=-1 is top, dy=0 is bottom
                    pos_x = (dx + 1) * tile_size
                    pos_y = (dy + 1) * tile_size
                    composite_map.paste(tile_img, (pos_x, pos_y))
                
                print(f"   → Stitched map size: {composite_map.size}")
                
                # Resize radar to match composite map
                if radar_img.size != composite_map.size:
                    print(f"   → Resizing radar from {radar_img.size} to {composite_map.size}")
                    radar_img = radar_img.resize(composite_map.size, Image.Resampling.LANCZOS)
                
                # Save map-only image (no radar overlay)
                map_only_path = f"radar_images/radar_{timestamp}_map_only.png"
                composite_map.save(map_only_path)
                print(f"   → Saved map-only image: {map_only_path}")
                
                # Save radar-only image (no map)
                radar_only_path = f"radar_images/radar_{timestamp}_radar_only.png"
                radar_img.save(radar_only_path)
                print(f"   → Saved radar-only image: {radar_only_path}")
                
                # Create composite with transparency (50% radar opacity)
                radar_transparent = radar_img.copy()
                radar_transparent.putalpha(128)  # 50% transparency
                composite_transparent = Image.alpha_composite(composite_map, radar_transparent)
                composite_transparent_path = f"radar_images/radar_{timestamp}_composite_transparent.png"
                composite_transparent.save(composite_transparent_path)
                print(f"   → Saved transparent composite: {composite_transparent_path}")
                
                print(f"   → Creating composite image (map + radar overlay)")
                composite = Image.alpha_composite(composite_map, radar_img)
                print(f"   → Composite size: {composite.size}")
                
                # Add red marker for user location
                # Calculate exact pixel position in the 2x2 grid
                tile_width = composite.width
                tile_height = composite.height
                
                # Calculate position relative to the top-left tile
                x_pixel = (center_x - int(center_x) + 1) * tile_size
                y_pixel = (center_y - int(center_y) + 1) * tile_size
                
                # Draw red circle marker
                from PIL import ImageDraw
                draw = ImageDraw.Draw(composite)
                marker_radius = 8
                draw.ellipse(
                    [x_pixel - marker_radius, y_pixel - marker_radius,
                     x_pixel + marker_radius, y_pixel + marker_radius],
                    fill=(255, 0, 0, 255),
                    outline=(255, 255, 255, 255),
                    width=2
                )
                
                # Add forecast visualization with movement arrows
                if movement_data and center_x is not None and center_y is not None:
                    from PIL import ImageDraw
                    draw = ImageDraw.Draw(composite)
                    
                    # Get storm movement data and centroids
                    avg_speed_x = movement_data.get('avg_speed_x', 0)
                    avg_speed_y = movement_data.get('avg_speed_y', 0)
                    forecast_1h = movement_data.get('forecast_1h', (0, 0))
                    storm_centroids = movement_data.get('storm_centroids', [])
                    
                    if storm_centroids:
                        # Get the most recent storm position
                        latest_centroid = storm_centroids[-1]
                        _, storm_x, storm_y, storm_intensity = latest_centroid
                        
                        # Calculate forecast positions
                        forecast_1h_x = storm_x + forecast_1h[0]
                        forecast_1h_y = storm_y + forecast_1h[1]
                        
                        forecast_5h = movement_data.get('forecast_5h', (0, 0))
                        forecast_5h_x = storm_x + forecast_5h[0]
                        forecast_5h_y = storm_y + forecast_5h[1]
                        
                        # Draw storm current position (blue circle)
                        storm_color = (0, 0, 255, 255)  # Blue
                        storm_radius = 10
                        draw.ellipse(
                            [storm_x - storm_radius, storm_y - storm_radius,
                             storm_x + storm_radius, storm_y + storm_radius],
                            fill=storm_color,
                            outline=(255, 255, 255, 255),
                            width=2
                        )
                        
                        # Draw 1h forecast position (green circle)
                        forecast_1h_color = (0, 255, 0, 255)  # Green
                        forecast_1h_radius = 8
                        draw.ellipse(
                            [forecast_1h_x - forecast_1h_radius, forecast_1h_y - forecast_1h_radius,
                             forecast_1h_x + forecast_1h_radius, forecast_1h_y + forecast_1h_radius],
                            fill=forecast_1h_color,
                            outline=(255, 255, 255, 255),
                            width=2
                        )
                        
                        # Draw 5h forecast position (purple circle)
                        forecast_5h_color = (128, 0, 128, 255)  # Purple
                        forecast_5h_radius = 6
                        draw.ellipse(
                            [forecast_5h_x - forecast_5h_radius, forecast_5h_y - forecast_5h_radius,
                             forecast_5h_x + forecast_5h_radius, forecast_5h_y + forecast_5h_radius],
                            fill=forecast_5h_color,
                            outline=(255, 255, 255, 255),
                            width=2
                        )
                        
                        # Draw arrow from storm to 1h forecast
                        arrow_1h_color = (255, 165, 0, 255)  # Orange
                        draw.line([storm_x, storm_y, forecast_1h_x, forecast_1h_y], fill=arrow_1h_color, width=3)
                        
                        # Draw arrow head at 1h position
                        arrow_size = 12
                        angle = math.atan2(forecast_1h[1], forecast_1h[0])
                        arrow_p1_x = forecast_1h_x - arrow_size * math.cos(angle - math.pi/6)
                        arrow_p1_y = forecast_1h_y - arrow_size * math.sin(angle - math.pi/6)
                        arrow_p2_x = forecast_1h_x - arrow_size * math.cos(angle + math.pi/6)
                        arrow_p2_y = forecast_1h_y - arrow_size * math.sin(angle + math.pi/6)
                        draw.polygon([forecast_1h_x, forecast_1h_y, arrow_p1_x, arrow_p1_y, arrow_p2_x, arrow_p2_y], fill=arrow_1h_color)
                        
                        # Draw dashed line from 1h to 5h forecast
                        arrow_5h_color = (128, 0, 128, 255)  # Purple
                        draw.line([forecast_1h_x, forecast_1h_y, forecast_5h_x, forecast_5h_y], fill=arrow_5h_color, width=2)
                        
                        # Add text labels
                        from PIL import ImageFont
                        try:
                            font = ImageFont.truetype("arial.ttf", 11)
                        except:
                            font = ImageFont.load_default()
                        
                        # Storm label
                        draw.text((storm_x + 12, storm_y - 15), "Storm", fill=storm_color, font=font)
                        # 1h forecast label
                        draw.text((forecast_1h_x + 12, forecast_1h_y - 15), "1h", fill=forecast_1h_color, font=font)
                        # 5h forecast label
                        draw.text((forecast_5h_x + 12, forecast_5h_y - 15), "5h", fill=forecast_5h_color, font=font)
                        
                        print(f"   → Added forecast visualization: storm→1h→5h")
                        print(f"   → Storm position: ({storm_x:.1f}, {storm_y:.1f})")
                        print(f"   → 1h forecast: ({forecast_1h_x:.1f}, {forecast_1h_y:.1f})")
                        print(f"   → 5h forecast: ({forecast_5h_x:.1f}, {forecast_5h_y:.1f})")
                
                # Save composite image
                composite_path = f"radar_images/radar_{timestamp}_composite.png"
                composite.save(composite_path)
                print(f"   → Saved composite radar+map image: {composite_path}")
                
                # Process the composite for intensity analysis
                img = composite
            
            # Resize for faster processing if image is large
            if img.width > 500 or img.height > 500:
                img = img.resize((500, 500), Image.Resampling.LANCZOS)
            
            # Get pixel data using new API
            pixels = list(img.get_flattened_data())
            
            # Analyze pixel intensity (same logic as above)
            high_intensity_count = 0
            moderate_intensity_count = 0
            total_pixels = len(pixels)
            
            for pixel in pixels:
                # RGBA has 4 values, take RGB
                r, g, b, a = pixel[:4]
                brightness = (r + g + b) / 3
                
                if r > 200 and g < 150 and b < 150:
                    high_intensity_count += 1
                elif r > 180 and g > 100 and b < 100:
                    moderate_intensity_count += 1
                elif brightness > 200:
                    moderate_intensity_count += 1
            
            if total_pixels == 0:
                return 0.0
            
            intensity_score = (
                (high_intensity_count * 1.0) + 
                (moderate_intensity_count * 0.5)
            ) / total_pixels
            
            normalized_intensity = min(1.0, intensity_score * 10)
            
            print(f"   → High intensity pixels: {high_intensity_count}")
            print(f"   → Moderate intensity pixels: {moderate_intensity_count}")
            print(f"   → Calculated radar intensity: {normalized_intensity:.3f}")
            
            return normalized_intensity
            
        except Exception as e:
            print(f"   → Direct image processing error: {e}")
            import traceback
            traceback.print_exc()
            return None


class MLStormPredictor:
    """Simple ML-based storm prediction using ensemble methods"""
    
    def __init__(self):
        self.feature_weights = {
            "precipitation_prob": 0.3,
            "wind_speed": 0.25,
            "precipitation": 0.2,
            "humidity": 0.1,
            "pressure_drop": 0.1,
            "cloud_cover": 0.05
        }
        
        # Trained thresholds (simplified for demonstration)
        self.thresholds = {
            "low_risk": 0.3,
            "medium_risk": 0.5,
            "high_risk": 0.7,
            "extreme_risk": 0.85
        }
    
    def extract_features(self, weather: WeatherResponse) -> dict:
        """Extract features for ML prediction with null checking"""
        # Check current weather data
        current = weather.current_weather
        
        features = {
            "precipitation_prob": 0.0,
            "wind_speed": current.wind_speed if current.wind_speed is not None else 0.0,
            "precipitation": 0.0,
            "humidity": current.relative_humidity if current.relative_humidity is not None else 50,
            "pressure_drop": 0.0,
            "cloud_cover": current.cloud_cover if current.cloud_cover is not None else 0
        }
        
        if weather.hourly:
            precip_prob = weather.hourly.precipitation_probability or [0]
            precip = weather.hourly.precipitation or [0]
            pressure = weather.hourly.pressure_msl or []
            
            features["precipitation_prob"] = (precip_prob[0] if precip_prob else 0) / 100.0
            features["precipitation"] = precip[0] if precip else 0.0
            
            # Calculate pressure drop
            if pressure and len(pressure) > 1 and all(p is not None for p in pressure):
                features["pressure_drop"] = max(0, pressure[0] - pressure[-1])
        
        return features
    
    def predict_storm_probability(self, features: dict) -> float:
        """Predict storm probability using weighted features"""
        weighted_sum = 0.0
        total_weight = 0.0
        
        for feature, weight in self.feature_weights.items():
            if feature in features:
                value = features[feature]
                # Normalize values to 0-1 range
                if feature == "wind_speed":
                    normalized = min(1.0, value / 100.0)  # Max 100 km/h
                elif feature == "precipitation":
                    normalized = min(1.0, value / 50.0)  # Max 50 mm
                elif feature == "humidity":
                    normalized = value / 100.0  # 0-100%
                elif feature == "pressure_drop":
                    normalized = min(1.0, value / 20.0)  # Max 20 hPa
                elif feature == "cloud_cover":
                    normalized = value / 100.0  # 0-100%
                else:
                    normalized = value  # Already normalized
                
                contribution = normalized * weight
                weighted_sum += contribution
                total_weight += weight
        
        final_probability = weighted_sum / total_weight if total_weight > 0 else 0.0
        print(f"   → Final ML Probability: {final_probability:.3f}")
        
        return final_probability
    
    def _get_max_value(self, feature: str) -> float:
        """Get maximum expected value for normalization"""
        max_values = {
            "precipitation_prob": 1.0,
            "wind_speed": 100.0,
            "precipitation": 50.0,
            "humidity": 100.0,
            "pressure_drop": 20.0,
            "cloud_cover": 100.0
        }
        return max_values.get(feature, 1.0)
    
    def calculate_confidence_interval(self, probability: float, sample_size: int = 6) -> Tuple[float, float]:
        """Calculate confidence interval using Wilson score interval"""
        if sample_size < 1:
            return (0.0, 1.0)
        
        z = 1.96  # 95% confidence
        n = sample_size
        p = probability
        
        # Wilson score interval
        denominator = 1 + (z**2 / n)
        center = (p + (z**2 / (2 * n))) / denominator
        margin = z * math.sqrt((p * (1 - p) / n) + (z**2 / (4 * n**2))) / denominator
        
        lower = max(0.0, center - margin)
        upper = min(1.0, center + margin)
        
        return (lower, upper)


class WeatherRepository:
    """Handles weather API calls and advanced storm risk analysis"""
    
    BASE_URL = "https://api.open-meteo.com/"
    
    def __init__(self):
        self.ml_predictor = MLStormPredictor()
        self.trend_analyzer = TrendAnalyzer()
        self.radar_analyzer = RadarAnalyzer()
    
    def get_current_weather(self, latitude: float, longitude: float) -> WeatherResponse:
        """Fetch current weather from Open-Meteo API with enhanced parameters"""
        params = {
            "latitude": latitude,
            "longitude": longitude,
            "current_weather": "true",
            "current": "temperature_2m,relative_humidity_2m,pressure_msl,cloud_cover",
            "hourly": "temperature_2m,precipitation_probability,precipitation,windspeed_10m,windgusts_10m,relative_humidity_2m,pressure_msl,cloud_cover",
            "daily": "weathercode,temperature_2m_max,temperature_2m_min,precipitation_probability_max,windspeed_10m_max,precipitation_sum",
            "timezone": "auto",
            "forecast_days": 7
        }
        
        response = requests.get(f"{self.BASE_URL}v1/forecast", params=params)
        response.raise_for_status()
        data = response.json()
        
        # Parse current weather
        current = data["current_weather"]
        current_data = data.get("current", {})
        
        current_weather = CurrentWeather(
            temperature=current["temperature"],
            wind_speed=current["windspeed"],
            wind_direction=current["winddirection"],
            weather_code=current["weathercode"],
            time=current["time"],
            relative_humidity=current_data.get("relative_humidity_2m"),
            pressure_msl=current_data.get("pressure_msl"),
            cloud_cover=current_data.get("cloud_cover")
        )
        
        # Parse hourly data
        hourly_data = None
        if "hourly" in data:
            hourly = data["hourly"]
            hourly_data = HourlyData(
                time=hourly["time"],
                temperature=hourly.get("temperature_2m"),
                precipitation_probability=hourly.get("precipitation_probability"),
                precipitation=hourly.get("precipitation"),
                wind_speed=hourly.get("windspeed_10m"),
                wind_gusts=hourly.get("windgusts_10m"),
                relative_humidity=hourly.get("relative_humidity_2m"),
                pressure_msl=hourly.get("pressure_msl"),
                cloud_cover=hourly.get("cloud_cover")
            )
        
        # Parse daily data
        daily_data = None
        if "daily" in data:
            daily = data["daily"]
            daily_data = DailyData(
                time=daily["time"],
                weather_code=daily["weathercode"],
                temperature_max=daily["temperature_2m_max"],
                temperature_min=daily["temperature_2m_min"],
                precipitation_probability_max=daily.get("precipitation_probability_max"),
                wind_speed_max=daily.get("windspeed_10m_max"),
                precipitation_sum=daily.get("precipitation_sum")
            )
        
        return WeatherResponse(
            latitude=data["latitude"],
            longitude=data["longitude"],
            current_weather=current_weather,
            hourly=hourly_data,
            daily=daily_data
        )
    
    def analyze_storm_risk(self, weather_response: WeatherResponse) -> StormRisk:
        """Advanced storm risk analysis with ML, radar, and trend analysis"""
        current_condition = get_weather_condition(weather_response.current_weather.weather_code)
        is_currently_stormy = current_condition.is_stormy
        
        # Get dynamic thresholds
        current_month = datetime.now().month
        season = DynamicThresholds.get_season(current_month)
        thresholds = DynamicThresholds.get_thresholds(weather_response.latitude, season)
        
        # ML-based prediction
        features = self.ml_predictor.extract_features(weather_response)
        ml_probability = self.ml_predictor.predict_storm_probability(features)
        confidence_interval = self.ml_predictor.calculate_confidence_interval(ml_probability)
        
        # Radar analysis
        radar_intensity = self.radar_analyzer.get_radar_intensity(
            weather_response.latitude, 
            weather_response.longitude
        )
        
        # Check hourly data for approaching storms
        hourly_data = weather_response.hourly
        if hourly_data:
            next_6_hours = (hourly_data.precipitation_probability or [])[:6]
            high_precipitation_hours = sum(1 for p in next_6_hours if p > thresholds["high_precip_prob"])
            moderate_precipitation_hours = sum(1 for p in next_6_hours if p > thresholds["moderate_precip_prob"])
            max_wind_speed = max((hourly_data.wind_speed or [])[:6]) if hourly_data.wind_speed else 0.0
            max_wind_gusts = max((hourly_data.wind_gusts or [])[:6]) if hourly_data.wind_gusts else 0.0
            current_precipitation = (hourly_data.precipitation or [0.0])[0] if hourly_data.precipitation else 0.0
            
            # Trend analysis
            precip_data = (hourly_data.precipitation or [])[:6]
            wind_data = (hourly_data.wind_speed or [])[:6]
            
            print(f"   → Trend Analysis Data:")
            print(f"      - Precipitation data (next 6h): {precip_data}")
            print(f"      - Wind speed data (next 6h): {wind_data}")
            
            precip_trend = self.trend_analyzer.analyze_trend(precip_data)
            wind_trend = self.trend_analyzer.analyze_trend(wind_data)
            
            print(f"      - Precipitation trend: {precip_trend}")
            print(f"      - Wind trend: {wind_trend}")
            
            # Enhanced storm detection with radar forecasting
            storm_movement_data = {}
            radii_analysis = {}
            
            if radar_intensity:
                # Fetch historical frames for movement analysis
                latitude = weather_response.latitude
                longitude = weather_response.longitude
                historical_frames = RadarAnalyzer.get_historical_radar_frames(latitude, longitude, num_frames=10)
                
                if historical_frames:
                    # Calculate storm movement
                    storm_movement_data = RadarAnalyzer.calculate_storm_movement(historical_frames)
                    
                    if storm_movement_data:
                        # Analyze storm risk at different radii
                        radii_analysis = RadarAnalyzer.analyze_storm_at_radii(latitude, longitude, storm_movement_data)
            
            # Enhanced storm detection logic with ML integration and approach detection
            # Prioritize radar-based approach detection when available
            if radii_analysis and radii_analysis.get('current', {}).get('is_approaching', False):
                is_storm_approaching = True
                approach_reason = "radar_movement"
            else:
                is_storm_approaching = (
                    is_currently_stormy or
                    high_precipitation_hours >= 2 or
                    (moderate_precipitation_hours >= 3 and max_wind_speed > thresholds["moderate_wind_speed"]) or
                    max_wind_speed > thresholds["high_wind_speed"] or
                    max_wind_gusts > thresholds["high_wind_speed"] * 1.3 or
                    current_precipitation > thresholds["heavy_precipitation"] or
                    ml_probability > thresholds["medium_risk"] or
                    (radar_intensity and radar_intensity > 0.6)
                )
                approach_reason = "weather_conditions"
            
            # Combine ML probability with rule-based probability
            rule_based_probability = self._calculate_rule_based_probability(
                is_currently_stormy, high_precipitation_hours, moderate_precipitation_hours,
                max_wind_speed, current_precipitation, thresholds
            )
            
            # Weighted combination (70% ML, 30% rules)
            storm_probability = (ml_probability * 0.7) + (rule_based_probability * 0.3)
            
            # Adjust based on trends
            if precip_trend == "increasing" and wind_trend == "increasing":
                storm_probability = min(1.0, storm_probability + 0.15)
            elif precip_trend == "decreasing" and wind_trend == "decreasing":
                storm_probability = max(0.0, storm_probability - 0.1)
            
            # Determine warning level
            warning_level = self._determine_warning_level(storm_probability, thresholds)
            
            # Estimate time to storm
            estimated_time_to_storm = self._estimate_time_to_storm(
                is_storm_approaching, is_currently_stormy, next_6_hours, thresholds, radii_analysis
            )
            
            # Trend analysis summary
            trend_summary = f"Precipitation: {precip_trend}, Wind: {wind_trend}"
            
            # Add forecast data to trend summary
            if storm_movement_data:
                forecast_1h = storm_movement_data.get('forecast_1h', (0, 0))
                forecast_5h = storm_movement_data.get('forecast_5h', (0, 0))
                trend_summary += f", Storm movement: ({forecast_1h[0]:.1f}, {forecast_1h[1]:.1f}) px in 1h"
            
            return StormRisk(
                is_currently_stormy=is_currently_stormy,
                is_storm_approaching=is_storm_approaching,
                storm_probability=storm_probability,
                confidence_interval=confidence_interval,
                estimated_time_to_storm=estimated_time_to_storm,
                current_condition=current_condition.description,
                wind_speed=weather_response.current_weather.wind_speed,
                precipitation_probability=(hourly_data.precipitation_probability or [0])[0] if hourly_data.precipitation_probability else 0,
                current_precipitation=current_precipitation,
                max_wind_speed_next_6_hours=max_wind_speed,
                warning_level=warning_level,
                trend_analysis=trend_summary,
                radar_intensity=radar_intensity
            )
        else:
            return StormRisk(
                is_currently_stormy=is_currently_stormy,
                is_storm_approaching=False,
                storm_probability=ml_probability,
                confidence_interval=confidence_interval,
                estimated_time_to_storm=-1,
                current_condition=current_condition.description,
                wind_speed=weather_response.current_weather.wind_speed,
                precipitation_probability=0,
                current_precipitation=0.0,
                max_wind_speed_next_6_hours=weather_response.current_weather.wind_speed,
                warning_level=WarningLevel.NONE,
                trend_analysis="insufficient_data",
                radar_intensity=radar_intensity
            )
    
    def _calculate_rule_based_probability(self, is_currently_stormy: bool, 
                                          high_precip_hours: int, moderate_precip_hours: int,
                                          max_wind: float, current_precip: float, 
                                          thresholds: dict) -> float:
        """Calculate rule-based storm probability"""
        if is_currently_stormy:
            return 1.0
        elif high_precip_hours >= 3:
            return 0.9
        elif high_precip_hours >= 2:
            return 0.8
        elif moderate_precip_hours >= 3 and max_wind > thresholds["moderate_wind_speed"]:
            return 0.7
        elif max_wind > thresholds["high_wind_speed"]:
            return 0.6
        elif current_precip > thresholds["heavy_precipitation"]:
            return 0.5
        else:
            return 0.2
    
    def _determine_warning_level(self, probability: float, thresholds: dict) -> WarningLevel:
        """Determine warning level based on probability"""
        if probability >= thresholds["red_warning_prob"]:
            return WarningLevel.RED
        elif probability >= thresholds["orange_warning_prob"]:
            return WarningLevel.ORANGE
        elif probability >= thresholds["yellow_warning_prob"]:
            return WarningLevel.YELLOW
        else:
            return WarningLevel.NONE
    
    def _estimate_time_to_storm(self, is_approaching: bool, is_current: bool,
                               next_6_hours: List[int], thresholds: dict, 
                               radii_analysis: dict = None) -> int:
        """Estimate time to storm in minutes, prioritizing radar-based estimates"""
        if not is_approaching or is_current:
            return -1
        
        # Use radar-based time to impact if available
        if radii_analysis and radii_analysis.get('current', {}).get('time_to_impact'):
            time_to_impact = radii_analysis['current']['time_to_impact']
            if time_to_impact != float('inf'):
                return int(time_to_impact)
        
        # Fallback to precipitation-based estimation
        try:
            first_high_precip_index = next((i for i, p in enumerate(next_6_hours) 
                                         if p > thresholds["high_precip_prob"]), -1)
            first_moderate_precip_index = next((i for i, p in enumerate(next_6_hours) 
                                              if p > thresholds["moderate_precip_prob"]), -1)
            
            if first_high_precip_index >= 0:
                return first_high_precip_index * 15  # 15-minute intervals
            elif first_moderate_precip_index >= 0:
                return first_moderate_precip_index * 15
            else:
                return 60  # Default to 1 hour
        except:
            return 60


def print_weather_report(weather: WeatherResponse, storm_risk: StormRisk):
    """Print a comprehensive weather report with all advanced features"""
    print("\n" + "="*70)
    print(f"🌩️  ADVANCED STORM ALERT SYSTEM")
    print(f"Location: {weather.latitude:.2f}°, {weather.longitude:.2f}°")
    print("="*70)
    
    # Current Conditions
    print(f"\n📊 CURRENT CONDITIONS:")
    print(f"  Temperature: {weather.current_weather.temperature:.1f}°C")
    print(f"  Wind Speed: {weather.current_weather.wind_speed:.1f} km/h")
    print(f"  Wind Direction: {weather.current_weather.wind_direction:.0f}°")
    print(f"  Condition: {storm_risk.current_condition}")
    print(f"  Humidity: {weather.current_weather.relative_humidity:.0f}%" if weather.current_weather.relative_humidity else "  Humidity: N/A")
    print(f"  Pressure: {weather.current_weather.pressure_msl:.0f} hPa" if weather.current_weather.pressure_msl else "  Pressure: N/A")
    print(f"  Cloud Cover: {weather.current_weather.cloud_cover:.0f}%" if weather.current_weather.cloud_cover else "  Cloud Cover: N/A")
    print(f"  Time: {weather.current_weather.time}")
    
    # Storm Risk Analysis
    print(f"\n⚡ STORM RISK ANALYSIS:")
    print(f"  Currently Stormy: {storm_risk.is_currently_stormy}")
    print(f"  Storm Approaching: {storm_risk.is_storm_approaching}")
    print(f"  Storm Probability: {storm_risk.storm_probability:.1%}")
    print(f"  Confidence Interval: {storm_risk.confidence_interval[0]:.1%} - {storm_risk.confidence_interval[1]:.1%}")
    print(f"  Current Precipitation: {storm_risk.current_precipitation:.1f} mm")
    print(f"  Precipitation Probability: {storm_risk.precipitation_probability}%")
    print(f"  Max Wind Speed (next 6h): {storm_risk.max_wind_speed_next_6_hours:.1f} km/h")
    print(f"  Trend Analysis: {storm_risk.trend_analysis}")
    print(f"  Radar Intensity: {storm_risk.radar_intensity:.2f}" if storm_risk.radar_intensity else "  Radar Intensity: N/A")
    
    if storm_risk.estimated_time_to_storm >= 0:
        print(f"  Estimated Time to Storm: {storm_risk.estimated_time_to_storm} minutes")
    else:
        print(f"  Estimated Time to Storm: N/A")
    
    # Warning Level
    print(f"\n🚨 WARNING LEVEL: {storm_risk.warning_level.value.upper()}")
    warning_descriptions = {
        WarningLevel.NONE: "No significant weather risk",
        WarningLevel.YELLOW: "Be aware - Potential weather hazards",
        WarningLevel.ORANGE: "Be prepared - Severe weather likely",
        WarningLevel.RED: "Take action - Extreme weather imminent"
    }
    print(f"  {warning_descriptions[storm_risk.warning_level]}")
    
    print("\n" + "="*70)
    
    # Alert message
    if storm_risk.warning_level == WarningLevel.RED:
        print("🔴 RED ALERT - EXTREME WEATHER - TAKE IMMEDIATE ACTION!")
    elif storm_risk.warning_level == WarningLevel.ORANGE:
        print("🟠 ORANGE ALERT - SEVERE WEATHER - BE PREPARED!")
    elif storm_risk.warning_level == WarningLevel.YELLOW:
        print("🟡 YELLOW ALERT - WEATHER HAZARDS - BE AWARE")
    else:
        print("✅ No significant weather risk at this time")
    
    print("="*70 + "\n")


def get_location_from_ip() -> Optional[Tuple[float, float]]:
    """Get approximate location from IP address with multiple fallbacks"""
    services = [
        ("https://ipapi.co/json/", "ipapi"),
        ("https://ipinfo.io/json", "ipinfo"),
        ("https://freegeoip.app/json/", "freegeoip")
    ]
    
    for service_url, service_name in services:
        try:
            print(f"   → Trying {service_name}: {service_url}")
            response = requests.get(service_url, timeout=5)
            print(f"   → Response status: {response.status_code}")
            
            if response.status_code == 200:
                data = response.json()
                print(f"   → Response data keys: {list(data.keys())}")
                
                # Try different field names for different services
                lat = data.get("latitude") or data.get("lat")
                lon = data.get("longitude") or data.get("lon") or data.get("long")
                city = data.get("city") or data.get("region_name")
                
                # Handle ipinfo's "loc" field (format: "lat,lon")
                if not lat or not lon:
                    loc = data.get("loc")
                    if loc and "," in loc:
                        lat, lon = loc.split(",")
                        lat = float(lat)
                        lon = float(lon)
                        print(f"   → Parsed from 'loc' field: {lat}, {lon}")
                
                print(f"   → Extracted - Lat: {lat}, Lon: {lon}, City: {city}")
                
                if lat and lon:
                    city_name = city or "Unknown"
                    print(f"📍 Auto-detected location: {city_name} ({lat:.4f}, {lon:.4f})")
                    return (float(lat), float(lon))
                else:
                    print(f"   → Service returned incomplete location data")
            else:
                print(f"   → HTTP error: {response.status_code}")
        except Exception as e:
            print(f"   → Service failed: {e}")
            continue
    
    print("⚠️  All location services failed")
    print("💡 Possible causes: VPN, corporate firewall, API rate limiting")
    print("💡 You can manually set location by editing the script")
    return None


def main():
    """Main entry point for testing"""
    import sys
    import io
    # Set UTF-8 encoding for Windows console
    if sys.platform == 'win32':
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
        sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
    
    print("🌩️  Storm Alert - Top-of-Class Python Version")
    print("Advanced ML-powered storm detection system")
    print("="*70)
    
    # Try auto-detect location first
    location = get_location_from_ip()
    if location:
        latitude, longitude = location
        print(f"Using auto-detected location for weather analysis\n")
    else:
        # Default to Oslo, Norway (same as Kotlin version)
        latitude = 59.91
        longitude = 10.75
        print(f"Using default location (Oslo, Norway)\n")
    
    print(f"\n📡 Fetching advanced weather data for {latitude}, {longitude}...")
    print("   Step 1: Fetching weather data from Open-Meteo API...")
    
    try:
        repository = WeatherRepository()
        print("   Step 2: Processing weather data...")
        weather = repository.get_current_weather(latitude, longitude)
        print("   Step 3: Running ML prediction analysis...")
        print("   Step 4: Analyzing radar imagery...")
        print("   Step 5: Computing trend analysis...")
        print("   Step 6: Calculating confidence intervals...")
        storm_risk = repository.analyze_storm_risk(weather)
        print("   Step 7: Generating comprehensive report...")
        
        print_weather_report(weather, storm_risk)
        
    except requests.RequestException as e:
        print(f"❌ Error fetching weather data: {e}")
    except Exception as e:
        print(f"❌ Unexpected error: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    main()
