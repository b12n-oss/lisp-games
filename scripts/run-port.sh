#!/usr/bin/env sh
# Launch one port for screen-grab, the internal capture tool (not public yet).
# Nothing else uses this script, and nothing in the repo needs it to build:
# the GIFs it helps produce are committed.
#
#   scripts/run-port.sh <game> <runtime-dir> <seconds> <command> [args...]
#
# Two things this exists for, both taken from the sibling raylib-pacman rather
# than rediscovered:
#
# 1. screen-grab's :run is TOKENIZED, not handed to a shell, so a
#    `cd X && ...` in the manifest would exec `cd` with the rest as its
#    arguments. This wrapper is the shell that string cannot be.
#
# 2. `exec` keeps the pid. screen-grab targets the pid it spawned, and a
#    capture aimed at a process that owns no window waits forever. Running the
#    real command through `exec` means the spawned pid IS the window owner for
#    babashka, Clojure (the clojure script execs java) and jolt. jank is
#    recorded from its AOT binary, which owns its own window; `lein run` would
#    start it as a child AND run the -O0 build.
#
# Seconds goes last because every port here takes [seconds] as its first
# argument, which is what makes the whole repo scriptable.
set -eu

game=${1:?usage: run-port.sh <game> <runtime-dir> <seconds> <command> [args...]}
dir=${2:?usage: run-port.sh <game> <runtime-dir> <seconds> <command> [args...]}
secs=${3:?usage: run-port.sh <game> <runtime-dir> <seconds> <command> [args...]}
shift 3
[ "$#" -gt 0 ] || { echo "run-port.sh: no command given" >&2; exit 2; }

cd "$(dirname "$0")/.." || exit 1
cd "$game/$dir" || exit 1
exec "$@" "$secs"
