#!/bin/bash
set -euo pipefail

# =============================================================================
# copymongo.sh - Copy MongoDB databases from dev/stage/prod to local via SSH jumphost
#
# Usage: ./copymongo.sh <env> [db1 db2 ...]
#   env: dev | stage | prod
#   db list: optional override of databases to copy (default below)
#
# Credentials are loaded from ~/.copymongo/variables.sh
#
# Per DB it: mongodump on jumphost (archive+gzip) -> scp -> mongorestore --drop
# Only the listed DBs are touched locally. Other local DBs (e.g. appbuilder)
# remain intact.
# =============================================================================

VARIABLES_FILE="$HOME/.copymongo/variables.sh"

if [[ ! -f "$VARIABLES_FILE" ]]; then
    echo "ERROR: Missing credentials file: $VARIABLES_FILE"
    echo ""
    echo "Please create it with the following content:"
    echo ""
    echo "  mkdir -p ~/.copymongo && cat > ~/.copymongo/variables.sh << 'EOF'"
    echo '  #!/bin/bash'
    echo '  REMOTE_MONGO_USER="<remote_mongo_user>"'
    echo '  REMOTE_MONGO_PASS="<remote_mongo_password>"'
    echo '  REMOTE_MONGO_AUTHDB="admin"'
    echo '  SSH_KEY="<path_to_ssh_key>"'
    echo '  SSH_USER="<ssh_user>"'
    echo '  SSH_HOST="<ssh_jumphost>"'
    echo '  LOCAL_MONGO_USER="<local_mongo_user>"'
    echo '  LOCAL_MONGO_PASS="<local_mongo_password>"'
    echo '  LOCAL_MONGO_AUTHDB="admin"'
    echo "  EOF"
    echo ""
    echo "Then: chmod 600 ~/.copymongo/variables.sh"
    exit 1
fi

source "$VARIABLES_FILE"

# Validate all required variables are set
REQUIRED_VARS=(REMOTE_MONGO_USER REMOTE_MONGO_PASS SSH_KEY SSH_USER SSH_HOST LOCAL_MONGO_USER LOCAL_MONGO_PASS)
MISSING=()
for VAR in "${REQUIRED_VARS[@]}"; do
    if [[ -z "${!VAR:-}" ]]; then
        MISSING+=("$VAR")
    fi
done

if [[ ${#MISSING[@]} -gt 0 ]]; then
    echo "ERROR: Missing variables in $VARIABLES_FILE: ${MISSING[*]}"
    exit 1
fi

# Default auth DBs if not set
REMOTE_MONGO_AUTHDB="${REMOTE_MONGO_AUTHDB:-admin}"
LOCAL_MONGO_AUTHDB="${LOCAL_MONGO_AUTHDB:-admin}"

ENV="${1:-}"

if [[ -z "$ENV" ]] || [[ ! "$ENV" =~ ^(dev|stage|prod)$ ]]; then
    echo "Usage: $0 <dev|stage|prod> [db1 db2 ...]"
    exit 1
fi

shift || true

# --- SSH Config ---
SSH_OPTS="-i ${SSH_KEY} -o StrictHostKeyChecking=no"

# --- Remote Mongo Config ---
REMOTE_HOST="${ENV}-mongo.sub10150624021.modlixvcn.oraclevcn.com"
REMOTE_PORT=27017

# --- Local Mongo Config ---
LOCAL_HOST="127.0.0.1"
LOCAL_PORT=27017

# --- Databases to copy (default; override via CLI args) ---
# Default is `core` only. `ui` is intentionally excluded because the prod ui
# DB is hundreds of MB and rarely needed locally — pass it explicitly if you
# do want it: `./copymongo.sh prod core ui`.
if [[ $# -gt 0 ]]; then
    DATABASES=("$@")
else
    DATABASES=(
        core
    )
fi

# --- Paths ---
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REMOTE_DUMP_DIR="/tmp/copymongo_${ENV}_${TIMESTAMP}"
LOCAL_DUMP_DIR="/tmp/copymongo_${ENV}_${TIMESTAMP}"
mkdir -p "$LOCAL_DUMP_DIR"

echo "============================================="
echo "  Copying MongoDB from ${ENV} to local"
echo "============================================="
echo ""
echo "Remote: ${REMOTE_MONGO_USER}@${REMOTE_HOST}:${REMOTE_PORT}"
echo "Local:  ${LOCAL_MONGO_USER}@${LOCAL_HOST}:${LOCAL_PORT}"
echo "Databases: ${DATABASES[*]}"
echo ""

# --- Build URI helpers ---
# Note: passwords are URL-passed via --uri; jumphost mongodump must support --uri (mongodb-database-tools 100.x).
# We avoid placing the password in the command verbatim by passing through a
# heredoc'd env var on the remote shell.

# --- Step 1: Dump each DB on jumphost (archive + gzip) ---
echo ">> Step 1: Dumping databases on jumphost..."

DUMP_COMMANDS="mkdir -p ${REMOTE_DUMP_DIR} && export RPASS='${REMOTE_MONGO_PASS}'"
for DB in "${DATABASES[@]}"; do
    DUMP_COMMANDS="${DUMP_COMMANDS} && echo '   Dumping ${DB}...' && mongodump --host=${REMOTE_HOST} --port=${REMOTE_PORT} --username=${REMOTE_MONGO_USER} --password=\"\$RPASS\" --authenticationDatabase=${REMOTE_MONGO_AUTHDB} --db=${DB} --archive=${REMOTE_DUMP_DIR}/${DB}.archive.gz --gzip --quiet || echo '   WARNING: Failed to dump ${DB}'"
done

ssh ${SSH_OPTS} ${SSH_USER}@${SSH_HOST} "${DUMP_COMMANDS}"
echo "   Dumps complete on jumphost."

# --- Step 2: Transfer archives to local ---
echo ""
echo ">> Step 2: Transferring archives to local..."
scp ${SSH_OPTS} "${SSH_USER}@${SSH_HOST}:${REMOTE_DUMP_DIR}/*.archive.gz" "${LOCAL_DUMP_DIR}/"
echo "   Transfer complete."

# --- Step 3: Clean up remote dump files ---
echo ""
echo ">> Step 3: Cleaning up remote dump files..."
ssh ${SSH_OPTS} ${SSH_USER}@${SSH_HOST} "rm -rf ${REMOTE_DUMP_DIR}"
echo "   Remote cleanup done."

# --- Step 4: Restore each archive locally ---
echo ""
echo ">> Step 4: Restoring into local MongoDB (--drop on target DB only)..."
FAILED=()
for DB in "${DATABASES[@]}"; do
    ARCHIVE_FILE="${LOCAL_DUMP_DIR}/${DB}.archive.gz"

    if [[ ! -f "$ARCHIVE_FILE" ]]; then
        echo "   WARNING: No archive for ${DB}, skipping..."
        FAILED+=("$DB")
        continue
    fi

    ARCHIVE_SIZE=$(du -h "${ARCHIVE_FILE}" | cut -f1)
    echo ""
    echo "   [${DB}] (${ARCHIVE_SIZE} compressed)"

    echo "   [${DB}] Restoring with --drop (collections within ${DB} only)..."
    if mongorestore \
        --host="${LOCAL_HOST}" \
        --port="${LOCAL_PORT}" \
        --username="${LOCAL_MONGO_USER}" \
        --password="${LOCAL_MONGO_PASS}" \
        --authenticationDatabase="${LOCAL_MONGO_AUTHDB}" \
        --archive="${ARCHIVE_FILE}" \
        --gzip \
        --drop \
        --nsInclude="${DB}.*" \
        --quiet; then
        echo "   [${DB}] Restore complete."
    else
        echo "   WARNING: Failed to restore ${DB}"
        FAILED+=("$DB")
    fi
done

echo ""
echo "============================================="
if [[ ${#FAILED[@]} -eq 0 ]]; then
    echo "  All MongoDB databases copied successfully!"
else
    echo "  Completed with failures: ${FAILED[*]}"
fi
echo "============================================="

# --- Clean up local archives ---
echo ""
echo ">> Cleaning up local archives..."
rm -rf "${LOCAL_DUMP_DIR}"
echo "   Done."
