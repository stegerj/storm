#!/usr/bin/env python3
"""Storm Alert - API Client Version
Calls the FastAPI service and recreates the output of weather_test.py
"""

import requests
import json
import time
import urllib.request
import base64
import os
from typing import Optional, Tuple
from datetime import datetime
from enum import Enum


def get_location() -> Optional[Tuple[float, float, str]]:
    """Get user's current location using IP geolocation"""
    try:
        # Use a free IP geolocation service
        response = urllib.request.urlopen('http://ip-api.com/json/')
        data = json.loads(response.read().decode('utf-8'))
        
        if data['status'] == 'success':
            lat = data['lat']
            lon = data['lon']
            city = data.get('city', 'Unknown')
            return (lat, lon, city)
        else:
            print(f"⚠️  Location API returned: {data.get('message', 'Unknown error')}")
            return None
    except Exception as e:
        print(f"⚠️  Failed to get location: {str(e)}")
        return None


class WarningLevel(Enum):
    """Multi-level warning system following international standards"""
    NONE = "none"
    YELLOW = "yellow"      # Be aware
    ORANGE = "orange"      # Be prepared
    RED = "red"            # Take action


class StormRisk:
    """Storm risk data from API"""
    def __init__(self, data: dict):
        self.is_currently_stormy = data.get("is_currently_stormy", False)
        self.is_storm_approaching = data.get("is_storm_approaching", False)
        self.storm_probability = data.get("storm_probability", 0.0)
        self.confidence_interval = tuple(data.get("confidence_interval", [0.0, 1.0]))
        self.estimated_time_to_storm = data.get("estimated_time_to_storm", -1)
        self.current_condition = data.get("current_condition", "Unknown")
        self.wind_speed = data.get("wind_speed", 0.0)
        self.precipitation_probability = data.get("precipitation_probability", 0)
        self.current_precipitation = data.get("current_precipitation", 0.0)
        self.max_wind_speed_next_6_hours = data.get("max_wind_speed_next_6_hours", 0.0)
        self.warning_level = WarningLevel(data.get("warning_level", "none"))
        self.trend_analysis = data.get("trend_analysis")
        self.radar_intensity = data.get("radar_intensity")
        self.forecast_data = data.get("forecast_data")
        self.risk_analysis = data.get("risk_analysis")


class StormAPIClient:
    """Client for the Storm Alert FastAPI service with automatic fallback"""
    
    def __init__(self, base_url: str = None):
        self.local_url = "http://localhost:8002"
        self.remote_url = "https://storm-n3iw.onrender.com"
        self.api_prefix = "/api/v2"
        
        if base_url:
            self.base_url = base_url
        else:
            self.base_url = self._detect_available_service()
    
    def _detect_available_service(self) -> str:
        """Detect which service is available (local or remote)"""
        try:
            # Try local service first
            response = requests.get(f"{self.local_url}{self.api_prefix}/health", timeout=2)
            if response.status_code == 200:
                print("✅ Using local API service")
                return self.local_url
        except:
            pass
        
        # Fallback to remote service
        print("ℹ️  Local service not available, using remote API")
        return self.remote_url
    
    def predict_storm(
        self, 
        latitude: float, 
        longitude: float,
        include_radar: bool = True,
        include_forecast: bool = True,
        include_radar_image: bool = False,
        overlay_mode: str = "radar",
        historical_frames: int = 10
    ) -> dict:
        """Call the storm prediction API"""
        url = f"{self.base_url}{self.api_prefix}/storm/predict"
        
        payload = {
            "latitude": latitude,
            "longitude": longitude,
            "include_radar": include_radar,
            "include_forecast": include_forecast,
            "include_radar_image": include_radar_image,
            "overlay_mode": overlay_mode,
            "historical_frames": historical_frames
        }
        
        try:
            response = requests.post(url, json=payload, timeout=120)  # Increased timeout for radar processing
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            print(f"❌ API call failed: {e}")
            return None
    
    def get_current_storm_risk(self, latitude: float, longitude: float) -> dict:
        """Quick current storm risk without radar"""
        url = f"{self.base_url}{self.api_prefix}/storm/current"
        
        params = {
            "latitude": latitude,
            "longitude": longitude
        }
        
        try:
            response = requests.get(url, params=params, timeout=30)
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            print(f"❌ API call failed: {e}")
            return None
    
    def health_check(self) -> dict:
        """Check API health"""
        url = f"{self.base_url}{self.api_prefix}/health"
        
        try:
            response = requests.get(url, timeout=10)
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            print(f"❌ Health check failed: {e}")
            return None


def display_storm_analysis(storm_risk: StormRisk, location: Tuple[float, float]):
    """Display storm analysis in the same format as weather_test.py"""
    latitude, longitude = location
    
    print("="*70)
    print("🌩️  STORM RISK ANALYSIS")
    print("="*70)
    print(f"📍 Location: {latitude:.4f}, {longitude:.4f}")
    print(f"🕐 Analysis Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    
    # Current Conditions
    print(f"\n🌡️  CURRENT CONDITIONS")
    print(f"  Weather: {storm_risk.current_condition}")
    print(f"  Temperature: {storm_risk.current_condition} (not available from API)")
    print(f"  Wind Speed: {storm_risk.wind_speed:.1f} km/h")
    print(f"  Precipitation: {storm_risk.current_precipitation:.1f} mm")
    print(f"  Currently Stormy: {'Yes' if storm_risk.is_currently_stormy else 'No'}")
    
    # Storm Probability
    print(f"\n📊 STORM PROBABILITY")
    print(f"  Probability: {storm_risk.storm_probability:.1%}")
    print(f"  Confidence: {storm_risk.confidence_interval[0]:.1%} - {storm_risk.confidence_interval[1]:.1%}")
    print(f"  Storm Approaching: {'Yes' if storm_risk.is_storm_approaching else 'No'}")
    
    # Time to Storm
    print(f"\n⏱️  TIME TO STORM")
    if storm_risk.estimated_time_to_storm > 0:
        hours = storm_risk.estimated_time_to_storm // 60
        minutes = storm_risk.estimated_time_to_storm % 60
        if hours > 0:
            print(f"  Estimated Time: {hours}h {minutes}m")
        else:
            print(f"  Estimated Time: {minutes} minutes")
    else:
        print(f"  Estimated Time: N/A")
    
    # Precipitation Forecast
    print(f"\n🌧️  PRECIPITATION FORECAST")
    print(f"  Current Probability: {storm_risk.precipitation_probability}%")
    print(f"  Max Wind Next 6h: {storm_risk.max_wind_speed_next_6_hours:.1f} km/h")
    
    # Radar Analysis (if available)
    if storm_risk.radar_intensity is not None:
        print(f"\n📡 RADAR ANALYSIS")
        print(f"  Radar Intensity: {storm_risk.radar_intensity:.2f}")
    
    # Movement Forecast (if available)
    if storm_risk.forecast_data:
        print(f"\n🔮 MOVEMENT FORECAST")
        forecast = storm_risk.forecast_data
        print(f"  Average Speed X: {forecast.get('avg_speed_x', 0):.2f}")
        print(f"  Average Speed Y: {forecast.get('avg_speed_y', 0):.2f}")
        forecast_1h = forecast.get('forecast_1h', (0, 0))
        forecast_5h = forecast.get('forecast_5h', (0, 0))
        print(f"  1-Hour Forecast: ({forecast_1h[0]:.1f}, {forecast_1h[1]:.1f})")
        print(f"  5-Hour Forecast: ({forecast_5h[0]:.1f}, {forecast_5h[1]:.1f})")
    
    # Multi-Radius Risk Analysis (if available)
    if storm_risk.risk_analysis:
        print(f"\n🎯 MULTI-RADIUS RISK ANALYSIS")
        risk = storm_risk.risk_analysis
        
        if 'current' in risk:
            current = risk['current']
            print(f"  Current Location:")
            print(f"    Risk Level: {current.get('risk_level', 'unknown').upper()}")
            print(f"    Distance: {current.get('distance_to_user_km', 0):.1f} km")
            time_to_impact = current.get('time_to_impact')
            if time_to_impact and time_to_impact != float('inf'):
                print(f"    Time to Impact: {time_to_impact:.1f} min")
            else:
                print(f"    Time to Impact: N/A")
        
        if 'radius_20km' in risk:
            radius_20 = risk['radius_20km']
            print(f"  20km Radius:")
            print(f"    Risk Level: {radius_20.get('risk_level', 'unknown').upper()}")
            print(f"    Approaching: {'Yes' if radius_20.get('is_approaching', False) else 'No'}")
        
        if 'radius_100km' in risk:
            radius_100 = risk['radius_100km']
            print(f"  100km Radius:")
            print(f"    Risk Level: {radius_100.get('risk_level', 'unknown').upper()}")
            print(f"    Approaching: {'Yes' if radius_100.get('is_approaching', False) else 'No'}")
    
    # Trend Analysis (if available)
    if storm_risk.trend_analysis:
        print(f"\n📈 TREND ANALYSIS")
        print(f"  {storm_risk.trend_analysis}")
    
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
    """Get approximate location from IP address"""
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
                
                lat = data.get("latitude") or data.get("lat")
                lon = data.get("longitude") or data.get("lon") or data.get("long")
                city = data.get("city") or data.get("region_name")
                
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


def save_radar_image(base64_data: str, filename: str):
    """Save base64 encoded radar image to file"""
    try:
        # Create output directory if it doesn't exist
        output_dir = "radar_images"
        os.makedirs(output_dir, exist_ok=True)
        
        # Decode base64 data
        img_data = base64.b64decode(base64_data)
        
        # Save to file
        filepath = os.path.join(output_dir, filename)
        with open(filepath, 'wb') as f:
            f.write(img_data)
        
        print(f"✅ Radar image saved to: {filepath}")
        return filepath
    except Exception as e:
        print(f"❌ Failed to save radar image: {e}")
        return None


def main():
    """Main entry point for API client testing"""
    import sys
    import io
    # Set UTF-8 encoding for Windows console
    if sys.platform == 'win32':
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
        sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
    
    print("🌩️  Storm Alert - API Client Version")
    print("Calls FastAPI service for storm detection")
    print("="*70)
    
    # Initialize client with automatic service detection
    client = StormAPIClient()
    print(f"🔗 Using API: {client.base_url}")
    print()
    
    # Check API health
    print("🔍 Checking API health...")
    health = client.health_check()
    if health:
        print(f"✅ API Status: {health.get('status', 'unknown')}")
        print(f"   Version: {health.get('version', 'unknown')}")
        print(f"   Time: {health.get('timestamp', 'unknown')}")
    else:
        print("❌ API health check failed")
    
    print()
    
    # Get user's actual location
    print("📍 Getting your location...")
    location_data = get_location()
    if location_data:
        latitude, longitude, city = location_data
        print(f"📍 Using your location: {city} ({latitude}, {longitude})")
    else:
        # Fallback to default coordinates if location fetch fails
        latitude = 51.5074  # London (fallback)
        longitude = -0.1278
        city = "London (fallback)"
        print(f"⚠️  Could not get location, using fallback: {city} ({latitude}, {longitude})")
    
    # Test different overlay modes
    overlay_modes = ["map", "radar", "arrows", "all"]
    
    for mode in overlay_modes:
        print(f"\n📡 Fetching storm prediction with overlay mode: {mode}")
        print(f"   Location: {latitude:.4f}, {longitude:.4f}")
        print(f"   Include Radar Image: Yes")
        print(f"   Overlay Mode: {mode}")
        print()
        
        response = client.predict_storm(
            latitude=latitude,
            longitude=longitude,
            include_radar=True,
            include_forecast=True,
            include_radar_image=True,
            overlay_mode=mode,
            historical_frames=5
        )
        
        if response and response.get("success"):
            print("✅ API call successful")
            
            # Save radar image if available
            radar_image = response.get("radar_image")
            if radar_image:
                timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
                filename = f"radar_{mode}_{timestamp}.png"
                save_radar_image(radar_image, filename)
            
            # Display analysis for the first mode only
            if mode == overlay_modes[0]:
                print("\n" + "="*70)
                storm_risk = StormRisk(response.get("storm_risk", {}))
                display_storm_analysis(storm_risk, (latitude, longitude))
        else:
            print("❌ Failed to get storm prediction from API")
            if response:
                print(f"   Error: {response.get('error', 'Unknown error')}")
    
    print("\n" + "="*70)
    print("✅ All overlay modes tested")
    print("📁 Radar images saved to 'radar_images/' directory")
    print("="*70)


if __name__ == "__main__":
    main()
