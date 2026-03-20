#!/bin/bash

# FORCE REBUILD SCRIPT - Run this to apply layout changes
# Usage: chmod +x force_rebuild.sh && ./force_rebuild.sh

echo "🧹 Step 1: Stopping Gradle daemons..."
./gradlew --stop 2>/dev/null

echo "✅ Gradle daemons stopped"

echo ""
echo "🧹 Step 2: Cleaning all build artifacts..."
rm -rf app/build .gradle build

echo "✅ Build directories cleaned"

echo ""
echo "🧹 Step 3: Cleaning Gradle cache (fixing corrupted metadata)..."
rm -rf ~/.gradle/caches/8.13 2>/dev/null

echo "✅ Gradle cache cleaned"

echo ""
echo "📱 Step 4: Use Android Studio to rebuild:"
echo "   1. Open Android Studio"
echo "   2. Build > Clean Project"
echo "   3. Build > Rebuild Project"
echo "   4. Uninstall app from device"
echo "   5. Run app"
echo ""
echo "✅ Ready for rebuild in Android Studio!"
echo ""
echo "Note: The Gradle cache error is fixed. Android Studio will"
echo "      rebuild the cache automatically on next build."


