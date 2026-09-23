/**
 * Dice Duel (GAME_DESIGN §11.5, UI.md §2 "Challenges", §9).
 *  - Vs house: `dice` item used on air (or ExtrasApi from the craps table side menu). House
 *    banked through ctx.wagers (pawn stakes allowed), always honest (no streak re-draw).
 *  - PvP: `dice` used on a player, or Casino Menu → Challenges. The target gets 30 s to accept;
 *    on accept both stakes move in ONE atomic economy.transact (escrow + payout at once), ties
 *    re-roll up to 3 times, then nothing moves (refund). Reported with wagers.recordPvp.
 */
import { type Player, system, world } from '@minecraft/server';
import { ActionFormData, ModalFormData } from '@minecraft/server-ui';
import {
  type Raw,
  type TableSession,
  HudPriority,
  ModalLayout,
  chips,
  chipsAcc,
  color,
  lines,
  mathRng,
  parseAmount,
  promptAmount,
  showForm,
  sliderStep,
  t,
} from '../../core';
import { resultForm } from './coin-flip';
import { type Challenge, ChallengeBook, type HouseDuel, type Roll, duelHouse, duelPvp, houseReturn, pvpPayout, rollTotal, tieTotals } from './logic';
import { betInfo, chooseStake, ctx, gameEnabled, openVirtual, resultLine, sleep } from './shared';

export const DICE_GAME = 'extras_dice';
const TITLE = 'gui.burmaldaholic.extras.dice.title';
const book = new ChallengeBook();

const yourRoll = (r: Roll): Raw => t('gui.burmaldaholic.extras.dice.your_roll', r[0], r[1], rollTotal(r));
const theirRoll = (name: string | Raw, r: Roll): Raw => t('gui.burmaldaholic.extras.dice.their_roll', name, r[0], r[1], rollTotal(r));

/** How the next dice session should start. */
const requested = new Map<string, 'house' | 'menu'>();

export function openDice(p: Player, mode: 'house' | 'menu'): void {
  if (!gameEnabled('extras.diceDuel.enabled')) return p.sendMessage(t('gui.burmaldaholic.error.disabled'));
  requested.set(p.id, mode);
  openVirtual(p, DICE_GAME);
}

const pvpEnabled = (): boolean => gameEnabled('extras.diceDuel.enabled') && ctx().config.bool('extras.diceDuel.pvpEnabled');

interface DiceData {
  busy?: boolean;
  last?: number;
}

export async function diceFlow(s: TableSession, rejoined: boolean): Promise<void> {
  const data = s.data as DiceData;
  if (rejoined && data.busy) return;
  data.busy = true;
  const p = s.player;
  let mode = requested.get(p.id) ?? 'menu';
  requested.delete(p.id);
  let error: Raw | undefined;
  try {
    for (;;) {
      if (!gameEnabled('extras.diceDuel.enabled')) return void p.sendMessage(t('gui.burmaldaholic.error.disabled'));
      if (mode === 'menu') {
        const pvp = pvpEnabled();
        const incoming = book.incoming(p.id, system.currentTick);
        const form = new ActionFormData()
          .title(t(TITLE))
          .body(lines(error ? color('§c', error) : undefined, t('gui.burmaldaholic.extras.dice.rules'), t('gui.burmaldaholic.common.balance', chips(ctx().economy.balance(p)))))
          .button(t('gui.burmaldaholic.extras.dice.vs_house'));
        if (pvp) form.button(t('gui.burmaldaholic.extras.dice.challenge_player'));
        if (pvp && incoming.length) form.button(t('gui.burmaldaholic.extras.dice.pending'));
        form.button(t('gui.burmaldaholic.common.close'));
        error = undefined;
        const res = await showForm(p, form);
        if (!res || res.canceled || res.selection === undefined || !s.isActive()) return;
        if (res.selection === 1 && pvp) {
          s.leave();
          return void (await challengeForm(p));
        }
        if (res.selection === 2 && pvp && incoming.length) {
          s.leave();
          return void (await challengesPage(p));
        }
        if (res.selection !== 0) return;
      }
      const out = await houseRound(s, data, error);
      error = out.error;
      if (!out.error && !out.again) return;
      mode = 'house'; // again, or re-ask the stake with the error
    }
  } finally {
    data.busy = false;
    if (s.isActive()) s.leave();
  }
}

/** One round vs the house. */
async function houseRound(s: TableSession, data: DiceData, error: Raw | undefined): Promise<{ error?: Raw; again?: boolean }> {
  const c = ctx();
  const p = s.player;
  const choice = await chooseStake(p, { title: t(TITLE), limits: {}, pawnAllowed: true, last: data.last, error, info: [t('gui.burmaldaholic.extras.dice.rules')] });
  if (!choice || !s.isActive()) return {};
  if (choice.amount) data.last = choice.amount;
  const r = c.wagers.place(p, { game: 'dice_duel', stake: choice.stake, limits: {}, pawnAllowed: true, notify: false });
  if (!r.ok) return { error: r.error };
  const ties = tieTotals(c.config.json('extras.diceDuel.houseWinsTieOn'));
  const duel = duelHouse(mathRng, ties); // table game: always honest
  const ev = c.wagers.settle(r.ticket, p, houseReturn(r.ticket.value, duel.outcome));
  const body = lines(yourRoll(duel.player), theirRoll(t('gui.burmaldaholic.extras.dice.dealer'), duel.dealer), outcomeLine(duel, r.ticket.value, ev?.net ?? 0), ev ? resultLine(ev.net) : undefined);
  c.hud.actionbar(p, 'extras.dice', yourRoll(duel.player), HudPriority.game, 30);
  if (!(await sleep(s, 15))) {
    if (p.isValid) p.sendMessage(body);
    return {};
  }
  p.sendMessage(outcomeLine(duel, r.ticket.value, ev?.net ?? 0));
  const again = await resultForm(p, lines(body, t('gui.burmaldaholic.common.balance', chips(c.economy.balance(p)))), t(TITLE));
  return { again };
}

function outcomeLine(d: HouseDuel, stake: number, net: number): Raw {
  switch (d.outcome) {
    case 'win':
      return color('§a', t('gui.burmaldaholic.extras.dice.win', chipsAcc(net)));
    case 'lose':
      return color('§c', t('gui.burmaldaholic.extras.dice.lose', chipsAcc(stake)));
    case 'push':
      return color('§7', t('gui.burmaldaholic.extras.dice.tie'));
    case 'house_tie': {
      const total = rollTotal(d.player);
      return color('§c', total === 7 ? t('gui.burmaldaholic.extras.dice.tie_seven') : t('gui.burmaldaholic.extras.dice.tie_house', total));
    }
  }
}

// ---- PvP ------------------------------------------------------------------------------------

const within = (a: Player, b: Player, r: number): boolean => {
  if (a.dimension.id !== b.dimension.id) return false;
  const dx = a.location.x - b.location.x;
  const dy = a.location.y - b.location.y;
  const dz = a.location.z - b.location.z;
  return dx * dx + dy * dy + dz * dz <= r * r;
};

const byId = (id: string): Player | undefined => world.getAllPlayers().find((x) => x.id === id);

/** Players the challenger may duel right now. */
function nearbyTargets(p: Player): Player[] {
  const r = ctx().config.int('extras.diceDuel.maxDistance');
  return world.getAllPlayers().filter((x) => x.id !== p.id && within(p, x, r));
}

/** Why `a` cannot duel `b` for `stake` (error for `a`), or undefined. */
function pvpBlocker(a: Player, b: Player, stake: number): Raw | undefined {
  const c = ctx();
  if (!pvpEnabled()) return t('gui.burmaldaholic.error.disabled');
  if (a.id === b.id) return t('msg.burmaldaholic.extras.dice.self');
  if (!b.isValid) return t('msg.burmaldaholic.extras.dice.target_unavailable', b.name);
  if (!within(a, b, c.config.int('extras.diceDuel.maxDistance'))) return t('gui.burmaldaholic.extras.dice.no_targets');
  if (c.economy.inDefault(a)) return t('gui.burmaldaholic.error.in_default');
  if (c.economy.inDefault(b)) return t('msg.burmaldaholic.extras.dice.target_unavailable', b.name);
  // Only `a`'s own limits/balance: the other side's are checked for them on accept.
  return c.limits.check(a, stake, {}, c.economy.balance(a));
}

/** Challenge form: dropdown of nearby players (or a fixed target) + amount. */
export async function challengeForm(p: Player, target?: Player, error?: Raw): Promise<void> {
  const c = ctx();
  if (!pvpEnabled()) return p.sendMessage(t('gui.burmaldaholic.error.disabled'));
  if (book.outgoing(p.id, system.currentTick)) return p.sendMessage(t('msg.burmaldaholic.extras.dice.already_pending'));
  const range = c.limits.range(p, {});
  const max = Math.max(range.min, range.max);
  let opponent = target;
  let amount: number | undefined;
  if (target) {
    amount = await promptAmount(p, {
      title: t('gui.burmaldaholic.extras.dice.challenge_player'),
      info: [...(error ? [color('§c', error)] : []), t('gui.burmaldaholic.extras.dice.opponent_line', target.name), ...betInfo(p, range)],
      min: range.min,
      max,
      validate: (n) => c.limits.check(p, n, {}, c.economy.balance(p)),
    });
  } else {
    const targets = nearbyTargets(p);
    if (!targets.length) return p.sendMessage(t('gui.burmaldaholic.extras.dice.no_targets'));
    const layout = new ModalLayout();
    const form = new ModalFormData().title(t('gui.burmaldaholic.extras.dice.challenge_player'));
    if (error) {
      form.label(color('§c', error));
      layout.passive();
    }
    for (const line of betInfo(p, range)) {
      form.label(line);
      layout.passive();
    }
    form.dropdown(t('gui.burmaldaholic.extras.dice.target'), targets.map((x) => x.name));
    const iTarget = layout.control();
    form.slider(t('gui.burmaldaholic.common.bet'), range.min, max, { valueStep: sliderStep(range.min, max), defaultValue: range.min });
    const iSlider = layout.control();
    form.textField(t('gui.burmaldaholic.common.exact_amount'), t('gui.burmaldaholic.common.amount'));
    const iText = layout.control();
    form.submitButton(t('gui.burmaldaholic.extras.dice.challenge_player'));
    const res = await showForm(p, form);
    if (!res || res.canceled) return;
    opponent = targets[Number(layout.value(res, iTarget)) || 0];
    const typed = String(layout.value(res, iText) ?? '').trim();
    amount = typed ? parseAmount(typed) : Number(layout.value(res, iSlider));
    if (amount === undefined || !Number.isSafeInteger(amount)) return challengeForm(p, undefined, t('gui.burmaldaholic.error.invalid_amount'));
  }
  if (!opponent || amount === undefined) return;
  const err = pvpBlocker(p, opponent, amount);
  if (err) return challengeForm(p, target, err);
  sendChallenge(p, opponent, amount);
}

function sendChallenge(from: Player, to: Player, stake: number): void {
  const c = ctx();
  const r = book.create(from.id, to.id, stake, system.currentTick, c.config.int('extras.diceDuel.challengeTimeoutTicks'));
  if (!r.ok) return from.sendMessage(t(r.error === 'self' ? 'msg.burmaldaholic.extras.dice.self' : 'msg.burmaldaholic.extras.dice.already_pending'));
  from.sendMessage(t('msg.burmaldaholic.extras.dice.challenge_sent', to.name, chipsAcc(stake)));
  to.sendMessage(color('§e', t('msg.burmaldaholic.extras.dice.challenge_received', from.name, chipsAcc(stake))));
  void answerForm(to, r.challenge);
}

/** The target's Accept / Decline form. */
async function answerForm(p: Player, ch: Challenge): Promise<void> {
  const from = byId(ch.from);
  const form = new ActionFormData()
    .title(t(TITLE))
    .body(lines(t('gui.burmaldaholic.extras.dice.challenge_body', from?.name ?? '?', chipsAcc(ch.stake)), t('gui.burmaldaholic.extras.dice.rules_pvp')))
    .button(color('§a', t('gui.burmaldaholic.extras.dice.accept')))
    .button(color('§c', t('gui.burmaldaholic.extras.dice.decline')));
  const res = await showForm(p, form);
  if (!res || res.canceled || res.selection === undefined) return; // stays pending until it expires
  if (res.selection === 0) accept(p, ch.id);
  else decline(p, ch.id);
}

function decline(p: Player, id: number): void {
  const ch = book.take(id, system.currentTick);
  if (!ch) return p.sendMessage(t('msg.burmaldaholic.extras.dice.expired'));
  byId(ch.from)?.sendMessage(t('msg.burmaldaholic.extras.dice.declined', p.name));
}

function accept(target: Player, id: number): void {
  const c = ctx();
  if (!c.isCasinoEnabled()) return target.sendMessage(t('gui.burmaldaholic.error.casino_off'));
  const ch = book.take(id, system.currentTick);
  if (!ch) return target.sendMessage(t('msg.burmaldaholic.extras.dice.expired'));
  const from = byId(ch.from);
  if (!from) return target.sendMessage(t('msg.burmaldaholic.extras.dice.expired'));
  const S = ch.stake;
  // Target-side checks (their limits and balance), then the challenger's again.
  const mine = pvpBlocker(target, from, S);
  if (mine) {
    target.sendMessage(mine);
    return from.sendMessage(t('msg.burmaldaholic.extras.dice.cancelled', target.name));
  }
  const theirs = pvpBlocker(from, target, S);
  if (theirs) {
    from.sendMessage(theirs);
    return target.sendMessage(t('msg.burmaldaholic.extras.dice.cancelled', from.name));
  }

  const duel = duelPvp(mathRng, 3); // a = challenger, b = target
  const rolls = duel.rounds.map((r) => lines(theirRoll(from.name, r.a), theirRoll(target.name, r.b)));
  for (const pl of [from, target]) for (const r of rolls) pl.sendMessage(r);
  if (duel.result === 'refund') {
    for (const pl of [from, target]) pl.sendMessage(t('msg.burmaldaholic.extras.dice.pvp_refund'));
    return;
  }
  const winner = duel.result === 'a' ? from : target;
  const loser = winner === from ? target : from;
  const { rake, winnerGets } = pvpPayout(S, c.config.int('extras.diceDuel.pvpRakePercent'));
  // Escrow and payout in one atomic transaction: loser −S, winner +(S − rake), rake → bank.
  const ok = c.economy.transact(
    [
      { account: loser, delta: -S },
      { account: winner, delta: S - rake },
      { account: 'bank', delta: rake },
    ],
    'dice_duel.pvp',
  );
  if (!ok) {
    for (const pl of [from, target]) pl.sendMessage(t('msg.burmaldaholic.extras.dice.cancelled', (pl === from ? target : from).name));
    return;
  }
  const msg = color('§6', t('msg.burmaldaholic.extras.dice.pvp_result', winner.name, loser.name, chipsAcc(winnerGets)));
  for (const pl of [from, target]) pl.sendMessage(msg);
  c.wagers.recordPvp(winner, 'dice_duel', S, S - rake);
  c.wagers.recordPvp(loser, 'dice_duel', S, -S);
}

/** Casino Menu → Challenges: pending incoming challenges + "Challenge a player". */
export async function challengesPage(p: Player): Promise<void> {
  if (!pvpEnabled()) return p.sendMessage(t('gui.burmaldaholic.error.disabled'));
  const incoming = book.incoming(p.id, system.currentTick);
  const form = new ActionFormData().title(t('gui.burmaldaholic.menu.challenges')).body(t('gui.burmaldaholic.extras.dice.pending'));
  for (const ch of incoming) form.button(t('gui.burmaldaholic.extras.dice.pending_entry', byId(ch.from)?.name ?? '?', chips(ch.stake)));
  form.button(t('gui.burmaldaholic.extras.dice.challenge_player'));
  form.button(t('gui.burmaldaholic.common.close'));
  const res = await showForm(p, form);
  if (!res || res.canceled || res.selection === undefined) return;
  const ch = incoming[res.selection];
  if (ch) return answerForm(p, ch);
  if (res.selection === incoming.length) return challengeForm(p);
}

/** Expiry sweep + disconnect cleanup (called from onWorldLoad). */
export function startChallenges(): void {
  system.runInterval(() => {
    for (const ch of book.expire(system.currentTick)) {
      byId(ch.from)?.sendMessage(t('msg.burmaldaholic.extras.dice.expired'));
      byId(ch.to)?.sendMessage(t('msg.burmaldaholic.extras.dice.expired'));
    }
  }, 20);
  world.beforeEvents.playerLeave.subscribe((e) => {
    const id = e.player.id;
    system.run(() => {
      for (const ch of book.dropPlayer(id)) {
        const other = byId(ch.from === id ? ch.to : ch.from);
        other?.sendMessage(t('msg.burmaldaholic.extras.dice.expired'));
      }
    });
  });
}

export function hasIncoming(p: Player): boolean {
  return book.incoming(p.id, system.currentTick).length > 0;
}
