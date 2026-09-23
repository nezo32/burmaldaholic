/**
 * Shared runtime helpers for the extras games: the module context holder, the "virtual
 * table" used by item-driven games, stake selection (chips / held item / XP / hearts),
 * actionbar animations and the chaos hook.
 */
import { EntityComponentTypes, type EntityInventoryComponent, type ItemStack, type Player, system } from '@minecraft/server';
import { ActionFormData, ModalFormData } from '@minecraft/server-ui';
import {
  HudPriority,
  type ModuleContext,
  type Raw,
  type Stake,
  type TableLimits,
  type TableSession,
  ModalLayout,
  chips,
  color,
  join,
  lines,
  lit,
  promptAmount,
  showForm,
  t,
} from '../../core';
import { CHAOS_SERVICE, type ChaosApi } from '../../chaos/api';

let current: ModuleContext | undefined;

export function setContext(ctx: ModuleContext): void {
  current = ctx;
}

/** The module context (available after world load). */
export function ctx(): ModuleContext {
  if (!current) throw new Error('extras used before world load');
  return current;
}

export const EXTRAS_PREFIX = 'extras';

/** Master switch + per-game switch (`extras.coinFlip.enabled` ...). */
export function gameEnabled(flag: string): boolean {
  const c = ctx().config;
  return c.bool('enabled') && c.bool(flag);
}

/** Open a table session that is not tied to a block (items: coin, dice, scratch card). */
export function openVirtual(player: Player, game: string): void {
  ctx().tables.open(player, {
    key: `${EXTRAS_PREFIX}:${game}:${player.id}`,
    game,
    dimension: player.dimension,
    location: player.location,
  });
}

/** Wait `ticks` (resolves false if the session ended or the player left meanwhile). */
export function sleep(s: TableSession | Player, ticks: number): Promise<boolean> {
  return new Promise((resolve) =>
    system.runTimeout(() => {
      const alive = 'isActive' in s ? s.isActive() && s.player.isValid : s.isValid;
      resolve(alive);
    }, Math.max(1, Math.floor(ticks))),
  );
}

/** Show animation frames on the game's actionbar channel. Returns false if interrupted. */
export async function animate(s: TableSession, channel: string, frames: readonly { raw: Raw; delay: number }[]): Promise<boolean> {
  for (const f of frames) {
    if (!s.isActive() || !s.player.isValid) return false;
    ctx().hud.actionbar(s.player, `extras.${channel}`, f.raw, HudPriority.game, Math.max(20, f.delay + 10));
    if (!(await sleep(s, f.delay))) return false;
  }
  return true;
}

/** Colored result line: win / loss / push, from the net of a settled round. */
export function resultLine(net: number): Raw {
  if (net > 0) return color('§a', t('gui.burmaldaholic.common.result.win', chips(net)));
  if (net < 0) return color('§c', t('gui.burmaldaholic.common.result.loss', chips(-net)));
  return color('§7', t('gui.burmaldaholic.common.result.push'));
}

/** Balance + limits lines for bet forms. */
export function betInfo(player: Player, range: { min: number; max: number }): Raw[] {
  const c = ctx();
  return [t('gui.burmaldaholic.common.balance', chips(c.economy.balance(player))), t('gui.burmaldaholic.common.limits', chips(range.min), chips(range.max))];
}

/** Trigger a chaos event if the chaos module exposes a trigger (see report: needed API). */
export function triggerChaos(player: Player, event: string): void {
  const chaos = ctx().services.get<ChaosApi & { trigger?: (p: Player, id: string) => unknown }>(CHAOS_SERVICE);
  try {
    chaos?.trigger?.(player, event);
  } catch (e) {
    ctx().log.warn(`chaos trigger ${event} failed: ${String(e)}`);
  }
}

// ---- stake selection (GAME_DESIGN §4.3) ---------------------------------------------------

export interface StakeChoice {
  stake: Stake;
  /** chips amount for "play again" defaults */
  amount?: number;
}

export interface StakeOptions {
  title: Raw;
  limits: TableLimits;
  pawnAllowed: boolean;
  /** error line from the previous attempt */
  error?: Raw;
  /** default chip amount (last bet) */
  last?: number;
  info?: Raw[];
}

function inventory(player: Player) {
  return (player.getComponent(EntityComponentTypes.Inventory) as EntityInventoryComponent | undefined)?.container;
}

const appraisalOf = (stack: ItemStack): number => {
  if (!stack.typeId.startsWith('minecraft:')) return 0;
  const key = `wager.appraisal.${stack.typeId.slice('minecraft:'.length)}`;
  const cfg = ctx().config;
  try {
    return cfg.int(key);
  } catch {
    return 0;
  }
};

/**
 * Ask for a stake. Chips go straight to the amount form when pawn stakes are off; otherwise an
 * ActionForm offers Chips / Held item / XP levels / Hearts (each only when enabled).
 */
export async function chooseStake(player: Player, o: StakeOptions): Promise<StakeChoice | undefined> {
  const c = ctx();
  const range = c.limits.range(player, o.limits);
  const pawn = o.pawnAllowed && c.config.bool('wager.pawnEnabled');
  const kinds: ('chips' | 'item' | 'xp' | 'hearts')[] = ['chips'];
  if (pawn && c.config.bool('wager.items.enabled')) kinds.push('item');
  if (pawn && c.config.bool('wager.xp.enabled')) kinds.push('xp');
  if (pawn && c.config.bool('wager.hearts.enabled')) kinds.push('hearts');

  let kind: (typeof kinds)[number] = 'chips';
  if (kinds.length > 1) {
    const form = new ActionFormData().title(o.title).body(lines(o.error ? color('§c', o.error) : undefined, ...(o.info ?? []), ...betInfo(player, range), t('gui.burmaldaholic.common.stake_type')));
    const labels: Record<(typeof kinds)[number], string> = {
      chips: 'gui.burmaldaholic.common.stake_chips',
      item: 'gui.burmaldaholic.common.stake_item',
      xp: 'gui.burmaldaholic.common.stake_xp',
      hearts: 'gui.burmaldaholic.common.stake_hearts',
    };
    for (const k of kinds) form.button(t(labels[k]));
    const res = await showForm(player, form);
    if (!res || res.canceled || res.selection === undefined) return undefined;
    kind = kinds[res.selection] ?? 'chips';
  }

  switch (kind) {
    case 'chips': {
      const amount = await promptAmount(player, {
        title: o.title,
        // With a stake-type form first, the error/info lines were already shown there.
        info: kinds.length > 1 ? betInfo(player, range) : [...(o.error ? [color('§c', o.error)] : []), ...(o.info ?? []), ...betInfo(player, range)],
        min: range.min,
        max: Math.max(range.min, range.max),
        default: o.last,
        validate: (n) => c.limits.check(player, n, o.limits, c.economy.balance(player)),
      });
      return amount === undefined ? undefined : { stake: { kind: 'chips', amount }, amount };
    }
    case 'item':
      return chooseItemStake(player, o.title);
    case 'xp': {
      const max = Math.min(player.level, c.config.int('wager.xp.maxLevels'));
      if (max < 1) {
        player.sendMessage(t('gui.burmaldaholic.error.xp_not_enough'));
        return undefined;
      }
      const n = await sliderPrompt(player, o.title, t('gui.burmaldaholic.wager.xp_levels'), max);
      return n === undefined ? undefined : { stake: { kind: 'xp', levels: n } };
    }
    case 'hearts': {
      const max = c.config.int('wager.hearts.maxPerBet');
      const n = await sliderPrompt(player, o.title, t('gui.burmaldaholic.wager.hearts'), max, t('gui.burmaldaholic.wager.hearts_warning'));
      return n === undefined ? undefined : { stake: { kind: 'hearts', hearts: n } };
    }
  }
}

async function sliderPrompt(player: Player, title: Raw, label: Raw, max: number, note?: Raw): Promise<number | undefined> {
  const layout = new ModalLayout();
  const form = new ModalFormData().title(title);
  if (note) {
    form.label(color('§e', note));
    layout.passive();
  }
  form.slider(label, 1, Math.max(1, max), { valueStep: 1, defaultValue: 1 });
  const i = layout.control();
  form.submitButton(t('gui.burmaldaholic.common.confirm'));
  const res = await showForm(player, form);
  if (!res || res.canceled) return undefined;
  const v = Number(layout.value(res, i));
  return Number.isInteger(v) && v >= 1 ? v : undefined;
}

/**
 * Item stakes use the HELD stack (core wagers). Item-driven games (Lucky Coin, dice) are held
 * themselves, so the player picks an appraised hotbar stack and we select that slot first.
 */
async function chooseItemStake(player: Player, title: Raw): Promise<StakeChoice | undefined> {
  const inv = inventory(player);
  const slots: { slot: number; stack: ItemStack; value: number }[] = [];
  for (let slot = 0; slot < 9 && inv; slot++) {
    const stack = inv.getItem(slot);
    if (!stack) continue;
    const a = appraisalOf(stack);
    if (a > 0) slots.push({ slot, stack, value: a * stack.amount });
  }
  if (!slots.length) {
    player.sendMessage(t('gui.burmaldaholic.error.pawn_not_accepted'));
    return undefined;
  }
  const form = new ActionFormData().title(title).body(t('gui.burmaldaholic.wager.title'));
  for (const s of slots) form.button(t('gui.burmaldaholic.wager.item_value', itemLabel(s.stack), chips(s.value)));
  const res = await showForm(player, form);
  if (!res || res.canceled || res.selection === undefined) return undefined;
  const pick = slots[res.selection];
  if (!pick) return undefined;
  player.selectedSlotIndex = pick.slot;
  return { stake: { kind: 'item' } };
}

/** "Diamond ×12" style label for a stack. */
function itemLabel(stack: ItemStack): Raw {
  const name: Raw = { translate: stack.localizationKey };
  return stack.amount > 1 ? join(name, lit(' '), t('gui.burmaldaholic.cashier.times', stack.amount)) : name;
}
