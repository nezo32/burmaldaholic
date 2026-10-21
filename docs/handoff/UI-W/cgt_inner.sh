#!/bin/bash
# Runs under both locks: filter fabric.mod.json to the board test, run, restore.
export JAVA_HOME=~/jdk-25.0.4.1+1
export BURMALDAHOLIC_CGT=W
J=/home/user/burmaldaholic/.claude/worktrees/agent-a60d35a15f5cae539/java
MJ=$J/src/gametest/resources/fabric.mod.json
W=/tmp/claude-0/-home-user-burmaldaholic/cf2313c3-3d92-597d-8e4c-924bb4d5f96c/scratchpad/W
cp "$MJ" "$W/fabric.mod.json.full"
python3 - "$MJ" <<'EOF'
import json, sys
p = sys.argv[1]
d = json.load(open(p))
d['entrypoints']['fabric-client-gametest'] = ['dev.nezo.burmaldaholic.gametest.v2.board.BoardClientGameTests']
d['entrypoints']['fabric-gametest'] = []
json.dump(d, open(p, 'w'), indent='\t')
EOF
cd "$J" || exit 1
rm -rf build/run/clientGameTest/screenshots/board_*
echo "lock acquired $(date)"
timeout 2700 xvfb-run -a -s "-screen 0 1280x800x24" ./gradlew --no-daemon runClientGameTest
rc=$?
cp "$W/fabric.mod.json.full" "$MJ"
echo "RUN-EXIT $rc $(date)"
