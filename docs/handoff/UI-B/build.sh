#!/bin/bash
# lane B full build (server gametests included); arg: extra gradle args (e.g. -Pmc=26.3)
cd /home/user/burmaldaholic/.claude/worktrees/agent-adf68662b2ca6ddcf/java
export JAVA_HOME=~/jdk-25.0.4.1+1
LOG=/tmp/claude-0/-home-user-burmaldaholic/cf2313c3-3d92-597d-8e4c-924bb4d5f96c/scratchpad/b/build$1.log
flock /tmp/claude-0/gradle-java-2.lock timeout 2700 ./gradlew --no-daemon build $1 > $LOG 2>&1
echo "exit $?"
grep -E "FAILED|BUILD|error:|failed|Failed|\[.*\] .*test.*fail" $LOG | grep -v "^> Task" | head -40
