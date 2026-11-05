#!/usr/bin/env sh
# EventPilot launcher (macOS/Linux). No arguments = GUI, otherwise CLI, e.g.  ./eventpilot.sh event list
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$DIR/target/eventpilot.jar"
if [ ! -f "$JAR" ]; then
  echo "Building EventPilot (first run)..."
  "$DIR/mvnw" -q -DskipTests package || exit 1
fi
exec java -jar "$JAR" "$@"
