"""Async weather data service using httpx"""
import httpx
import asyncio
from typing import Optional, Dict, Any, List, Tuple
from datetime import datetime
import structlog
import time
import math
import base64
from io import BytesIO
from PIL import Image, ImageDraw, ImageFont

from app.config import settings

logger = structlog.get_logger()


class WeatherService:
    """Async weather data fetching service with caching and rate limiting"""
    
    def __init__(self):
        self.client: Optional[httpx.AsyncClient] = None
        self.base_url = settings.open_meteo_url
        self.cache: Dict[str, tuple] = {}  # Simple in-memory cache
        self.cache_ttl = settings.cache_ttl_seconds
    
    async def __aenter__(self):
        self.client = httpx.AsyncClient(timeout=30.0)
        return self
    
    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if self.client:
            await self.client.aclose()
    
    async def fetch_current_weather(
        self, 
        latitude: float, 
        longitude: float
    ) -> Dict[str, Any]:
        """Fetch current weather with multiple API fallbacks"""
        cache_key = f"{latitude:.2f},{longitude:.2f}"
        
        # Check cache first
        if cache_key in self.cache:
            cached_data, cached_time = self.cache[cache_key]
            if time.time() - cached_time < self.cache_ttl:
                logger.info("weather_cache_hit", lat=latitude, lon=longitude)
                return cached_data
        
        # Try Open-Meteo first
        try:
            data = await self._fetch_from_open_meteo(latitude, longitude)
            if data:
                self.cache[cache_key] = (data, time.time())
                return data
        except Exception as e:
            logger.warning("open_meteo_failed", error=str(e), trying="openweathermap")
        
        # Fallback to OpenWeatherMap if API key is available
        if settings.openweathermap_api_key:
            try:
                data = await self._fetch_from_openweathermap(latitude, longitude)
                if data:
                    self.cache[cache_key] = (data, time.time())
                    logger.info("weather_fallback_success", provider="openweathermap")
                    return data
            except Exception as e:
                logger.warning("openweathermap_failed", error=str(e))
        
        # Final fallback to default data
        logger.warning("all_apis_failed", using="default_data")
        return self._get_default_weather_data(latitude, longitude)
    
    async def _fetch_from_open_meteo(self, latitude: float, longitude: float) -> Optional[Dict[str, Any]]:
        """Fetch from Open-Meteo with retry logic"""
        max_retries = 2
        base_delay = 1.0
        
        for attempt in range(max_retries):
            try:
                params = {
                    "latitude": latitude,
                    "longitude": longitude,
                    "current_weather": "true"
                }
                
                logger.info("fetching_open_meteo", lat=latitude, lon=longitude, attempt=attempt+1)
                
                response = await self.client.get(
                    f"{self.base_url}/forecast",
                    params=params
                )
                
                if response.status_code == 429:
                    delay = base_delay * (2 ** attempt)
                    logger.warning("open_meteo_rate_limited", delay=delay)
                    await asyncio.sleep(delay)
                    continue
                
                response.raise_for_status()
                data = response.json()
                return self._parse_weather_response(data)
                
            except httpx.HTTPError as e:
                if attempt == max_retries - 1:
                    raise
                await asyncio.sleep(base_delay * (2 ** attempt))
        
        return None
    
    async def _fetch_from_openweathermap(self, latitude: float, longitude: float) -> Optional[Dict[str, Any]]:
        """Fetch from OpenWeatherMap as fallback"""
        try:
            params = {
                "lat": latitude,
                "lon": longitude,
                "appid": settings.openweathermap_api_key,
                "units": "metric"
            }
            
            logger.info("fetching_openweathermap", lat=latitude, lon=longitude)
            
            response = await self.client.get(
                f"{settings.openweathermap_url}/weather",
                params=params
            )
            response.raise_for_status()
            data = response.json()
            return self._parse_openweathermap_response(data)
            
        except Exception as e:
            logger.error("openweathermap_error", error=str(e))
            return None
    
    def _get_default_weather_data(self, latitude: float, longitude: float) -> Dict[str, Any]:
        """Return default weather data when API fails"""
        logger.warning("using_default_weather_data", lat=latitude, lon=longitude)
        return {
            "latitude": latitude,
            "longitude": longitude,
            "current_weather": {
                "temperature": 20.0,
                "wind_speed": 10.0,
                "wind_direction": 180.0,
                "weather_code": 0,
                "time": datetime.utcnow().isoformat()
            },
            "hourly": {
                "time": [],
                "temperature": [20.0] * 6,
                "precipitation_probability": [0] * 6,
                "precipitation": [0.0] * 6,
                "wind_speed": [10.0] * 6,
                "wind_gusts": [15.0] * 6
            }
        }
    
    def _parse_weather_response(self, data: Dict[str, Any]) -> Dict[str, Any]:
        """Parse Open-Meteo API response"""
        current = data.get("current_weather", {})
        hourly = data.get("hourly", {})
        
        return {
            "latitude": data.get("latitude"),
            "longitude": data.get("longitude"),
            "current_weather": {
                "temperature": current.get("temperature"),
                "wind_speed": current.get("windspeed"),
                "wind_direction": current.get("winddirection"),
                "weather_code": current.get("weathercode"),
                "time": current.get("time")
            },
            "hourly": {
                "time": hourly.get("time", [])[:6],  # Next 6 hours
                "temperature": hourly.get("temperature_2m", [])[:6],
                "precipitation_probability": hourly.get("precipitation_probability", [])[:6],
                "precipitation": hourly.get("precipitation", [])[:6],
                "wind_speed": hourly.get("windspeed_10m", [])[:6],
                "wind_gusts": hourly.get("windgusts_10m", [])[:6]
            }
        }
    
    def _parse_openweathermap_response(self, data: Dict[str, Any]) -> Dict[str, Any]:
        """Parse OpenWeatherMap API response to match our format"""
        main = data.get("main", {})
        wind = data.get("wind", {})
        weather = data.get("weather", [{}])[0]
        
        # Convert OpenWeatherMap weather code to WMO code
        owm_to_wmo = {
            "Clear": 0,
            "Clouds": 1,
            "Rain": 61,
            "Drizzle": 51,
            "Thunderstorm": 95,
            "Snow": 71,
            "Mist": 45,
            "Fog": 45
        }
        
        weather_code = owm_to_wmo.get(weather.get("main", "Clear"), 0)
        
        return {
            "latitude": data.get("coord", {}).get("lat"),
            "longitude": data.get("coord", {}).get("lon"),
            "current_weather": {
                "temperature": main.get("temp", 20.0),
                "wind_speed": wind.get("speed", 10.0),
                "wind_direction": wind.get("deg", 180.0),
                "weather_code": weather_code,
                "time": datetime.utcnow().isoformat()
            },
            "hourly": {
                "time": [],
                "temperature": [main.get("temp", 20.0)] * 6,
                "precipitation_probability": [0] * 6,
                "precipitation": [0.0] * 6,
                "wind_speed": [wind.get("speed", 10.0)] * 6,
                "wind_gusts": [wind.get("gust", 15.0)] * 6
            }
        }


class RadarService:
    """Async radar data service using RainViewer API"""
    
    def __init__(self):
        self.client: Optional[httpx.AsyncClient] = None
        self.api_url = settings.rainviewer_url
        self.tile_host = settings.rainviewer_host
    
    async def __aenter__(self):
        self.client = httpx.AsyncClient(timeout=30.0)
        return self
    
    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if self.client:
            await self.client.aclose()
    
    async def fetch_radar_metadata(self) -> Dict[str, Any]:
        """Fetch radar metadata from RainViewer"""
        try:
            response = await self.client.get(f"{self.api_url}/public/weather-maps.json")
            response.raise_for_status()
            return response.json()
        except httpx.HTTPError as e:
            logger.error("radar_metadata_error", error=str(e))
            raise
    
    async def fetch_radar_tile(
        self, 
        host: str, 
        path: str, 
        x: int, 
        y: int, 
        zoom: int = 7
    ) -> bytes:
        """Fetch a single radar tile"""
        tile_url = f"{host}{path}/{settings.radar_tile_size}/{zoom}/{x}/{y}/{settings.radar_color}/{settings.radar_options}.png"
        response = await self.client.get(tile_url)
        response.raise_for_status()
        return response.content
    
    async def generate_radar_image_with_overlays(
        self,
        latitude: float,
        longitude: float,
        movement_data: Optional[dict],
        overlay_mode: str = "radar"
    ) -> Optional[str]:
        """Generate radar image with forecast overlays, return base64 encoded"""
        try:
            logger.info("radar_image_generation_start", lat=latitude, lon=longitude, mode=overlay_mode)
            
            # Calculate tile coordinates for center
            zoom = settings.radar_zoom_level
            n = 2 ** zoom
            center_x = int(((longitude + 180) / 360 * n))
            lat_rad = math.radians(latitude)
            center_y = int(((1 - math.asinh(math.tan(lat_rad)) / math.pi) / 2 * n))
            
            logger.info("tile_coordinates_calculated", center_x=center_x, center_y=center_y, zoom=zoom)
            
            composite_size = settings.radar_tile_size * 2
            composite = Image.new('RGBA', (composite_size, composite_size))
            
            # Layer 1: Map tiles (always include as base layer except for radar-only mode)
            if overlay_mode in ["map", "radar", "arrows", "all"]:
                logger.info("fetching_map_tiles")
                map_tiles = await self._fetch_map_tiles(center_x, center_y, zoom)
                logger.info("map_tiles_fetched", count=len(map_tiles))
                for tile_data, dx, dy in map_tiles:
                    try:
                        tile_img = Image.open(BytesIO(tile_data))
                        if tile_img.mode != 'RGBA':
                            tile_img = tile_img.convert('RGBA')
                        x_pos = (dx + 1) * settings.radar_tile_size
                        y_pos = (dy + 1) * settings.radar_tile_size
                        composite.paste(tile_img, (x_pos, y_pos))
                    except Exception as e:
                        logger.warning("map_tile_composite_failed", dx=dx, dy=dy, error=str(e))
                
                # Draw a marker at the center (user location)
                draw = ImageDraw.Draw(composite)
                center_x_px = settings.radar_tile_size
                center_y_px = settings.radar_tile_size
                draw.ellipse(
                    [center_x_px - 5, center_y_px - 5, center_x_px + 5, center_y_px + 5],
                    fill=(255, 0, 0),
                    outline=(255, 255, 255),
                    width=2
                )
            
            # Layer 2: Radar tiles (if requested)
            if overlay_mode in ["radar", "all"]:
                logger.info("fetching_radar_metadata")
                metadata = await self.fetch_radar_metadata()
                radar_data = metadata.get("radar", {})
                now_frame = radar_data.get("now")
                past_frames = radar_data.get("past", [])
                
                logger.info("radar_metadata_received", has_now=bool(now_frame), past_frames_count=len(past_frames))
                
                if now_frame or past_frames:
                    path = now_frame.get("path") if now_frame else past_frames[-1].get("path") if past_frames else None
                    logger.info("radar_path_extracted", path=path)
                    
                    if path:
                        radar_tiles = []
                        for dx in [-1, 0]:
                            for dy in [-1, 0]:
                                tile_x = center_x + dx
                                tile_y = center_y + dy
                                try:
                                    tile_data = await self.fetch_radar_tile(
                                        metadata.get("host", settings.rainviewer_host),
                                        path,
                                        tile_x,
                                        tile_y,
                                        zoom
                                    )
                                    radar_tiles.append((tile_data, dx, dy))
                                except Exception as e:
                                    logger.warning("radar_tile_fetch_failed", x=tile_x, y=tile_y, error=str(e))
                        
                        logger.info("radar_tiles_fetched", count=len(radar_tiles))
                        
                        for tile_data, dx, dy in radar_tiles:
                            try:
                                tile_img = Image.open(BytesIO(tile_data))
                                if tile_img.mode != 'RGBA':
                                    tile_img = tile_img.convert('RGBA')
                                
                                # Make radar semi-transparent for overlay
                                if overlay_mode == "all":
                                    tile_img = tile_img.convert('RGBA')
                                    r, g, b, a = tile_img.split()
                                    a = a.point(lambda x: x * 0.6)  # 60% opacity
                                    tile_img = Image.merge('RGBA', (r, g, b, a))
                                
                                x_pos = (dx + 1) * settings.radar_tile_size
                                y_pos = (dy + 1) * settings.radar_tile_size
                                composite.paste(tile_img, (x_pos, y_pos), tile_img)
                            except Exception as e:
                                logger.warning("radar_tile_composite_failed", dx=dx, dy=dy, error=str(e))
                else:
                    logger.warning("no_radar_frames_available")
            
            # Layer 3: Forecast arrows (if requested)
            if overlay_mode in ["arrows", "all"] and movement_data:
                logger.info("drawing_forecast_overlays")
                composite = self._draw_forecast_overlays(composite, movement_data)
            
            # Convert to base64
            logger.info("converting_to_base64")
            buffer = BytesIO()
            composite.save(buffer, format='PNG')
            img_base64 = base64.b64encode(buffer.getvalue()).decode('utf-8')
            
            logger.info("radar_image_generated", lat=latitude, lon=longitude, mode=overlay_mode, size=len(img_base64))
            return img_base64
            
        except Exception as e:
            logger.error("radar_image_generation_failed", error=str(e), exc_info=True)
            return None
    
    async def _fetch_map_tiles(self, center_x: int, center_y: int, zoom: int) -> List[Tuple[bytes, int, int]]:
        """Fetch OpenStreetMap tiles for the area"""
        tiles = []
        for dx in [-1, 0]:
            for dy in [-1, 0]:
                tile_x = center_x + dx
                tile_y = center_y + dy
                try:
                    tile_url = f"https://tile.openstreetmap.org/{zoom}/{tile_x}/{tile_y}.png"
                    response = await self.client.get(tile_url, timeout=10)
                    response.raise_for_status()
                    tiles.append((response.content, dx, dy))
                except Exception as e:
                    logger.warning("map_tile_fetch_failed", x=tile_x, y=tile_y, error=str(e))
        return tiles
    
    def _draw_forecast_overlays(self, image: Image.Image, movement_data: dict) -> Image.Image:
        """Draw circles around detected storm areas on radar image"""
        draw = ImageDraw.Draw(image)
        
        # First try to use current frame storms (from current radar image)
        current_storms = movement_data.get('current_storms', [])
        
        logger.info("drawing_overlays", current_storms_count=len(current_storms))
        
        if current_storms:
            # Draw circles for storms detected in current frame
            for timestamp, storm_id, storm_x, storm_y, intensity, pixel_count in current_storms:
                logger.info("drawing_current_storm_circle", storm_x=storm_x, storm_y=storm_y, intensity=intensity, pixels=pixel_count)
                
                # The storm coordinates are from the 2x2 tile grid (512x512) used for analysis
                # The composite image is also 2x2 tile grid (512x512) with radar tiles pasted at (256,256) offset
                # The radar tiles in the composite start at (256, 256) because they use offsets (-1,-1), (-1,0), (0,-1), (0,0)
                # So we need to add the offset to map from analysis coordinates to composite coordinates
                adjusted_x = storm_x + settings.radar_tile_size
                adjusted_y = storm_y + settings.radar_tile_size
                
                logger.info("coordinate_adjustment", original_x=storm_x, original_y=storm_y, adjusted_x=adjusted_x, adjusted_y=adjusted_y)
                
                # Choose color based on intensity
                if intensity == "high":
                    circle_color = (255, 0, 0)  # Red for high intensity
                else:
                    circle_color = (255, 255, 0)  # Yellow for medium intensity
                
                # Draw circle around storm area (radius based on pixel count for larger storms)
                radius = 15 + min(pixel_count // 10, 30)
                draw.ellipse(
                    [adjusted_x - radius, adjusted_y - radius, adjusted_x + radius, adjusted_y + radius],
                    outline=circle_color,
                    width=3
                )
                
                # Draw a small dot at the storm center
                draw.ellipse(
                    [adjusted_x - 3, adjusted_y - 3, adjusted_x + 3, adjusted_y + 3],
                    fill=circle_color
                )
                
                logger.info("current_storm_circle_drawn", x=adjusted_x, y=adjusted_y, radius=radius, color=circle_color)
        else:
            # Fallback to single storm forecast (backward compatibility)
            center_x = image.width // 2
            center_y = image.height // 2
            
            forecast_1h = movement_data.get('forecast_1h', (0, 0))
            forecast_5h = movement_data.get('forecast_5h', (0, 0))
            
            # Draw current position (blue circle)
            draw.ellipse(
                [center_x - 10, center_y - 10, center_x + 10, center_y + 10],
                outline=(0, 0, 255),
                width=2
            )
            
            # Draw 1-hour forecast position (green circle)
            forecast_1h_x = center_x + forecast_1h[0]
            forecast_1h_y = center_y + forecast_1h[1]
            draw.ellipse(
                [forecast_1h_x - 8, forecast_1h_y - 8, forecast_1h_x + 8, forecast_1h_y + 8],
                outline=(0, 255, 0),
                width=2
            )
            
            # Draw 5-hour forecast position (purple circle)
            forecast_5h_x = center_x + forecast_5h[0]
            forecast_5h_y = center_y + forecast_5h[1]
            draw.ellipse(
                [forecast_5h_x - 6, forecast_5h_y - 6, forecast_5h_x + 6, forecast_5h_y + 6],
                outline=(128, 0, 128),
                width=2
            )
            
            # Draw arrow from center to 1-hour forecast (orange)
            draw.line(
                [center_x, center_y, forecast_1h_x, forecast_1h_y],
                fill=(255, 165, 0),
                width=3
            )
            
            # Draw line from 1-hour to 5-hour forecast (purple)
            draw.line(
                [forecast_1h_x, forecast_1h_y, forecast_5h_x, forecast_5h_y],
                fill=(128, 0, 128),
                width=2
            )
        
        return image
    
    def _draw_arrow_head(self, draw, from_x, from_y, to_x, to_y, color):
        """Draw an arrow head at the end of a line"""
        # Calculate angle
        angle = math.atan2(to_y - from_y, to_x - from_x)
        
        # Arrow head size
        arrow_size = 10
        
        # Calculate arrow head points
        left_angle = angle + math.pi * 0.85
        right_angle = angle - math.pi * 0.85
        
        left_x = to_x + arrow_size * math.cos(left_angle)
        left_y = to_y + arrow_size * math.sin(left_angle)
        right_x = to_x + arrow_size * math.cos(right_angle)
        right_y = to_y + arrow_size * math.sin(right_angle)
        
        # Draw arrow head
        draw.polygon(
            [(to_x, to_y), (left_x, left_y), (right_x, right_y)],
            fill=color
        )
