/**
 * Tiny service locator for cross-module APIs. A module exposes its public surface by
 * declaring an interface in its own `api.ts` and calling `services.provide('loan', impl)`.
 * Consumers import only the *type* from `<module>/api.ts` and call `services.get('loan')`.
 * Never import another module's internals (enforced by scripts/check-arch.mjs).
 */
export class Services {
  private readonly map = new Map<string, unknown>();

  provide<T>(name: string, impl: T): void {
    if (this.map.has(name)) throw new Error(`service '${name}' already provided`);
    this.map.set(name, impl);
  }

  /** Returns undefined if the providing module is absent/disabled - handle it. */
  get<T>(name: string): T | undefined {
    return this.map.get(name) as T | undefined;
  }
}
