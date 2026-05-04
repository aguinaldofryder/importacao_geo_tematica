# =============================================================================
# scripts/e2e.ps1 — Suite E2E da Story 6.3 (Windows / PowerShell)
#
# Equivalente de e2e.sh para PowerShell. Cobre os mesmos ACs 1–6.
#
# Uso:
#   .\scripts\e2e.ps1 .\target\importacao-geo-1.0.0-SNAPSHOT-runner.exe
#   .\scripts\e2e.ps1 dist\importacao-geo-windows-x64.exe
#
# Pré-requisitos no PATH:
#   docker  — Docker Desktop com WSL2 backend (padrão em windows-latest GH Actions)
#   psql    — presente por padrão em runners GH Actions; local: adicionar ao PATH
#              da instalação PostgreSQL (%ProgramFiles%\PostgreSQL\<ver>\bin)
#   jq      — pré-instalado em runners GH Actions; local: winget install jqlang.jq
# =============================================================================
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Binary
)

$ErrorActionPreference = 'Stop'

# ── Constantes ───────────────────────────────────────────────────────────────
$PgContainer  = "e2e-pg-$PID"
$PgPort       = 5433
$PgDb         = "paranacity"
$PgUser       = "postgres"
$PgPass       = "postgres"

$ScriptDir    = $PSScriptRoot
$RepoRoot     = (Resolve-Path "$ScriptDir\..").Path
$FixturesDir  = "$RepoRoot\src\test\resources\fixtures"
$SeedSql      = "$FixturesDir\seed-e2e.sql"
$XlsxTerr     = "$FixturesDir\TERRITORIAL-10linhas.xlsx"
$XlsxPred     = "$FixturesDir\PREDIAL-10linhas.xlsx"

$WorkdirT     = "$env:TEMP\e2e-territorial-$PID"
$WorkdirP     = "$env:TEMP\e2e-predial-$PID"

$env:PGPASSWORD = $PgPass

# ── Utilitários ───────────────────────────────────────────────────────────────

function Write-Fase  { param([string]$Msg) Write-Host "[E2E] === $Msg ===" -ForegroundColor Cyan }
function Write-Info  { param([string]$Msg) Write-Host "[E2E] $Msg"         -ForegroundColor Gray }
function Write-Falha { param([string]$Msg) Write-Error "[E2E] FALHA: $Msg"; exit 1 }

function Invoke-Native {
    <#
    .SYNOPSIS
    Executa um comando externo e lança exceção se o exit code for != 0.
    #>
    param([string]$Cmd, [string[]]$Args)
    & $Cmd @Args
    if ($LASTEXITCODE -ne 0) {
        throw "Comando '$Cmd $($Args -join ' ')' falhou com exit code $LASTEXITCODE"
    }
}

function Remove-WorkDirs {
    if (Test-Path $WorkdirT) { Remove-Item -Recurse -Force $WorkdirT -ErrorAction SilentlyContinue }
    if (Test-Path $WorkdirP) { Remove-Item -Recurse -Force $WorkdirP -ErrorAction SilentlyContinue }
}

# ── Cleanup via try/finally ───────────────────────────────────────────────────
try {

# =============================================================================
# Fase 0 — Pré-requisitos
# =============================================================================
Write-Fase "Fase 0 — Pré-requisitos"

foreach ($cmd in @("docker", "psql", "jq")) {
    $found = Get-Command $cmd -ErrorAction SilentlyContinue
    if (-not $found) {
        Write-Falha "Comando '$cmd' não encontrado no PATH. Instale-o antes de executar o E2E."
    }
    Write-Info "  $cmd : $($found.Source)"
}

$Binary = (Resolve-Path $Binary).Path
if (-not (Test-Path $Binary)) { Write-Falha "Binário não encontrado: $Binary" }
Write-Info "  binário: $Binary"

foreach ($f in @($SeedSql, $XlsxTerr, $XlsxPred)) {
    if (-not (Test-Path $f)) { Write-Falha "Fixture não encontrada: $f" }
}
Write-Info "  fixtures: ok"

# =============================================================================
# Fase 1 — Subir PostgreSQL
# =============================================================================
Write-Fase "Fase 1 — Subir PostgreSQL (container: $PgContainer, porta: $PgPort)"

Invoke-Native docker @("run", "--rm", "-d",
    "--name", $PgContainer,
    "-p", "${PgPort}:5432",
    "-e", "POSTGRES_PASSWORD=$PgPass",
    "-e", "POSTGRES_DB=$PgDb",
    "postgres:16") | Out-Null

Write-Info "Container iniciado."

# =============================================================================
# Fase 2 — Aguardar prontidão
# =============================================================================
Write-Fase "Fase 2 — Aguardar prontidão do banco"

$max = 30
for ($i = 1; $i -le $max; $i++) {
    docker exec $PgContainer pg_isready -U $PgUser -q 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Info "Banco pronto após ${i}s."
        break
    }
    if ($i -eq $max) { Write-Falha "Banco não ficou pronto em ${max}s." }
    Start-Sleep 1
}

# =============================================================================
# Fase 3 — Seed
# =============================================================================
Write-Fase "Fase 3 — Seed"

Invoke-Native psql @("-h", "127.0.0.1", "-p", $PgPort,
    "-U", $PgUser, "-d", $PgDb,
    "-f", $SeedSql, "-q")

Write-Info "Seed aplicado."

# Helper: escreve config/application.properties num diretório de trabalho
function New-AppConfig {
    param([string]$Dir)
    New-Item -ItemType Directory -Force -Path "$Dir\config" | Out-Null
    @"
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:${PgPort}/${PgDb}
quarkus.datasource.username=${PgUser}
quarkus.datasource.password=${PgPass}
quarkus.datasource.jdbc.additional-jdbc-properties.currentSchema=aise
importacao.codigo-imovel.territorial=MATRICULA
importacao.codigo-imovel.predial=MATRICULA
importacao.colunas-fixas.territorial=TESTADA
importacao.colunas-fixas.predial=AREA_CONSTRUIDA
"@ | Set-Content "$Dir\config\application.properties" -Encoding UTF8
}

# =============================================================================
# Fase 4 — validar-conexao
# =============================================================================
Write-Fase "Fase 4 — validar-conexao"

New-Item -ItemType Directory -Force -Path $WorkdirT | Out-Null
New-AppConfig $WorkdirT

Push-Location $WorkdirT
try   { Invoke-Native $Binary @("validar-conexao") }
finally { Pop-Location }

Write-Info "Conexão validada."

# =============================================================================
# Fase 5 — Fluxo TERRITORIAL
# =============================================================================
Write-Fase "Fase 5 — Fluxo TERRITORIAL"

# config já criado no WorkdirT; apenas garante existência
New-AppConfig $WorkdirT

Write-Info "Fase 5.1 — mapear (territorial)"
Push-Location $WorkdirT
try {
    Invoke-Native $Binary @("mapear", "--arquivo", $XlsxTerr, "--fluxo", "territorial")
} finally { Pop-Location }

$mappingT = "$WorkdirT\mapping.json"
$pendenteT = (Get-Content $mappingT -Raw | jq '[.colunasDinamicas | to_entries[] | select(.value.status == "PENDENTE")] | length')
if ([int]$pendenteT -ne 0) {
    Write-Falha "$pendenteT colunas PENDENTE no mapping.json territorial."
}
Write-Info "  mapping.json territorial: 0 PENDENTE — ok"

Write-Info "Fase 5.2 — validar (territorial)"
Push-Location $WorkdirT
try   { Invoke-Native $Binary @("validar", "--mapeamento", ".\mapping.json") }
finally { Pop-Location }

Write-Info "Fase 5.3 — importar (territorial)"
Push-Location $WorkdirT
try {
    Invoke-Native $Binary @("importar",
        "--arquivo", $XlsxTerr,
        "--fluxo", "territorial",
        "--mapeamento", ".\mapping.json")
} finally { Pop-Location }

$SqlT = (Get-ChildItem "$WorkdirT\saida\*.sql" -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
$LogT = (Get-ChildItem "$WorkdirT\saida\*.log" -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
if (-not $SqlT) { Write-Falha "Nenhum .sql gerado (territorial)." }
if (-not $LogT) { Write-Falha "Nenhum .log gerado (territorial)." }
Write-Info "  SQL: $SqlT"
Write-Info "  LOG: $LogT"

# =============================================================================
# Fase 6 — Fluxo PREDIAL
# =============================================================================
Write-Fase "Fase 6 — Fluxo PREDIAL"

New-Item -ItemType Directory -Force -Path $WorkdirP | Out-Null
New-AppConfig $WorkdirP

Write-Info "Fase 6.1 — mapear (predial)"
Push-Location $WorkdirP
try {
    Invoke-Native $Binary @("mapear", "--arquivo", $XlsxPred, "--fluxo", "predial")
} finally { Pop-Location }

$mappingP = "$WorkdirP\mapping.json"
$pendenteP = (Get-Content $mappingP -Raw | jq '[.colunasDinamicas | to_entries[] | select(.value.status == "PENDENTE")] | length')
if ([int]$pendenteP -ne 0) {
    Write-Falha "$pendenteP colunas PENDENTE no mapping.json predial."
}
Write-Info "  mapping.json predial: 0 PENDENTE — ok"

Write-Info "Fase 6.2 — validar (predial)"
Push-Location $WorkdirP
try   { Invoke-Native $Binary @("validar", "--mapeamento", ".\mapping.json") }
finally { Pop-Location }

Write-Info "Fase 6.3 — importar (predial)"
Push-Location $WorkdirP
try {
    Invoke-Native $Binary @("importar",
        "--arquivo", $XlsxPred,
        "--fluxo", "predial",
        "--mapeamento", ".\mapping.json")
} finally { Pop-Location }

$SqlP = (Get-ChildItem "$WorkdirP\saida\*.sql" -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
$LogP = (Get-ChildItem "$WorkdirP\saida\*.log" -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
if (-not $SqlP) { Write-Falha "Nenhum .sql gerado (predial)." }
if (-not $LogP) { Write-Falha "Nenhum .log gerado (predial)." }
Write-Info "  SQL: $SqlP"
Write-Info "  LOG: $LogP"

# =============================================================================
# Fase 7 — Assertivas AC4, AC5, AC6
# =============================================================================
Write-Fase "Fase 7 — Assertivas"

# ── AC4 — Validação sintática SQL ────────────────────────────────────────────
function Test-SqlSyntax {
    param([string]$Label, [string]$SqlFile)
    Write-Info "AC4 — Validação sintática SQL: $Label"
    $tmpFile = [System.IO.Path]::GetTempFileName() -replace '\.tmp$', '.sql'
    try {
        "BEGIN;" | Set-Content $tmpFile -Encoding UTF8
        Get-Content $SqlFile | Add-Content $tmpFile -Encoding UTF8
        "`nROLLBACK;" | Add-Content $tmpFile -Encoding UTF8

        psql -h 127.0.0.1 -p $PgPort -U $PgUser -d $PgDb `
            "--set=ON_ERROR_STOP=on" -f $tmpFile -q
        if ($LASTEXITCODE -ne 0) {
            Write-Falha "AC4: SQL inválido em $SqlFile ($Label)"
        }
    } finally {
        Remove-Item $tmpFile -ErrorAction SilentlyContinue
    }
    Write-Info "  AC4 $Label: ok"
}

Test-SqlSyntax "territorial" $SqlT
Test-SqlSyntax "predial"     $SqlP

# ── AC5 — Contadores JSON ────────────────────────────────────────────────────
function Test-Contadores {
    param([string]$Label, [string]$LogFile)
    Write-Info "AC5 — Contadores JSON: $Label"

    $resumo = Select-String -Path $LogFile -Pattern '"evento":"resumo"' | Select-Object -Last 1
    if (-not $resumo) { Write-Falha "AC5: linha de resumo JSON não encontrada em $LogFile ($Label)" }

    $json = $resumo.Line

    $lidos   = $json | jq '.lidos'
    $sucesso = $json | jq '.sucesso'
    $erro    = $json | jq '.erro'
    $updates = $json | jq '.principal_updates'
    $inserts = $json | jq '.respostas_insert'

    if ($lidos   -ne "10") { Write-Falha "AC5 $Label: lidos=$lidos (esperado 10)" }
    if ($sucesso -ne "10") { Write-Falha "AC5 $Label: sucesso=$sucesso (esperado 10)" }
    if ($erro    -ne "0")  { Write-Falha "AC5 $Label: erro=$erro (esperado 0)" }
    if ($updates -ne "10") { Write-Falha "AC5 $Label: principal_updates=$updates (esperado 10)" }
    if ([int]$inserts -le 0) { Write-Falha "AC5 $Label: respostas_insert=$inserts (esperado > 0)" }

    Write-Info "  AC5 $Label: lidos=$lidos sucesso=$sucesso erro=$erro updates=$updates inserts=$inserts — ok"
}

Test-Contadores "territorial" $LogT
Test-Contadores "predial"     $LogP

# ── AC6 — Ausência de INSERT proibido (CON-03) ───────────────────────────────
Write-Info "AC6 — Ausência de INSERT proibido (CON-03)"

foreach ($entry in @(@{Label="territorial"; File=$SqlT}, @{Label="predial"; File=$SqlP})) {
    $match = Select-String -Path $entry.File `
        -Pattern 'insert\s+into\s+(aise\.)?(tribcadastroimobiliario|tribimobiliariosegmento)' `
        -CaseSensitive:$false
    if ($match) {
        Write-Falha "AC6: INSERT proibido encontrado em $($entry.File) ($($entry.Label)) — violação de CON-03"
    }
    Write-Info "  AC6 $($entry.Label): nenhum INSERT proibido — ok"
}

# =============================================================================
# Resultado final
# =============================================================================
Write-Fase "Suite E2E concluída com sucesso (AC1–AC6)"

} finally {
    # Cleanup sempre executado
    Write-Info "Cleanup — removendo container $PgContainer e diretórios temporários..."
    docker rm -f $PgContainer 2>$null | Out-Null
    Remove-WorkDirs
}
