#!/bin/bash
set -euo pipefail

# =============================================================================
# copydb.sh - Copy MySQL databases from dev/stage/prod to local via SSH jumphost
#
# Usage: ./copydb.sh <env>
#   env: dev | stage | prod
#
# Credentials are loaded from ~/.copydb/variables.sh
# =============================================================================

VARIABLES_FILE="$HOME/.copydb/variables.sh"

if [[ ! -f "$VARIABLES_FILE" ]]; then
    echo "ERROR: Missing credentials file: $VARIABLES_FILE"
    echo ""
    echo "Please create it with the following content:"
    echo ""
    echo "  mkdir -p ~/.copydb && cat > ~/.copydb/variables.sh << 'EOF'"
    echo '  #!/bin/bash'
    echo '  REMOTE_USER="<remote_db_user>"'
    echo '  REMOTE_PASS="<remote_db_password>"'
    echo '  SSH_KEY="<path_to_ssh_key>"'
    echo '  SSH_USER="<ssh_user>"'
    echo '  SSH_HOST="<ssh_jumphost>"'
    echo '  LOCAL_USER="<local_db_user>"'
    echo '  LOCAL_PASS="<local_db_password>"'
    echo "  EOF"
    echo ""
    echo "Then: chmod 600 ~/.copydb/variables.sh"
    exit 1
fi

source "$VARIABLES_FILE"

# Validate all required variables are set
REQUIRED_VARS=(REMOTE_USER REMOTE_PASS SSH_KEY SSH_USER SSH_HOST LOCAL_USER LOCAL_PASS)
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

ENV="${1:-}"

if [[ -z "$ENV" ]] || [[ ! "$ENV" =~ ^(dev|stage|prod)$ ]]; then
    echo "Usage: $0 <dev|stage|prod>"
    exit 1
fi

# --- SSH Config ---
SSH_OPTS="-i ${SSH_KEY} -o StrictHostKeyChecking=no"

# --- Remote DB Config ---
REMOTE_HOST="${ENV}-mysql.sub10150624021.modlixvcn.oraclevcn.com"
REMOTE_PORT=3306

# --- Local DB Config ---
LOCAL_HOST="127.0.0.1"
LOCAL_PORT=3306

# --- Databases to copy ---
DATABASES=(
    security
    files
    multi
    core
    entity_processor
    notification
    message
    entity_collector
    ai
    worker
)

# --- Paths ---
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REMOTE_DUMP_DIR="/tmp/copydb_${ENV}_${TIMESTAMP}"
LOCAL_DUMP_DIR="/tmp/copydb_${ENV}_${TIMESTAMP}"
mkdir -p "$LOCAL_DUMP_DIR"

echo "============================================="
echo "  Copying databases from ${ENV} to local"
echo "============================================="
echo ""
echo "Remote: ${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_PORT}"
echo "Local:  ${LOCAL_USER}@${LOCAL_HOST}:${LOCAL_PORT}"
echo "Databases: ${DATABASES[*]}"
echo ""

# --- Step 1: Dump all databases on jumphost ---
echo ">> Step 1: Dumping databases on jumphost..."

DUMP_COMMANDS="mkdir -p ${REMOTE_DUMP_DIR}"
for DB in "${DATABASES[@]}"; do
    DUMP_COMMANDS="${DUMP_COMMANDS} && echo '   Dumping ${DB}...' && mysqldump -h ${REMOTE_HOST} -P ${REMOTE_PORT} -u ${REMOTE_USER} -p'${REMOTE_PASS}' --single-transaction --routines --triggers --events --set-gtid-purged=OFF ${DB} | gzip > ${REMOTE_DUMP_DIR}/${DB}.sql.gz 2>/dev/null || echo '   WARNING: Failed to dump ${DB}'"
done

ssh ${SSH_OPTS} ${SSH_USER}@${SSH_HOST} "${DUMP_COMMANDS}"
echo "   Dumps complete on jumphost."

# --- Step 2: Transfer zipped dumps to local ---
echo ""
echo ">> Step 2: Transferring dumps to local..."
scp ${SSH_OPTS} ${SSH_USER}@${SSH_HOST}:${REMOTE_DUMP_DIR}/*.sql.gz "${LOCAL_DUMP_DIR}/"
echo "   Transfer complete."

# --- Step 3: Clean up remote dump files ---
echo ""
echo ">> Step 3: Cleaning up remote dump files..."
ssh ${SSH_OPTS} ${SSH_USER}@${SSH_HOST} "rm -rf ${REMOTE_DUMP_DIR}"
echo "   Remote cleanup done."

# --- Step 4: Drop, recreate, and import each database locally ---
echo ""
echo ">> Step 4: Importing into local MySQL..."
FAILED=()
for DB in "${DATABASES[@]}"; do
    DUMP_FILE="${LOCAL_DUMP_DIR}/${DB}.sql.gz"

    if [[ ! -f "$DUMP_FILE" ]]; then
        echo "   WARNING: No dump file for ${DB}, skipping..."
        FAILED+=("$DB")
        continue
    fi

    DUMP_SIZE=$(du -h "${DUMP_FILE}" | cut -f1)
    echo ""
    echo "   [${DB}] (${DUMP_SIZE} compressed)"

    echo "   [${DB}] Dropping and recreating database..."
    mysql -h "${LOCAL_HOST}" -P ${LOCAL_PORT} -u "${LOCAL_USER}" -p"${LOCAL_PASS}" \
        -e "DROP DATABASE IF EXISTS \`${DB}\`; CREATE DATABASE \`${DB}\`;"

    echo "   [${DB}] Importing..."
    if gunzip -c "${DUMP_FILE}" | mysql -h "${LOCAL_HOST}" -P ${LOCAL_PORT} -u "${LOCAL_USER}" -p"${LOCAL_PASS}" \
        "${DB}"; then
        echo "   [${DB}] Import complete."
    else
        echo "   WARNING: Failed to import ${DB}"
        FAILED+=("$DB")
    fi
done

# --- Reset passwords and PINs in security_user ---
echo ""
echo ">> Resetting passwords and PINs in security.security_user..."
mysql -h "${LOCAL_HOST}" -P ${LOCAL_PORT} -u "${LOCAL_USER}" -p"${LOCAL_PASS}" security <<'SQL'
UPDATE security_user
SET password = 'Pass@1234', password_hashed = 0
WHERE password IS NOT NULL AND password != '';

UPDATE security_user
SET pin = '000000', pin_hashed = 0
WHERE pin IS NOT NULL AND pin != '';
SQL
echo "   Passwords set to 'Pass@1234' (password_hashed=0)"
echo "   PINs set to '000000' (pin_hashed=0)"

echo ""
echo "============================================="
if [[ ${#FAILED[@]} -eq 0 ]]; then
    echo "  All databases copied successfully!"
else
    echo "  Completed with failures: ${FAILED[*]}"
fi
echo "============================================="

# --- Clean up local dump files ---
echo ""
echo ">> Cleaning up local dump files..."
rm -rf "${LOCAL_DUMP_DIR}"
echo "   Done."
