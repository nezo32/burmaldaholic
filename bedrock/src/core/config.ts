import { world } from '@minecraft/server';
import { type ConfigDef, type ConfigValue, configKey, configPropertyId, sanitize, validateDefs } from './logic/config-schema';

/** Global registry of all modules' config definitions (filled by the bootstrapper). */
export const configRegistry = new Map<string, { module: string; def: ConfigDef }>();

export function registerConfig(module: string, defs: readonly ConfigDef[]): void {
  validateDefs(module, defs);
  for (const def of defs) configRegistry.set(configKey(module, def.name), { module, def });
}

/** Per-module view over world dynamic properties. */
export class ConfigStore {
  constructor(private readonly module: string) {}

  private entry(name: string) {
    const e = configRegistry.get(configKey(this.module, name));
    if (!e) throw new Error(`config ${this.module}.${name} is not declared in CasinoModule.config`);
    return e;
  }

  get(name: string): ConfigValue {
    const { def } = this.entry(name);
    return sanitize(def, world.getDynamicProperty(configPropertyId(configKey(this.module, name))));
  }

  bool(name: string): boolean {
    return this.get(name) === true;
  }

  int(name: string): number {
    const v = this.get(name);
    return typeof v === 'number' ? v : 0;
  }

  str(name: string): string {
    return String(this.get(name));
  }

  set(name: string, value: ConfigValue): void {
    const { def } = this.entry(name);
    world.setDynamicProperty(configPropertyId(configKey(this.module, name)), sanitize(def, value));
  }
}
