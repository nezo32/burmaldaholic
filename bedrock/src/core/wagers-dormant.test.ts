/**
 * WagerService with casino mode OFF (review M2) and amount hardening (review m9), against a
 * fake @minecraft/server: lost hearts are not enforced while the mod is dormant (their expiry
 * moves by the dormant time), and a Soul Wager lost while offline waits for the mode to be on.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';

type Props = Map<string, unknown>;
const worldProps: Props = new Map();
const online: FakePlayer[] = [];
let spawnHandler: ((e: { player: FakePlayer; initialSpawn: boolean }) => void) | undefined;
let intervals: (() => void)[] = [];
let timeouts: (() => void)[] = [];
let now = 1000;

class FakePlayer {
  isValid = true;
  props: Props = new Map();
  messages: unknown[] = [];
  kills = 0;
  health = { currentValue: 20, effectiveMax: 20, setCurrentValue: (v: number) => (this.health.currentValue = v) };
  constructor(
    readonly id: string,
    readonly name: string,
  ) {}
  getDynamicProperty(k: string) {
    return this.props.get(k);
  }
  setDynamicProperty(k: string, v: unknown) {
    if (v === undefined) this.props.delete(k);
    else this.props.set(k, v);
  }
  sendMessage(m: unknown) {
    this.messages.push(m);
  }
  getComponent(id: string) {
    return id === 'minecraft:health' ? this.health : undefined;
  }
  kill() {
    this.kills++;
  }
  addTag() {}
  removeTag() {}
  playSound() {}
}

vi.mock('@minecraft/server', () => ({
  world: {
    getDynamicProperty: (k: string) => worldProps.get(k),
    setDynamicProperty: (k: string, v: unknown) => (v === undefined ? worldProps.delete(k) : worldProps.set(k, v)),
    getDynamicPropertyIds: () => [...worldProps.keys()],
    getAllPlayers: () => online.filter((p) => p.isValid),
    getAbsoluteTime: () => now,
    getEntity: (id: string) => online.find((p) => p.id === id && p.isValid),
    sendMessage: () => {},
    isHardcore: false,
    scoreboard: { getObjective: () => undefined, addObjective: () => undefined },
    afterEvents: { playerSpawn: { subscribe: (fn: typeof spawnHandler) => (spawnHandler = fn) } },
  },
  system: {
    runInterval: (fn: () => void) => (intervals.push(fn), 0),
    runTimeout: (fn: () => void) => (timeouts.push(fn), 0),
    run: () => 0,
    currentTick: 0,
    clearRun: () => {},
  },
  EntityComponentTypes: { Health: 'minecraft:health', Inventory: 'minecraft:inventory', Equippable: 'minecraft:equippable' },
  EquipmentSlot: { Offhand: 'Offhand', Mainhand: 'Mainhand' },
  ItemComponentTypes: { Durability: 'minecraft:durability', Enchantable: 'minecraft:enchantable' },
  ItemStack: class {},
}));
vi.mock('@minecraft/server-ui', () => ({ ActionFormData: class {}, ModalFormData: class {}, MessageFormData: class {}, FormCancelationReason: {}, uiManager: {} }));

const { ConfigService } = await import('./config');
const { Economy } = await import('./economy');
const { Limits } = await import('./limits');
const { StreakService } = await import('./streak');
const { WagerService } = await import('./wagers');
const { offlineStore } = await import('./offline');

const BAL = 'burmaldaholic:balance';
const HEARTS = 'burmaldaholic:core.hearts';
const MODE = 'burmaldaholic:casino_mode';

function setup() {
  const config = new ConfigService();
  const economy = new Economy(config, { actionbar: () => {} } as never);
  const wagers = new WagerService(economy, new Limits(config), config, new StreakService(config));
  wagers.start();
  return { economy, wagers };
}
const tick = () => intervals.forEach((f) => f());
const flushTimeouts = () => {
  const list = timeouts;
  timeouts = [];
  list.forEach((f) => f());
};
const setMode = (on: boolean) => worldProps.set(MODE, on);
/** Let `ticks` pass with the 10-tick interval running (casino on: no dormancy counted). */
const advance = (ticks: number) => {
  for (let i = 0; i < ticks; i += 10) {
    now += Math.min(10, ticks - i);
    tick();
  }
};

describe('casino mode off: heart penalties and Soul Wager deaths wait (M2)', () => {
  beforeEach(() => {
    worldProps.clear();
    online.length = 0;
    intervals = [];
    timeouts = [];
    now = 1000;
  });

  it('lost hearts are not enforced while off, and expire later by the dormant time', () => {
    setup();
    const p = new FakePlayer('-1', 'Ann');
    online.push(p);
    p.setDynamicProperty(HEARTS, JSON.stringify([{ hearts: 3, until: now + 24000 }]));
    tick(); // on: clamped to 20 - 6
    expect(p.health.currentValue).toBe(14);

    setMode(false);
    p.health.currentValue = 20;
    now += 10_000;
    tick();
    expect(p.health.currentValue).toBe(20); // dormant: vanilla health

    setMode(true);
    now += 20;
    tick();
    expect(p.health.currentValue).toBe(14);
    // Original expiry (1000 + 24000) passed, but 10 000 dormant ticks were added.
    advance(1000 + 24000 + 5000 - now);
    p.health.currentValue = 20;
    tick();
    expect(p.health.currentValue).toBe(14);
    advance(1000 + 24000 + 10_000 + 40 - now);
    p.health.currentValue = 20;
    tick();
    expect(p.health.currentValue).toBe(20);
    expect(p.getDynamicProperty(HEARTS)).toBeUndefined();
  });

  it('a Soul Wager lost while offline does not kill on join with the mode off; it does once on', () => {
    setup();
    offlineStore.update('-2', (e) => ({ ...e, soulDeath: true }));
    setMode(false);
    const p = new FakePlayer('-2', 'Bo');
    online.push(p);
    spawnHandler?.({ player: p, initialSpawn: true });
    tick();
    flushTimeouts();
    expect(p.kills).toBe(0);

    setMode(true);
    tick();
    flushTimeouts();
    expect(p.kills).toBe(1);
    tick();
    flushTimeouts();
    expect(p.kills).toBe(1); // once
  });

  it('the mode turning off during the 2 s delay keeps the death pending', () => {
    setup();
    offlineStore.update('-3', (e) => ({ ...e, soulDeath: true }));
    const p = new FakePlayer('-3', 'Cy');
    online.push(p);
    spawnHandler?.({ player: p, initialSpawn: true });
    setMode(false);
    flushTimeouts();
    expect(p.kills).toBe(0);
    setMode(true);
    tick();
    flushTimeouts();
    expect(p.kills).toBe(1);
  });
});

describe('negative / fractional amounts are refused (m9)', () => {
  beforeEach(() => {
    worldProps.clear();
    online.length = 0;
  });

  it('debit and raise', () => {
    const { economy, wagers } = setup();
    const p = new FakePlayer('-4', 'Di');
    p.setDynamicProperty(BAL, 1000);
    online.push(p);
    expect(economy.debit(p as never, -50, 'x')).toBe(false);
    expect(economy.debit(p as never, 1.5, 'x')).toBe(false);
    expect(p.getDynamicProperty(BAL)).toBe(1000);
    const r = wagers.place(p as never, { game: 'blackjack', stake: { kind: 'chips', amount: 100 }, limits: {} });
    if (!r.ok) throw new Error('place failed');
    expect(wagers.raise(r.ticket, p as never, -100)).toBe(false);
    expect(wagers.raise(r.ticket, p as never, 0)).toBe(false);
    expect(p.getDynamicProperty(BAL)).toBe(900);
    expect(r.ticket.value).toBe(100);
    expect(wagers.raise(r.ticket, p as never, 50)).toBe(true);
    expect(r.ticket.value).toBe(150);
  });
});
