#!/bin/bash
# Starts the local nocode-saas stack.
# Logs:   ./logs/<service>.log
# PIDs:   ./logs/<service>.pid
# Stop:   ./stopall.sh
#
# Usage:
#   ./startall.sh            # start the whole stack
#   ./startall.sh <service>  # restart a single service (e.g. ./startall.sh core)
#
# 'ui-client' is the React dev server from the nocode-ui repo (npm run local, :1234), not the
# Spring 'ui' service on :8002.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"

# The WhatsApp bridge is Go and lives in its own repo alongside this one. Everything below treats it
# as optional: plenty of people working on nocode-saas have no reason to check it out, and the stack
# must still come up for them.
BRIDGE_DIR="${BRIDGE_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)/whatsapp-bridge}"
BRIDGE_BIN="$BRIDGE_DIR/out/bridge"

# The React client is likewise its own repo, and likewise optional: backend-only work has no reason
# to run a webpack dev server. Named 'ui-client' rather than 'ui' because 'ui' is already taken by
# the Spring UI service on :8002, and confusing the two costs an afternoon.
CLIENT_NAME="ui-client"
CLIENT_DIR="${CLIENT_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)/nocode-ui/ui-app/client}"
# Must match devServer.port in that repo's webpack.local.js. Both the readiness wait and the
# orphan sweep in stop_client key off it.
CLIENT_PORT="${CLIENT_PORT:-1234}"

ALL_SERVICES="config eureka core files entity-processor message security multi ui gateway worker adzump bridge ui-client"

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

# The WhatsApp bridge. Its own function rather than a branch inside start_service, because it
# shares nothing with the Maven path: different repo, different toolchain, and a compiled binary
# instead of a wrapper process.
#
# Built and run natively here, one shard. docker-compose.yml in that repo runs two shards and is the
# better choice when you are specifically testing the things that only break with more than one
# (placement, stray detection, the per-shard store lock). This is the everyday single-shard case,
# and it puts the log where every other service's log is.
#
# Local settings match whatsapp-bridge/docker-compose.yml deliberately, so switching between the two
# does not mean re-pairing: same instance id, same store path, same port. The secrets are the ones
# configfiles/application-default.yml already sets on the message service side, so registration
# works with no environment set on either side.
start_bridge() {
  local log="$LOG_DIR/bridge.log"
  local pidfile="$LOG_DIR/bridge.pid"

  if [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then
    echo "[bridge] already running (pid $(cat "$pidfile"))"
    return 0
  fi

  if [ ! -d "$BRIDGE_DIR" ]; then
    echo "[bridge] skipped: no repo at $BRIDGE_DIR (set BRIDGE_DIR to override)"
    return 0
  fi

  if ! command -v go >/dev/null 2>&1; then
    echo "[bridge] skipped: go is not on PATH"
    return 0
  fi

  # Built rather than 'go run'. go run execs the binary from a temp directory under a name that
  # changes every time, which would leave stop_bridge nothing stable to match on.
  echo "[bridge] building -> $BRIDGE_BIN"
  if ! ( cd "$BRIDGE_DIR" && go build -o "$BRIDGE_BIN" ./cmd/bridge ) >"$log" 2>&1; then
    echo "[bridge] BUILD FAILED — see $log"
    return 0
  fi

  # Store path derives from the instance id rather than hardcoding inst-01. Overriding the id alone
  # would otherwise point a second instance at the first one's device store, and two processes on
  # one store is the unrecoverable case: it corrupts the Signal ratchet beyond a re-pair. The flock
  # inside the bridge would refuse the start, but that presents as a broken bridge rather than as
  # the guard doing its job, so do not rely on it to cover a wrong default here.
  local instance="${BRIDGE_INSTANCE_ID:-inst-01}"
  local store="${BRIDGE_STORE_PATH:-$BRIDGE_DIR/.local/$instance/store.db}"
  mkdir -p "$(dirname "$store")"

  echo "[bridge] starting -> $log"
  (
    cd "$BRIDGE_DIR"
    export BRIDGE_INSTANCE_ID="$instance"
    export BRIDGE_STORE_PATH="$store"
    export BRIDGE_COUNTRIES="${BRIDGE_COUNTRIES:-IN}"
    export BRIDGE_SESSION_CAP="${BRIDGE_SESSION_CAP:-25}"
    export BRIDGE_LISTEN_ADDR="${BRIDGE_LISTEN_ADDR:-:9481}"
    export BRIDGE_SELF_URL="${BRIDGE_SELF_URL:-http://localhost:9481}"
    export BRIDGE_CONTROL_PLANE_URL="${BRIDGE_CONTROL_PLANE_URL:-http://localhost:8010}"
    export BRIDGE_BOOTSTRAP_SECRET="${BRIDGE_BOOTSTRAP_SECRET:-local-bootstrap-secret}"
    export BRIDGE_HMAC_SECRET="${BRIDGE_HMAC_SECRET:-local-hmac-secret}"
    # Shortened from the 5-15s production defaults so a test conversation does not take a minute
    # per message. The delay is the feature; this is the only place it is right to shrink it.
    export BRIDGE_SEND_MIN_DELAY="${BRIDGE_SEND_MIN_DELAY:-1s}"
    export BRIDGE_SEND_MAX_DELAY="${BRIDGE_SEND_MAX_DELAY:-3s}"
    nohup "$BRIDGE_BIN" >>"$log" 2>&1 & echo $! >"$pidfile"
  )
  echo "[bridge] started (pid $(cat "$pidfile"))"
}

# Stopping the bridge matches the binary path exactly, and that precision is load-bearing.
#
# stop_service's generic "$dir[/ ]" pattern must NOT be reused here. Editors and tooling routinely
# carry the repo directory in their own command lines (an IDE with the folder open, or an agent
# invoked with --add-dir pointing at it), so a directory-prefix match would send TERM and then KILL
# to those processes. The built binary path appears only in the bridge's own process.
stop_bridge() {
  local pidfile="$LOG_DIR/bridge.pid"

  if [ -f "$pidfile" ]; then
    local pid; pid=$(cat "$pidfile")
    kill -TERM "$pid" 2>/dev/null || true
    rm -f "$pidfile"
  fi

  if pgrep -xf "$BRIDGE_BIN" >/dev/null 2>&1; then
    echo "[bridge] stopping (pids: $(pgrep -xf "$BRIDGE_BIN" | tr '\n' ' '))"
    pkill -TERM -xf "$BRIDGE_BIN" 2>/dev/null || true
    # Give it room to close the WhatsApp sockets cleanly and release the store lock. An abrupt kill
    # leaves WhatsApp believing the device is still linked, and leaves the flock held until the OS
    # reaps it, which presents on the next start as a mysterious refusal.
    for ((i=0; i<20; i++)); do
      pgrep -xf "$BRIDGE_BIN" >/dev/null 2>&1 || break
      sleep 1
    done
    if pgrep -xf "$BRIDGE_BIN" >/dev/null 2>&1; then
      echo "[bridge] still alive after TERM, sending KILL"
      pkill -KILL -xf "$BRIDGE_BIN" 2>/dev/null || true
    fi
  fi
}

# The React dev server (nocode-ui), via 'npm run local': webpack-dev-server on :1234, proxying
# /api to the local gateway. Its own function for the same reason the bridge has one: different
# repo, different toolchain, and nothing in common with the Maven path.
start_client() {
  local log="$LOG_DIR/$CLIENT_NAME.log"
  local pidfile="$LOG_DIR/$CLIENT_NAME.pid"

  if [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then
    echo "[$CLIENT_NAME] already running (pid $(cat "$pidfile"))"
    return 0
  fi

  if [ ! -d "$CLIENT_DIR" ]; then
    echo "[$CLIENT_NAME] skipped: no repo at $CLIENT_DIR (set CLIENT_DIR to override)"
    return 0
  fi

  if ! command -v npm >/dev/null 2>&1; then
    echo "[$CLIENT_NAME] skipped: npm is not on PATH"
    return 0
  fi

  # Deliberately not running 'npm install' from here. It is a one-time setup step that can take
  # minutes, and mutating node_modules as a side effect of starting the backend stack is the wrong
  # trade, especially when the reason it is missing is usually a half-finished checkout.
  if [ ! -d "$CLIENT_DIR/node_modules" ]; then
    echo "[$CLIENT_NAME] skipped: no node_modules (run 'npm install' in $CLIENT_DIR first)"
    return 0
  fi

  echo "[$CLIENT_NAME] starting -> $log"
  ( cd "$CLIENT_DIR" && nohup npm run local >"$log" 2>&1 & echo $! >"$pidfile" )
  echo "[$CLIENT_NAME] started (pid $(cat "$pidfile"))"
  return 0
}

# TERM a process and every descendant, deepest first.
#
# npm is a wrapper: 'npm run local' runs a shell which runs the webpack node process, so the dev
# server is a grandchild of the recorded pid. TERMing only that pid, or only its direct children
# the way stopall.sh's generic loop does, leaves node alive still holding :1234.
kill_tree() {
  local pid="$1" sig="${2:-TERM}" child
  for child in $(pgrep -P "$pid" 2>/dev/null); do
    kill_tree "$child" "$sig"
  done
  kill -"$sig" "$pid" 2>/dev/null || true
  return 0
}

# Walking the real process tree, and then the listening socket, rather than pattern-matching the
# repo directory the way stop_service does. Same hazard the bridge comment describes, and worse
# here: the nocode-ui path is exactly what an editor or an agent started with --add-dir carries in
# its command line, so 'pgrep -f "$CLIENT_DIR"' would TERM and then KILL the tools you are working
# in. Both mechanisms below can only reach the dev server itself.
stop_client() {
  local pidfile="$LOG_DIR/$CLIENT_NAME.pid"

  if [ -f "$pidfile" ]; then
    local pid; pid=$(cat "$pidfile")
    echo "[$CLIENT_NAME] stopping (pid $pid and descendants)"
    kill_tree "$pid"
    rm -f "$pidfile"
  fi

  # Whatever still holds the port. Catches orphans from a crash, or from a Ctrl-C that took out npm
  # but not the node process underneath it, which is the common case and the one that makes the
  # next start fail with EADDRINUSE.
  local holders
  holders=$(lsof -ti "tcp:$CLIENT_PORT" -sTCP:LISTEN 2>/dev/null || true)
  if [ -n "$holders" ]; then
    echo "[$CLIENT_NAME] :$CLIENT_PORT still held (pids: $(echo $holders | tr '\n' ' '))"
    kill -TERM $holders 2>/dev/null || true
    for ((i=0; i<15; i++)); do
      holders=$(lsof -ti "tcp:$CLIENT_PORT" -sTCP:LISTEN 2>/dev/null || true)
      [ -z "$holders" ] && break
      sleep 1
    done
    if [ -n "$holders" ]; then
      echo "[$CLIENT_NAME] still alive after TERM, sending KILL"
      kill -KILL $holders 2>/dev/null || true
    fi
  fi
  return 0
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

  if [ "$name" = "bridge" ]; then
    stop_bridge
    return
  fi

  if [ "$name" = "$CLIENT_NAME" ]; then
    stop_client
    return
  fi

  local dir="$SCRIPT_DIR/$name"
  local pidfile="$LOG_DIR/$name.pid"

  # The service dir path appears in both the mvn launcher's command line
  # (-Dmaven.multiModuleProjectDirectory=.../<name>) and the forked JVM's
  # (-cp .../<name>/target/classes). Matching it catches every process for this
  # service, including the forked app JVM and any orphans the pidfile missed.
  # Braces so the trailing [/ ] reads as the character class it is, rather than as array indexing.
  local pat="${dir}[/ ]"

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
    bridge) echo 9481 ;;
    "$CLIENT_NAME") echo "$CLIENT_PORT" ;;
    *) echo "" ;;
  esac
}

# Start a single service, applying config's special env when needed, then wait on its port.
start_one() {
  local name="$1"
  if [ "$name" = "bridge" ]; then
    start_bridge
    # Only wait when it actually launched. A skipped bridge has no port and would otherwise burn
    # the full timeout telling you something is wrong when nothing is.
    [ -f "$LOG_DIR/bridge.pid" ] && wait_for_port bridge 9481 30
    return
  fi
  if [ "$name" = "$CLIENT_NAME" ]; then
    start_client
    # 300s, well past the other services' 180. This is a cold webpack build of the whole client,
    # not a JVM coming up. Same reason as the bridge for gating on the pidfile: a skipped client
    # has no port, and burning the timeout would report a problem where there is none.
    [ -f "$LOG_DIR/$CLIENT_NAME.pid" ] && wait_for_port "$CLIENT_NAME" "$CLIENT_PORT" 300
    return
  fi
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
for svc in core files entity-processor message security multi ui gateway worker adzump; do
  start_service "$svc"
done

# 4. The WhatsApp bridge, last and outside the loop: it is a different repo and toolchain, it needs
# a build first, and it registers against the message service started just above. Registration
# retries, so it does not matter that message is still coming up.
start_bridge

# 5. The React dev server. Last because it is the slowest to become useful and the least urgent:
# it proxies /api to the gateway rather than calling anything at boot, so it does not care that the
# backend above is still starting. No wait here: the whole-stack path does not block on any of the
# parallel services either, and a cold webpack build would add minutes to it.
start_client

echo
echo "All services launched. Tail a log with:  tail -f logs/<service>.log"
[ -f "$LOG_DIR/$CLIENT_NAME.pid" ] && echo "UI dev server building on http://localhost:$CLIENT_PORT. Tail with:  tail -f logs/$CLIENT_NAME.log"
exit 0
