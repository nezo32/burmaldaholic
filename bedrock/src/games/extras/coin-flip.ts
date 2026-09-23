/**
 * Coin Flip (GAME_DESIGN §11.1, §4.4; UI.md §9): Lucky Coin item, vs the house, anywhere.
 * ActionForm Heads / Tails (/ Soul Wager) → stake type → amount → flip → result form.
 * The round is placed, drawn (streak re-draw applies) and settled at once; the flip animation
 * afterwards is cosmetic, so a disconnect can never leave the round open.
 */
import { type Player, world } from '@minecraft/server';
import { ActionFormData, ModalFormData } from '@minecraft/server-ui';
import {
  type Raw,
  type Stake,
  type TableSession,
  HudPriority,
  ModalLayout,
  chips,
  chipsAcc,
  color,
  formatMultiplier,
  join,
  lines,
  lit,
  mathRng,
  showForm,
  t,
} from '../../core';
import { type CoinSide, coinReturn, coinRtp, flipCoin, isSoulConfirmWord, soulReturn } from './logic';
import { chooseStake, ctx, gameEnabled, resultLine, sleep } from './shared';

export const COIN_GAME = 'extras_coin';
const TITLE = 'gui.burmaldaholic.extras.coin.title';

const sideName = (s: CoinSide): Raw => t(`gui.burmaldaholic.extras.coin.${s}`);

interface CoinData {
  last?: number;
  busy?: boolean;
}

export async function coinFlow(s: TableSession, rejoined: boolean): Promise<void> {
  const data = s.data as CoinData;
  if (rejoined && data.busy) return;
  data.busy = true;
  const p = s.player;
  let error: Raw | undefined;
  try {
    for (;;) {
      if (!gameEnabled('extras.coinFlip.enabled')) return void p.sendMessage(t('gui.burmaldaholic.error.disabled'));
      const c = ctx();
      const payout = c.config.num('extras.coinFlip.payout');
      const soul = soulAvailable();
      const form = new ActionFormData()
        .title(t(TITLE))
        .body(
          lines(
            error ? color('§c', error) : undefined,
            t('gui.burmaldaholic.extras.coin.payout', lit(formatMultiplier(1 + payout))),
            t('gui.burmaldaholic.common.balance', chips(c.economy.balance(p))),
          ),
        )
        .button(t('gui.burmaldaholic.extras.coin.heads'))
        .button(t('gui.burmaldaholic.extras.coin.tails'));
      if (soul) form.button(color('§4', t('gui.burmaldaholic.extras.soul.button')));
      error = undefined;
      const res = await showForm(p, form);
      if (!res || res.canceled || res.selection === undefined || !s.isActive()) return;
      if (res.selection === 2) {
        if (!(await soulFlow(s))) return;
        continue;
      }
      const side: CoinSide = res.selection === 0 ? 'heads' : 'tails';
      const choice = await chooseStake(p, { title: t(TITLE), limits: {}, pawnAllowed: true, last: data.last });
      if (!choice || !s.isActive()) return;
      if (choice.amount) data.last = choice.amount;
      const outcome = await play(s, side, choice.stake, payout);
      if (outcome.error) {
        error = outcome.error;
        continue;
      }
      if (!outcome.again) return;
    }
  } finally {
    data.busy = false;
    if (s.isActive()) s.leave();
  }
}

/** Place → draw → settle → animate → result. */
async function play(s: TableSession, side: CoinSide, stake: Stake, payout: number): Promise<{ error?: Raw; again?: boolean }> {
  const c = ctx();
  const p = s.player;
  const worst = stake.kind === 'chips' ? coinReturn(stake.amount, true, payout) : undefined;
  const r = c.wagers.place(p, { game: 'coin_flip', stake, limits: {}, pawnAllowed: true, worstCase: worst, notify: false });
  if (!r.ok) return { error: r.error };
  const pWin = c.odds.probability(p.id, 'coin_flip', 0.5);
  const { result } = c.odds.draw(p.id, coinRtp(payout, 0.5), mathRng, () => flipCoin(mathRng, side, pWin), (x) => !x.win);
  const ev = c.wagers.settle(r.ticket, p, coinReturn(r.ticket.value, result.win, payout));
  const text = result.win
    ? color('§a', t('gui.burmaldaholic.extras.coin.result_win', sideName(result.landed), chipsAcc(Math.max(0, ev?.net ?? 0))))
    : color('§c', t('gui.burmaldaholic.extras.coin.result_lose', sideName(result.landed)));
  c.hud.actionbar(p, 'extras.coin', t('gui.burmaldaholic.extras.coin.flipping'), HudPriority.game, 30);
  if (!(await sleep(s, 20))) {
    if (p.isValid) p.sendMessage(text);
    return {};
  }
  p.sendMessage(text);
  return { again: await resultForm(p, lines(text, ev ? resultLine(ev.net) : undefined, t('gui.burmaldaholic.common.balance', chips(c.economy.balance(p))))) };
}

/** ActionForm result with [Play again] [Close]; true = again. */
export async function resultForm(p: Player, body: Raw, title: Raw = t(TITLE)): Promise<boolean> {
  const form = new ActionFormData().title(title).body(body).button(t('gui.burmaldaholic.common.play_again')).button(t('gui.burmaldaholic.common.close'));
  const res = await showForm(p, form);
  return !!res && !res.canceled && res.selection === 0;
}

// ---- Soul Wager (§4.4, UI.md §9) ------------------------------------------------------------

function soulAvailable(): boolean {
  return world.isHardcore && ctx().config.bool('wager.hardcoreSoulWager');
}

/** Returns false when the player closed the flow. */
async function soulFlow(s: TableSession): Promise<boolean> {
  const c = ctx();
  const p = s.player;
  const value = Math.max(c.config.int('wager.soul.minValue'), c.economy.balance(p));
  const title = t('gui.burmaldaholic.extras.soul.warning_title');
  const cancelled = (): false => {
    if (p.isValid) p.sendMessage(t('gui.burmaldaholic.extras.soul.cancelled'));
    return false;
  };

  // 1st confirmation: warning.
  const warn = new ActionFormData()
    .title(title)
    .body(color('§c', t('gui.burmaldaholic.extras.soul.warning', chipsAcc(value))))
    .button(color('§4', t('gui.burmaldaholic.extras.soul.button')))
    .button(t('gui.burmaldaholic.common.cancel'));
  const w = await showForm(p, warn);
  if (!w || w.canceled || w.selection !== 0 || !s.isActive()) return cancelled();

  // 2nd confirmation: type-to-confirm (Bedrock substitute for "hold 5 s").
  const layout = new ModalLayout();
  const m = new ModalFormData().title(title);
  m.label(t('gui.burmaldaholic.extras.soul.hold'));
  layout.passive();
  m.textField(t('gui.burmaldaholic.extras.soul.confirm_prompt', t('gui.burmaldaholic.extras.soul_confirm_word')), t('gui.burmaldaholic.extras.soul_confirm_word'));
  const iWord = layout.control();
  m.submitButton(color('§4', t('gui.burmaldaholic.extras.soul.button')));
  const res = await showForm(p, m);
  if (!res || res.canceled || !s.isActive() || !isSoulConfirmWord(String(layout.value(res, iWord) ?? ''))) return cancelled();

  // Side.
  const sideForm = new ActionFormData().title(title).body(t('gui.burmaldaholic.extras.soul.hold')).button(t('gui.burmaldaholic.extras.coin.heads')).button(t('gui.burmaldaholic.extras.coin.tails'));
  const sr = await showForm(p, sideForm);
  if (!sr || sr.canceled || sr.selection === undefined || !s.isActive()) return cancelled();
  const side: CoinSide = sr.selection === 0 ? 'heads' : 'tails';

  // The 5-second hold: the signature sinks in before the coin is thrown.
  for (let sec = 5; sec > 0; sec--) {
    c.hud.actionbar(p, 'extras.coin', color('§4', join(t('gui.burmaldaholic.extras.soul.hold'), lit(' ('), sec, lit(')'))), HudPriority.alert, 25);
    if (!(await sleep(s, 20))) return cancelled();
  }

  const r = c.wagers.place(p, { game: 'coin_flip', stake: { kind: 'soul' }, soulAllowed: true, notify: false });
  if (!r.ok) {
    p.sendMessage(r.error);
    return true;
  }
  // Soul Wager: honest 50/50 plus the usual modifiers; no streak re-draw on your life.
  const result = flipCoin(mathRng, side, c.odds.probability(p.id, 'coin_flip', 0.5));
  const landed = sideName(result.landed);
  if (result.win) {
    c.wagers.settle(r.ticket, p, soulReturn(r.ticket.value, true));
    p.sendMessage(color('§6', t('gui.burmaldaholic.extras.coin.result_win', landed, chipsAcc(r.ticket.value))));
    p.sendMessage(t('msg.burmaldaholic.extras.soul.won', chipsAcc(r.ticket.value)));
    world.sendMessage(t('msg.burmaldaholic.extras.soul.broadcast_won', p.name));
    return true;
  }
  p.sendMessage(color('§4', t('gui.burmaldaholic.extras.coin.result_lose', landed)));
  c.wagers.settle(r.ticket, p, 0); // core kills the player (Soul Wager death)
  return false;
}
