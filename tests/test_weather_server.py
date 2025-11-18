"""
Unit tests for the weather server with mocked HTTP requests.
"""
import pytest
from unittest.mock import AsyncMock, patch
import httpx
from weather_server import get_forecast, get_alerts, make_nws_request


@pytest.mark.asyncio
async def test_make_nws_request_success():
    """Test successful NWS API request."""
    mock_response_data = {"test": "data"}
    
    with patch("httpx.AsyncClient") as mock_client:
        mock_response = AsyncMock()
        mock_response.json = lambda: mock_response_data
        mock_response.raise_for_status = lambda: None
        
        mock_client_instance = AsyncMock()
        mock_client_instance.__aenter__ = AsyncMock(return_value=mock_client_instance)
        mock_client_instance.__aexit__ = AsyncMock(return_value=None)
        mock_client_instance.get = AsyncMock(return_value=mock_response)
        mock_client.return_value = mock_client_instance
        
        result = await make_nws_request("https://api.weather.gov/test")
        
        assert result == mock_response_data
        mock_client_instance.get.assert_called_once()


@pytest.mark.asyncio
async def test_make_nws_request_http_error():
    """Test NWS API request with HTTP error."""
    with patch("httpx.AsyncClient") as mock_client:
        mock_response = AsyncMock()
        mock_response.status_code = 404
        mock_response.text = "Not Found"
        
        def raise_error():
            raise httpx.HTTPStatusError(
                "Error", request=AsyncMock(), response=mock_response
            )
        
        mock_response.raise_for_status = raise_error
        
        mock_client_instance = AsyncMock()
        mock_client_instance.__aenter__ = AsyncMock(return_value=mock_client_instance)
        mock_client_instance.__aexit__ = AsyncMock(return_value=None)
        mock_client_instance.get = AsyncMock(return_value=mock_response)
        mock_client.return_value = mock_client_instance
        
        result = await make_nws_request("https://api.weather.gov/test")
        
        assert result is None


@pytest.mark.asyncio
async def test_make_nws_request_timeout():
    """Test NWS API request with timeout."""
    with patch("httpx.AsyncClient") as mock_client:
        mock_client_instance = AsyncMock()
        mock_client_instance.__aenter__ = AsyncMock(return_value=mock_client_instance)
        mock_client_instance.__aexit__ = AsyncMock(return_value=None)
        mock_client_instance.get = AsyncMock(side_effect=httpx.TimeoutException("Timeout"))
        mock_client.return_value = mock_client_instance
        
        result = await make_nws_request("https://api.weather.gov/test")
        
        assert result is None


@pytest.mark.asyncio
async def test_get_forecast_success():
    """Test successful forecast retrieval."""
    # Mock grid point response
    mock_points_data = {
        "properties": {
            "gridId": "MTR",
            "gridX": 88,
            "gridY": 97,
            "forecast": "https://api.weather.gov/gridpoints/MTR/88,97/forecast"
        }
    }
    
    # Mock forecast response
    mock_forecast_data = {
        "properties": {
            "periods": [
                {
                    "name": "Today",
                    "temperature": 72,
                    "shortForecast": "Sunny"
                }
            ],
            "units": {
                "temperature": "F"
            }
        }
    }
    
    with patch("weather_server.make_nws_request") as mock_request:
        mock_request.side_effect = [mock_points_data, mock_forecast_data]
        
        result = await get_forecast(37.7749, -122.4194)
        
        assert "error" not in result
        assert "location" in result
        assert "forecast" in result
        assert result["location"]["latitude"] == 37.7749
        assert result["location"]["longitude"] == -122.4194
        assert len(result["forecast"]) == 1
        assert result["forecast"][0]["name"] == "Today"


@pytest.mark.asyncio
async def test_get_forecast_no_grid_point():
    """Test forecast retrieval when grid point data is unavailable."""
    with patch("weather_server.make_nws_request") as mock_request:
        mock_request.return_value = None
        
        result = await get_forecast(37.7749, -122.4194)
        
        assert "error" in result
        assert "Failed to retrieve grid point data" in result["error"]


@pytest.mark.asyncio
async def test_get_forecast_no_forecast_url():
    """Test forecast retrieval when forecast URL is missing."""
    mock_points_data = {
        "properties": {
            "gridId": "MTR",
            "gridX": 88,
            "gridY": 97
            # Missing "forecast" key
        }
    }
    
    with patch("weather_server.make_nws_request") as mock_request:
        mock_request.return_value = mock_points_data
        
        result = await get_forecast(37.7749, -122.4194)
        
        assert "error" in result
        assert "Forecast URL not found" in result["error"]


@pytest.mark.asyncio
async def test_get_alerts_success():
    """Test successful alerts retrieval."""
    mock_alerts_data = {
        "features": [
            {
                "id": "alert1",
                "properties": {
                    "event": "Heat Advisory",
                    "headline": "Heat Advisory issued",
                    "description": "High temperatures expected",
                    "severity": "Moderate",
                    "urgency": "Expected",
                    "areaDesc": "San Francisco",
                    "effective": "2024-01-01T00:00:00Z",
                    "expires": "2024-01-02T00:00:00Z"
                }
            }
        ]
    }
    
    with patch("weather_server.make_nws_request") as mock_request:
        mock_request.return_value = mock_alerts_data
        
        result = await get_alerts(37.7749, -122.4194)
        
        assert "error" not in result
        assert "location" in result
        assert "alerts" in result
        assert result["count"] == 1
        assert result["alerts"][0]["event"] == "Heat Advisory"
        assert result["location"]["latitude"] == 37.7749
        assert result["location"]["longitude"] == -122.4194


@pytest.mark.asyncio
async def test_get_alerts_no_data():
    """Test alerts retrieval when API returns no data."""
    with patch("weather_server.make_nws_request") as mock_request:
        mock_request.return_value = None
        
        result = await get_alerts(37.7749, -122.4194)
        
        assert "error" in result
        assert "Failed to retrieve alerts data" in result["error"]


@pytest.mark.asyncio
async def test_get_alerts_empty_features():
    """Test alerts retrieval when no alerts are present."""
    mock_alerts_data = {
        "features": []
    }
    
    with patch("weather_server.make_nws_request") as mock_request:
        mock_request.return_value = mock_alerts_data
        
        result = await get_alerts(37.7749, -122.4194)
        
        assert "error" not in result
        assert result["count"] == 0
        assert len(result["alerts"]) == 0


@pytest.mark.asyncio
async def test_get_forecast_invalid_coordinates():
    """Test forecast with coordinates that don't return valid grid point."""
    with patch("weather_server.make_nws_request") as mock_request:
        mock_request.return_value = {"invalid": "data"}
        
        result = await get_forecast(0.0, 0.0)
        
        assert "error" in result
        assert "Failed to retrieve grid point data" in result["error"]

