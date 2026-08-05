"""Application configuration using pydantic-settings"""
from pydantic_settings import BaseSettings
from typing import Optional
from enum import Enum


class Environment(str, Enum):
    DEVELOPMENT = "development"
    PRODUCTION = "production"
    TESTING = "testing"


class Settings(BaseSettings):
    """Application settings with environment variable support"""
    
    # Environment
    environment: Environment = Environment.DEVELOPMENT
    debug: bool = False
    
    # API Configuration
    app_name: str = "Storm Alert API"
    app_version: str = "2.0.0"
    api_prefix: str = "/api/v2"
    
    # External APIs
    open_meteo_url: str = "https://api.open-meteo.com/v1"
    openweathermap_url: str = "https://api.openweathermap.org/data/2.5"
    openweathermap_api_key: str = ""  # Set via environment variable
    rainviewer_url: str = "https://api.rainviewer.com"
    rainviewer_host: str = "https://tilecache.rainviewer.com"
    
    # Radar Configuration
    radar_zoom_level: int = 7
    radar_tile_size: int = 256
    max_historical_frames: int = 10
    radar_color: int = 0
    radar_options: str = "1_0"
    
    # Analysis Configuration
    pixels_per_km: float = 0.5
    approach_alignment_threshold: float = 0.3
    max_reasonable_accel: float = 10.0
    max_reasonable_velocity: float = 50.0
    
    # Caching
    cache_ttl_seconds: int = 1800  # 30 minutes to reduce API calls
    cache_max_size: int = 1000
    
    # Logging
    log_level: str = "INFO"
    
    # Server Configuration
    host: str = "0.0.0.0"
    port: int = 8000
    workers: int = 1
    
    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
        case_sensitive = False


# Global settings instance
settings = Settings()
