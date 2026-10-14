#!/bin/bash
# CB-5 lane ONLY: runs the client GameTests of the scratch copy (cb5_client) under the client + java locks.
# usage: cb5_client.sh <logname> [-Dprops...]
cd /tmp/claude-0/-home-user-burmaldaholic/cf2313c3-3d92-597d-8e4c-924bb4d5f96c/scratchpad/cb5_client/java || exit 1
export JAVA_HOME=/root/jdk-25.0.4.1+1
LOG=/tmp/claude-0/-home-user-burmaldaholic/cf2313c3-3d92-597d-8e4c-924bb4d5f96c/scratchpad/cb5_$1.log
shift
export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS $*"
flock /tmp/claude-0/gradle-client.lock flock /tmp/claude-0/gradle-java.lock timeout 2700 xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew --no-daemon runClientGameTest > "$LOG" 2>&1
echo "exit $?"
