"""Async weather data service using httpx"""
import httpx
import asyncio
from typing import Optional, Dict, Any
from datetime import datetime
import structlog

from app.config import settings

logger = structlog.get_logger()


class WeatherService:
    """Async weather data fetching service"""
    
    def __init__(self):
        self.client: Optional[httpx.AsyncClient] = None
        self.base_url = settings.open_meteo_url
    
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
        """Fetch current weather from Open-Meteo API"""
        try:
            # Use minimal parameters that are known to work
            params = {
                "latitude": latitude,
                "longitude": longitude,
                "current_weather": "true"
            }
            
            logger.info("fetching_weather", lat=latitude, lon=longitude)
            
            response = await self.client.get(
                f"{self.base_url}/forecast",
                params=params
            )
            logger.info("weather_response_status", status=response.status_code)
            response.raise_for_status()
            
            data = response.json()
            logger.info("weather_fetched_success", lat=latitude, lon=longitude)
            
            return self._parse_weather_response(data)
            
        except httpx.HTTPError as e:
            logger.error("weather_fetch_error", error=str(e), lat=latitude, lon=longitude)
            raise
        except Exception as e:
            logger.error("weather_parse_error", error=str(e))
            raise
    
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
