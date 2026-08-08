"""Simple test script for API enhancements"""
import asyncio
import httpx
import json

async def test_api():
    """Test the enhanced API with multiple radar image variants"""
    
    # Test data
    test_request = {
        "latitude": 52.5200,  # Berlin
        "longitude": 13.4050,
        "include_radar": True,
        "include_forecast": True,
        "include_radar_image": True,
        "overlay_mode": "all",
        "historical_frames": 5
    }
    
    print("Testing API with enhanced radar image variants...")
    print(f"Request: {json.dumps(test_request, indent=2)}")
    
    try:
        async with httpx.AsyncClient(timeout=120.0) as client:
            # Test local API
            response = await client.post(
                "http://localhost:8002/api/v2/storm/predict",
                json=test_request
            )
            
            print(f"\nStatus Code: {response.status_code}")
            
            if response.status_code == 200:
                data = response.json()
                print(f"\nResponse received successfully!")
                print(f"Success: {data.get('success')}")
                print(f"Processing time: {data.get('processing_time_ms')}ms")
                
                # Check for radar_images
                radar_images = data.get('radar_images')
                if radar_images:
                    print(f"\n✓ Radar images received: {len(radar_images)} variants")
                    for img in radar_images:
                        print(f"  - Mode: {img['mode']}, Description: {img['description']}")
                        print(f"    Image size: {len(img['image'])} bytes")
                else:
                    print("\n✗ No radar_images in response")
                
                # Check backward compatibility
                radar_image = data.get('radar_image')
                if radar_image:
                    print(f"\n✓ Backward compatible radar_image present: {len(radar_image)} bytes")
                else:
                    print("\n✗ No backward compatible radar_image")
                
                print("\n✓ API test PASSED")
                return True
            else:
                print(f"\n✗ API test FAILED")
                print(f"Error: {response.text}")
                return False
                
    except Exception as e:
        print(f"\n✗ API test FAILED with exception: {e}")
        return False

if __name__ == "__main__":
    result = asyncio.run(test_api())
    exit(0 if result else 1)
