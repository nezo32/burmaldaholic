/**
 * First-op Setup form (GAME_DESIGN §2.1, Bedrock): the first operator to join a world with
 * the add-on gets a ModalForm (casino mode, Last Chance in Hardcore, chaos events). Until it is
 * answered the mode is ON with defaults; a dismissed form re-appears on that op's next join,
 * max 3 times. The answer writes `burmaldaholic:casino_mode` and the config overrides.
 */
import { type Player, world } from '@minecraft/server';
import { ModalFormData } from '@minecraft/server-ui';
import { isCasinoEnabled, setCasinoEnabled } from './casino';
import type { ConfigService } from './config';
import { ModalLayout, showForm } from './forms';
import { SETUP_PROP, dismissSetup, parseSetup, shouldShowSetup } from './logic/mode';
import { t } from './logic/rawtext';
import { isOperator } from './menu';
import { worldJson } from './store';

export async function maybeShowSetup(player: Player, config: ConfigService): Promise<void> {
  const state = parseSetup(world.getDynamicProperty(SETUP_PROP));
  if (!shouldShowSetup(state, player.id, isOperator(player))) return;
  const layout = new ModalLayout();
  const lc = config.def('lastChance.hardcoreMode');
  const form = new ModalFormData().title(t('gui.burmaldaholic.core.setup.title')).label(t('gui.burmaldaholic.core.setup.intro'));
  layout.passive();
  form.toggle(t('gui.burmaldaholic.core.setup.casino_mode'), { defaultValue: isCasinoEnabled() });
  const iMode = layout.control();
  const opts = lc?.options ?? ['DISABLED', 'HIGH_STAKES'];
  form.dropdown(
    t('gui.burmaldaholic.core.setup.hardcore_last_chance'),
    opts.map((_, i) => t(lc?.optionLabels?.[i] ?? 'gui.burmaldaholic.common.off')),
    { defaultValueIndex: Math.max(0, opts.indexOf(config.str('lastChance.hardcoreMode'))), tooltip: lc?.tooltip ? t(lc.tooltip) : undefined },
  );
  const iLc = layout.control();
  form.toggle(t('gui.burmaldaholic.core.setup.chaos'), { defaultValue: config.bool('chaos.enabled') });
  const iChaos = layout.control();
  form.submitButton(t('gui.burmaldaholic.core.setup.submit'));
  const res = await showForm(player, form);
  if (!res || res.canceled) {
    worldJson.write(SETUP_PROP, dismissSetup(state, player.id));
    return;
  }
  const on = layout.value(res, iMode) !== false;
  setCasinoEnabled(on);
  config.set('lastChance.hardcoreMode', opts[Number(layout.value(res, iLc) ?? 0)] ?? 'DISABLED');
  config.set('chaos.enabled', layout.value(res, iChaos) !== false);
  worldJson.write(SETUP_PROP, { ...state, done: true });
  world.sendMessage(t(on ? 'msg.burmaldaholic.core.mode_enabled' : 'msg.burmaldaholic.core.mode_disabled'));
}
