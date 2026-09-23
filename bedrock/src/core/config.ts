/**
 * Config service: every CONFIG.md key (logic/config-catalog.ts) plus keys modules declare in
 * `CasinoModule.config`. Effective values = defaults overridden by the world dynamic property
 * `burmaldaholic:config` (flat JSON of overrides only, CONFIG.md "Storage").
 */
import { world } from '@minecraft/server';
import { CONFIG_CATALOG } from './logic/config-catalog';
import {
  CONFIG_PROPERTY,
  type ConfigDef,
  ConfigOverrides,
  type ConfigValue,
  type JsonValue,
  type ModuleConfigDef,
  type Sanitized,
  configPrefix,
  fromModuleDef,
  validateDefs,
} from './logic/config-schema';
import { createLogger } from './log';

const log = createLogger('core.config');

/** All definitions by key (catalog + module extras), in catalog order. */
export const configDefs = new Map<string, ConfigDef>(CONFIG_CATALOG.map((d) => [d.key, d]));

/** Bootstrapper: add a module's extra keys (catalog keys are skipped). */
export function registerModuleConfig(module: string, defs: readonly ModuleConfigDef[]): void {
  const extra: ConfigDef[] = [];
  for (const d of defs) {
    const def = fromModuleDef(module, d);
    if (configDefs.has(def.key)) continue; // already in CONFIG.md
    extra.push(def);
  }
  validateDefs([...extra]);
  for (const d of extra) {
    configDefs.set(d.key, d);
    log.warn(`${d.key} is not in docs/design/CONFIG.md; add it there (label config.burmaldaholic.${d.key})`);
  }
}

export type ConfigListener = (key: string, value: ConfigValue) => void;

/** World-wide config store (one instance, shared). */
export class ConfigService {
  private cache: ConfigOverrides | undefined;
  private readonly listeners: ConfigListener[] = [];

  private overrides(): ConfigOverrides {
    if (!this.cache) {
      const raw = world.getDynamicProperty(CONFIG_PROPERTY);
      this.cache = ConfigOverrides.parse(configDefs, raw);
      if (typeof raw === 'string') {
        try {
          for (const k of Object.keys(JSON.parse(raw) as object)) if (!configDefs.has(k)) log.warn(`ignoring unknown config key ${k}`);
        } catch {
          log.warn('corrupt burmaldaholic:config, using defaults');
        }
      }
    }
    return this.cache;
  }

  def(key: string): ConfigDef | undefined {
    return configDefs.get(key);
  }

  get(key: string): ConfigValue {
    return this.overrides().get(key);
  }

  bool(key: string): boolean {
    return this.get(key) === true;
  }

  /** int / long / double values. */
  num(key: string): number {
    const v = this.get(key);
    return typeof v === 'number' ? v : 0;
  }

  int(key: string): number {
    return Math.trunc(this.num(key));
  }

  str(key: string): string {
    return String(this.get(key));
  }

  json<T extends JsonValue = JsonValue>(key: string): T {
    return this.get(key) as T;
  }

  isOverridden(key: string): boolean {
    return this.overrides().has(key);
  }

  /** Set (clamped/validated) and persist. Returns the sanitize report. */
  set(key: string, value: unknown): Sanitized {
    const o = this.overrides();
    const r = o.set(key, value);
    if (!r.invalid) this.persist(key);
    return r;
  }

  reset(key: string): void {
    this.overrides().reset(key);
    this.persist(key);
  }

  onChange(l: ConfigListener): void {
    this.listeners.push(l);
  }

  private persist(key: string): void {
    world.setDynamicProperty(CONFIG_PROPERTY, this.overrides().serialize());
    const v = this.get(key);
    for (const l of this.listeners) {
      try {
        l(key, v);
      } catch (e) {
        log.error('config listener failed', e);
      }
    }
  }
}

/**
 * A module's view of the config: full CONFIG.md keys (`ctx.config.int('blackjack.decks')`),
 * or names relative to the module prefix (`ctx.config.bool('enabled')` in blackjack reads
 * `blackjack.enabled`; lastchance uses the `lastChance.` prefix).
 */
export class ConfigStore {
  constructor(
    private readonly module: string,
    private readonly svc: ConfigService,
  ) {}

  /** Resolve a relative name to the full key. */
  key(name: string): string {
    if (configDefs.has(name)) return name;
    const full = `${configPrefix(this.module)}.${name}`;
    if (configDefs.has(full)) return full;
    throw new Error(`config '${name}' is neither a CONFIG.md key nor declared by module ${this.module}`);
  }

  get(name: string): ConfigValue {
    return this.svc.get(this.key(name));
  }
  bool(name: string): boolean {
    return this.svc.bool(this.key(name));
  }
  num(name: string): number {
    return this.svc.num(this.key(name));
  }
  int(name: string): number {
    return this.svc.int(this.key(name));
  }
  str(name: string): string {
    return this.svc.str(this.key(name));
  }
  json<T extends JsonValue = JsonValue>(name: string): T {
    return this.svc.json<T>(this.key(name));
  }
  set(name: string, value: unknown): Sanitized {
    return this.svc.set(this.key(name), value);
  }
  onChange(l: ConfigListener): void {
    this.svc.onChange(l);
  }
}
