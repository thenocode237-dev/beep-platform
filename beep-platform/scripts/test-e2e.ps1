# Windows/Docker recipe. Uses a temporary role restricted to the audit table; no Docker socket mount.
$taskPreviousErrorAction = $ErrorActionPreference
$ErrorActionPreference = 'Continue' # Windows PowerShell classifies Docker progress on stderr as ErrorRecord.
$taskProjectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $taskProjectRoot
$taskAuditCreated = $false
try {
  $taskProvider = (docker compose -f docker-compose.yml -f docker-compose.dev.yml exec -T core printenv PAYMENT_PROVIDER).Trim()
  if ($LASTEXITCODE -ne 0 -or $taskProvider -ne 'sandbox') { throw 'Les tests exigent une stack locale saine avec PAYMENT_PROVIDER=sandbox.' }
  docker build -f scripts/Dockerfile.e2e -t beep/e2e:local .
  if ($LASTEXITCODE -ne 0) { throw 'Construction de l’exécuteur impossible.' }
  $taskStamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  $taskStaffEmail = "e2e-phase2-$taskStamp@beep.localhost"
  $taskStaffTotp = 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP'
  $taskAuditRole = "phase2_audit_$taskStamp"
  $taskAuditPassword = [Guid]::NewGuid().ToString('N')
  $taskPgAdmin = (docker exec beep-postgres-1 printenv POSTGRES_USER).Trim()
  @"
CREATE ROLE $taskAuditRole LOGIN PASSWORD '$taskAuditPassword';
GRANT CONNECT ON DATABASE backoffice TO $taskAuditRole;
GRANT USAGE ON SCHEMA public TO $taskAuditRole;
GRANT SELECT, UPDATE ON audit_log TO $taskAuditRole;
"@ | docker exec -i beep-postgres-1 psql -U $taskPgAdmin -d backoffice -v ON_ERROR_STOP=1
  if ($LASTEXITCODE -ne 0) { throw 'Création du rôle de test impossible.' }
  $taskAuditCreated = $true
  docker compose -f docker-compose.yml -f docker-compose.dev.yml run --rm -e SPRING_PROFILES_ACTIVE=dev,seed -e BO_SEED_EMAIL=$taskStaffEmail -e BO_SEED_TOTP_SECRET=$taskStaffTotp backoffice-api
  if ($LASTEXITCODE -ne 0) { throw 'Création du compte staff de test impossible.' }
  docker run --rm --network beep_edge --network beep_db -e E2E_HTTP=http://proxy -e E2E_STAFF_EMAIL=$taskStaffEmail -e E2E_STAFF_TOTP_SECRET=$taskStaffTotp -e E2E_BILLING_SANDBOX=1 -e E2E_AUDIT_PG=1 -e PGHOST=postgres -e PGDATABASE=backoffice -e PGUSER=$taskAuditRole -e PGPASSWORD=$taskAuditPassword --mount "type=bind,source=$taskProjectRoot,target=/w,readonly" beep/e2e:local
  if ($LASTEXITCODE -ne 0) { throw 'La recette de bout en bout a échoué.' }
} finally {
  if ($taskAuditCreated) {
    @"
UPDATE staff_users SET active=false WHERE email='$taskStaffEmail';
DELETE FROM staff_sessions WHERE staff_id IN (SELECT id FROM staff_users WHERE email='$taskStaffEmail');
REVOKE SELECT, UPDATE ON audit_log FROM $taskAuditRole;
REVOKE USAGE ON SCHEMA public FROM $taskAuditRole;
REVOKE CONNECT ON DATABASE backoffice FROM $taskAuditRole;
DROP ROLE $taskAuditRole;
"@ | docker exec -i beep-postgres-1 psql -U $taskPgAdmin -d backoffice -v ON_ERROR_STOP=1
  }
  Pop-Location
  $ErrorActionPreference = $taskPreviousErrorAction
}
