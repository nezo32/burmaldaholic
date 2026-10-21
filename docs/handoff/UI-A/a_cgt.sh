#!/bin/bash
# usage: a_cgt.sh <ClientTestClass fqcn>... ; runs only those client gametests (temporarily edits fabric.mod.json)
F=/home/user/burmaldaholic/.claude/worktrees/agent-a51292b1a957e7a6b/java/src/gametest/resources/fabric.mod.json
cp "$F" /tmp/claude-0/-home-user-burmaldaholic/cf2313c3-3d92-597d-8e4c-924bb4d5f96c/scratchpad/fabric.mod.json.bak
python3 - "$F" "$@" <<'EOF'
import json,sys
p=sys.argv[1]; d=json.load(open(p))
d['entrypoints']['fabric-client-gametest']=sys.argv[2:]
json.dump(d,open(p,'w'),indent='\t')
EOF
rm -rf /home/user/burmaldaholic/.claude/worktrees/agent-a51292b1a957e7a6b/java/build/run/clientGameTest/screenshots
bash /tmp/claude-0/-home-user-burmaldaholic/cf2313c3-3d92-597d-8e4c-924bb4d5f96c/scratchpad/a_gradle.sh client runClientGameTest > /tmp/claude-0/-home-user-burmaldaholic/cf2313c3-3d92-597d-8e4c-924bb4d5f96c/scratchpad/cgt.log 2>&1
rc=$?
cp /tmp/claude-0/-home-user-burmaldaholic/cf2313c3-3d92-597d-8e4c-924bb4d5f96c/scratchpad/fabric.mod.json.bak "$F"
echo "exit $rc"
grep -E 'FAIL|AssertionError|Exception|BUILD|Tests? (passed|failed)' /tmp/claude-0/-home-user-burmaldaholic/cf2313c3-3d92-597d-8e4c-924bb4d5f96c/scratchpad/cgt.log | grep -v JAVA_TOOL | head -30
ls /home/user/burmaldaholic/.claude/worktrees/agent-a51292b1a957e7a6b/java/build/run/clientGameTest/screenshots 2>/dev/null | head -50
