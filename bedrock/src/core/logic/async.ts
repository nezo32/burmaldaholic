/**
 * Fire-and-forget for async form flows. PURE.
 * A form flow started from an event or timer (`void this.showMain(s)`) had no rejection
 * handler: when the player left mid-flow a later `p.sendMessage` / `economy.balance(p)` threw
 * and surfaced as an unhandled rejection without context (review m8). `detach` attaches one.
 */
export function detach(result: unknown, onError: (e: unknown) => void): void {
  if (result && typeof (result as PromiseLike<unknown>).then === 'function') {
    (result as PromiseLike<unknown>).then(undefined, (e: unknown) => {
      try {
        onError(e);
      } catch {
        /* the logger itself failed: nothing left to do */
      }
    });
  }
}
