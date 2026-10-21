#!/bin/bash
# usage: gw.sh <lock-slot 1|2|client> <gradle args...>
cd /home/user/burmaldaholic/.claude/worktrees/agent-a37c8ab1842efc996/java || exit 1
export JAVA_HOME=~/jdk-25.0.4.1+1
slot=$1; shift
case $slot in
  1) exec flock /tmp/claude-0/gradle-java.lock timeout 2700 ./gradlew --no-daemon "$@" ;;
  2) exec flock /tmp/claude-0/gradle-java-2.lock timeout 2700 ./gradlew --no-daemon "$@" ;;
  client) exec flock /tmp/claude-0/gradle-client.lock flock /tmp/claude-0/gradle-java.lock timeout 2700 xvfb-run -a -s "-screen 0 2560x1440x24" ./gradlew --no-daemon "$@" ;;
esac
