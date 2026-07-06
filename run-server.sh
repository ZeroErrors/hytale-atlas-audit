#!/usr/bin/env bash
# Builds the plugin and runs the Hytale server locally.
#
# Usage: ./run-server.sh

set -euo pipefail
cd "$(dirname "$0")"

# Ensure assets are set up
if [ ! -e run/Assets.zip ]; then
  ./setup.sh
fi

# Build
echo "Building..."
./mvnw -B package -q

# Resolve server JAR from local Maven cache
SERVER_VERSION=$(sed -n 's/.*<hytale.server.version>\(.*\)<\/hytale.server.version>.*/\1/p' pom.xml)
SERVER_JAR="$HOME/.m2/repository/com/hypixel/hytale/Server/$SERVER_VERSION/Server-$SERVER_VERSION.jar"

if [ ! -f "$SERVER_JAR" ]; then
  echo "Server JAR not found: $SERVER_JAR"
  echo "Run './mvnw package' to download dependencies."
  exit 1
fi

# Run server with plugin loaded from target/
cd run
exec java -jar "$SERVER_JAR" \
  --disable-sentry --allow-op --assets Assets.zip \
  --mods ../target/
