# Storm Alert API

Modern FastAPI service for advanced storm forecasting with radar analysis and multi-radius risk assessment.

## Features

- **Async/await architecture** for high performance
- **Pydantic validation** for type safety
- **Structured logging** with JSON output
- **Radar integration** with RainViewer API
- **Storm movement forecasting** using polynomial regression
- **Multi-radius risk analysis** (current, 20km, 100km)
- **OpenAPI documentation** auto-generated
- **CORS support** for web/mobile clients

## Quick Start

### Local Development

```bash
# Install dependencies
pip install -r requirements.txt

# Run the server
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

# Access API documentation
# http://localhost:8000/api/v2/docs
```

### Docker

```bash
# Build image
docker build -t storm-api .

# Run container
docker run -p 8000:8000 storm-api
```

## API Endpoints

### POST /api/v2/storm/predict

Full storm prediction with radar analysis and movement forecasting.

**Request:**
```json
{
  "latitude": 44.5,
  "longitude": 11.34,
  "include_radar": true,
  "include_forecast": true,
  "historical_frames": 10
}
```

**Response:**
```json
{
  "success": true,
  "location": [44.5, 11.34],
  "timestamp": "2024-01-15T10:30:00",
  "storm_risk": {
    "is_currently_stormy": false,
    "is_storm_approaching": true,
    "storm_probability": 0.75,
    "confidence_interval": [0.65, 0.85],
    "estimated_time_to_storm": 45,
    "warning_level": "orange",
    "forecast_data": {
      "forecast_1h": [30.0, 18.0],
      "forecast_5h": [150.0, 90.0]
    },
    "risk_analysis": {
      "current": {
        "risk_level": "high",
        "time_to_impact": 45.0
      }
    }
  },
  "processing_time_ms": 1250.5,
  "api_version": "2.0.0"
}
```

### GET /api/v2/storm/current

Quick current storm risk without radar analysis (faster response).

**Request:**
```
GET /api/v2/storm/current?latitude=44.5&longitude=11.34
```

### GET /api/v2/health

Health check endpoint.

## Deployment

### Render (Free Tier)

1. Create account at [render.com](https://render.com)
2. Connect GitHub repository
3. Deploy using `render.yaml` configuration
4. API will be available at `https://your-app.onrender.com`

### Google Cloud Run

```bash
# Build and deploy
gcloud run deploy storm-api --source .
```

## Configuration

Environment variables (see `.env.example`):

- `ENVIRONMENT`: development/production/testing
- `DEBUG`: true/false
- `LOG_LEVEL`: INFO/DEBUG/ERROR
- `OPEN_METEO_URL`: Open-Meteo API URL
- `RAINVIEWER_URL`: RainViewer API URL

## Architecture

```
app/
├── main.py              # FastAPI application
├── config.py            # Configuration management
├── models.py            # Pydantic models
└── services/
    ├── weather_service.py   # Weather data fetching
    └── analysis_service.py  # Storm analysis logic
```

## Technology Stack

- **FastAPI** - Modern web framework
- **httpx** - Async HTTP client
- **pydantic** - Data validation
- **numpy** - Numerical computing
- **Pillow** - Image processing
- **structlog** - Structured logging
- **uvicorn** - ASGI server

## License

MIT
