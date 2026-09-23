/**
 * Inventory helpers: chip items <-> value, giving items with overflow dropped at the
 * player's feet (GAME_DESIGN §3.2), counting / removing item types.
 */
import { EntityComponentTypes, type EntityInventoryComponent, ItemStack, type Player } from '@minecraft/server';
import { type ChipValue, breakdown, chipItemId, chipValueOf } from './logic/economy-math';

function container(player: Player) {
  return (player.getComponent(EntityComponentTypes.Inventory) as EntityInventoryComponent | undefined)?.container;
}

/**
 * Give `count` items of `typeId`; whatever does not fit is dropped at the player's feet.
 * Returns true if something had to be dropped (caller may show error.inventory_full).
 */
export function giveItems(player: Player, typeId: string, count: number): boolean {
  const inv = container(player);
  let dropped = false;
  let rest = Math.floor(count);
  while (rest > 0) {
    const stack = new ItemStack(typeId, 1);
    const n = Math.min(rest, stack.maxAmount);
    stack.amount = n;
    rest -= n;
    const left = inv?.addItem(stack);
    if (left) {
      player.dimension.spawnItem(left, player.location);
      dropped = true;
    }
  }
  return dropped;
}

/** Give an exact item stack back (pawn returns); overflow dropped. */
export function giveStack(player: Player, stack: ItemStack): boolean {
  const left = container(player)?.addItem(stack) ?? stack;
  if (left) {
    player.dimension.spawnItem(left, player.location);
    return true;
  }
  return false;
}

/** Withdraw `amount` chips as items (greedy, optional preferred denomination). */
export function giveChips(player: Player, amount: number, only?: ChipValue): boolean {
  let dropped = false;
  for (const { value, count } of breakdown(amount, only)) dropped = giveItems(player, chipItemId(value), count) || dropped;
  return dropped;
}

/** Total chip value carried in the inventory. */
export function chipValueInInventory(player: Player): number {
  const inv = container(player);
  if (!inv) return 0;
  let sum = 0;
  for (let i = 0; i < inv.size; i++) {
    const it = inv.getItem(i);
    if (it) sum += chipValueOf(it.typeId) * it.amount;
  }
  return sum;
}

/** Remove every chip item; returns their total value. */
export function takeAllChips(player: Player): number {
  const inv = container(player);
  if (!inv) return 0;
  let sum = 0;
  for (let i = 0; i < inv.size; i++) {
    const it = inv.getItem(i);
    const v = it ? chipValueOf(it.typeId) : 0;
    if (it && v > 0) {
      sum += v * it.amount;
      inv.setItem(i, undefined);
    }
  }
  return sum;
}

/** Remove the held chip stack; returns its value (0 if the selected item is not a chip). */
export function takeHeldChips(player: Player): number {
  const inv = container(player);
  const slot = player.selectedSlotIndex;
  const it = inv?.getItem(slot);
  const v = it ? chipValueOf(it.typeId) : 0;
  if (!inv || !it || v === 0) return 0;
  inv.setItem(slot, undefined);
  return v * it.amount;
}

/** Count items of a type in the inventory. */
export function countItems(player: Player, typeId: string): number {
  const inv = container(player);
  if (!inv) return 0;
  let n = 0;
  for (let i = 0; i < inv.size; i++) {
    const it = inv.getItem(i);
    if (it?.typeId === typeId) n += it.amount;
  }
  return n;
}

/** Remove up to `count` items of a type; returns how many were removed. */
export function removeItems(player: Player, typeId: string, count: number): number {
  const inv = container(player);
  if (!inv) return 0;
  let left = count;
  for (let i = 0; i < inv.size && left > 0; i++) {
    const it = inv.getItem(i);
    if (it?.typeId !== typeId) continue;
    const n = Math.min(left, it.amount);
    left -= n;
    if (n === it.amount) inv.setItem(i, undefined);
    else {
      it.amount -= n;
      inv.setItem(i, it);
    }
  }
  return count - left;
}

/** The held item stack (a copy) and its slot. */
export function heldItem(player: Player): { stack: ItemStack | undefined; slot: number } {
  const slot = player.selectedSlotIndex;
  return { stack: container(player)?.getItem(slot), slot };
}

/** The stack in an inventory slot (0–8 hotbar, 9–35 main inventory). */
export function itemAt(player: Player, slot: number): { stack: ItemStack | undefined; slot: number } {
  const c = container(player);
  if (!c || !Number.isInteger(slot) || slot < 0 || slot >= c.size) return { stack: undefined, slot };
  return { stack: c.getItem(slot), slot };
}

/** Clear a slot (after taking a pawn stake). */
export function clearSlot(player: Player, slot: number): void {
  container(player)?.setItem(slot, undefined);
}
