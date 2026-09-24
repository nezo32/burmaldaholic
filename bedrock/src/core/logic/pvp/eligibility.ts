/** PvP eligibility (PVP.md §3.2 + BOTS.md §5.5 debtor exception). PURE; same order as Java Eligibility. */

export interface EligibilityFacts {
  casinoOn: boolean;
  /** pvp.enabled && pvp.<mode>.enabled */
  modeEnabled: boolean;
  spectator: boolean;
  owesDebt: boolean;
  frozen: boolean;
  /** every other participant is a house-funded bot */
  onlyHouseBots: boolean;
  debtorsMayPlay: boolean;
  busy: boolean;
  sameDimension: boolean;
  distance: number;
  joinRadius: number;
  stake: number;
  minStake: number;
  tierMax: number;
  balance: number;
  self: boolean;
  vipOk: boolean;
  machineOpen: boolean;
  owner: boolean;
  acceptsInvites: boolean;
  declineCooldown: boolean;
  pendingFull: boolean;
  /** house bots refuse this player today (only when bots take part) */
  botsCapped: boolean;
}

/** First failing rule's error key, or undefined. */
export function checkEligibility(f: EligibilityFacts): string | undefined {
  if (!f.casinoOn) return 'gui.burmaldaholic.error.casino_off';
  if (!f.modeEnabled) return 'gui.burmaldaholic.error.disabled';
  if (f.spectator) return 'gui.burmaldaholic.pvp.error.spectator';
  if (f.frozen || (f.owesDebt && !(f.onlyHouseBots && f.debtorsMayPlay))) return 'gui.burmaldaholic.pvp.error.debt';
  if (f.busy) return 'gui.burmaldaholic.error.busy';
  if (!f.sameDimension) return 'gui.burmaldaholic.pvp.error.other_dimension';
  if (f.distance > f.joinRadius) return 'gui.burmaldaholic.pvp.error.too_far';
  if (f.stake > 0 && f.stake < f.minStake) return 'gui.burmaldaholic.pvp.error.stake_min';
  if (f.stake > f.tierMax) return 'gui.burmaldaholic.error.bet_too_high';
  if (f.stake > f.balance) return 'gui.burmaldaholic.error.insufficient_funds';
  if (f.self) return 'gui.burmaldaholic.pvp.error.self';
  if (!f.vipOk) return 'gui.burmaldaholic.error.vip_required';
  if (!f.machineOpen) return 'gui.burmaldaholic.error.table_closed';
  if (f.owner) return 'gui.burmaldaholic.pvp.error.owner';
  if (!f.acceptsInvites) return 'gui.burmaldaholic.pvp.error.no_invites';
  if (f.declineCooldown) return 'gui.burmaldaholic.pvp.error.cooldown_target';
  if (f.pendingFull) return 'gui.burmaldaholic.pvp.error.pending';
  if (f.botsCapped) return 'gui.burmaldaholic.bots.error.capped';
  return undefined;
}
