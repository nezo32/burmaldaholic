#!/bin/bash
# lane B client GameTest run (only one client run machine-wide)
cd /home/user/burmaldaholic/.claude/worktrees/agent-adf68662b2ca6ddcf/java
export JAVA_HOME=~/jdk-25.0.4.1+1
export BURMALDAHOLIC_CGT=B
flock /tmp/claude-0/gradle-client.lock flock /tmp/claude-0/gradle-java.lock timeout 2700 xvfb-run -a -s "-screen 0 2560x1440x24" ./gradlew --no-daemon runClientGameTest > /tmp/claude-0/-home-user-burmaldaholic/cf2313c3-3d92-597d-8e4c-924bb4d5f96c/scratchpad/b/cgt.log 2>&1
echo "exit $?"
grep -E "lane B|AssertionError|FAILED|BUILD" /tmp/claude-0/-home-user-burmaldaholic/cf2313c3-3d92-597d-8e4c-924bb4d5f96c/scratchpad/b/cgt.log | head -30
