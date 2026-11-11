#!/bin/sh
set -e

echo "[entrypoint] Starting Nginx on :80 (CSR + /news/*.html → service.news) ..."
exec nginx -g 'daemon off;'
