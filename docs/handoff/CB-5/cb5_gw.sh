#!/bin/bash
# CB-5 lane ONLY. usage: cb5_gw.sh <logname> [filter|-] [extra -D props...] -- <gradle tasks...>
# Runs gradle in the CB-5 worktree with the shared lock; output to scratchpad/cb5_<logname>.log
cd /home/user/burmaldaholic/.claude/worktrees/agent-affcf8598c6661906/java || exit 1
export JAVA_HOME=/root/jdk-25.0.4.1+1
LOG=/tmp/claude-0/-home-user-burmaldaholic/cf2313c3-3d92-597d-8e4c-924bb4d5f96c/scratchpad/cb5_$1.log
shift
FILTER=$1
shift
PROPS=""
while [ "$1" != "--" ] && [ -n "$1" ]; do PROPS="$PROPS $1"; shift; done
shift
if [ "$FILTER" != "-" ]; then PROPS="$PROPS -Dfabric-api.gametest.filter=$FILTER"; fi
export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS $PROPS"
LOCK=${LOCK:-/tmp/claude-0/gradle-java-2.lock}
if [ -n "$CLIENT" ]; then
  flock /tmp/claude-0/gradle-client.lock flock "$LOCK" timeout 2700 xvfb-run -a -s "-screen 0 1280x800x24" ./gradlew --no-daemon "$@" > "$LOG" 2>&1
else
  flock "$LOCK" timeout 2700 ./gradlew --no-daemon "$@" > "$LOG" 2>&1
fi
echo "exit $?"
