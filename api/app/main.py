"""FastAPI application for Storm Alert API"""
import time
import math
from datetime import datetime
from typing import Optional
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
import structlog

from app.config import settings
from app.models import (
    StormPredictionRequest, StormPredictionResponse, 
    ErrorResponse, HealthResponse, WarningLevel
)
from app.services.weather_service import WeatherService, RadarService
from app.services.analysis_service import StormMovementAnalyzer, StormRiskAnalyzer

# Configure structured logging
structlog.configure(
    processors=[
        structlog.stdlib.filter_by_level,
        structlog.stdlib.add_logger_name,
        structlog.stdlib.add_log_level,
        structlog.stdlib.PositionalArgumentsFormatter(),
        structlog.processors.TimeStamper(fmt="iso"),
        structlog.processors.StackInfoRenderer(),
        structlog.processors.format_exc_info,
        structlog.processors.JSONRenderer()
    ],
    context_class=dict,
    logger_factory=structlog.stdlib.LoggerFactory(),
    cache_logger_on_first_use=True,
)

logger = structlog.get_logger()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan manager"""
    logger.info("application_startup", version=settings.app_version)
    yield
    logger.info("application_shutdown")


# Create FastAPI application
app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description="Advanced storm forecasting API with radar analysis and multi-radius risk assessment",
    docs_url=f"{settings.api_prefix}/docs",
    redoc_url=f"{settings.api_prefix}/redoc",
    openapi_url=f"{settings.api_prefix}/openapi.json",
    lifespan=lifespan
)

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Configure appropriately for production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get(f"{settings.api_prefix}/health", response_model=HealthResponse)
async def health_check():
    """Health check endpoint"""
    return HealthResponse(
        status="healthy",
        version=settings.app_version,
        timestamp=datetime.utcnow().isoformat(),
        services={
            "weather_api": "operational",
            "radar_api": "operational",
            "analysis_service": "operational"
        }
    )


@app.get("/")
async def root():
    """Root endpoint with API information"""
    return {
        "service": "Storm Alert API",
        "version": settings.app_version,
        "docs": f"{settings.api_prefix}/docs",
        "health": f"{settings.api_prefix}/health"
    }


@app.post(
    f"{settings.api_prefix}/storm/predict",
    response_model=StormPredictionResponse,
    summary="Predict storm risk for a location",
    description="Analyze weather data and radar imagery to predict storm risk with movement forecasting"
)
async def predict_storm(
    request: StormPredictionRequest,
    background_tasks: BackgroundTasks
):
    """
    Predict storm risk for a given location.
    
    - **latitude**: Latitude in decimal degrees (-90 to 90)
    - **longitude**: Longitude in decimal degrees (-180 to 180)
    - **include_radar**: Include radar analysis (default: true)
    - **include_forecast**: Include movement forecast (default: true)
    - **historical_frames**: Number of historical radar frames (1-20, default: 10)
    """
    start_time = time.time()
    
    try:
        logger.info(
            "storm_prediction_request",
            lat=request.latitude,
            lon=request.longitude,
            include_radar=request.include_radar,
            include_forecast=request.include_forecast
        )
        
        # Fetch weather data
        async with WeatherService() as weather_service:
            weather_data = await weather_service.fetch_current_weather(
                request.latitude,
                request.longitude
            )
        
        # Analyze storm risk from weather data
        storm_risk = analyze_weather_risk(weather_data)
        
        # Add radar analysis if requested
        radar_image = None
        if request.include_radar:
            radar_intensity = await analyze_radar_intensity(
                request.latitude,
                request.longitude
            )
            storm_risk.radar_intensity = radar_intensity
            
            # Add movement forecast if requested
            if request.include_forecast:
                movement_data = await analyze_storm_movement(
                    request.latitude,
                    request.longitude,
                    request.historical_frames
                )
                
                if movement_data:
                    storm_risk.forecast_data = movement_data
                    
                    # Add multi-radius risk analysis
                    risk_analysis = StormRiskAnalyzer.analyze_at_radii(
                        request.latitude,
                        request.longitude,
                        movement_data
                    )
                    storm_risk.risk_analysis = risk_analysis
                    
                    # Update storm approaching status based on radar
                    if risk_analysis:
                        current_analysis = risk_analysis.get('current')
                        if current_analysis:
                            storm_risk.is_storm_approaching = current_analysis.is_approaching
                            if current_analysis.time_to_impact != float('inf'):
                                storm_risk.estimated_time_to_storm = int(current_analysis.time_to_impact)
                    
                    # Generate radar image with overlays if requested
                    if request.include_radar_image:
                        async with RadarService() as radar_service:
                            radar_image = await radar_service.generate_radar_image_with_overlays(
                                request.latitude,
                                request.longitude,
                                movement_data,
                                request.overlay_mode
                            )
        
        processing_time = (time.time() - start_time) * 1000
        
        logger.info(
            "storm_prediction_complete",
            lat=request.latitude,
            lon=request.longitude,
            processing_time_ms=processing_time,
            risk_level=storm_risk.warning_level
        )
        
        # Log prediction in background
        background_tasks.add_task(
            log_prediction,
            request.latitude,
            request.longitude,
            storm_risk,
            processing_time
        )
        
        return StormPredictionResponse(
            success=True,
            location=(request.latitude, request.longitude),
            timestamp=datetime.utcnow().isoformat(),
            storm_risk=storm_risk,
            processing_time_ms=processing_time,
            radar_image=radar_image,
            api_version=settings.app_version
        )
        
    except Exception as e:
        logger.error(
            "storm_prediction_error",
            lat=request.latitude,
            lon=request.longitude,
            error=str(e)
        )
        raise HTTPException(status_code=500, detail=str(e))


@app.get(f"{settings.api_prefix}/storm/current", response_model=StormPredictionResponse)
async def get_current_storm_risk(
    latitude: float,
    longitude: float
):
    """
    Get current storm risk without radar analysis (faster response).
    
    - **latitude**: Latitude in decimal degrees
    - **longitude**: Longitude in decimal degrees
    """
    start_time = time.time()
    
    try:
        logger.info("current_storm_risk_request", lat=latitude, lon=longitude)
        
        async with WeatherService() as weather_service:
            weather_data = await weather_service.fetch_current_weather(latitude, longitude)
        
        storm_risk = analyze_weather_risk(weather_data)
        processing_time = (time.time() - start_time) * 1000
        
        return StormPredictionResponse(
            success=True,
            location=(latitude, longitude),
            timestamp=datetime.utcnow().isoformat(),
            storm_risk=storm_risk,
            processing_time_ms=processing_time,
            api_version=settings.app_version
        )
        
    except Exception as e:
        logger.error("current_storm_risk_error", lat=latitude, lon=longitude, error=str(e))
        raise HTTPException(status_code=500, detail=str(e))


async def analyze_radar_intensity(latitude: float, longitude: float) -> Optional[float]:
    """Analyze radar intensity for a location"""
    try:
        async with RadarService() as radar_service:
            metadata = await radar_service.fetch_radar_metadata()
            
            # Get latest radar path
            radar_data = metadata.get("radar", {})
            now_frame = radar_data.get("now")
            past_frames = radar_data.get("past", [])
            
            path = now_frame.get("path") if now_frame else past_frames[-1].get("path") if past_frames else None
            
            if not path:
                return None
            
            # Calculate tile coordinates
            zoom = settings.radar_zoom_level
            n = 2 ** zoom
            center_x = (longitude + 180) / 360 * n
            lat_rad = math.radians(latitude)
            center_y = (1 - math.asinh(math.tan(lat_rad)) / math.pi) / 2 * n
            
            # Fetch and analyze radar tiles (simplified for API)
            # In production, you'd fetch and analyze actual tiles
            return 0.5  # Placeholder intensity
            
    except Exception as e:
        logger.error("radar_intensity_error", lat=latitude, lon=longitude, error=str(e))
        return None


async def analyze_storm_movement(
    latitude: float, 
    longitude: float, 
    num_frames: int
) -> Optional[dict]:
    """Analyze storm movement from historical radar frames"""
    try:
        async with RadarService() as radar_service:
            metadata = await radar_service.fetch_radar_metadata()
            
            radar_data = metadata.get("radar", {})
            past_frames = radar_data.get("past", [])
            
            if len(past_frames) < 2:
                logger.warning("not_enough_historical_frames", count=len(past_frames))
                return None
            
            # Get historical frames
            frames_to_fetch = past_frames[-num_frames:] if len(past_frames) >= num_frames else past_frames
            
            # Fetch historical radar frames
            analyzer = StormMovementAnalyzer()
            historical_frames = await analyzer.fetch_historical_frames(
                metadata.get("host", settings.rainviewer_host),
                frames_to_fetch,
                latitude,
                longitude
            )
            
            if len(historical_frames) < 2:
                logger.warning("not_enough_frames_fetched", count=len(historical_frames))
                return None
            
            # Analyze movement from fetched frames
            movement_data = analyzer.analyze_movement(historical_frames)
            
            return movement_data
        
    except Exception as e:
        logger.error("storm_movement_error", lat=latitude, lon=longitude, error=str(e))
        return None


def analyze_weather_risk(weather_data: dict) -> object:
    """Analyze storm risk from weather data"""
    from app.models import StormRisk, WarningLevel
    
    current = weather_data.get("current_weather", {})
    hourly = weather_data.get("hourly", {})
    
    # Extract current conditions
    weather_code = current.get("weather_code", 0)
    wind_speed = current.get("wind_speed", 0)
    temperature =current.get("temperature", 0)
    
    # Determine if currently stormy
    is_stormy = weather_code in [95, 96, 99]
    
    # Extract hourly data
    precip_probs = hourly.get("precipitation_probability", [])
    wind_speeds = hourly.get("wind_speed", [])
    
    # Calculate storm probability (simplified)
    high_precip_hours = sum(1 for p in precip_probs if p and p > 70)
    max_wind = max([w for w in wind_speeds if w is not None]) if wind_speeds else wind_speed
    
    storm_probability = 0.1  # Base probability
    if is_stormy:
        storm_probability = 1.0
    elif high_precip_hours >= 3:
        storm_probability = 0.9
    elif max_wind > 60:
        storm_probability = 0.6
    
    # Determine warning level
    if storm_probability >= 0.9:
        warning_level = WarningLevel.RED
    elif storm_probability >= 0.7:
        warning_level = WarningLevel.ORANGE
    elif storm_probability >= 0.5:
        warning_level = WarningLevel.YELLOW
    else:
        warning_level = WarningLevel.NONE
    
    return StormRisk(
        is_currently_stormy=is_stormy,
        is_storm_approaching=False,  # Will be updated by radar analysis
        storm_probability=storm_probability,
        confidence_interval=(max(0.0, storm_probability - 0.1), min(1.0, storm_probability + 0.1)),
        estimated_time_to_storm=-1,
        current_condition=get_weather_description(weather_code),
        wind_speed=wind_speed,
        precipitation_probability=int(precip_probs[0]) if precip_probs else 0,
        current_precipitation=0.0,
        max_wind_speed_next_6_hours=max_wind,
        warning_level=warning_level,
        trend_analysis="stable"
    )


def get_weather_description(code: int) -> str:
    """Get weather description from code"""
    weather_codes = {
        0: "Clear sky",
        1: "Mainly clear",
        2: "Partly cloudy",
        3: "Overcast",
        45: "Foggy",
        48: "Foggy",
        51: "Drizzle",
        53: "Drizzle",
        55: "Drizzle",
        61: "Rain",
        63: "Rain",
        65: "Rain",
        71: "Snow",
        73: "Snow",
        75: "Snow",
        80: "Rain showers",
        81: "Rain showers",
        82: "Rain showers",
        95: "Thunderstorm",
        96: "Thunderstorm with hail",
        99: "Thunderstorm with hail",
    }
    return weather_codes.get(code, "Unknown")


async def log_prediction(
    latitude: float, 
    longitude: float, 
    storm_risk: object, 
    processing_time: float
):
    """Log prediction in background"""
    logger.info(
        "prediction_logged",
        lat=latitude,
        lon=longitude,
        risk_level=storm_risk.warning_level,
        processing_time_ms=processing_time
    )


@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    """Global exception handler"""
    logger.error("unhandled_exception", error=str(exc))
    return JSONResponse(
        status_code=500,
        content=ErrorResponse(
            success=False,
            error="Internal server error",
            detail=str(exc),
            timestamp=datetime.utcnow().isoformat(),
            api_version=settings.app_version
        ).dict()
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug
    )
