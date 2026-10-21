#!/bin/bash
# usage: cgt.sh <log> <class1,class2,...>   (runs only those client gametest classes; restores fabric.mod.json)
export JAVA_HOME=~/jdk-25.0.4.1+1
W=/home/user/burmaldaholic/.claude/worktrees/agent-aff40330cf445df3c/java
log=$1; classes=$2
F=$W/src/gametest/resources/fabric.mod.json
B=/tmp/claude-0/-home-user-burmaldaholic/cf2313c3-3d92-597d-8e4c-924bb4d5f96c/scratchpad/E/fmj.bak
: > $log
# wait for the client lock FIRST (unmodified tree meanwhile), then swap the entrypoints only while we run
exec 9>/tmp/claude-0/gradle-client.lock
flock 9
cp $F $B
trap 'cp $B $F' EXIT
python3 - "$F" "$classes" <<'PY'
import json,sys
f,cl=sys.argv[1],sys.argv[2]
d=json.load(open(f)); d['entrypoints']['fabric-client-gametest']=cl.split(',')
json.dump(d,open(f,'w'),indent='\t')
PY
cd $W
rm -rf build/run/clientGameTest/screenshots
BURMALDAHOLIC_CGT=E flock /tmp/claude-0/gradle-java.lock timeout 2700 xvfb-run -a -s "-screen 0 2560x1440x24" ./gradlew --no-daemon runClientGameTest >> $log 2>&1
echo "exit $?" >> $log
