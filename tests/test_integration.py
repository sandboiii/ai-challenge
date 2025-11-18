"""
Integration tests for the weather server.

These tests make real API calls and can be skipped if the API is unavailable.
Use pytest -m integration to run only integration tests.
"""
import pytest
import asyncio
from weather_server import get_forecast, get_alerts


# Mark all tests in this file as integration tests
pytestmark = pytest.mark.integration


@pytest.mark.asyncio
@pytest.mark.integration
async def test_get_forecast_real_api():
    """Test get_forecast with real NWS API (San Francisco coordinates)."""
    # San Francisco, CA coordinates
    latitude = 37.7749
    longitude = -122.4194
    
    result = await get_forecast(latitude, longitude)
    
    # Should not have error for valid US coordinates
    assert "error" not in result, f"API returned error: {result.get('error')}"
    
    # Should have location and forecast data
    assert "location" in result
    assert "forecast" in result
    assert isinstance(result["forecast"], list)
    assert len(result["forecast"]) > 0
    
    # Verify location data
    assert result["location"]["latitude"] == latitude
    assert result["location"]["longitude"] == longitude
    assert "grid_id" in result["location"]


@pytest.mark.asyncio
@pytest.mark.integration
async def test_get_alerts_real_api():
    """Test get_alerts with real NWS API (San Francisco coordinates)."""
    # San Francisco, CA coordinates
    latitude = 37.7749
    longitude = -122.4194
    
    result = await get_alerts(latitude, longitude)
    
    # Should not have error for valid US coordinates
    assert "error" not in result, f"API returned error: {result.get('error')}"
    
    # Should have location and alerts data
    assert "location" in result
    assert "alerts" in result
    assert isinstance(result["alerts"], list)
    assert "count" in result
    assert result["count"] == len(result["alerts"])
    
    # Verify location data
    assert result["location"]["latitude"] == latitude
    assert result["location"]["longitude"] == longitude


@pytest.mark.asyncio
@pytest.mark.integration
async def test_get_forecast_new_york():
    """Test get_forecast with New York coordinates."""
    # New York, NY coordinates
    latitude = 40.7128
    longitude = -74.0060
    
    result = await get_forecast(latitude, longitude)
    
    assert "error" not in result, f"API returned error: {result.get('error')}"
    assert "forecast" in result
    assert len(result["forecast"]) > 0


@pytest.mark.asyncio
@pytest.mark.integration
async def test_get_forecast_invalid_coordinates():
    """Test get_forecast with coordinates outside US (should fail gracefully)."""
    # Coordinates in the middle of the ocean (outside US)
    latitude = 0.0
    longitude = 0.0
    
    result = await get_forecast(latitude, longitude)
    
    # Should return an error for coordinates outside US
    assert "error" in result


@pytest.mark.asyncio
@pytest.mark.integration
async def test_get_alerts_chicago():
    """Test get_alerts with Chicago coordinates."""
    # Chicago, IL coordinates
    latitude = 41.8781
    longitude = -87.6298
    
    result = await get_alerts(latitude, longitude)
    
    assert "error" not in result, f"API returned error: {result.get('error')}"
    assert "alerts" in result
    assert isinstance(result["alerts"], list)
    # Note: There may or may not be active alerts, so we just check the structure

