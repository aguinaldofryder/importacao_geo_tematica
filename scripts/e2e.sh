#!/usr/bin/env bash
# =============================================================================
# scripts/e2e.sh — Suite E2E da Story 6.3: valida o binário nativo contra um
# PostgreSQL Docker real, cobrindo os ACs 1–6.
#
# Uso:
#   bash scripts/e2e.sh <caminho-para-o-binario>
#
# Exemplo:
#   bash scripts/e2e.sh ./target/importacao-geo-1.0.0-SNAPSHOT-runner
#   bash scripts/e2e.sh dist/importacao-geo-linux-x64
#
# Exit codes:
#   0 — todas as assertivas passaram
#   1 — falha de pré-requisito ou erro inesperado
# =============================================================================
set -euo pipefail

# ── Utilitários de saída ─────────────────────────────────────────────────────

fase() { echo "[E2E] === $* ===" >&2; }
info() { echo "[E2E] $*" >&2; }
fail() { echo "[E2E] FALHA: $*" >&2; exit 1; }

# ── Argumento obrigatório ────────────────────────────────────────────────────

if [ $# -lt 1 ]; then
    echo "Uso: $0 <caminho-para-o-binario>" >&2
    exit 1
fi
BINARY="$1"

# Resolve caminho absoluto uma vez (os subshells cd para dirs temporários)
BINARY="$(realpath "$BINARY")"

# Diretório raiz do repositório (dois níveis acima deste script)
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FIXTURES_DIR="$REPO_ROOT/src/test/resources/fixtures"
SEED_SQL="$FIXTURES_DIR/seed-e2e.sql"
XLSX_TERRITORIAL="$FIXTURES_DIR/TERRITORIAL-10linhas.xlsx"
XLSX_PREDIAL="$FIXTURES_DIR/PREDIAL-10linhas.xlsx"

# Diretórios de trabalho por fluxo (PID garante unicidade em runs paralelos)
WORKDIR_T="/tmp/e2e-territorial-$$"
WORKDIR_P="/tmp/e2e-predial-$$"

# Container Docker do banco E2E
PG_CONTAINER="e2e-pg-$$"
PG_PORT=5433         # porta externa diferente da 5432 para não colidir com PG local
PG_DB="paranacity"
PG_USER="postgres"
PG_PASS="postgres"

# =============================================================================
# Fase 8 — Cleanup (registrado antes de qualquer side effect)
# =============================================================================
cleanup() {
    info "Cleanup — removendo container $PG_CONTAINER e diretórios temporários..."
    docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
    rm -rf "$WORKDIR_T" "$WORKDIR_P"
}
trap cleanup EXIT

# =============================================================================
# Fase 0 — Pré-requisitos
# =============================================================================
fase "Fase 0 — Pré-requisitos"

for cmd in docker psql jq; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        fail "Comando '$cmd' não encontrado no PATH. Instale-o antes de executar o E2E."
    fi
    info "  $cmd: $(command -v "$cmd")"
done

if [ ! -f "$BINARY" ]; then
    fail "Binário não encontrado: $BINARY"
fi
if [ ! -x "$BINARY" ]; then
    fail "Binário não é executável: $BINARY — execute: chmod +x $BINARY"
fi
info "  binário: $BINARY"

for f in "$SEED_SQL" "$XLSX_TERRITORIAL" "$XLSX_PREDIAL"; do
    [ -f "$f" ] || fail "Fixture não encontrada: $f"
done
info "  fixtures: ok"

# =============================================================================
# Fase 1 — Subir PostgreSQL
# =============================================================================
fase "Fase 1 — Subir PostgreSQL (container: $PG_CONTAINER, porta: $PG_PORT)"

docker run --rm -d \
    --name "$PG_CONTAINER" \
    -p "${PG_PORT}:5432" \
    -e POSTGRES_PASSWORD="$PG_PASS" \
    -e POSTGRES_DB="$PG_DB" \
    postgres:16 >/dev/null

info "Container iniciado."

# =============================================================================
# Fase 2 — Aguardar prontidão do banco (até 30 s)
# =============================================================================
fase "Fase 2 — Aguardar prontidão do banco"

MAX_TENTATIVAS=30
for i in $(seq 1 $MAX_TENTATIVAS); do
    if docker exec "$PG_CONTAINER" pg_isready -U "$PG_USER" -q 2>/dev/null; then
        info "Banco pronto após ${i}s."
        break
    fi
    if [ "$i" -eq "$MAX_TENTATIVAS" ]; then
        fail "Banco não ficou pronto em ${MAX_TENTATIVAS}s."
    fi
    sleep 1
done

# =============================================================================
# Fase 3 — Seed
# =============================================================================
fase "Fase 3 — Seed (seed-e2e.sql)"

PGPASSWORD="$PG_PASS" psql \
    -h 127.0.0.1 -p "$PG_PORT" \
    -U "$PG_USER" -d "$PG_DB" \
    -f "$SEED_SQL" -q

info "Seed aplicado com sucesso."

# =============================================================================
# Fase 4 — validar-conexao
# =============================================================================
fase "Fase 4 — validar-conexao"

# ── Helper: escreve config/application.properties num diretório de trabalho ─
# Declara TODAS as propriedades relevantes explicitamente para evitar que o
# binário nativo aplique fallback sobre algum config/application.properties
# do diretório do binário ou do ambiente local.
escrever_config() {
    local workdir="$1"
    mkdir -p "$workdir/config"
    cat > "$workdir/config/application.properties" << APPCFG
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:${PG_PORT}/${PG_DB}
quarkus.datasource.username=${PG_USER}
quarkus.datasource.password=${PG_PASS}
quarkus.datasource.jdbc.additional-jdbc-properties.currentSchema=aise
importacao.codigo-imovel.territorial=MATRICULA
importacao.codigo-imovel.predial=MATRICULA
importacao.colunas-fixas.territorial=TESTADA
importacao.colunas-fixas.predial=AREA_CONSTRUIDA
APPCFG
}

mkdir -p "$WORKDIR_T"
escrever_config "$WORKDIR_T"

# validar-conexao usa o config do diretório atual
(cd "$WORKDIR_T" && "$BINARY" validar-conexao) \
    || fail "validar-conexao retornou exit code não-zero."

info "Conexão validada com sucesso."

# =============================================================================
# Fase 5 — Fluxo TERRITORIAL
# =============================================================================
fase "Fase 5 — Fluxo TERRITORIAL"

# config já escrito na fase 4 pelo escrever_config; chamada redundante para clareza
escrever_config "$WORKDIR_T"

info "Fase 5.1 — mapear (territorial)"
(cd "$WORKDIR_T" && "$BINARY" mapear \
    --arquivo "$XLSX_TERRITORIAL" \
    --fluxo territorial) \
    || fail "mapear --fluxo territorial falhou."

# Verifica zero PENDENTE no mapping.json gerado
PENDENTE_T=$(jq '[.colunasDinamicas | to_entries[] | select(.value.status == "PENDENTE")] | length' \
    "$WORKDIR_T/mapping.json")
if [ "$PENDENTE_T" -ne 0 ]; then
    fail "$PENDENTE_T colunas PENDENTE no mapping.json territorial. Verifique o seed e os cabeçalhos do fixture."
fi
info "  mapping.json territorial: 0 PENDENTE — ok"

info "Fase 5.2 — validar (territorial)"
(cd "$WORKDIR_T" && "$BINARY" validar --mapeamento ./mapping.json) \
    || fail "validar --mapeamento (territorial) falhou."

info "Fase 5.3 — importar (territorial)"
(cd "$WORKDIR_T" && "$BINARY" importar \
    --arquivo "$XLSX_TERRITORIAL" \
    --fluxo territorial \
    --mapeamento ./mapping.json) \
    || fail "importar --fluxo territorial retornou exit code não-zero."

# Captura os artefatos gerados em ./saida/
SQL_T=$(ls "$WORKDIR_T/saida/"*.sql 2>/dev/null | head -1)
LOG_T=$(ls "$WORKDIR_T/saida/"*.log 2>/dev/null | head -1)
[ -n "$SQL_T" ] || fail "Nenhum .sql gerado pelo fluxo territorial."
[ -n "$LOG_T" ] || fail "Nenhum .log gerado pelo fluxo territorial."
info "  SQL gerado : $SQL_T"
info "  LOG gerado : $LOG_T"

# =============================================================================
# Fase 6 — Fluxo PREDIAL
# =============================================================================
fase "Fase 6 — Fluxo PREDIAL"

mkdir -p "$WORKDIR_P"
escrever_config "$WORKDIR_P"

info "Fase 6.1 — mapear (predial)"
(cd "$WORKDIR_P" && "$BINARY" mapear \
    --arquivo "$XLSX_PREDIAL" \
    --fluxo predial) \
    || fail "mapear --fluxo predial falhou."

PENDENTE_P=$(jq '[.colunasDinamicas | to_entries[] | select(.value.status == "PENDENTE")] | length' \
    "$WORKDIR_P/mapping.json")
if [ "$PENDENTE_P" -ne 0 ]; then
    fail "$PENDENTE_P colunas PENDENTE no mapping.json predial. Verifique o seed e os cabeçalhos do fixture."
fi
info "  mapping.json predial: 0 PENDENTE — ok"

info "Fase 6.2 — validar (predial)"
(cd "$WORKDIR_P" && "$BINARY" validar --mapeamento ./mapping.json) \
    || fail "validar --mapeamento (predial) falhou."

info "Fase 6.3 — importar (predial)"
(cd "$WORKDIR_P" && "$BINARY" importar \
    --arquivo "$XLSX_PREDIAL" \
    --fluxo predial \
    --mapeamento ./mapping.json) \
    || fail "importar --fluxo predial retornou exit code não-zero."

SQL_P=$(ls "$WORKDIR_P/saida/"*.sql 2>/dev/null | head -1)
LOG_P=$(ls "$WORKDIR_P/saida/"*.log 2>/dev/null | head -1)
[ -n "$SQL_P" ] || fail "Nenhum .sql gerado pelo fluxo predial."
[ -n "$LOG_P" ] || fail "Nenhum .log gerado pelo fluxo predial."
info "  SQL gerado : $SQL_P"
info "  LOG gerado : $LOG_P"

# =============================================================================
# Fase 7 — Assertivas AC4, AC5, AC6
# =============================================================================
fase "Fase 7 — Assertivas"

# ── Função auxiliar de validação SQL via BEGIN/ROLLBACK (AC4) ────────────────
validar_sql() {
    local label="$1"
    local sql_file="$2"
    info "AC4 — Validação sintática SQL: $label"
    local tmpfile
    tmpfile=$(mktemp /tmp/e2e-check-XXXXXX.sql)
    {
        printf 'BEGIN;\n'
        printf 'SET search_path TO aise;\n'
        cat "$sql_file"
        printf '\nROLLBACK;\n'
    } > "$tmpfile"
    PGPASSWORD="$PG_PASS" psql \
        -h 127.0.0.1 -p "$PG_PORT" \
        -U "$PG_USER" -d "$PG_DB" \
        --set ON_ERROR_STOP=on \
        -f "$tmpfile" -q \
        || { rm -f "$tmpfile"; fail "AC4: SQL inválido em $sql_file ($label)"; }
    rm -f "$tmpfile"
    info "  AC4 $label: ok"
}

validar_sql "territorial" "$SQL_T"
validar_sql "predial"     "$SQL_P"

# ── Função auxiliar de validação de contadores (AC5) ────────────────────────
validar_contadores() {
    local label="$1"
    local log_file="$2"
    info "AC5 — Contadores JSON: $label"

    local resumo
    resumo=$(grep '"evento":"resumo"' "$log_file" | tail -1) \
        || fail "AC5: linha de resumo JSON não encontrada em $log_file ($label)"

    local lidos sucesso erro updates inserts
    lidos=$(echo "$resumo"   | jq '.lidos')
    sucesso=$(echo "$resumo" | jq '.sucesso')
    erro=$(echo "$resumo"    | jq '.erro')
    updates=$(echo "$resumo" | jq '.principal_updates')
    inserts=$(echo "$resumo" | jq '.respostas_insert')

    [ "$lidos"   = "10" ] || fail "AC5 $label: lidos=$lidos (esperado 10)"
    [ "$sucesso" = "10" ] || fail "AC5 $label: sucesso=$sucesso (esperado 10)"
    [ "$erro"    = "0"  ] || fail "AC5 $label: erro=$erro (esperado 0)"
    [ "$updates" = "10" ] || fail "AC5 $label: principal_updates=$updates (esperado 10)"
    [ "$inserts" -gt 0  ] || fail "AC5 $label: respostas_insert=$inserts (esperado > 0 no primeiro run)"

    info "  AC5 $label: lidos=$lidos sucesso=$sucesso erro=$erro updates=$updates inserts=$inserts — ok"
}

validar_contadores "territorial" "$LOG_T"
validar_contadores "predial"     "$LOG_P"

# ── AC6 — Ausência de INSERT proibido (CON-03) ───────────────────────────────
info "AC6 — Ausência de INSERT proibido (CON-03)"

for label_sql in "territorial:$SQL_T" "predial:$SQL_P"; do
    lbl="${label_sql%%:*}"
    sql="${label_sql#*:}"
    if grep -qiE "insert[[:space:]]+into[[:space:]]+(aise\.)?(tribcadastroimobiliario|tribimobiliariosegmento)" "$sql"; then
        fail "AC6: INSERT proibido encontrado em $sql ($lbl) — violação de CON-03"
    fi
    info "  AC6 $lbl: nenhum INSERT proibido — ok"
done

# =============================================================================
# Resultado final
# =============================================================================
fase "Suite E2E concluída com sucesso (AC1–AC6)"
exit 0
