#!/usr/bin/env bash
# ── Gauge Docker Setup Script ───────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Gauge Docker Setup ==="

# ── 1. Generate dummy SSL certificates (if not present) ─────────────
mkdir -p certs
if [ -f certs/cert.pem ] && [ -f certs/key.pem ]; then
    echo "[OK] SSL certificates already present."
else
    echo "[..] Generating self-signed SSL certificates..."
    openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
        -keyout certs/key.pem \
        -out certs/cert.pem \
        -subj "/CN=localhost"
    echo "[OK] Certificates generated."
fi

# ── 2. Build the uber-jar (if not present) ──────────────────────────
JAR=$(ls ../target/gauge-*-runner.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
    echo "[..] No runner jar found. Building with Maven..."
    (cd .. && mvn -B clean package -DskipTests)
    echo "[OK] Build complete."
else
    echo "[OK] Runner jar found: $JAR"
fi

# ── 3. Build and start containers ───────────────────────────────────
echo "[..] Building and starting containers..."
docker compose up --build -d

echo ""
echo "=== Setup complete ==="
echo "Gauge is running behind nginx at https://localhost"
echo "Use 'docker compose logs -f' to tail logs."
