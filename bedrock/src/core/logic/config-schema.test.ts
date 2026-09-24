import { describe, expect, it } from 'vitest';
import { CONFIG_CATALOG } from './config-catalog';
import fs from 'node:fs';
import path from 'node:path';
import { type ConfigDef, ConfigOverrides, crossValidate, enumOptionLabel, fromModuleDef, parseInput, sanitizeValue, validateDefs } from './config-schema';

const int: ConfigDef = { key: 'blackjack.decks', type: 'int', default: 6, min: 1, max: 8, section: 'blackjack', owner: 'blackjack', label: 'x' };
const dbl: ConfigDef = { key: 'blackjack.penetration', type: 'double', default: 0.75, min: 0.25, max: 0.9, section: 'blackjack', owner: 'blackjack', label: 'x' };
const en: ConfigDef = { key: 'lastChance.hardcoreMode', type: 'enum', default: 'DISABLED', options: ['DISABLED', 'HIGH_STAKES'], section: 'lastchance', owner: 'lastchance', label: 'x' };
const list: ConfigDef = { key: 'poker.botMix.micro', type: 'json', shape: 'list', each: [0, 100], default: [50, 40, 10], section: 'poker', owner: 'poker', label: 'x' };
const map = new Map(CONFIG_CATALOG.map((d) => [d.key, d]));

describe('sanitizeValue', () => {
  it('clamps ints and rounds', () => {
    expect(sanitizeValue(int, 50)).toEqual({ value: 8, clamped: true, invalid: false });
    expect(sanitizeValue(int, 3.4).value).toBe(3);
    expect(sanitizeValue(int, 0).value).toBe(1);
  });
  it('wrong type -> default', () => {
    expect(sanitizeValue(int, 'x')).toEqual({ value: 6, clamped: false, invalid: true });
    expect(sanitizeValue(en, 'NOPE').value).toBe('DISABLED');
    expect(sanitizeValue(int, undefined)).toEqual({ value: 6, clamped: false, invalid: false });
  });
  it('keeps doubles', () => expect(sanitizeValue(dbl, 0.8).value).toBe(0.8));
  it('validates json shape and element ranges', () => {
    expect(sanitizeValue(list, [1, 2, 3]).invalid).toBe(false);
    expect(sanitizeValue(list, [1, 200]).invalid).toBe(true);
    expect(sanitizeValue(list, { a: 1 }).invalid).toBe(true);
    expect(sanitizeValue(map.get('extras.wheel.segments')!, [1, 2]).invalid).toBe(true);
  });
});

describe('parseInput', () => {
  it('parses typed values', () => {
    expect(parseInput(int, '1 000')).toBe(1000);
    expect(parseInput(int, '2.5')).toBeUndefined();
    expect(parseInput(dbl, '0,5')).toBe(0.5);
    expect(parseInput(en, 'high_stakes')).toBe('HIGH_STAKES');
    expect(parseInput(list, '[1,2,3]')).toEqual([1, 2, 3]);
    expect(parseInput({ ...int, type: 'bool', default: true }, 'off')).toBe(false);
  });
});

describe('catalog (generated from CONFIG.md)', () => {
  it('is valid (unique keys, defaults in range)', () => expect(() => validateDefs(CONFIG_CATALOG)).not.toThrow());
  it('has the spec defaults', () => {
    expect(map.get('economy.startingBalance')?.default).toBe(50);
    expect(map.get('economy.maxBalance')?.default).toBe(1_000_000_000);
    expect(map.get('streak.luckyPerStep')?.default).toBe(0.005);
    expect(map.get('vip.maxBet.bronze')?.default).toBe(100);
    expect(map.get('chaos.weight.golden_hour')?.default).toBe(2);
    expect(map.get('wager.appraisal.elytra')?.default).toBe(500);
    expect(map.has('core.casinoMode')).toBe(false);
  });
  it('wheel segments match appendix B counts', () => {
    const seg = map.get('extras.wheel.segments')!.default as string[];
    const n = (c: string) => seg.filter((s) => s === c).length;
    expect([seg.length, n('B'), n('C'), n('H'), n('M'), n('D'), n('T'), n('E'), n('X')]).toEqual([54, 25, 1, 5, 11, 7, 3, 1, 1]);
  });
  it('slots v2: Treasure Hunt weights total 102 073 (SLOTS.md §7.3: 80 073 prizes + 22 000 creeper); v1 keys gone', () => {
    const w = map.get('slots.overworld.pick.weights')!.default as Record<string, number>;
    expect(Object.values(w).reduce((a, b) => a + b, 0)).toBe(102_073);
    expect(w.creeper).toBe(22_000);
    for (const tier of ['copper', 'gold', 'netherite']) expect(map.has(`slots.${tier}.weights`)).toBe(false);
  });
  it('every def has a label and section', () => {
    for (const d of CONFIG_CATALOG) {
      expect(d.label.startsWith('config.burmaldaholic.')).toBe(true);
      expect(d.section).toBeTruthy();
    }
  });
});

describe('overrides', () => {
  it('stores only non-defaults and round-trips', () => {
    const o = ConfigOverrides.parse(map, undefined);
    o.set('blackjack.decks', 6);
    expect(o.serialize()).toBe('{}');
    expect(o.set('blackjack.decks', 99).clamped).toBe(true);
    const o2 = ConfigOverrides.parse(map, o.serialize());
    expect(o2.get('blackjack.decks')).toBe(8);
    o2.reset('blackjack.decks');
    expect(o2.get('blackjack.decks')).toBe(6);
  });
  it('ignores unknown and corrupt data', () => {
    expect(ConfigOverrides.parse(map, '{"nope":1}').serialize()).toBe('{}');
    expect(ConfigOverrides.parse(map, '{bad').get('blackjack.decks')).toBe(6);
  });
  it('applies cross-key rules', () => {
    const o = ConfigOverrides.parse(map, JSON.stringify({ 'economy.emeraldSellRate': 5, 'vip.threshold.gold': 10 }));
    expect(o.get('economy.emeraldSellRate')).toBe(9);
    expect(o.get('vip.threshold.gold')).toBe(5001);
  });
  it('crossValidate floors minHouseEdge', () => expect(crossValidate((k) => (k === 'streak.minHouseEdge' ? 0.001 : undefined))).toEqual({ 'streak.minHouseEdge': 0.005 }));
});

describe('module declarations', () => {
  it('prefixes relative names (lastChance spelling)', () => {
    expect(fromModuleDef('slots', { type: 'bool', name: 'enabled', default: true }).key).toBe('slots.enabled');
    expect(fromModuleDef('lastchance', { type: 'bool', name: 'enabled', default: true }).key).toBe('lastChance.enabled');
    expect(fromModuleDef('vip', { type: 'int', name: 'x.y', default: 1, min: 0, max: 2 }).key).toBe('x.y');
  });
});

describe('enum option labels in the admin form (review m7)', () => {
  it('uses optionLabels, else the conventional key - never the raw option name', () => {
    expect(enumOptionLabel(en, 1)).toBe('config.burmaldaholic.lastChance.hardcoreMode.high_stakes');
    expect(enumOptionLabel({ ...en, optionLabels: ['a', 'b'] }, 1)).toBe('b');
  });
  it('every catalog enum option label exists in the lang files', () => {
    const dir = path.resolve(__dirname, '../../../lang');
    const keys = new Set<string>();
    for (const m of fs.readdirSync(dir)) {
      const f = path.join(dir, m, 'en_US.lang');
      if (fs.existsSync(f)) for (const line of fs.readFileSync(f, 'utf8').split('\n')) keys.add(line.split('=')[0]!);
    }
    const missing = CONFIG_CATALOG.filter((d) => d.type === 'enum').flatMap((d) => (d.options ?? []).map((_, i) => enumOptionLabel(d, i)).filter((k) => !keys.has(k)));
    expect(missing).toEqual([]);
  });
});
