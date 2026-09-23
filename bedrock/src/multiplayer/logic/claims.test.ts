import { describe, expect, it } from 'vitest';
import { type Casino, casinoAt, checkClaim, inClaim, mayBreak, nextCasinoId, parseTableKey } from './claims';

const OW = 'minecraft:overworld';
const c = (id: string, owner: string, x: number, z: number, radius = 24, dimension = OW): Casino => ({
  id,
  ownerId: owner,
  ownerName: owner,
  dimension,
  x,
  y: 64,
  z,
  radius,
  created: 0,
});
const rules = { enabled: true, radius: 24, maxPerPlayer: 1 };

describe('claims', () => {
  it('inClaim is a horizontal cylinder, full height, per dimension', () => {
    const k = c('c1', 'a', 100, 100);
    expect(inClaim(k, OW, { x: 124, y: -60, z: 100 })).toBe(true);
    expect(inClaim(k, OW, { x: 100, y: 300, z: 100 })).toBe(true);
    expect(inClaim(k, OW, { x: 125, y: 64, z: 100 })).toBe(false);
    expect(inClaim(k, 'minecraft:nether', { x: 100, y: 64, z: 100 })).toBe(false);
  });
  it('inClaim diagonal edge', () => {
    const k = c('c1', 'a', 0, 0);
    expect(inClaim(k, OW, { x: 16, y: 0, z: 17 })).toBe(true); // 545 ≤ 576
    expect(inClaim(k, OW, { x: 17, y: 0, z: 17 })).toBe(false); // 578 > 576
  });
  it('floors fractional positions', () => {
    const k = c('c1', 'a', 0, 0, 4);
    expect(inClaim(k, OW, { x: 4.9, y: 0, z: 0.5 })).toBe(true);
    expect(inClaim(k, OW, { x: -4.1, y: 0, z: 0 })).toBe(false);
  });
  it('casinoAt finds the containing casino', () => {
    const list = [c('c1', 'a', 0, 0), c('c2', 'b', 100, 0)];
    expect(casinoAt(list, OW, { x: 90, y: 0, z: 5 })?.id).toBe('c2');
    expect(casinoAt(list, OW, { x: 50, y: 0, z: 0 })).toBeUndefined();
  });
  it('rejects overlaps (distance < r1 + r2)', () => {
    const list = [c('c1', 'a', 0, 0)];
    expect(checkClaim(list, 'b', OW, { x: 47, y: 0, z: 0 }, rules)).toEqual({ ok: false, reason: 'overlap' });
    expect(checkClaim(list, 'b', OW, { x: 48, y: 0, z: 0 }, rules)).toEqual({ ok: true });
    expect(checkClaim(list, 'b', 'minecraft:nether', { x: 0, y: 0, z: 0 }, rules)).toEqual({ ok: true });
  });
  it('rejects spawn protection in the spawn dimension only', () => {
    const r = { ...rules, spawn: { x: 0, y: 64, z: 0 } };
    expect(checkClaim([], 'a', OW, { x: 39, y: 0, z: 0 }, r)).toEqual({ ok: false, reason: 'overlap' });
    expect(checkClaim([], 'a', OW, { x: 40, y: 0, z: 0 }, r)).toEqual({ ok: true });
    expect(checkClaim([], 'a', 'minecraft:the_end', { x: 0, y: 0, z: 0 }, r)).toEqual({ ok: true });
  });
  it('enforces maxPerPlayer and disabled', () => {
    const list = [c('c1', 'a', 1000, 1000)];
    expect(checkClaim(list, 'a', OW, { x: 0, y: 0, z: 0 }, rules)).toEqual({ ok: false, reason: 'limit' });
    expect(checkClaim(list, 'a', OW, { x: 0, y: 0, z: 0 }, { ...rules, maxPerPlayer: 2 })).toEqual({ ok: true });
    expect(checkClaim([], 'a', OW, { x: 0, y: 0, z: 0 }, { ...rules, maxPerPlayer: 0 })).toEqual({ ok: false, reason: 'disabled' });
    expect(checkClaim([], 'a', OW, { x: 0, y: 0, z: 0 }, { ...rules, enabled: false })).toEqual({ ok: false, reason: 'disabled' });
  });
  it('break permissions', () => {
    expect(mayBreak({ isOwner: true, isOperator: false, protect: true, isCharter: true })).toBe(true);
    expect(mayBreak({ isOwner: false, isOperator: true, protect: true, isCharter: false })).toBe(true);
    expect(mayBreak({ isOwner: false, isOperator: false, protect: true, isCharter: false })).toBe(false);
    expect(mayBreak({ isOwner: false, isOperator: false, protect: false, isCharter: false })).toBe(true);
    expect(mayBreak({ isOwner: false, isOperator: false, protect: false, isCharter: true })).toBe(false);
  });
  it('ids and table keys', () => {
    expect(nextCasinoId(0)).toEqual({ id: 'c1', counter: 1 });
    expect(nextCasinoId(41).id).toBe('c42');
    expect(parseTableKey('minecraft:overworld|-12,64,300')).toEqual({ dimension: OW, x: -12, y: 64, z: 300 });
    expect(parseTableKey('npc:123')).toBeUndefined();
  });
});
