#!/bin/bash
# Starts the local nocode-saas stack.
# Logs:   ./logs/<service>.log
# PIDs:   ./logs/<service>.pid
# Stop:   ./stopall.sh
#
# Usage:
#   ./startall.sh            # start the whole stack
#   ./startall.sh <service>  # restart a single service (e.g. ./startall.sh core)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"

ALL_SERVICES="config eureka core files entity-processor security multi ui gateway worker adzump"

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
  echo "[$name] started (pid $(cat "$pidfile"))"
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

stop_service() {
  local name="$1"
  local dir="$SCRIPT_DIR/$name"
  local pidfile="$LOG_DIR/$name.pid"

  # The service dir path appears in both the mvn launcher's command line
  # (-Dmaven.multiModuleProjectDirectory=.../<name>) and the forked JVM's
  # (-cp .../<name>/target/classes). Matching it catches every process for this
  # service, including the forked app JVM and any orphans the pidfile missed.
  local pat="$dir[/ ]"

  if [ -f "$pidfile" ]; then
    local pid; pid=$(cat "$pidfile")
    pkill -TERM -P "$pid" 2>/dev/null || true
    kill -TERM "$pid" 2>/dev/null || true
    rm -f "$pidfile"
  fi

  if pgrep -f "$pat" >/dev/null 2>&1; then
    echo "[$name] stopping (pids: $(pgrep -f "$pat" | tr '\n' ' '))"
    pkill -TERM -f "$pat" 2>/dev/null || true
    for ((i=0; i<30; i++)); do
      pgrep -f "$pat" >/dev/null 2>&1 || break
      sleep 1
    done
    if pgrep -f "$pat" >/dev/null 2>&1; then
      echo "[$name] still alive after TERM, sending KILL"
      pkill -KILL -f "$pat" 2>/dev/null || true
    fi
  fi
}

# Known ports (for restart-and-wait). Services without a mapping just skip the wait.
port_for() {
  case "$1" in
    config) echo 8888 ;;
    eureka) echo 9999 ;;
    gateway) echo 8080 ;;
    ui) echo 8002 ;;
    adzump) echo 8012 ;;
    *) echo "" ;;
  esac
}

# Start a single service, applying config's special env when needed, then wait on its port.
start_one() {
  local name="$1"
  if [ "$name" = "config" ]; then
    (
      export EUREKA_INSTANCE_IP_ADDRESS=127.0.0.1
      export EUREKA_INSTANCE_HOSTNAME=localhost
      export SPRING_PROFILES_ACTIVE=native,local
      export SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS="file://$SCRIPT_DIR/configfiles"
      start_service config
    )
  else
    start_service "$name"
  fi
  local port; port=$(port_for "$name")
  [ -n "$port" ] && wait_for_port "$name" "$port"
}

# Single-service restart mode: ./startall.sh <service>
if [ -n "$1" ]; then
  target="$1"
  if ! [[ " $ALL_SERVICES " == *" $target "* ]]; then
    echo "Unknown service '$target'. Valid: $ALL_SERVICES"
    exit 1
  fi
  echo "Restarting [$target]"
  stop_service "$target"
  start_one "$target"
  echo "[$target] restarted. Tail with:  tail -f logs/$target.log"
  exit 0
fi

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
for svc in core files entity-processor security multi ui gateway worker adzump; do
  start_service "$svc"
done

echo
echo "All services launched. Tail a log with:  tail -f logs/<service>.log"
