/**
 * WagerService integration test with a fake @minecraft/server: offline settlement must pay
 * exactly once (settle while offline, rejoin, restart before rejoin, double settle calls).
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';

type Props = Map<string, unknown>;
const worldProps: Props = new Map();
const online: FakePlayer[] = [];
let spawnHandler: ((e: { player: FakePlayer; initialSpawn: boolean }) => void) | undefined;

class FakePlayer {
  isValid = true;
  props: Props = new Map();
  messages: unknown[] = [];
  /** total XP points; `level` is derived like vanilla */
  xp = 0;
  get level(): number {
    let l = 0;
    while (xpAt(l + 1) <= this.xp) l++;
    return l;
  }
  getTotalXp() {
    return this.xp;
  }
  resetLevel() {
    this.xp = 0;
  }
  addExperience(n: number) {
    this.xp += n;
  }
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
  getComponent() {
    return undefined;
  }
  addTag() {}
  removeTag() {}
  playSound() {}
}

const xpNext = (l: number) => (l < 16 ? 2 * l + 7 : l < 31 ? 5 * l - 38 : 9 * l - 158);
const xpAt = (l: number) => {
  let sum = 0;
  for (let i = 0; i < l; i++) sum += xpNext(i);
  return sum;
};

vi.mock('@minecraft/server', () => ({
  world: {
    getDynamicProperty: (k: string) => worldProps.get(k),
    setDynamicProperty: (k: string, v: unknown) => (v === undefined ? worldProps.delete(k) : worldProps.set(k, v)),
    getDynamicPropertyIds: () => [...worldProps.keys()],
    getAllPlayers: () => online.filter((p) => p.isValid),
    getAbsoluteTime: () => 1000,
    getEntity: (id: string) => online.find((p) => p.id === id && p.isValid),
    sendMessage: () => {},
    isHardcore: false,
    scoreboard: { getObjective: () => undefined, addObjective: () => undefined },
    afterEvents: { playerSpawn: { subscribe: (fn: typeof spawnHandler) => (spawnHandler = fn) } },
  },
  system: { runInterval: () => 0, runTimeout: () => 0, run: () => 0, currentTick: 0, clearRun: () => {} },
  EntityComponentTypes: { Health: 'minecraft:health', Inventory: 'minecraft:inventory' },
  ItemComponentTypes: { Durability: 'minecraft:durability', Enchantable: 'minecraft:enchantable' },
  ItemStack: class {},
}));
vi.mock('@minecraft/server-ui', () => ({ ActionFormData: class {}, ModalFormData: class {}, MessageFormData: class {}, FormCancelationReason: {}, uiManager: {} }));

const { ConfigService } = await import('./config');
const { Economy } = await import('./economy');
const { Limits } = await import('./limits');
const { StreakService } = await import('./streak');
const { WagerService } = await import('./wagers');

const BAL = 'burmaldaholic:balance';

function setup() {
  const config = new ConfigService();
  const economy = new Economy(config, { actionbar: () => {} } as never);
  const wagers = new WagerService(economy, new Limits(config), config, new StreakService(config));
  wagers.start();
  return { economy, wagers };
}

/** A disconnect: the old Player object turns invalid. A rejoin creates a new object. */
function disconnect(p: FakePlayer) {
  p.isValid = false;
  online.splice(online.indexOf(p), 1);
}
function rejoin(old: FakePlayer): FakePlayer {
  const p = new FakePlayer(old.id, old.name);
  p.props = new Map(old.props);
  online.push(p);
  spawnHandler?.({ player: p, initialSpawn: true });
  return p;
}

describe('WagerService offline settlement', () => {
  beforeEach(() => {
    worldProps.clear();
    online.length = 0;
  });

  it('settle while offline pays once on rejoin; a second settle call is a no-op', () => {
    const { wagers } = setup();
    const alex = new FakePlayer('-1', 'Alex');
    alex.setDynamicProperty(BAL, 1000);
    online.push(alex);
    const r = wagers.place(alex as never, { game: 'blackjack', stake: { kind: 'chips', amount: 100 }, limits: {} });
    expect(r.ok).toBe(true);
    if (!r.ok) return;
    expect(alex.getDynamicProperty(BAL)).toBe(900);
    disconnect(alex);
    const ev = wagers.settle(r.ticket, alex as never, 250);
    expect(ev?.deferred).toBe(true);
    expect(wagers.settle(r.ticket, alex as never, 250)).toBeUndefined();
    const back = rejoin(alex);
    expect(back.getDynamicProperty(BAL)).toBe(1150);
    // a later join pays nothing more
    disconnect(back);
    const again = rejoin(back);
    expect(again.getDynamicProperty(BAL)).toBe(1150);
  });

  it('a restart between the offline settlement and the rejoin does not refund the round too', () => {
    const first = setup();
    const bea = new FakePlayer('-2', 'Bea');
    bea.setDynamicProperty(BAL, 500);
    online.push(bea);
    const r = first.wagers.place(bea as never, { game: 'roulette', stake: { kind: 'chips', amount: 200 }, skipLimits: true });
    expect(r.ok).toBe(true);
    if (!r.ok) return;
    disconnect(bea);
    first.wagers.settle(r.ticket, undefined, 0); // lost while offline
    // server restart: new boot, new service
    setup();
    const back = rejoin(bea);
    expect(back.getDynamicProperty(BAL)).toBe(300); // not refunded
  });

  it('an open round of an earlier run is still refunded on join', () => {
    const first = setup();
    const cy = new FakePlayer('-3', 'Cy');
    cy.setDynamicProperty(BAL, 500);
    online.push(cy);
    const r = first.wagers.place(cy as never, { game: 'craps', stake: { kind: 'chips', amount: 50 }, skipLimits: true });
    expect(r.ok).toBe(true);
    disconnect(cy);
    setup();
    const back = rejoin(cy);
    expect(back.getDynamicProperty(BAL)).toBe(500);
  });

  describe('restart with a drawn outcome (review M1: no free roll)', () => {
    /** A server stop: every player is gone; a new boot starts a new service. */
    function restart() {
      for (const p of [...online]) disconnect(p);
      return setup();
    }
    const drawnKeys = () => [...worldProps.keys()].filter((k) => k.startsWith('burmaldaholic:core.drawn.'));

    it('a drawn losing round is settled at its result exactly once, not refunded', () => {
      const first = setup();
      const fay = new FakePlayer('-10', 'Fay');
      fay.setDynamicProperty(BAL, 1000);
      online.push(fay);
      const settled: unknown[] = [];
      const r = first.wagers.place(fay as never, { game: 'slots', stake: { kind: 'chips', amount: 100 }, skipLimits: true });
      if (!r.ok) throw new Error('place failed');
      first.wagers.draw(r.ticket, 0); // losing grid drawn, reels still spinning
      expect(fay.getDynamicProperty(BAL)).toBe(900);
      // the player sees the losing grid and quits: server stop before the spin settles
      const second = restart();
      second.wagers.onSettled((e) => settled.push(e));
      expect(drawnKeys()).toEqual([]); // parked at world load
      const back = rejoin(fay);
      expect(back.getDynamicProperty(BAL)).toBe(900); // NOT refunded
      expect(settled).toHaveLength(1);
      expect(settled[0]).toMatchObject({ game: 'slots', staked: 100, totalReturn: 0, net: -100, deferred: true });
      expect(back.messages).toContainEqual(expect.objectContaining({ translate: 'msg.burmaldaholic.core.round_played_out' }));
      // later restarts and joins pay nothing more
      const third = restart();
      third.wagers.onSettled((e) => settled.push(e));
      const again = rejoin(back);
      expect(again.getDynamicProperty(BAL)).toBe(900);
      expect(settled).toHaveLength(1);
    });

    it('a drawn winning round pays its drawn result; an undrawn round is refunded', () => {
      const first = setup();
      const gus = new FakePlayer('-11', 'Gus');
      gus.setDynamicProperty(BAL, 1000);
      online.push(gus);
      const won = first.wagers.place(gus as never, { game: 'roulette', stake: { kind: 'chips', amount: 100 }, skipLimits: true });
      const open = first.wagers.place(gus as never, { game: 'craps', stake: { kind: 'chips', amount: 50 }, skipLimits: true });
      if (!won.ok || !open.ok) throw new Error('place failed');
      first.wagers.draw(won.ticket, 3600);
      expect(gus.getDynamicProperty(BAL)).toBe(850);
      restart();
      const back = rejoin(gus);
      expect(back.getDynamicProperty(BAL)).toBe(850 + 3600 + 50);
      expect(back.messages).toContainEqual(expect.objectContaining({ translate: 'msg.burmaldaholic.core.round_refunded' }));
    });

    it('a round drawn while its player is offline is settled too', () => {
      const first = setup();
      const hal = new FakePlayer('-12', 'Hal');
      hal.setDynamicProperty(BAL, 500);
      online.push(hal);
      const r = first.wagers.place(hal as never, { game: 'roulette', stake: { kind: 'chips', amount: 200 }, skipLimits: true });
      if (!r.ok) throw new Error('place failed');
      disconnect(hal); // the player property can no longer be written
      first.wagers.draw(r.ticket, 0);
      restart();
      const back = rejoin(hal);
      expect(back.getDynamicProperty(BAL)).toBe(300);
    });

    it('a raise after the draw is settled with the raised stake; re-drawing replaces the result', () => {
      const first = setup();
      const ivy = new FakePlayer('-13', 'Ivy');
      ivy.setDynamicProperty(BAL, 1000);
      online.push(ivy);
      const r = first.wagers.place(ivy as never, { game: 'blackjack', stake: { kind: 'chips', amount: 100 }, limits: {} });
      if (!r.ok) throw new Error('place failed');
      first.wagers.draw(r.ticket, 200); // stand now: win
      expect(first.wagers.raise(r.ticket, ivy as never, 100)).toBe(true); // double
      first.wagers.draw(r.ticket, 0); // the double card busts
      const settled: { staked: number; totalReturn: number }[] = [];
      const second = restart();
      second.wagers.onSettled((e) => settled.push(e));
      const back = rejoin(ivy);
      expect(back.getDynamicProperty(BAL)).toBe(800);
      expect(settled).toEqual([expect.objectContaining({ staked: 200, totalReturn: 0 })]);
    });

    it('a drawn round settled normally leaves nothing for the restart', () => {
      const first = setup();
      const jo = new FakePlayer('-14', 'Jo');
      jo.setDynamicProperty(BAL, 1000);
      online.push(jo);
      const r = first.wagers.place(jo as never, { game: 'slots', stake: { kind: 'chips', amount: 100 }, skipLimits: true });
      if (!r.ok) throw new Error('place failed');
      first.wagers.draw(r.ticket, 500);
      first.wagers.settle(r.ticket, jo as never, 500);
      expect(first.wagers.settle(r.ticket, jo as never, 500)).toBeUndefined();
      expect(drawnKeys()).toEqual([]);
      expect(jo.getDynamicProperty(BAL)).toBe(1400);
      restart();
      const back = rejoin(jo);
      expect(back.getDynamicProperty(BAL)).toBe(1400);
    });

    it('fallback: a drawn ticket only on the player (no world copy) is settled on join, once', () => {
      const first = setup();
      const kim = new FakePlayer('-15', 'Kim');
      kim.setDynamicProperty(BAL, 1000);
      online.push(kim);
      const r = first.wagers.place(kim as never, { game: 'slots', stake: { kind: 'chips', amount: 100 }, skipLimits: true });
      if (!r.ok) throw new Error('place failed');
      first.wagers.draw(r.ticket, 40);
      for (const k of drawnKeys()) worldProps.delete(k);
      restart();
      const back = rejoin(kim);
      expect(back.getDynamicProperty(BAL)).toBe(940);
      restart();
      expect(rejoin(back).getDynamicProperty(BAL)).toBe(940);
    });

    it('a restart between parking and the join does not settle twice', () => {
      const first = setup();
      const lou = new FakePlayer('-16', 'Lou');
      lou.setDynamicProperty(BAL, 1000);
      online.push(lou);
      const r = first.wagers.place(lou as never, { game: 'roulette', stake: { kind: 'chips', amount: 100 }, skipLimits: true });
      if (!r.ok) throw new Error('place failed');
      first.wagers.draw(r.ticket, 200);
      restart(); // parks the result
      restart(); // nobody joined in between
      const back = rejoin(lou);
      expect(back.getDynamicProperty(BAL)).toBe(1100);
    });
  });

  it('refund while offline, and a stale Player object of a player who is back online', () => {
    const { wagers } = setup();
    const dee = new FakePlayer('-4', 'Dee');
    dee.setDynamicProperty(BAL, 100);
    online.push(dee);
    const r1 = wagers.place(dee as never, { game: 'blackjack', stake: { kind: 'chips', amount: 40 }, limits: {} });
    const r2 = wagers.place(dee as never, { game: 'blackjack', stake: { kind: 'chips', amount: 10 }, limits: {} });
    if (!r1.ok || !r2.ok) throw new Error('place failed');
    disconnect(dee);
    wagers.refund(r1.ticket, dee as never);
    const back = rejoin(dee);
    expect(back.getDynamicProperty(BAL)).toBe(90);
    // the table still holds the old (invalid) Player object: core resolves the online one
    wagers.settle(r2.ticket, dee as never, 20);
    expect(back.getDynamicProperty(BAL)).toBe(110);
  });

  it('vetoes, house resolver and prepaid rounds', () => {
    const { wagers } = setup();
    const eve = new FakePlayer('-5', 'Eve');
    eve.setDynamicProperty(BAL, 1000);
    online.push(eve);
    wagers.addVeto((_p, i) => (i.tableKey === 'frozen' ? { translate: 'x' } : undefined));
    const houses: string[] = [];
    wagers.setHouseResolver((_p, game, key) => {
      houses.push(`${game}@${key}`);
      return undefined;
    });
    expect(wagers.place(eve as never, { game: 'wheel', stake: { kind: 'chips', amount: 10 }, tableKey: 'frozen', notify: false }).ok).toBe(false);
    expect(wagers.place(eve as never, { game: 'wheel', stake: { kind: 'chips', amount: 10 }, tableKey: 't1', notify: false }).ok).toBe(true);
    expect(houses).toEqual(['wheel@t1']);
    const ev = wagers.record(eve as never, { game: 'scratch', staked: 25, totalReturn: 100 });
    expect(ev.net).toBe(75);
    expect(eve.getDynamicProperty(BAL)).toBe(1000 - 10 + 100);
    expect(ev.theoreticalLoss).toBeCloseTo(25 * 0.15, 6);
  });

  describe('pawn stakes are house-only (GAME_DESIGN §4.3 CHANGED, review m4)', () => {
    const OWNED = 'gui.burmaldaholic.error.pawn_owned_table';
    const bankroll = { kind: 'bankroll' as const, id: 'c1' };

    it('refuses item/XP/hearts/soul stakes at an owned table, accepts chips there and pawns at house tables', () => {
      const { economy, wagers } = setup();
      economy.transact([{ account: { bankroll: 'c1' }, delta: 100_000 }, { account: 'bank', delta: -100_000 }], 'test.fund');
      wagers.setHouseResolver((_p, _g, key) => (key === 'owned' ? bankroll : undefined));
      const kim = new FakePlayer('-20', 'Kim');
      kim.setDynamicProperty(BAL, 1000);
      kim.xp = xpAt(10);
      online.push(kim);
      for (const stake of [{ kind: 'xp', levels: 5 }, { kind: 'item' }, { kind: 'hearts', hearts: 1 }, { kind: 'soul' }] as const) {
        const r = wagers.place(kim as never, { game: 'coin_flip', stake, pawnAllowed: true, soulAllowed: true, tableKey: 'owned', notify: false });
        expect(r).toEqual({ ok: false, error: { translate: OWNED } });
        expect(wagers.check(kim as never, 'coin_flip', 'owned', stake)).toEqual({ translate: OWNED });
      }
      expect(kim.xp).toBe(xpAt(10)); // nothing taken
      // an explicit bankroll house is refused the same way
      expect(wagers.place(kim as never, { game: 'wheel', stake: { kind: 'xp', levels: 5 }, pawnAllowed: true, house: bankroll, tableKey: 'x', notify: false })).toEqual({ ok: false, error: { translate: OWNED } });
      // chips at the owned table, and without a stake the check passes
      expect(wagers.check(kim as never, 'coin_flip', 'owned')).toBeUndefined();
      expect(wagers.place(kim as never, { game: 'coin_flip', stake: { kind: 'chips', amount: 10 }, limits: {}, tableKey: 'owned', notify: false }).ok).toBe(true);
      // a house-banked table still takes pawns
      const r = wagers.place(kim as never, { game: 'coin_flip', stake: { kind: 'xp', levels: 5 }, pawnAllowed: true, tableKey: 'house', notify: false });
      expect(r.ok).toBe(true);
      expect(kim.level).toBe(5);
    });

    it('module vetoes keep their own message (owner cannot play) before the pawn rule', () => {
      const { wagers } = setup();
      wagers.setHouseResolver(() => bankroll);
      wagers.addVeto(() => ({ translate: 'gui.burmaldaholic.error.owner_cannot_play' }));
      const lu = new FakePlayer('-21', 'Lu');
      online.push(lu);
      expect(wagers.check(lu as never, 'coin_flip', 'owned', { kind: 'xp', levels: 1 })).toEqual({ translate: 'gui.burmaldaholic.error.owner_cannot_play' });
    });
  });

  it('an XP stake takes whole levels only and keeps the progress (GAME_DESIGN §4.3.2 CHANGED, review m3)', () => {
    const { wagers } = setup();
    const max = new FakePlayer('-22', 'Max');
    online.push(max);
    // level 10 and half-way to 11 (xpNext(10) = 27 -> 13 points in)
    max.xp = xpAt(10) + 13;
    const r = wagers.place(max as never, { game: 'coin_flip', stake: { kind: 'xp', levels: 4 }, pawnAllowed: true, notify: false });
    if (!r.ok) throw new Error('place failed');
    expect(r.ticket.value).toBe(Math.floor((xpAt(10) - xpAt(6)) / 4)); // V counts whole levels only
    expect(max.level).toBe(6);
    expect(max.xp - xpAt(6)).toBe(Math.floor((13 / 27) * xpNext(6))); // progress kept
    wagers.settle(r.ticket, max as never, 0); // lost: the levels are gone, the progress stays
    expect(max.level).toBe(6);
    expect(max.xp).toBeGreaterThan(xpAt(6));
    // a win/push gives back exactly what was taken
    max.xp = xpAt(10) + 13;
    const w = wagers.place(max as never, { game: 'coin_flip', stake: { kind: 'xp', levels: 4 }, pawnAllowed: true, notify: false });
    if (!w.ok) throw new Error('place failed');
    wagers.settle(w.ticket, max as never, w.ticket.value);
    expect(max.xp).toBe(xpAt(10) + 13);
  });

  describe('casino mode off mid-round (GAME_DESIGN §4.1 CHANGED): closeOut', () => {
    it('settles a drawn round at its persisted draw and refunds an undrawn one, once', () => {
      const { wagers } = setup();
      const settled: unknown[] = [];
      wagers.onSettled((e) => settled.push(e));
      const nia = new FakePlayer('-23', 'Nia');
      nia.setDynamicProperty(BAL, 1000);
      online.push(nia);
      const drawn = wagers.place(nia as never, { game: 'roulette', stake: { kind: 'chips', amount: 100 }, skipLimits: true });
      const undrawn = wagers.place(nia as never, { game: 'craps', stake: { kind: 'chips', amount: 50 }, skipLimits: true });
      if (!drawn.ok || !undrawn.ok) throw new Error('place failed');
      wagers.draw(drawn.ticket, 0); // the ball already landed on a losing number
      expect(nia.getDynamicProperty(BAL)).toBe(850);
      expect(wagers.closeOut(drawn.ticket, nia as never)).toBe('settled');
      expect(wagers.closeOut(undrawn.ticket, nia as never)).toBe('refunded');
      expect(nia.getDynamicProperty(BAL)).toBe(900); // the drawn loss stands, the 50 came back
      expect(settled).toEqual([expect.objectContaining({ game: 'roulette', staked: 100, totalReturn: 0 })]);
      expect(wagers.closeOut(drawn.ticket, nia as never)).toBeUndefined();
      expect(wagers.closeOut(undrawn.ticket, nia as never)).toBeUndefined();
      expect(nia.getDynamicProperty(BAL)).toBe(900);
    });

    it('pays a drawn win to a disconnected player on their next join', () => {
      const { wagers } = setup();
      const oz = new FakePlayer('-24', 'Oz');
      oz.setDynamicProperty(BAL, 500);
      online.push(oz);
      const r = wagers.place(oz as never, { game: 'slots', stake: { kind: 'chips', amount: 100 }, skipLimits: true });
      if (!r.ok) throw new Error('place failed');
      wagers.draw(r.ticket, 300);
      disconnect(oz);
      expect(wagers.closeOut(r.ticket, oz as never)).toBe('settled');
      expect(rejoin(oz).getDynamicProperty(BAL)).toBe(700);
    });
  });
});
