#!/bin/sh
# Kubernetes exec probes for cxe-nsjail-runner (no network listener).
set -eu

mode="${1:-startup}"
nsjail="${CXE_NSJAIL_BIN:-/usr/local/bin/nsjail}"

case "${mode}" in
  startup)
    test -x /opt/cxe-runner/bin/nsjail-runner
    test -x "${nsjail}"
    java -version >/dev/null 2>&1
    python3 --version >/dev/null 2>&1
    ;;
  liveness)
    pgrep -f 'nsjail-runner' >/dev/null
    ;;
  readiness)
    pgrep -f 'nsjail-runner' >/dev/null
    ;;
  *)
    echo "unknown probe mode: ${mode}" >&2
    exit 1
    ;;
esac
