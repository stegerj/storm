"""Storm movement and risk analysis service"""
import math
import numpy as np
from typing import Optional, Dict, Any, List, Tuple
from datetime import datetime
import structlog
from PIL import Image, ImageDraw
from io import BytesIO
import httpx
from scipy.ndimage import label
from scipy.spatial.distance import cdist

from app.config import settings
from app.models import (
    StormCentroid, MovementVector, ForecastData, StormForecast,
    RiskAnalysis, MultiRadiusAnalysis, RiskLevel
)

logger = structlog.get_logger()


class StormMovementAnalyzer:
    """Analyze storm movement from historical radar frames"""
    
    def __init__(self):
        pass
    
    async def fetch_historical_frames(
        self, 
        host: str, 
        frames: List[dict], 
        latitude: float, 
        longitude: float
    ) -> List[Tuple[int, Image.Image]]:
        """Fetch historical radar frames for storm movement analysis"""
        zoom = settings.radar_zoom_level
        tile_size = settings.radar_tile_size
        n = 2 ** zoom
        center_x = int(((longitude + 180) / 360 * n))
        lat_rad = math.radians(latitude)
        center_y = int(((1 - math.asinh(math.tan(lat_rad)) / math.pi) / 2 * n))
        
        historical_frames = []
        
        for frame in frames:
            frame_time = frame.get("time")
            frame_path = frame.get("path")
            
            if not frame_time or not frame_path:
                continue
            
            # Fetch 2x2 grid for each historical frame (for efficiency)
            # Tiles at offsets (-1,-1), (-1,0), (0,-1), (0,0)
            tiles_to_fetch = []
            for dx in [-1, 0]:
                for dy in [-1, 0]:
                    x_tile = center_x + dx
                    y_tile = center_y + dy
                    tiles_to_fetch.append((x_tile, y_tile, dx, dy))
            
            frame_tiles = []
            for x_tile, y_tile, dx, dy in tiles_to_fetch:
                tile_url = f"{host}{frame_path}/{tile_size}/{zoom}/{x_tile}/{y_tile}/0/1_0.png"
                try:
                    async with httpx.AsyncClient() as client:
                        response = await client.get(tile_url, timeout=10)
                        if response.status_code == 200:
                            tile_img = Image.open(BytesIO(response.content))
                            if tile_img.mode != 'RGBA':
                                tile_img = tile_img.convert('RGBA')
                            frame_tiles.append((tile_img, dx, dy))
                except Exception as e:
                    logger.warning("tile_fetch_failed", x=x_tile, y=y_tile, error=str(e))
            
            if frame_tiles:
                # Composite the 2x2 grid into a single image
                # The grid is 2x2 tiles, so composite is 512x512
                composite = Image.new('RGBA', (tile_size * 2, tile_size * 2), (0, 0, 0, 0))
                for tile_img, dx, dy in frame_tiles:
                    x_pos = (dx + 1) * tile_size
                    y_pos = (dy + 1) * tile_size
                    composite.paste(tile_img, (x_pos, y_pos), tile_img)
                
                historical_frames.append((frame_time, composite))
                logger.info("historical_frame_composited", time=frame_time, tiles=len(frame_tiles))
            else:
                logger.warning("no_tiles_for_frame", time=frame_time)
        
        logger.info("historical_frames_fetched", count=len(historical_frames))
        return historical_frames
    
    def analyze_movement(
        self, 
        historical_frames: List[Tuple[int, Image.Image]]
    ) -> Optional[Dict[str, Any]]:
        """Analyze storm movement from historical radar frames"""
        logger.info("analyze_movement_called", frames_count=len(historical_frames))
        
        if len(historical_frames) < 1:
            logger.warning("not_enough_frames_for_analysis", count=len(historical_frames))
            return None
        
        try:
            logger.info("starting_analysis", total_frames=len(historical_frames))
            
            # Step 1: Save all historical radar images (pure radar, no map)
            for i, (timestamp, frame_img) in enumerate(historical_frames):
                logger.info("saving_radar_only", frame_index=i, timestamp=timestamp)
                self._save_radar_only((timestamp, frame_img), f"frame_{i}")
            
            # Step 2: Extract rainy areas from all frames
            logger.info("extracting_centroids")
            all_storms = self._extract_centroids(historical_frames)
            logger.info("rainy_areas_extracted", count=len(all_storms))
            
            # Step 3: Save historical images with rainy area overlays
            for i, (timestamp, frame_img) in enumerate(historical_frames):
                frame_storms = [s for s in all_storms if s[0] == timestamp]
                logger.info("saving_radar_overlay", frame_index=i, storms_count=len(frame_storms))
                self._save_radar_with_overlay((timestamp, frame_img), frame_storms, f"frame_{i}")
            
            # Step 4: Track movement if we have multiple frames
            if len(historical_frames) >= 2:
                logger.info("tracking_storms")
                storm_tracks = self._track_storms_across_frames(all_storms)
                logger.info("storm_tracks_identified", count=len(storm_tracks))
                
                # Step 5: Calculate movement vectors
                movements = self._calculate_movement_vectors(storm_tracks)
                logger.info("movement_vectors_calculated", count=len(movements))
                
                # Step 6: Save historical images with movement arrows
                for i, (timestamp, frame_img) in enumerate(historical_frames):
                    frame_storms = [s for s in all_storms if s[0] == timestamp]
                    frame_movements = [m for m in movements if m['timestamp'] == timestamp]
                    logger.info("saving_radar_movement", frame_index=i, movements_count=len(frame_movements))
                    self._save_radar_with_movement((timestamp, frame_img), frame_storms, frame_movements, f"frame_{i}")
            
            logger.info("analysis_complete")
            
            return {
                'storm_forecasts': [],
                'current_storms': [s for s in all_storms if s[0] == historical_frames[-1][0]],
                'all_storms': all_storms,
                'avg_speed_x': 0.0,
                'avg_speed_y': 0.0,
                'forecast_1h': (0.0, 0.0),
                'forecast_5h': (0.0, 0.0),
                'storm_centroids': [],
                'movements': [],
                'acceleration': (0.0, 0.0)
            }
            
        except Exception as e:
            logger.error("analysis_failed", error=str(e), exc_info=True)
            return None
    
    def _calculate_movement_vectors(self, storm_tracks: Dict[int, List[Tuple]]) -> List[Dict]:
        """Calculate movement vectors for all storm tracks"""
        movements = []
        
        for track_id, track in storm_tracks.items():
            for i in range(1, len(track)):
                prev_time, prev_x, prev_y, prev_intensity, prev_count = track[i-1]
                curr_time, curr_x, curr_y, curr_intensity, curr_count = track[i]
                
                time_diff = (curr_time - prev_time) / 60.0  # Convert to minutes
                if time_diff > 0:
                    dx = curr_x - prev_x
                    dy = curr_y - prev_y
                    speed_x = dx / time_diff
                    speed_y = dy / time_diff
                    
                    movements.append({
                        'timestamp': curr_time,
                        'track_id': track_id,
                        'x': curr_x,
                        'y': curr_y,
                        'dx': dx,
                        'dy': dy,
                        'speed_x': speed_x,
                        'speed_y': speed_y,
                        'intensity': curr_intensity
                    })
        
        return movements
    
    @staticmethod
    def _extract_centroids(frames: List[Tuple[int, Image.Image]]) -> List[Tuple]:
        """Extract storm centroids from radar frames"""
        all_storms = []
        
        for timestamp, frame_img in frames:
            # Convert to RGBA if needed
            if frame_img.mode != 'RGBA':
                frame_img = frame_img.convert('RGBA')
            
            # Convert to numpy array for analysis
            pixels = np.array(frame_img)
            
            # Detect ALL precipitation pixels (rain, storm, etc.)
            # RainViewer uses color gradients: green (light rain) -> yellow (moderate) -> red (heavy)
            # We'll detect any pixel that's not transparent/black/white background
            # Check if pixel has significant color (not transparent background)
            alpha_channel = pixels[:, :, 3] if pixels.shape[2] == 4 else np.ones((pixels.shape[0], pixels.shape[1])) * 255
            
            # Detect any colored pixel (precipitation) - not transparent or grayscale
            # Precipitation pixels have alpha > 50 and are not grayscale
            has_color = (
                (alpha_channel > 50) &  # Not transparent
                (np.abs(pixels[:, :, 0] - pixels[:, :, 1]) > 20) |  # R != G
                (np.abs(pixels[:, :, 0] - pixels[:, :, 2]) > 20) |  # R != B
                (np.abs(pixels[:, :, 1] - pixels[:, :, 2]) > 20)    # G != B
            )
            
            # Also detect pixels with high color intensity (colored precipitation)
            color_intensity = (pixels[:, :, 0] + pixels[:, :, 1] + pixels[:, :, 2]) / 3
            has_precipitation = has_color & (color_intensity > 30)
            
            storm_mask = has_precipitation
            
            if np.any(storm_mask):
                # Use connected component labeling to identify individual storm cells
                labeled_array, num_features = label(storm_mask)
                
                for feature_id in range(1, num_features + 1):
                    cell_mask = (labeled_array == feature_id)
                    y_coords, x_coords = np.where(cell_mask)
                    
                    if len(y_coords) > 5:  # Minimum size threshold
                        avg_x = float(np.mean(x_coords))
                        avg_y = float(np.mean(y_coords))
                        pixel_count = int(np.sum(cell_mask))
                        
                        # Determine intensity based on average color
                        avg_r = np.mean(pixels[cell_mask, 0])
                        avg_g = np.mean(pixels[cell_mask, 1])
                        avg_b = np.mean(pixels[cell_mask, 2])
                        
                        # Classify intensity based on color
                        if avg_r > 200 and avg_g < 150 and avg_b < 150:
                            intensity = "high"  # Red - heavy rain
                        elif avg_r > 200 and avg_g > 200 and avg_b < 100:
                            intensity = "medium"  # Yellow - moderate rain
                        elif avg_g > 150 and avg_r < 200:
                            intensity = "light"  # Green - light rain
                        else:
                            intensity = "unknown"
                        
                        all_storms.append((timestamp, feature_id, avg_x, avg_y, intensity, pixel_count))
                        logger.info("storm_cell_detected", time=timestamp, id=feature_id, 
                                   x=avg_x, y=avg_y, intensity=intensity, pixels=pixel_count)
                
                logger.info("storm_cells_detected", time=timestamp, count=num_features)
            else:
                logger.info("no_storm_cells_in_frame", time=timestamp)
        
        logger.info("total_storm_cells_extracted", count=len(all_storms))
        return all_storms
    
    @staticmethod
    def _save_radar_with_overlay(frame: Tuple[int, Image.Image], storms: List[Tuple], label: str):
        """Save radar image with storm circles overlay (radar-only, no map)"""
        try:
            timestamp, frame_img = frame
            
            # Create a copy to draw on
            img_copy = frame_img.copy()
            draw = ImageDraw.Draw(img_copy)
            
            # Draw circles for each storm
            for storm_timestamp, storm_id, storm_x, storm_y, intensity, pixel_count in storms:
                # Choose color based on intensity
                if intensity == "high":
                    circle_color = (255, 0, 0)  # Red for high intensity
                else:
                    circle_color = (255, 255, 0)  # Yellow for medium intensity
                
                # Draw circle around storm area
                radius = 15 + min(pixel_count // 10, 30)
                draw.ellipse(
                    [storm_x - radius, storm_y - radius, storm_x + radius, storm_y + radius],
                    outline=circle_color,
                    width=3
                )
                
                # Draw a small dot at the storm center
                draw.ellipse(
                    [storm_x - 3, storm_y - 3, storm_x + 3, storm_y + 3],
                    fill=circle_color
                )
            
            # Save to radar_images directory
            import os
            radar_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "radar_images")
            os.makedirs(radar_dir, exist_ok=True)
            from datetime import datetime
            timestamp_str = datetime.fromtimestamp(timestamp).strftime("%Y%m%d_%H%M%S")
            filename = f"radar_{label}_{timestamp_str}.png"
            filepath = os.path.join(radar_dir, filename)
            img_copy.save(filepath)
            logger.info("radar_overlay_saved", label=label, filepath=filepath, storms_count=len(storms))
            
        except Exception as e:
            logger.error("save_radar_overlay_failed", label=label, error=str(e))
    
    @staticmethod
    def _save_radar_only(frame: Tuple[int, Image.Image], label: str):
        """Save radar image without overlay (pure radar)"""
        try:
            timestamp, frame_img = frame
            
            # Save to radar_images directory
            import os
            radar_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "radar_images")
            os.makedirs(radar_dir, exist_ok=True)
            from datetime import datetime
            timestamp_str = datetime.fromtimestamp(timestamp).strftime("%Y%m%d_%H%M%S")
            filename = f"radar_only_{label}_{timestamp_str}.png"
            filepath = os.path.join(radar_dir, filename)
            frame_img.save(filepath)
            logger.info("radar_only_saved", label=label, filepath=filepath)
            
        except Exception as e:
            logger.error("save_radar_only_failed", label=label, error=str(e))
    
    @staticmethod
    def _save_radar_with_movement(frame: Tuple[int, Image.Image], storms: List[Tuple], movements: List[Dict], label: str):
        """Save radar image with storm circles and movement arrows"""
        try:
            timestamp, frame_img = frame
            
            # Create a copy to draw on
            img_copy = frame_img.copy()
            draw = ImageDraw.Draw(img_copy)
            
            # Draw circles for each storm
            for storm_timestamp, storm_id, storm_x, storm_y, intensity, pixel_count in storms:
                # Choose color based on intensity
                if intensity == "high":
                    circle_color = (255, 0, 0)  # Red for high intensity
                elif intensity == "medium":
                    circle_color = (255, 255, 0)  # Yellow for medium intensity
                elif intensity == "light":
                    circle_color = (0, 255, 0)  # Green for light intensity
                else:
                    circle_color = (255, 255, 255)  # White for unknown
                
                # Draw circle around storm area
                radius = 15 + min(pixel_count // 10, 30)
                draw.ellipse(
                    [storm_x - radius, storm_y - radius, storm_x + radius, storm_y + radius],
                    outline=circle_color,
                    width=3
                )
                
                # Draw a small dot at the storm center
                draw.ellipse(
                    [storm_x - 3, storm_y - 3, storm_x + 3, storm_y + 3],
                    fill=circle_color
                )
            
            # Draw movement arrows
            for movement in movements:
                x = movement['x']
                y = movement['y']
                dx = movement['dx']
                dy = movement['dy']
                intensity = movement['intensity']
                
                # Scale the arrow for visibility
                arrow_scale = 5.0
                end_x = x + dx * arrow_scale
                end_y = y + dy * arrow_scale
                
                # Choose arrow color based on intensity
                if intensity == "high":
                    arrow_color = (255, 0, 0)
                elif intensity == "medium":
                    arrow_color = (255, 255, 0)
                elif intensity == "light":
                    arrow_color = (0, 255, 0)
                else:
                    arrow_color = (255, 255, 255)
                
                # Draw arrow line
                draw.line([x, y, end_x, end_y], fill=arrow_color, width=2)
                
                # Draw arrow head
                arrow_size = 5
                angle = math.atan2(dy, dx)
                arrow_p1_x = end_x - arrow_size * math.cos(angle - math.pi / 6)
                arrow_p1_y = end_y - arrow_size * math.sin(angle - math.pi / 6)
                arrow_p2_x = end_x - arrow_size * math.cos(angle + math.pi / 6)
                arrow_p2_y = end_y - arrow_size * math.sin(angle + math.pi / 6)
                draw.polygon([(end_x, end_y), (arrow_p1_x, arrow_p1_y), (arrow_p2_x, arrow_p2_y)], fill=arrow_color)
            
            # Save to radar_images directory
            import os
            radar_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "radar_images")
            os.makedirs(radar_dir, exist_ok=True)
            from datetime import datetime
            timestamp_str = datetime.fromtimestamp(timestamp).strftime("%Y%m%d_%H%M%S")
            filename = f"radar_movement_{label}_{timestamp_str}.png"
            filepath = os.path.join(radar_dir, filename)
            img_copy.save(filepath)
            logger.info("radar_movement_saved", label=label, filepath=filepath, storms_count=len(storms), movements_count=len(movements))
            
        except Exception as e:
            logger.error("save_radar_movement_failed", label=label, error=str(e))
    
    @staticmethod
    def _track_storms_across_frames(all_storms: List[Tuple]) -> Dict[int, List[Tuple]]:
        """Track individual storm cells across consecutive frames"""
        # Group storms by timestamp
        storms_by_frame = {}
        for timestamp, storm_id, x, y, intensity, pixel_count in all_storms:
            if timestamp not in storms_by_frame:
                storms_by_frame[timestamp] = []
            storms_by_frame[timestamp].append((storm_id, x, y, intensity, pixel_count))
        
        # Sort frames by timestamp
        sorted_timestamps = sorted(storms_by_frame.keys())
        
        # Track storms across frames using nearest neighbor matching
        storm_tracks = {}  # track_id -> list of (timestamp, x, y, intensity, pixel_count)
        next_track_id = 0
        
        for i in range(len(sorted_timestamps) - 1):
            current_time = sorted_timestamps[i]
            next_time = sorted_timestamps[i + 1]
            
            current_storms = storms_by_frame[current_time]
            next_storms = storms_by_frame[next_time]
            
            if not current_storms or not next_storms:
                continue
            
            # Extract positions
            current_positions = np.array([[s[1], s[2]] for s in current_storms])
            next_positions = np.array([[s[1], s[2]] for s in next_storms])
            
            # Calculate distance matrix
            distances = cdist(current_positions, next_positions)
            
            # Match storms using nearest neighbor (with max distance threshold)
            max_distance = 50.0  # pixels
            matched_current = set()
            matched_next = set()
            
            for current_idx in range(len(current_storms)):
                if current_idx in matched_current:
                    continue
                
                # Find nearest unmatched next storm
                best_next_idx = None
                best_distance = float('inf')
                
                for next_idx in range(len(next_storms)):
                    if next_idx in matched_next:
                        continue
                    
                    dist = distances[current_idx, next_idx]
                    if dist < best_distance and dist < max_distance:
                        best_distance = dist
                        best_next_idx = next_idx
                
                if best_next_idx is not None:
                    # Match found - assign to existing track or create new
                    current_storm = current_storms[current_idx]
                    next_storm = next_storms[best_next_idx]
                    
                    # Check if this current storm is already in a track
                    found_track = None
                    for track_id, track in storm_tracks.items():
                        if track[-1][0] == current_time:
                            last_x, last_y = track[-1][1], track[-1][2]
                            if abs(last_x - current_storm[1]) < 1.0 and abs(last_y - current_storm[2]) < 1.0:
                                found_track = track_id
                                break
                    
                    if found_track is not None:
                        # Add to existing track
                        storm_tracks[found_track].append((next_time, next_storm[1], next_storm[2], next_storm[3], next_storm[4]))
                    else:
                        # Create new track
                        storm_tracks[next_track_id] = [
                            (current_time, current_storm[1], current_storm[2], current_storm[3], current_storm[4]),
                            (next_time, next_storm[1], next_storm[2], next_storm[3], next_storm[4])
                        ]
                        next_track_id += 1
                    
                    matched_current.add(current_idx)
                    matched_next.add(best_next_idx)
            
            # Unmatched storms in next frame start new tracks
            for next_idx in range(len(next_storms)):
                if next_idx not in matched_next:
                    next_storm = next_storms[next_idx]
                    storm_tracks[next_track_id] = [
                        (next_time, next_storm[1], next_storm[2], next_storm[3], next_storm[4])
                    ]
                    next_track_id += 1
        
        logger.info("storm_tracks_created", count=len(storm_tracks))
        
        # Filter tracks that are too short (less than 2 frames)
        valid_tracks = {tid: track for tid, track in storm_tracks.items() if len(track) >= 2}
        logger.info("valid_tracks_filtered", total=len(storm_tracks), valid=len(valid_tracks))
        
        return valid_tracks
    
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
