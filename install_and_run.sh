#!/bin/bash
# Install and launch Agent Cooper app on connected Android device

APP_PACKAGE="xyz.sandboiii.agentcooper"
MAIN_ACTIVITY="xyz.sandboiii.agentcooper.presentation.MainActivity"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

echo "Installing Agent Cooper..."
adb install -r "$APK_PATH"

if [ $? -eq 0 ]; then
    echo "Launching Agent Cooper..."
    adb shell am start -n "$APP_PACKAGE/$MAIN_ACTIVITY"
else
    echo "Installation failed!"
    exit 1
fi

