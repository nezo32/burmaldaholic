/// <reference types="node" />
/**
 * Independent tester suite: "casino mode off ⇒ nothing happens" (GAME_DESIGN §2.1/§2.2,
 * architecture §4) by static scan of every event subscription, interval and custom command.
 *
 * Each `*.subscribe(...)` / `system.runInterval(...)` callback (the entry points; runTimeout /
 * run are continuations of flows that already started) in runtime code must either be wrapped in `ctx.guard(...)` or test the mode itself
 * (isCasinoEnabled / active() / enabled() / isEnabled()). The few that intentionally run while
 * dormant (cleanup on leave, admin scriptevents, money owed back to players, lifecycle) are
 * listed below with the reason; a new unguarded handler fails this test until it is guarded
 * or reviewed and added here.
 */
import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

const ROOT = path.resolve(__dirname, '../..');
const SRC = path.join(ROOT, 'src');
const files = (fs.readdirSync(SRC, { recursive: true }) as string[])
  .filter((f) => f.endsWith('.ts') && !f.endsWith('.test.ts') && !f.includes(`${path.sep}logic${path.sep}`))
  .map((f) => path.join(SRC, f));

const GUARDS = /\bguard\(|isCasinoEnabled\(|\.active\(\)|\benabled\(\)|isEnabled\(\)|casinoOn\b/;

/** Reviewed: runs while dormant on purpose (file:signal -> reason). */
const ALLOWED: Record<string, string> = {
  'core/registry.ts:startup': 'lifecycle: registers commands/components (commands are guarded by registerCommand)',
  'core/module-core.ts:scriptEventReceive': 'admin scriptevents must work while casino mode is OFF (§2.1)',
  'core/wagers.ts:playerSpawn': 'returns chips owed to the player (offline settlements, refunds §4.1)',
  'core/wagers.ts:runInterval': 'bails out itself while dormant (activeNow: hearts / soul deaths wait, review M2)',
  'core/earning.ts:playerLeave': 'cleanup of the trade-detection maps',
  'core/tables.ts:playerLeave': 'disconnect: ends the session (auto-completes the round)',
  'core/hud.ts:runInterval': 'HUD service receives enabled() and clears itself while dormant',
  'core/hud.ts:playerLeave': 'cleanup',
  'core/achievement-hooks.ts:playerSpawn': 'achievements.unlock() checks enabled() itself',
  'loan/index.ts:scriptEventReceive': 'admin debt commands (ops) while dormant',
  'loan/index.ts:playerLeave': 'cleanup',
  'loan/shark.ts:beforeEvents.entityHurt': 'only protects the shark while a loan form is open',
  'loan/shark.ts:entitySpawn': 'bookkeeping of shark homes',
  'loan/shark.ts:entityLoad': 'bookkeeping of shark homes',
  'loan/shark.ts:entityDie': 'bookkeeping: schedules the shark respawn',
  'loan/collectors.ts:entityLoad': 'removes orphaned squad members',
  'loan/collectors.ts:entityHurt': 'squad members exist only while loans are active (despawned when dormant)',
  'loan/collectors.ts:entityDie': 'squad members exist only while loans are active (despawned when dormant)',
  'loan/collectors.ts:runInterval': 'tick() despawns every squad when !active()',
  'chaos/engine.ts:playerSpawn': 'bookkeeping (respawn time)',
  'chaos/engine.ts:playerLeave': 'cleanup',
  'chaos/engine.ts:weatherChange': 'records the current weather only',
  'chaos/engine.ts:runInterval': 'removes expired chaos mobs (also after the mode was turned off)',
  'vip/vip.ts:playerLeave': 'cleanup',
  'vip/vip.ts:runInterval': 'name tags: restored to plain names while dormant',
  'vip/contracts.ts:playerLeave': 'cleanup',
  'lastchance/index.ts:playerLeave': 'cleanup',
  'multiplayer/ownership.ts:playerLeave': 'cleanup',
  'worldgen/index.ts:scriptEventReceive': 'admin worldgen commands (ops)',
  'games/extras/dice-game.ts:runInterval': 'expires pending PvP challenges (messages only)',
  'games/extras/dice-game.ts:playerLeave': 'drops the leaver\'s challenges',
  'games/slots/index.ts:runInterval': 'reel animation of an already-placed spin',
  'games/blackjack/index.ts:entityRemove': 'closes the NPC table when the dealer entity is removed',
  'games/roulette/table.ts:runInterval': 'round clock; tables are closed with casino_off',
  'games/roulette/table.ts:playerSpawn': 'pays results parked while offline',
  'games/poker/index.ts:playerSpawn': 'returns a table stack left by a disconnect',
};

/** Text of the call's argument list starting at `open` (index of "("). */
function callArgs(src: string, open: number): string {
  let depth = 0;
  for (let i = open; i < src.length; i++) {
    const c = src[i];
    if (c === '(') depth++;
    else if (c === ')' && --depth === 0) return src.slice(open + 1, i);
  }
  return src.slice(open + 1);
}

interface Site {
  key: string;
  file: string;
  line: number;
  guarded: boolean;
}
function sites(): Site[] {
  const out: Site[] = [];
  for (const f of files) {
    const src = fs.readFileSync(f, 'utf8');
    const rel = path.relative(SRC, f).split(path.sep).join('/');
    const re = /(?:(before|after)Events\.(\w+)\.subscribe|system\.(runInterval))\(/g;
    for (let m = re.exec(src); m; m = re.exec(src)) {
      const signal = m[3] ?? (m[1] === 'before' && m[2] !== 'startup' && m[2] !== 'playerLeave' ? `beforeEvents.${m[2]}` : m[2]!);
      const args = callArgs(src, m.index + m[0].length - 1);
      out.push({ key: `${rel}:${signal}`, file: rel, line: src.slice(0, m.index).split('\n').length, guarded: GUARDS.test(args) });
    }
  }
  return out;
}

describe('casino mode OFF: every runtime hook is guarded or reviewed', () => {
  const all = sites();
  it('found the hooks', () => expect(all.length).toBeGreaterThan(60));
  it('no unreviewed unguarded subscription / interval', () => {
    const bad = all.filter((s) => !s.guarded && !ALLOWED[s.key]).map((s) => `${s.file}:${s.line} (${s.key})`);
    expect(bad).toEqual([]);
  });
  it('the reviewed list has no stale entries', () => {
    const keys = new Set(all.filter((s) => !s.guarded).map((s) => s.key));
    expect(Object.keys(ALLOWED).filter((k) => !keys.has(k))).toEqual([]);
  });
  it('every custom command goes through registerCommand (casino-guarded unless bypassCasinoGuard)', () => {
    for (const f of files) {
      const src = fs.readFileSync(f, 'utf8');
      if (f.endsWith(path.join('core', 'commands.ts')) || f.endsWith(path.join('core', 'registry.ts'))) continue;
      expect(/customCommandRegistry\.registerCommand/.test(src), path.relative(SRC, f)).toBe(false);
    }
    const bypass = files.flatMap((f) => [...fs.readFileSync(f, 'utf8').matchAll(/bypassCasinoGuard:\s*true/g)].map(() => path.relative(SRC, f)));
    // only admin/op commands may bypass the guard
    for (const f of bypass) expect(fs.readFileSync(path.join(SRC, f), 'utf8')).toMatch(/permissionLevel|CommandPermissionLevel|isOperator|admin/i);
  });
  it('vanilla difficulty, hardcore flag and game rules are never written (§2.2)', () => {
    for (const f of files) {
      const src = fs.readFileSync(f, 'utf8');
      expect(src, f).not.toMatch(/setDifficulty\(|gameRules\.\w+\s*=|runCommand(?:Async)?\(\s*['"`]\/?(?:gamerule|difficulty)/);
    }
  });
});
