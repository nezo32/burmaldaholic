# temporary (NOT committed): only lane B's client classes in fabric.mod.json; restore with git checkout
j = '/home/user/burmaldaholic/.claude/worktrees/agent-adf68662b2ca6ddcf/java/src/gametest/resources/fabric.mod.json'
s = open(j).read()
start = s.index('"fabric-client-gametest": [')
end = s.index(']', start)
s = s[:start] + '"fabric-client-gametest": [\n\t\t\t"dev.nezo.burmaldaholic.gametest.v2.poker.PokerV2ClientGameTests"\n\t\t' + s[end:]
open(j, 'w').write(s)
