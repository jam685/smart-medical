# 一键启动 Loki + Promtail（Windows）
# 用法：powershell -ExecutionPolicy Bypass -File start-observability.ps1

$ErrorActionPreference = "Continue"
$installDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Output "=== Smart-Medical Observability Stack ==="
Write-Output "Dir: $installDir"

# ---------- 1. 清旧进程 ----------
Write-Output "[1/4] Killing old processes..."
Get-Process -Name "loki-windows-amd64" -ErrorAction SilentlyContinue | Stop-Process -Force
Get-Process -Name "promtail-windows-amd64" -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2

# ---------- 2. 清 Loki 数据（tsdb WAL 在 Windows 上经常留坏文件）----------
Write-Output "[2/4] Cleaning Loki data dir..."
$lokiData = Join-Path $installDir "loki-data"
if (Test-Path $lokiData) {
  try {
    Remove-Item $lokiData -Recurse -Force
  } catch {
    Write-Output "  Warning: could not fully clean loki-data: $_"
  }
}
New-Item -ItemType Directory -Force -Path $lokiData | Out-Null

# ---------- 3. 启动 Loki ----------
Write-Output "[3/4] Starting Loki..."
$lokiExe = Join-Path $installDir "loki-windows-amd64.exe"
$lokiCfg = Join-Path $installDir "loki-config.yaml"
if (-not (Test-Path $lokiExe)) { Write-Output "  FAIL: loki exe not found!"; exit 1 }

Start-Process -FilePath $lokiExe -ArgumentList "--config.file=$lokiCfg" -WorkingDirectory $installDir -WindowStyle Hidden
Write-Output "  Waiting for Loki to become ready..."
$ready = $false
for ($i = 0; $i -lt 20; $i++) {
  Start-Sleep -Seconds 1
  try {
    $r = Invoke-WebRequest -Uri "http://127.0.0.1:3100/metrics" -UseBasicParsing -TimeoutSec 2
    if ($r.StatusCode -eq 200) { $ready = $true; break }
  } catch {}
}
if ($ready) {
  Write-Output "  OK: Loki listening on http://127.0.0.1:3100"
} else {
  Write-Output "  WARN: Loki not responding yet (check logs manually)"
}

# ---------- 4. 启动 Promtail ----------
Write-Output "[4/4] Starting Promtail..."
$promExe = Join-Path $installDir "promtail-windows-amd64.exe"
$promCfg = Join-Path $installDir "promtail-config.yaml"
if (-not (Test-Path $promExe)) { Write-Output "  FAIL: promtail exe not found!"; exit 1 }

Start-Process -FilePath $promExe -ArgumentList "--config.file=$promCfg" -WorkingDirectory $installDir -WindowStyle Hidden
Write-Output "  Waiting for Promtail..."
for ($i = 0; $i -lt 10; $i++) {
  Start-Sleep -Seconds 1
  try {
    $r = Invoke-WebRequest -Uri "http://127.0.0.1:9080/targets" -UseBasicParsing -TimeoutSec 2
    if ($r.StatusCode -eq 200) { break }
  } catch {}
}
Write-Output "  OK: Promtail on http://127.0.0.1:9080/targets"

Write-Output ""
Write-Output "=== Stack Running ==="
Write-Output "  Loki:     http://127.0.0.1:3100   (LogQL: {job=`"smart-medical-backend`"})"
Write-Output "  Promtail: http://127.0.0.1:9080/targets"
Write-Output "  Grafana:  下载后访问 http://127.0.0.1:3000  加 Loki datasource=http://127.0.0.1:3100"
Write-Output ""

# 保持脚本活着
Write-Output "Press Ctrl+C to stop..."
while ($true) { Start-Sleep -Seconds 60 }
