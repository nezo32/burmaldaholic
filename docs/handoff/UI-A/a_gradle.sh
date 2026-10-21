#!/bin/bash
# usage: a_gradle.sh <lock> <gradle args...>
export JAVA_HOME=~/jdk-25.0.4.1+1
cd /home/user/burmaldaholic/.claude/worktrees/agent-a51292b1a957e7a6b/java || exit 1
LOCK="$1"; shift
if [ "$LOCK" = client ]; then
  exec flock /tmp/claude-0/gradle-client.lock flock /tmp/claude-0/gradle-java.lock timeout 2700 xvfb-run -a -s "-screen 0 2560x1440x24" ./gradlew --no-daemon "$@"
elif [ "$LOCK" = 2 ]; then
  exec flock /tmp/claude-0/gradle-java-2.lock timeout 2700 ./gradlew --no-daemon "$@"
else
  exec flock /tmp/claude-0/gradle-java.lock timeout 2700 ./gradlew --no-daemon "$@"
fi
