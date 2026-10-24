#!/bin/sh
set -eu

export EXECUTION_EXTRA_CLASSPATH="${EXECUTION_EXTRA_CLASSPATH:-/opt/cxe-runner/lib/*}"
export CXE_NSJAIL_BIN="${CXE_NSJAIL_BIN:-/usr/local/bin/nsjail}"
export CXE_RUNNER_WORKSPACE_ROOT="${CXE_RUNNER_WORKSPACE_ROOT:-/workspace}"
export CXE_RUNNER_QUARANTINE_ROOT="${CXE_RUNNER_QUARANTINE_ROOT:-/quarantine}"

exec /opt/cxe-runner/bin/nsjail-runner "$@"
