"""
MCP Weather Client

Test client for the weather MCP server.
"""
import asyncio
import sys
import json
from pathlib import Path
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client


async def main():
    """Main function to test the weather server."""
    # Get the path to the server script
    server_script = Path(__file__).parent / "weather_server.py"
    
    # Configure server parameters
    # Use python3 from the same environment
    import sys
    python_executable = sys.executable
    
    server_params = StdioServerParameters(
        command=python_executable,
        args=[str(server_script)],
        env=None
    )
    
    try:
        async with stdio_client(server_params) as (read_stream, write_stream):
            async with ClientSession(read_stream, write_stream) as session:
                # Initialize the session
                await session.initialize()
                
                print("Connected to weather server successfully!")
                print("-" * 60)
                
                # List available tools
                tools_response = await session.list_tools()
                print(f"\nAvailable tools: {[tool.name for tool in tools_response.tools]}")
                print("-" * 60)
                
                # Test coordinates (San Francisco, CA)
                test_latitude = 37.7749
                test_longitude = -122.4194
                
                print(f"\nTesting with coordinates: {test_latitude}, {test_longitude}")
                print("-" * 60)
                
                # Test get_forecast tool
                print("\n1. Testing get_forecast tool...")
                try:
                    forecast_result = await session.call_tool(
                        "get_forecast",
                        arguments={
                            "latitude": test_latitude,
                            "longitude": test_longitude
                        }
                    )
                    print("Forecast result:")
                    # Handle MCP content types properly
                    if forecast_result.content:
                        for item in forecast_result.content:
                            if hasattr(item, 'text'):
                                print(item.text)
                            elif hasattr(item, 'type') and item.type == 'text':
                                print(item.text)
                            else:
                                print(json.dumps(item, indent=2, ensure_ascii=False, default=str))
                    else:
                        print("No content returned")
                except Exception as e:
                    print(f"Error calling get_forecast: {e}")
                    import traceback
                    traceback.print_exc()
                
                print("-" * 60)
                
                # Test get_alerts tool
                print("\n2. Testing get_alerts tool...")
                try:
                    alerts_result = await session.call_tool(
                        "get_alerts",
                        arguments={
                            "latitude": test_latitude,
                            "longitude": test_longitude
                        }
                    )
                    print("Alerts result:")
                    # Handle MCP content types properly
                    if alerts_result.content:
                        for item in alerts_result.content:
                            if hasattr(item, 'text'):
                                print(item.text)
                            elif hasattr(item, 'type') and item.type == 'text':
                                print(item.text)
                            else:
                                print(json.dumps(item, indent=2, ensure_ascii=False, default=str))
                    else:
                        print("No content returned")
                except Exception as e:
                    print(f"Error calling get_alerts: {e}")
                    import traceback
                    traceback.print_exc()
                
                print("-" * 60)
                print("\nTest completed!")
                
    except Exception as e:
        print(f"Failed to connect to server: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())

