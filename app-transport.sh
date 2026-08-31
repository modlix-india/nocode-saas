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
# created automatically if it does not exist yet. Applying a transport is an upsert
# keyed on the object's name, so re-running overwrites the target's definitions and
# never deletes anything the transport does not carry.
#
# Usage:
#   app-transport.sh <from-env> <to-env> <appCode> [ui|core|both] [clientCode]
#                    [--objects TYPE=name1,name2 ...] [--chunk N] [--keep-going] [--verify]
#
#     from-env / to-env : local | dev | stage | prod
#     appCode           : app to move (e.g. leadzump, appbuilder)
#     ui|core|both      : which definitions to move    (default: both)
#     clientCode        : owner client of the app       (default: SYSTEM)
#     --objects         : move ONLY these named objects (default: the whole app).
#                         Repeatable, and repeats of the same TYPE are merged.
#                         TYPE is the transport folder name; for ui those are
#                         Application, Filler, Function, Page, Schema, Style,
#                         Theme, URIPath. Names are the object names as the app
#                         knows them (page name, "app.functionName", theme name).
#                         NOTE: URIPath cannot be scoped by the name you see in
#                         the zip. Its files are named by id (URIPath overrides
#                         getTransportName()) while the export filter matches on
#                         the name field, so --objects URIPath=<id> matches
#                         nothing and still answers 200. Scope URIPaths by their
#                         actual route, e.g. --objects URIPath=/api/exotel/call
#     --chunk N         : objects per apply request     (default: 30)
#     --keep-going      : do not stop at the first chunk that fails; report a
#                         summary of everything that did not apply, and exit 1
#     --verify          : after applying, re-export from the target and confirm
#                         every source object is present
#
#   (the parts and clientCode args are position-independent — "both"/"ui"/"core" is
#    recognised as the parts flag wherever it appears; anything else is the clientCode.)
#
# Scoping with --objects is the safe way to promote work into an environment that has
# moved on independently: nothing outside the named objects is written.
#
# Examples:
#   app-transport.sh dev prod leadzump
#   app-transport.sh local dev myapp ui
#   app-transport.sh dev stage leadzump both FIN
#   app-transport.sh dev stage leadzump --chunk 15 --verify
#   app-transport.sh local dev leadzump ui --objects Page=shell,dealProfile
#   app-transport.sh dev local leadzump ui --objects Page=home,leads --objects Theme=themeFile
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

# Non-local environments are addressed by their gateway on the VCN, not through the
# public edge, so this script needs the VPN.
#
# Not a preference. The edge's generic "location ~ /api/" block sets no
# proxy_read_timeout, so nginx's 60s default applies, and applying one large page is
# slower than that: leadzump's dealProfile is 1532 components and took 52s on dev and
# 62s on stage, where it was cut off mid-write with a 504 and nothing committed. The
# neighbouring "location ~* /api/core/function/" block already carries an explicit
# 300s for the same reason. The gateway is exactly what nginx proxies to, so going
# direct drops the timeout and nothing else.
env_url() {
  case "$1" in
    local) echo "https://apps.local.modlix.com" ;;
    dev)   echo "http://dev.sub10150624021.modlixvcn.oraclevcn.com:8080" ;;
    stage) echo "http://stage.sub10150624021.modlixvcn.oraclevcn.com:8080" ;;
    prod)  echo "http://prod.sub10150624021.modlixvcn.oraclevcn.com:8080" ;;
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

for bin in curl jq zip unzip; do command -v "$bin" >/dev/null 2>&1 || die "$bin is required but not installed"; done

# ---- args -----------------------------------------------------------------
FROM="${1:-}"; TO="${2:-}"; APP="${3:-}"
[ $# -ge 3 ] && shift 3 || set --
PARTS="both"; CLIENT="SYSTEM"; OBJECT_SPECS=""; CHUNK=30; KEEP_GOING=0; VERIFY=0
while [ $# -gt 0 ]; do
  case "$1" in
    "")            ;;
    --objects|-o)  shift; [ $# -gt 0 ] || die "--objects needs TYPE=name1,name2"
                   OBJECT_SPECS="${OBJECT_SPECS}${1}"$'\n' ;;
    --objects=*)   OBJECT_SPECS="${OBJECT_SPECS}${1#--objects=}"$'\n' ;;
    --chunk|-n)    shift; [ $# -gt 0 ] || die "--chunk needs a number"; CHUNK="$1" ;;
    --chunk=*)     CHUNK="${1#--chunk=}" ;;
    --keep-going)  KEEP_GOING=1 ;;
    --verify)      VERIFY=1 ;;
    ui|core|both)  PARTS="$1" ;;
    -*)            die "unknown option '$1'" ;;
    *)             CLIENT="$1" ;;
  esac
  shift
done

case "$CHUNK" in
  ''|*[!0-9]*) die "--chunk must be a positive whole number (got '$CHUNK')" ;;
esac
[ "$CHUNK" -gt 0 ] || die "--chunk must be greater than 0"

[ -n "$FROM" ] && [ -n "$TO" ] && [ -n "$APP" ] || {
  sed -n '3,47p' "$0" | sed 's/^# \{0,1\}//'; exit 1;
}

# --- objectList: {"Page":["a","b"],"Theme":["c"]} — empty means "the whole app" ---
OBJECT_LIST_JSON=""
if [ -n "$OBJECT_SPECS" ]; then
  OBJECT_LIST_JSON="$(printf '%s' "$OBJECT_SPECS" | jq -Rn '
      [ inputs
        | select(length > 0)
        | (index("=")) as $i
        | if $i == null then ("bad --objects spec: " + .) | error else . end
        | {k: .[:$i], v: (.[$i+1:] | split(",") | map(select(length > 0)))} ]
      | group_by(.k)
      | map({key: .[0].k, value: (map(.v) | add | unique)})
      | from_entries')" || die "could not parse --objects (use TYPE=name1,name2)"
  [ "$(printf '%s' "$OBJECT_LIST_JSON" | jq -r 'to_entries | map(select(.value | length > 0)) | length')" != "0" ] \
    || die "--objects listed no names"
fi
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
if [ -n "$OBJECT_LIST_JSON" ]; then
  info "  scoped to:"
  printf '%s' "$OBJECT_LIST_JSON" \
    | jq -r 'to_entries[] | "      \(.key) (\(.value | length)): \(.value | join(", "))"' >&2
else
  info "  scope: whole app"
fi
info "  applying up to $CHUNK objects per request"

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

EXPORT_BODY="$(jq -nc --arg a "$APP" --arg c "$CLIENT" \
    --argjson ol "${OBJECT_LIST_JSON:-null}" \
    '{appCode: $a, clientCode: $c} + (if $ol == null then {} else {objectList: $ol} end)')"

APPLY_SERVICES=""
for svc in $SERVICES; do
  out="$WORK/$svc.$(ext_for "$svc")"
  info "Exporting $svc definitions from $FROM ..."
  st="$(curl -sS -o "$out" -w '%{http_code}' -X POST \
      "$SRC_URL/api/$svc/transports/makeTransport" \
      -H 'Content-Type: application/json' \
      -H "Authorization: Bearer $SRC_TOKEN" -H "appCode: $CONTEXT_APP" -H "clientCode: $AUTH_CLIENT" \
      -d "$EXPORT_BODY")"
  [ "$st" = "200" ] || die "$svc export failed (HTTP $st): $(head -c 300 "$out")"
  [ "$(head -c2 "$out")" = "PK" ] || die "$svc export did not return a zip: $(head -c 300 "$out")"
  # a scoped export that matched nothing is still a valid (empty) zip
  count="$(unzip -Z1 "$out" 2>/dev/null | grep -c '/.*\.json$' || true)"
  if [ "$count" -eq 0 ]; then
    info "  $svc: no matching objects, skipping"
    continue
  fi
  info "  $svc: $count objects, $(wc -c <"$out" | tr -d ' ') bytes"
  APPLY_SERVICES="$APPLY_SERVICES $svc"
done
[ -n "${APPLY_SERVICES// /}" ] \
  || die "nothing to transport — check the --objects TYPE names and object names"

# ===========================================================================
# 2. TARGET — login, create app if missing, apply the requested parts
# ===========================================================================
info "Authenticating on $TO ..."
DST_TOKEN="$(login "$TO")"

info "Checking whether '$APP' exists on $TO ..."
CHK="$(authed "$DST_URL/api/security/applications/appCode/$APP" \
    -H "Authorization: Bearer $DST_TOKEN" -H "appCode: $CONTEXT_APP" -H "clientCode: $AUTH_CLIENT")"
if [ "$(status "$CHK")" = "200" ] && [ -n "$(body "$CHK" | jq -r '.appCode // empty')" ]; then
  if [ -n "$OBJECT_LIST_JSON" ]; then
    info "  exists — only the named objects will be overwritten"
  else
    info "  exists — definitions will be overwritten"
  fi
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

# ===========================================================================
# 3. APPLY — in chunks
#
# Applying a whole grown-up app in one request outruns the target's nginx
# proxy_read_timeout (~60s on dev/stage/prod) and comes back 504. That is not a
# "slow but applied": WebFlux cancels the chain when the client disconnects, so
# a 504 leaves the app partially written or untouched. Measured on stage with
# leadzump ui: 30 pages 57s ok, 60 pages 64s ok, 100 pages 504.
#
# So the exported zip is split locally into chunks of at most $CHUNK objects and
# applied one at a time. Splitting the zip rather than re-exporting each chunk
# with an objectList keeps the bytes exactly as the server produced them, costs
# one export instead of N, and avoids scoping by object name -- which silently
# matches nothing for a type that overrides getTransportName() (URIPath names
# its files by id, so --objects URIPath=<id> applies nothing and returns 200).
# ===========================================================================

# Type folders in the order each service's getServieMap() applies them. Apply is
# a per-object upsert, so order does not affect correctness; this only keeps the
# sequence recognisable. Folders not listed here are applied last, sorted.
TYPE_ORDER_ui="Application Page Style Theme Function Schema Filler URIPath"
TYPE_ORDER_core="Template Storage Function Schema EventAction EventDefinition Filler"

FAILED_LIST=""

# ordered_entries <svc> <tree> — echo relative "Type/name.json" paths, one per line
ordered_entries() {
  local svc="$1" tree="$2" known seen="" t
  eval "known=\"\${TYPE_ORDER_${svc}:-}\""
  for t in $known; do
    [ -d "$tree/$t" ] || continue
    ( cd "$tree" && find "$t" -maxdepth 1 -name '*.json' | LC_ALL=C sort )
    seen="$seen $t"
  done
  for t in $( cd "$tree" && find . -maxdepth 1 -type d ! -name . | sed 's|^\./||' | LC_ALL=C sort ); do
    case " $seen " in *" $t "*) continue ;; esac
    ( cd "$tree" && find "$t" -maxdepth 1 -name '*.json' | LC_ALL=C sort )
  done
}

# summarise <entries...> — "Page x30" / "Theme x3, Filler x1"
summarise() {
  printf '%s\n' "$@" | sed 's|/.*||' | LC_ALL=C uniq -c \
    | awk '{printf "%s%s x%s", (NR>1 ? ", " : ""), $2, $1}'
}

# build_chunk <tree> <out.zip> <base-code> <index> <entries...>
build_chunk() {
  local tree="$1" out="$2" base="$3" idx="$4"; shift 4
  local stage rel
  stage="$(mktemp -d "$WORK/chunk.XXXXXX")"
  for rel in "$@"; do
    mkdir -p "$stage/$(dirname "$rel")"
    cp "$tree/$rel" "$stage/$rel"
  done
  # Every chunk needs its OWN uniqueTransportCode. AbstractTransportService.create()
  # looks the code up first and returns the EXISTING transport when it matches, and
  # the controller then applies whatever that stored document holds. Reuse one code
  # across chunks and chunks 2..N each silently re-apply chunk 1's zip, answering 200.
  jq -c --arg c "${base}c$(printf '%03d' "$idx")" '.uniqueTransportCode = $c' \
      "$tree/transport.json" > "$stage/transport.json" \
      || die "could not rewrite transport.json for chunk $idx"
  rm -f "$out"
  ( cd "$stage" && zip -q -r -X "$out" . ) || die "could not build chunk zip $idx"
  rm -rf "$stage"
}

# apply_file <svc> <file> — echoes "<http-status> <seconds>", never fails
apply_file() {
  local svc="$1" file="$2" resp
  if ! resp="$(curl -sS -o "$WORK/apply.body" -w '%{http_code} %{time_total}' -X POST \
      "$DST_URL/api/$svc/transports/createAndApply?isForBaseApp=true&applicationCode=$APP" \
      -H "Authorization: Bearer $DST_TOKEN" -H "appCode: $CONTEXT_APP" -H "clientCode: $AUTH_CLIENT" \
      -F "file=@$file" 2>/dev/null)"; then
    printf '000 0'
    return 0
  fi
  printf '%s' "$resp"
}

CHUNK_SEQ=0

# apply_chunk <svc> <tree> <base-code> <entries...>
apply_chunk() {
  local svc="$1" tree="$2" base="$3"; shift 3
  local n=$# half out st secs what
  [ "$n" -gt 0 ] || return 0

  CHUNK_SEQ=$((CHUNK_SEQ + 1))
  local idx="$CHUNK_SEQ" zipf="$WORK/$svc-chunk-$CHUNK_SEQ.zip"
  build_chunk "$tree" "$zipf" "$base" "$idx" "$@"

  out="$(apply_file "$svc" "$zipf")"
  st="${out%% *}"; secs="${out##* }"
  what="$(summarise "$@")"

  case "$st" in
    200|201) info "  chunk $idx: $what — ok (${secs}s)"; return 0 ;;
  esac

  # A timeout or an overloaded target is worth retrying smaller: apply is an
  # upsert keyed on the object name and never deletes, so re-sending objects
  # that may already have landed is safe.
  case "$st" in
    000|502|503|504)
      if [ "$n" -gt 1 ]; then
        info "  chunk $idx: $what — HTTP $st after ${secs}s, splitting and retrying"
        half=$(( (n + 1) / 2 ))
        apply_chunk "$svc" "$tree" "$base" "${@:1:half}"
        apply_chunk "$svc" "$tree" "$base" "${@:half+1}"
        return 0
      fi
      ;;
  esac

  local detail
  detail="$(jq -rc '.message // empty' "$WORK/apply.body" 2>/dev/null | head -c 300 || true)"
  [ -n "$detail" ] || detail="$(head -c 200 "$WORK/apply.body" 2>/dev/null | tr -d '\n' || true)"
  info "  chunk $idx: $what — FAILED (HTTP $st after ${secs}s) ${detail:+: $detail}"
  FAILED_LIST="${FAILED_LIST}$(printf '%s\n' "$@" | sed "s|^|      $svc/|")"$'\n'

  if [ "$KEEP_GOING" != "1" ]; then
    echo "error: $svc apply failed on chunk $idx (HTTP $st). Objects in that chunk:" >&2
    printf '%s\n' "$@" | sed 's|^|      |' >&2
    echo "  Everything logged 'ok' above did land. Apply is an idempotent upsert," >&2
    echo "  so re-running this command is safe; add --keep-going to push past failures." >&2
    exit 1
  fi
  return 0
}

# verify_applied <svc> <source-tree> — re-export the target and diff object names
verify_applied() {
  local svc="$1" tree="$2" out ttree st missing count
  out="$WORK/$svc-target.$(ext_for "$svc")"
  info "Verifying $svc on $TO ..."
  st="$(curl -sS -o "$out" -w '%{http_code}' -X POST "$DST_URL/api/$svc/transports/makeTransport" \
      -H 'Content-Type: application/json' \
      -H "Authorization: Bearer $DST_TOKEN" -H "appCode: $CONTEXT_APP" -H "clientCode: $AUTH_CLIENT" \
      -d "$(jq -nc --arg a "$APP" --arg c "$CLIENT" '{appCode: $a, clientCode: $c}')")" || st="000"
  if [ "$st" != "200" ]; then
    info "  could not re-export $svc from $TO (HTTP $st) — skipping verify"
    return 0
  fi
  ttree="$WORK/$svc-target-tree"; mkdir -p "$ttree"
  unzip -q -o "$out" -d "$ttree" || { info "  target export is not a readable zip — skipping verify"; return 0; }

  # Compare the name INSIDE each JSON, never the filename: URIPath files are
  # named by id, and the apply creates a fresh document, so the same route has a
  # different id in each environment.
  names_of() {
    find "$1" -mindepth 2 -maxdepth 2 -name '*.json' 2>/dev/null | while IFS= read -r f; do
      printf '%s\t%s\n' "$(basename "$(dirname "$f")")" "$(jq -r '.name // empty' "$f" 2>/dev/null)"
    done | LC_ALL=C sort -u
  }

  missing="$(comm -23 <(names_of "$tree") <(names_of "$ttree") || true)"
  count="$(names_of "$tree" | grep -c . || true)"
  if [ -n "$missing" ]; then
    info "  MISSING on $TO after apply:"
    printf '%s\n' "$missing" | sed 's/^/      /' >&2
    return 1
  fi
  info "  all $count $svc objects present on $TO"
  return 0
}

VERIFY_FAILED=0
for svc in $APPLY_SERVICES; do
  file="$WORK/$svc.$(ext_for "$svc")"
  info "Applying $svc definitions to $TO ..."

  tree="$WORK/$svc-tree"
  mkdir -p "$tree"
  unzip -q -o "$file" -d "$tree" || die "could not unpack the exported $svc transport"
  [ -f "$tree/transport.json" ] || die "$svc transport has no transport.json"
  base="$(jq -r '.uniqueTransportCode // empty' "$tree/transport.json")"
  [ -n "$base" ] || die "$svc transport.json carries no uniqueTransportCode"

  entries=()
  while IFS= read -r line; do
    [ -n "$line" ] && entries+=("$line")
  done < <(ordered_entries "$svc" "$tree")

  if [ "${#entries[@]}" -eq 0 ]; then
    info "  $svc: nothing to apply"
    continue
  fi
  info "  ${#entries[@]} objects, up to $CHUNK per request"

  i=0
  # The first apply into an environment is far slower than the rest, and by a
  # margin that has nothing to do with payload: 2 tiny pages cold took 53s while
  # 30 pages warm took 10s. (Most likely the un-indexed uniqueTransportCode
  # lookup in AbstractTransportService.create() scanning the transport
  # collection off disk, which is then served from cache.) So spend the cold
  # request on a single object instead of gambling a full chunk on it.
  if [ "${#entries[@]}" -gt 1 ] && [ "$CHUNK" -gt 1 ]; then
    apply_chunk "$svc" "$tree" "$base" "${entries[0]}"
    i=1
  fi
  while [ "$i" -lt "${#entries[@]}" ]; do
    remaining=$(( ${#entries[@]} - i ))
    take=$(( remaining < CHUNK ? remaining : CHUNK ))
    apply_chunk "$svc" "$tree" "$base" "${entries[@]:i:take}"
    i=$(( i + take ))
  done

  if [ "$VERIFY" = "1" ]; then
    verify_applied "$svc" "$tree" || VERIFY_FAILED=1
  fi
done

if [ -n "${FAILED_LIST//[[:space:]]/}" ]; then
  echo "error: some objects did not apply:" >&2
  printf '%s' "$FAILED_LIST" >&2
  exit 1
fi
[ "$VERIFY_FAILED" = "0" ] || die "verify found objects missing on $TO"

if [ -n "$OBJECT_LIST_JSON" ]; then
  info "Done. '$APP' promoted $FROM -> $TO ($PARTS, scoped:$APPLY_SERVICES)."
else
  info "Done. '$APP' promoted $FROM -> $TO ($PARTS)."
fi
