"""Pydantic models for API requests and responses"""
from pydantic import BaseModel, Field, validator
from typing import Optional, List, Tuple, Literal
from datetime import datetime
from enum import Enum


class WarningLevel(str, Enum):
    """Multi-level warning system"""
    NONE = "none"
    YELLOW = "yellow"
    ORANGE = "orange"
    RED = "red"


class RiskLevel(str, Enum):
    """Risk assessment levels"""
    CRITICAL = "critical"
    HIGH = "high"
    MEDIUM = "medium"
    LOW = "low"


class WeatherCondition(BaseModel):
    """Weather condition interpretation"""
    code: int
    description: str
    is_stormy: bool


class CurrentWeather(BaseModel):
    """Current weather data"""
    temperature: float = Field(..., ge=-50, le=60, description="Temperature in Celsius")
    wind_speed: float = Field(..., ge=0, le=200, description="Wind speed in km/h")
    wind_direction: float = Field(..., ge=0, le=360, description="Wind direction in degrees")
    weather_code: int = Field(..., ge=0, le=99, description="WMO weather code")
    time: str
    relative_humidity: Optional[float] = Field(None, ge=0, le=100)
    pressure_msl: Optional[float] = Field(None, ge=800, le=1200)
    cloud_cover: Optional[float] = Field(None, ge=0, le=100)


class StormCentroid(BaseModel):
    """Storm position from radar analysis"""
    timestamp: int
    x: float
    y: float
    pixel_count: int


class MovementVector(BaseModel):
    """Storm movement vector"""
    speed_x: float
    speed_y: float
    time_diff: float


class ForecastData(BaseModel):
    """Storm movement forecast"""
    avg_speed_x: float
    avg_speed_y: float
    forecast_1h: Tuple[float, float]
    forecast_5h: Tuple[float, float]
    storm_centroids: List[StormCentroid]
    movements: List[MovementVector]
    acceleration: Tuple[float, float]


class RiskAnalysis(BaseModel):
    """Risk analysis at a specific radius"""
    radius_km: float
    distance_to_user_km: float
    storm_speed_km_per_min: float
    alignment: float = Field(..., ge=-1.0, le=1.0)
    is_approaching: bool
    time_to_impact: float
    risk_level: RiskLevel
    forecast_1h_px: Optional[Tuple[float, float]] = None
    forecast_5h_px: Optional[Tuple[float, float]] = None


class MultiRadiusAnalysis(BaseModel):
    """Risk analysis at multiple radii"""
    current: RiskAnalysis
    radius_20km: RiskAnalysis
    radius_100km: RiskAnalysis


class StormRisk(BaseModel):
    """Complete storm risk assessment"""
    is_currently_stormy: bool
    is_storm_approaching: bool
    storm_probability: float = Field(..., ge=0.0, le=1.0)
    confidence_interval: Tuple[float, float]
    estimated_time_to_storm: int = Field(..., ge=-1)
    current_condition: str
    wind_speed: float
    precipitation_probability: int = Field(..., ge=0, le=100)
    current_precipitation: float = 0.0
    max_wind_speed_next_6_hours: float = 0.0
    warning_level: WarningLevel
    trend_analysis: Optional[str] = None
    radar_intensity: Optional[float] = Field(None, ge=0.0, le=1.0)
    forecast_data: Optional[ForecastData] = None
    risk_analysis: Optional[MultiRadiusAnalysis] = None


class StormPredictionRequest(BaseModel):
    """Request model for storm prediction"""
    latitude: float = Field(..., ge=-90, le=90, description="Latitude in decimal degrees")
    longitude: float = Field(..., ge=-180, le=180, description="Longitude in decimal degrees")
    include_radar: bool = Field(True, description="Include radar analysis")
    include_forecast: bool = Field(True, description="Include movement forecast")
    include_radar_image: bool = Field(False, description="Include radar image with overlays")
    overlay_mode: str = Field("radar", description="Overlay mode: map, radar, arrows, all")
    historical_frames: int = Field(10, ge=1, le=20, description="Number of historical radar frames")
    
    @validator('historical_frames')
    def validate_frames(cls, v):
        if v < 1 or v > 20:
            raise ValueError('historical_frames must be between 1 and 20')
        return v
    
    @validator('overlay_mode')
    def validate_overlay_mode(cls, v):
        valid_modes = ["map", "radar", "arrows", "all"]
        if v not in valid_modes:
            raise ValueError(f'overlay_mode must be one of {valid_modes}')
        return v


class StormPredictionResponse(BaseModel):
    """Response model for storm prediction"""
    success: bool
    location: Tuple[float, float]
    timestamp: str
    storm_risk: StormRisk
    processing_time_ms: float
    radar_image: Optional[str] = None  # Base64 encoded radar image with overlays
    api_version: str = "2.0.0"


class ErrorResponse(BaseModel):
    """Error response model"""
    success: bool = False
    error: str
    detail: Optional[str] = None
    timestamp: str
    api_version: str = "2.0.0"


class HealthResponse(BaseModel):
    """Health check response"""
    status: str
    version: str
    timestamp: str
    services: dict
