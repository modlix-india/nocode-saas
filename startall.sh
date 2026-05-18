#!/bin/bash
# Starts the local nocode-saas stack.
# Logs:   ./logs/<service>.log
# PIDs:   ./logs/<service>.pid
# Stop:   ./stopall.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"

start_service() {
  local name="$1"
  local dir="$SCRIPT_DIR/$name"
  local log="$LOG_DIR/$name.log"
  local pidfile="$LOG_DIR/$name.pid"

  if [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then
    echo "[$name] already running (pid $(cat "$pidfile"))"
    return
  fi

  echo "[$name] starting -> $log"
  ( cd "$dir" && nohup mvn spring-boot:run >"$log" 2>&1 & echo $! >"$pidfile" )
}

wait_for_port() {
  local name="$1" port="$2" timeout="${3:-180}"
  echo "[$name] waiting on :$port (timeout ${timeout}s)"
  for ((i=0; i<timeout; i++)); do
    if nc -z localhost "$port" 2>/dev/null; then
      echo "[$name] up on :$port"
      return 0
    fi
    sleep 1
  done
  echo "[$name] WARNING: not responding on :$port after ${timeout}s — continuing anyway"
}

# 1. Config server — needs special env vars (avoids comma-parsing issues in -Dspring-boot.run.arguments)
(
  export EUREKA_INSTANCE_IP_ADDRESS=127.0.0.1
  export EUREKA_INSTANCE_HOSTNAME=localhost
  export SPRING_PROFILES_ACTIVE=native,local
  export SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS="file://$SCRIPT_DIR/configfiles"
  start_service config
)
wait_for_port config 8888 240

# 2. Eureka (service discovery)
start_service eureka
wait_for_port eureka 9999 120

# 3. Everything else — parallel
for svc in core files entity-processor security multi ui gateway worker; do
  start_service "$svc"
done

echo
echo "All services launched. Tail a log with:  tail -f logs/<service>.log"
