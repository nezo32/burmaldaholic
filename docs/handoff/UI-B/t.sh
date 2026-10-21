#!/bin/bash
# usage: t.sh <test filter> ; runs unit tests in lane B worktree and prints failures
cd /home/user/burmaldaholic/.claude/worktrees/agent-adf68662b2ca6ddcf/java
export JAVA_HOME=~/jdk-25.0.4.1+1
flock /tmp/claude-0/gradle-java-2.lock timeout 2700 ./gradlew --no-daemon -q ${TASKS:-} test --tests "$1" 2>&1 | grep -v JAVA_TOOL | tail -25
python3 /tmp/claude-0/-home-user-burmaldaholic/cf2313c3-3d92-597d-8e4c-924bb4d5f96c/scratchpad/b/fail.py
