"""
MCP Weather Server

Provides weather forecast and alerts using the US National Weather Service API.
"""
from typing import Any
import logging
import httpx
from mcp.server.fastmcp import FastMCP

# Configure logging to stderr (required for STDIO servers)
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    handlers=[logging.StreamHandler()]  # Defaults to stderr
)
logger = logging.getLogger(__name__)

# Initialize FastMCP server
mcp = FastMCP("weather")

# Constants
NWS_API_BASE = "https://api.weather.gov"
USER_AGENT = "weather-app/1.0"


async def make_nws_request(url: str) -> dict[str, Any] | None:
    """
    Makes a request to the National Weather Service API with error handling.
    
    Args:
        url: The full URL to request
        
    Returns:
        JSON response as dict, or None if request failed
    """
    headers = {
        "User-Agent": USER_AGENT,
        "Accept": "application/geo+json"
    }
    
    async with httpx.AsyncClient() as client:
        try:
            logger.info(f"Making request to: {url}")
            response = await client.get(url, headers=headers, timeout=30.0)
            response.raise_for_status()
            return response.json()
        except httpx.HTTPStatusError as e:
            logger.error(f"HTTP error occurred: {e.response.status_code} - {e.response.text}")
        except httpx.TimeoutException:
            logger.error("Request timed out")
        except httpx.RequestError as e:
            logger.error(f"Request error occurred: {e}")
        except Exception as e:
            logger.error(f"Unexpected error occurred: {e}")
    
    return None


@mcp.tool()
async def get_forecast(latitude: float, longitude: float) -> dict[str, Any]:
    """
    Gets the weather forecast for the given coordinates.
    
    Args:
        latitude: Latitude coordinate (must be within US)
        longitude: Longitude coordinate (must be within US)
        
    Returns:
        Dictionary containing forecast data or error message
    """
    # First, get the grid point for these coordinates
    points_url = f"{NWS_API_BASE}/points/{latitude},{longitude}"
    points_data = await make_nws_request(points_url)
    
    if not points_data or "properties" not in points_data:
        return {
            "error": "Failed to retrieve grid point data. Make sure coordinates are within the US."
        }
    
    # Extract forecast URL from grid point data
    forecast_url = points_data["properties"].get("forecast")
    if not forecast_url:
        return {
            "error": "Forecast URL not found in grid point data"
        }
    
    # Get the forecast
    forecast_data = await make_nws_request(forecast_url)
    
    if not forecast_data or "properties" not in forecast_data:
        return {
            "error": "Failed to retrieve forecast data"
        }
    
    return {
        "location": {
            "latitude": latitude,
            "longitude": longitude,
            "grid_id": points_data["properties"].get("gridId"),
            "grid_x": points_data["properties"].get("gridX"),
            "grid_y": points_data["properties"].get("gridY"),
        },
        "forecast": forecast_data["properties"].get("periods", []),
        "units": forecast_data["properties"].get("units", {}),
    }


@mcp.tool()
async def get_alerts(latitude: float, longitude: float) -> dict[str, Any]:
    """
    Gets active weather alerts for the given coordinates.
    
    Args:
        latitude: Latitude coordinate (must be within US)
        longitude: Longitude coordinate (must be within US)
        
    Returns:
        Dictionary containing alert data or error message
    """
    alerts_url = f"{NWS_API_BASE}/alerts/active?point={latitude},{longitude}"
    alerts_data = await make_nws_request(alerts_url)
    
    if not alerts_data:
        return {
            "error": "Failed to retrieve alerts data"
        }
    
    features = alerts_data.get("features", [])
    
    return {
        "location": {
            "latitude": latitude,
            "longitude": longitude,
        },
        "alerts": [
            {
                "id": feature.get("id"),
                "event": feature.get("properties", {}).get("event"),
                "headline": feature.get("properties", {}).get("headline"),
                "description": feature.get("properties", {}).get("description"),
                "severity": feature.get("properties", {}).get("severity"),
                "urgency": feature.get("properties", {}).get("urgency"),
                "areas": feature.get("properties", {}).get("areaDesc"),
                "effective": feature.get("properties", {}).get("effective"),
                "expires": feature.get("properties", {}).get("expires"),
            }
            for feature in features
        ],
        "count": len(features),
    }


if __name__ == "__main__":
    mcp.run()

