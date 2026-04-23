#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

DB_NAME="${DB_NAME:-paranacity}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
BACKUP_FILE="${ROOT_DIR}/dump-paranacity.backup"

if [[ ! -f "$BACKUP_FILE" ]]; then
  echo "ERRO: backup não encontrado em $BACKUP_FILE"
  exit 1
fi

export PGPASSWORD="$DB_PASSWORD"

echo "Aguardando PostgreSQL ficar pronto..."
until pg_isready -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -q; do
  sleep 1
done
echo "PostgreSQL pronto."

echo "Criando banco '$DB_NAME' (ignora erro se já existir)..."
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -c "CREATE DATABASE \"$DB_NAME\";" 2>/dev/null || true

echo "Restaurando dump (346 MB — aguarde)..."
pg_restore \
  --host="$DB_HOST" \
  --port="$DB_PORT" \
  --username="$DB_USER" \
  --dbname="$DB_NAME" \
  --no-owner \
  --no-privileges \
  --verbose \
  "$BACKUP_FILE"

echo "Restore concluído. Banco '$DB_NAME' disponível em $DB_HOST:$DB_PORT"
