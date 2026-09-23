export interface Logger {
  info(...a: unknown[]): void;
  warn(...a: unknown[]): void;
  error(...a: unknown[]): void;
}

/** Content log output (visible with "Content Log GUI" enabled). Not player-facing. */
export function createLogger(scope: string): Logger {
  const p = `[burmaldaholic:${scope}]`;
  return {
    info: (...a) => console.info(p, ...a),
    warn: (...a) => console.warn(p, ...a),
    error: (...a) => console.error(p, ...a),
  };
}
