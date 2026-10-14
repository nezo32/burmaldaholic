#!/bin/bash
# usage: g.sh <log> <tasks...>
export JAVA_HOME=~/jdk-25.0.4.1+1
cd /home/user/burmaldaholic/.claude/worktrees/agent-aff40330cf445df3c/java
log=$1; shift
L=/tmp/claude-0/gradle-java.lock
if ! flock -n /tmp/claude-0/gradle-java-2.lock true; then :; else L=/tmp/claude-0/gradle-java-2.lock; fi
flock $L timeout 2700 ./gradlew --no-daemon "$@" > $log 2>&1
echo "exit $?" >> $log
