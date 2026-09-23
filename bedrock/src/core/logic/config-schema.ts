/**
 * Config definitions. PURE. Each module declares its own config entries in its
 * CasinoModule.config array; keys are namespaced `<module>.<name>` automatically.
 * Values persist as world dynamic properties `burmaldaholic:cfg.<module>.<name>`.
 * The admin form (core) renders all definitions; labels come from lang key
 * `msg.burmaldaholic.<module>.config.<name>`.
 */
export type ConfigDef =
  | { type: 'bool'; name: string; default: boolean }
  | { type: 'int'; name: string; default: number; min: number; max: number; step?: number }
  | { type: 'enum'; name: string; default: string; options: readonly string[] };

export type ConfigValue = boolean | number | string;

export const configKey = (module: string, name: string): string => `${module}.${name}`;
export const configPropertyId = (fullKey: string): string => `burmaldaholic:cfg.${fullKey}`;

/** Coerce a stored (possibly stale/invalid) value to a valid one for the definition. */
export function sanitize(def: ConfigDef, raw: unknown): ConfigValue {
  switch (def.type) {
    case 'bool':
      return typeof raw === 'boolean' ? raw : def.default;
    case 'int': {
      if (typeof raw !== 'number' || !Number.isFinite(raw)) return def.default;
      return Math.min(def.max, Math.max(def.min, Math.round(raw)));
    }
    case 'enum':
      return typeof raw === 'string' && def.options.includes(raw) ? raw : def.default;
  }
}

export function validateDefs(module: string, defs: readonly ConfigDef[]): void {
  const seen = new Set<string>();
  for (const d of defs) {
    if (!/^[a-z][a-z0-9_]*$/.test(d.name)) throw new Error(`config ${module}.${d.name}: bad name`);
    if (seen.has(d.name)) throw new Error(`config ${module}.${d.name}: duplicate`);
    seen.add(d.name);
    if (d.type === 'int' && !(d.min <= d.default && d.default <= d.max)) {
      throw new Error(`config ${module}.${d.name}: default out of range`);
    }
    if (d.type === 'enum' && !d.options.includes(d.default)) {
      throw new Error(`config ${module}.${d.name}: default not in options`);
    }
  }
}
