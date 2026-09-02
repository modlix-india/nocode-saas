#!/bin/bash
# Stops services started by startall.sh, including the React dev server (logs/ui-client.pid).

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"

CLIENT_NAME="ui-client"
CLIENT_PORT="${CLIENT_PORT:-1234}"

# TERM a process and every descendant, deepest first. Replaces the plain 'pkill -TERM -P' this loop
# used to do, which reached only one level down: 'npm run local' runs a shell which runs the webpack
# node process, so the dev server is a grandchild and survived. Maven's forked app JVMs are a direct
# child today, so this changes nothing for them, but it costs nothing either.
kill_tree() {
  local pid="$1" sig="${2:-TERM}" child
  for child in $(pgrep -P "$pid" 2>/dev/null); do
    kill_tree "$child" "$sig"
  done
  kill -"$sig" "$pid" 2>/dev/null || true
  return 0
}

for pidfile in "$LOG_DIR"/*.pid; do
  [ -f "$pidfile" ] || continue
  name=$(basename "$pidfile" .pid)
  pid=$(cat "$pidfile")
  if kill -0 "$pid" 2>/dev/null; then
    echo "[$name] stopping pid $pid"
    kill_tree "$pid"
  else
    echo "[$name] not running"
  fi
  rm -f "$pidfile"
done

# The dev-server port, separately from the pidfile loop above. A Ctrl-C in the terminal that started
# it, or a crashed npm, routinely leaves the node process orphaned and still listening, which shows
# up next time as EADDRINUSE rather than as anything pointing back here. Matching on the listening
# socket is exact, so this can only reach the dev server; a 'pgrep -f' over the nocode-ui path would
# also match editors and agents that have the repo open.
holders=$(lsof -ti "tcp:$CLIENT_PORT" -sTCP:LISTEN 2>/dev/null)
if [ -n "$holders" ]; then
  echo "[$CLIENT_NAME] :$CLIENT_PORT still held (pids: $(echo $holders | tr '\n' ' '))"
  kill -TERM $holders 2>/dev/null
  for ((i=0; i<15; i++)); do
    holders=$(lsof -ti "tcp:$CLIENT_PORT" -sTCP:LISTEN 2>/dev/null)
    [ -z "$holders" ] && break
    sleep 1
  done
  if [ -n "$holders" ]; then
    echo "[$CLIENT_NAME] still alive after TERM, sending KILL"
    kill -KILL $holders 2>/dev/null
  fi
fi
