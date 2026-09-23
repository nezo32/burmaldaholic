import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import { type LeaveReason, leavePolicy } from './sessions';

describe('leavePolicy (GAME_DESIGN §4.1, review B1)', () => {
  it('a broken table plays the round out like a leave: no free refund', () => {
    expect(leavePolicy('broken')).toBe('play_out');
  });
  it('leave / walk-away / disconnect / replaced play out', () => {
    for (const r of ['leave', 'distance', 'disconnect', 'replaced'] as LeaveReason[]) expect(leavePolicy(r)).toBe('play_out');
  });
  it('only casino mode off refunds', () => {
    expect(leavePolicy('casino_off')).toBe('refund');
  });
});

describe('every game follows leavePolicy (B1 regression)', () => {
  it("no game special-cases the 'broken' reason (it must play out like a leave)", () => {
    const root = path.resolve(__dirname, '../../games');
    const files = (fs.readdirSync(root, { recursive: true }) as string[]).filter((f) => f.endsWith('.ts') && !f.endsWith('.test.ts'));
    const hits = files.filter((f) => /reason\s*===\s*'broken'|'broken'\s*===\s*reason/.test(fs.readFileSync(path.join(root, f), 'utf8')));
    expect(hits).toEqual([]);
  });
});
