/**
 * In-game admin settings: `/burmaldaholic:casino` (operators) opens a form to toggle casino mode and
 * edit every module's declared config. Values persist as world dynamic properties.
 */
import { type Player } from '@minecraft/server';
import { ActionFormData, ModalFormData } from '@minecraft/server-ui';
import { isCasinoEnabled, setCasinoEnabled } from './casino';
import { ConfigStore, configRegistry } from './config';
import type { ConfigDef } from './logic/config-schema';
import { t } from './logic/rawtext';

function modulesWithConfig(): string[] {
  const s = new Set<string>();
  for (const { module } of configRegistry.values()) s.add(module);
  return [...s];
}

export async function openAdminMenu(player: Player): Promise<void> {
  const modules = modulesWithConfig();
  const form = new ActionFormData()
    .title(t('msg.burmaldaholic.core.admin.title'))
    .body(t(isCasinoEnabled() ? 'msg.burmaldaholic.core.admin.status_on' : 'msg.burmaldaholic.core.admin.status_off'))
    .button(t(isCasinoEnabled() ? 'msg.burmaldaholic.core.admin.disable' : 'msg.burmaldaholic.core.admin.enable'));
  for (const m of modules) form.button(t(`msg.burmaldaholic.${m}.name`));
  const res = await form.show(player);
  if (res.canceled || res.selection === undefined) return;
  if (res.selection === 0) {
    setCasinoEnabled(!isCasinoEnabled());
    player.sendMessage(t(isCasinoEnabled() ? 'msg.burmaldaholic.core.admin.status_on' : 'msg.burmaldaholic.core.admin.status_off'));
    return;
  }
  const module = modules[res.selection - 1];
  if (module) await openModuleConfig(player, module);
}

async function openModuleConfig(player: Player, module: string): Promise<void> {
  const store = new ConfigStore(module);
  const defs: ConfigDef[] = [];
  for (const { module: m, def } of configRegistry.values()) if (m === module) defs.push(def);

  // Only interactive controls (no label/header) so formValues indices match defs.
  const form = new ModalFormData().title(t(`msg.burmaldaholic.${module}.name`));
  for (const d of defs) {
    const label = t(`msg.burmaldaholic.${module}.config.${d.name}`);
    if (d.type === 'bool') form.toggle(label, { defaultValue: store.bool(d.name) });
    else if (d.type === 'int') form.slider(label, d.min, d.max, { defaultValue: store.int(d.name), valueStep: d.step ?? 1 });
    else
      form.dropdown(
        label,
        d.options.map((o) => t(`msg.burmaldaholic.${module}.config.${d.name}.${o}`)),
        { defaultValueIndex: Math.max(0, d.options.indexOf(store.str(d.name))) },
      );
  }
  form.submitButton(t('msg.burmaldaholic.core.admin.save'));
  const res = await form.show(player);
  if (res.canceled || !res.formValues) return;
  defs.forEach((d, i) => {
    const v = res.formValues?.[i];
    if (v === undefined) return;
    store.set(d.name, d.type === 'enum' ? (d.options[Number(v)] ?? d.default) : v);
  });
  player.sendMessage(t('msg.burmaldaholic.core.admin.saved'));
}
