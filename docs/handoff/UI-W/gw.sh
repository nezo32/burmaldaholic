#!/bin/bash
# Lane W gradle runner: runs in the W worktree under a shared lock. Usage: W/gw.sh <lockfile> <gradle args...>
export JAVA_HOME=~/jdk-25.0.4.1+1
cd /home/user/burmaldaholic/.claude/worktrees/agent-a60d35a15f5cae539/java || exit 1
LOCK="$1"
shift
flock "$LOCK" timeout 2700 ./gradlew --no-daemon -q "$@" 2>&1 | grep -v "^Note:\|warning:\|JAVA_TOOL"
exit ${PIPESTATUS[0]}
