#!/bin/bash

# ===================================================================
# GRADLE CACHE FIX - Run this if you get Gradle cache errors
# ===================================================================

echo "🧹 Cleaning Gradle cache corruption..."
echo ""

# Stop all Gradle daemons
echo "1. Stopping Gradle daemons..."
killall -9 java 2>/dev/null
sleep 2

# Remove corrupted cache
echo "2. Removing corrupted Gradle cache..."
rm -rf ~/.gradle/caches/8.13
rm -rf ~/.gradle/daemon

# Remove project build files
echo "3. Cleaning project build files..."
rm -rf .gradle
rm -rf app/build
rm -rf build

echo ""
echo "✅ Cache cleaned successfully!"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  NEXT STEPS - Do this in Android Studio:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "  1. File → Invalidate Caches..."
echo "  2. Click 'Invalidate and Restart'"
echo "  3. After restart, let it sync automatically"
echo "  4. Build → Rebuild Project"
echo "  5. Run the app"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "The Gradle cache will be rebuilt automatically!"
echo ""

