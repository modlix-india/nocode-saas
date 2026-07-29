#!/usr/bin/env bash
#
# app-transport.sh — export a Modlix app from one environment and import it into another
#                    (same-app promotion: appCode / clientCode are preserved).
#
# Export and import are done per service, directly against each service:
#   ui   = pages, styles, themes, uri paths ...       (/api/ui/transports)
#   core = functions, storages, schemas, events ...   (/api/core/transports)
#
#   export  ->  POST /api/{svc}/transports/makeTransport   {appCode,clientCode}  -> zip
#   import  ->  POST /api/{svc}/transports/createAndApply   (multipart file=zip)
#
# Only the parts you ask for are fetched and applied. Security roles/permissions are
# NOT transported (that transport is a server-side no-op). The target app row is
# created automatically if it does not exist yet. Applying a transport is an upsert,
# so re-running overwrites the target's definitions.
#
# Usage:
#   app-transport.sh <from-env> <to-env> <appCode> [ui|core|both] [clientCode]
#
#     from-env / to-env : local | dev | stage | prod
#     appCode           : app to move (e.g. leadzump, appbuilder)
#     ui|core|both      : which definitions to move    (default: both)
#     clientCode        : owner client of the app       (default: SYSTEM)
#
#   (the last two are position-independent — "both"/"ui"/"core" is recognised
#    as the parts flag wherever it appears; anything else is the clientCode.)
#
# Examples:
#   app-transport.sh dev prod leadzump
#   app-transport.sh local dev myapp ui
#   app-transport.sh dev stage leadzump both FIN
#
# Secrets: passwords are read from ~/.nocode-saas/variables.sh (never stored here).

set -euo pipefail

# ---------------------------------------------------------------------------
# Per-environment identity (non-secret). Passwords come from variables.sh.
#   local + dev  -> kiran@modlix.com    (userId 142, client SYSTEM)
#   stage + prod -> sysadmin@modlix.com (userId 1,   client SYSTEM)
# ---------------------------------------------------------------------------
CONTEXT_APP="appbuilder"   # builder app the API calls run under (as the UI does)
AUTH_CLIENT="SYSTEM"       # the login user's own client (same on every env)

env_url() {
  case "$1" in
    local) echo "https://apps.local.modlix.com" ;;
    dev)   echo "https://apps.dev.modlix.com" ;;
    stage) echo "https://apps.stage.modlix.com" ;;
    prod)  echo "https://apps.modlix.com" ;;
    *) return 1 ;;
  esac
}
env_email()  { case "$1" in local|dev) echo "kiran@modlix.com" ;; stage|prod) echo "sysadmin@modlix.com" ;; *) return 1 ;; esac; }
env_userid() { case "$1" in local|dev) echo "142" ;;             stage|prod) echo "1" ;;                  *) return 1 ;; esac; }
env_password() {
  case "$1" in
    local) echo "${MODLIX_LOCAL_PASSWORD:-}" ;;
    dev)   echo "${MODLIX_DEV_PASSWORD:-}" ;;
    stage) echo "${MODLIX_STAGE_PASSWORD:-}" ;;
    prod)  echo "${MODLIX_PROD_PASSWORD:-}" ;;
    *) return 1 ;;
  esac
}

# ---------------------------------------------------------------------------
die()  { echo "error: $*" >&2; exit 1; }
info() { echo "==> $*" >&2; }

for bin in curl jq; do command -v "$bin" >/dev/null 2>&1 || die "$bin is required but not installed"; done

# ---- args -----------------------------------------------------------------
FROM="${1:-}"; TO="${2:-}"; APP="${3:-}"
PARTS="both"; CLIENT="SYSTEM"
for a in "${4:-}" "${5:-}"; do
  case "$a" in
    "")           ;;
    ui|core|both) PARTS="$a" ;;
    *)            CLIENT="$a" ;;
  esac
done

[ -n "$FROM" ] && [ -n "$TO" ] && [ -n "$APP" ] || {
  sed -n '3,38p' "$0" | sed 's/^# \{0,1\}//'; exit 1;
}
env_url "$FROM" >/dev/null 2>&1 || die "unknown from-env '$FROM' (use: local dev stage prod)"
env_url "$TO"   >/dev/null 2>&1 || die "unknown to-env '$TO' (use: local dev stage prod)"
[ "$FROM" != "$TO" ] || die "from-env and to-env are the same ($FROM)"

# which services to move, in dependency order (core before ui)
case "$PARTS" in
  core) SERVICES="core" ;;
  ui)   SERVICES="ui" ;;
  both) SERVICES="core ui" ;;
esac

# ---- secrets --------------------------------------------------------------
VARS="${HOME}/.nocode-saas/variables.sh"
[ -f "$VARS" ] || die "missing $VARS (create it with the MODLIX_*_PASSWORD vars)"
# shellcheck disable=SC1090
. "$VARS"

SRC_URL="$(env_url "$FROM")"; DST_URL="$(env_url "$TO")"

info "Transport '$APP' (client $CLIENT, parts: $PARTS)"
info "  from  $FROM  $SRC_URL   as $(env_email "$FROM")"
info "  to    $TO    $DST_URL   as $(env_email "$TO")"

# ---- confirm when writing to prod -----------------------------------------
if [ "$TO" = "prod" ] && [ "${FORCE:-0}" != "1" ]; then
  printf 'This IMPORTS into PRODUCTION (%s). Type the appCode to proceed: ' "$APP" >&2
  read -r ans
  [ "$ans" = "$APP" ] || die "aborted"
fi

# ---- helpers --------------------------------------------------------------
# login <env> -> echoes accessToken, dies on failure
login() {
  local env base email uid pw resp token
  env="$1"; base="$(env_url "$env")"; email="$(env_email "$env")"
  uid="$(env_userid "$env")"; pw="$(env_password "$env")"
  [ -n "$pw" ] || die "no password set for '$env' in $VARS (MODLIX_$(echo "$env" | tr a-z A-Z)_PASSWORD)"

  resp="$(curl -sS -X POST "$base/api/security/authenticate" \
      -H 'Content-Type: application/json' \
      -H "appCode: $CONTEXT_APP" -H "clientCode: $AUTH_CLIENT" \
      -d "{\"userName\":\"$email\",\"userId\":$uid,\"password\":\"$pw\"}")" \
      || die "login request to $env failed"
  token="$(printf '%s' "$resp" | jq -r '.accessToken // empty')"
  [ -n "$token" ] || die "login to $env failed: $(printf '%s' "$resp" | jq -rc '.message // .' 2>/dev/null | head -c 300)"
  printf '%s' "$token"
}

authed() { curl -sS -w '\n%{http_code}' "$@"; }         # appends HTTP status on last line
status() { printf '%s' "$1" | tail -n1; }
body()   { printf '%s' "$1" | sed '$d'; }

WORK="$(mktemp -d "${TMPDIR:-/tmp}/modl.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
ext_for() { case "$1" in core) echo cmodl ;; ui) echo umodl ;; esac; }

# ===========================================================================
# 1. SOURCE — login, read app metadata, export the requested parts
# ===========================================================================
info "Authenticating on $FROM ..."
SRC_TOKEN="$(login "$FROM")"

info "Reading app metadata from $FROM ..."
META_RESP="$(authed "$SRC_URL/api/security/applications/appCode/$APP" \
    -H "Authorization: Bearer $SRC_TOKEN" -H "appCode: $CONTEXT_APP" -H "clientCode: $AUTH_CLIENT")"
[ "$(status "$META_RESP")" = "200" ] || die "app '$APP' not found on $FROM (HTTP $(status "$META_RESP"))"
APP_NAME="$(body "$META_RESP" | jq -r '.appName // empty')"
APP_TYPE="$(body "$META_RESP" | jq -r '.appType // "APP"')"
APP_ACCESS="$(body "$META_RESP" | jq -r '.appAccessType // "OWN"')"
info "  $APP  \"$APP_NAME\"  type=$APP_TYPE  access=$APP_ACCESS"

for svc in $SERVICES; do
  out="$WORK/$svc.$(ext_for "$svc")"
  info "Exporting $svc definitions from $FROM ..."
  st="$(curl -sS -o "$out" -w '%{http_code}' -X POST \
      "$SRC_URL/api/$svc/transports/makeTransport" \
      -H 'Content-Type: application/json' \
      -H "Authorization: Bearer $SRC_TOKEN" -H "appCode: $CONTEXT_APP" -H "clientCode: $AUTH_CLIENT" \
      -d "{\"appCode\":\"$APP\",\"clientCode\":\"$CLIENT\"}")"
  [ "$st" = "200" ] || die "$svc export failed (HTTP $st): $(head -c 300 "$out")"
  [ "$(head -c2 "$out")" = "PK" ] || die "$svc export did not return a zip: $(head -c 300 "$out")"
  info "  $svc: $(wc -c <"$out" | tr -d ' ') bytes"
done

# ===========================================================================
# 2. TARGET — login, create app if missing, apply the requested parts
# ===========================================================================
info "Authenticating on $TO ..."
DST_TOKEN="$(login "$TO")"

info "Checking whether '$APP' exists on $TO ..."
CHK="$(authed "$DST_URL/api/security/applications/appCode/$APP" \
    -H "Authorization: Bearer $DST_TOKEN" -H "appCode: $CONTEXT_APP" -H "clientCode: $AUTH_CLIENT")"
if [ "$(status "$CHK")" = "200" ] && [ -n "$(body "$CHK" | jq -r '.appCode // empty')" ]; then
  info "  exists — definitions will be overwritten"
else
  info "  not found — creating it"
  CREATE="$(authed -X POST "$DST_URL/api/multi/application" \
      -H 'Content-Type: application/json' \
      -H "Authorization: Bearer $DST_TOKEN" -H "appCode: $CONTEXT_APP" -H "clientCode: $AUTH_CLIENT" \
      -d "{\"appCode\":\"$APP\",\"appName\":$(jq -Rn --arg n "${APP_NAME:-$APP}" '$n'),\"appType\":\"$APP_TYPE\",\"appAccessType\":\"$APP_ACCESS\"}")"
  case "$(status "$CREATE")" in
    200|201) info "  created" ;;
    *) die "app create failed (HTTP $(status "$CREATE")): $(body "$CREATE" | jq -rc '.message // .' 2>/dev/null | head -c 300)" ;;
  esac
fi

for svc in $SERVICES; do
  file="$WORK/$svc.$(ext_for "$svc")"
  info "Applying $svc definitions to $TO ..."
  resp="$(authed -X POST \
      "$DST_URL/api/$svc/transports/createAndApply?isForBaseApp=true&applicationCode=$APP" \
      -H "Authorization: Bearer $DST_TOKEN" -H "appCode: $CONTEXT_APP" -H "clientCode: $AUTH_CLIENT" \
      -F "file=@$file")"
  case "$(status "$resp")" in
    200|201) info "  $svc ok" ;;
    *) die "$svc apply failed (HTTP $(status "$resp")): $(body "$resp" | jq -rc '.message // .' 2>/dev/null | head -c 300)" ;;
  esac
done

info "Done. '$APP' promoted $FROM -> $TO ($PARTS)."
