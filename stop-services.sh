#!/usr/bin/env bash
#
# SCB LC Checker — stop script for the running Java application.
#
#   1) find the process listening on the app port (8080) and stop it
#   2) kill any lingering launch methods (mvn spring-boot:run / java -jar lc-checker)
#
# Usage:
#   ./stop-services.sh
#
set -euo pipefail

# ---------- config ----------
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_PORT=8080
# ----------------------------

cd "$PROJECT_DIR"

echo "Stopping SCB LC Checker Java application ..."

# a) free the app port
OLD_PID="$(lsof -tiTCP:"$APP_PORT" -sTCP:LISTEN 2>/dev/null || true)"
if [ -n "$OLD_PID" ]; then
  echo "  - found listener on port $APP_PORT: PID $OLD_PID"
  echo "  - sending SIGTERM ..."
  kill "$OLD_PID" 2>/dev/null || true
  sleep 2
  if kill -0 "$OLD_PID" 2>/dev/null; then
    echo "  - still alive, sending SIGKILL ..."
    kill -9 "$OLD_PID" 2>/dev/null || true
  fi
else
  echo "  - no process listening on port $APP_PORT."
fi

# b) kill lingering launch methods (mvn spring-boot:run / java -jar lc-checker)
echo "  - cleaning up any lingering launch processes ..."
pkill -f 'spring-boot:run'    2>/dev/null || true
pkill -f 'lc-checker.*\.jar' 2>/dev/null || true
sleep 1

# c) verify
REMAINING="$(lsof -tiTCP:"$APP_PORT" -sTCP:LISTEN 2>/dev/null || true)"
if [ -n "$REMAINING" ]; then
  echo "  WARNING: port $APP_PORT is still held by PID $REMAINING."
  exit 1
fi

echo "Done. Java application stopped."
