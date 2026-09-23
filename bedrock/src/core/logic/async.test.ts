import { describe, expect, it } from 'vitest';
import { detach } from './async';

describe('detach (review m8)', () => {
  it('routes a rejected flow to the handler instead of an unhandled rejection', async () => {
    const seen: unknown[] = [];
    detach(Promise.reject(new Error('player left')), (e) => seen.push((e as Error).message));
    detach(Promise.resolve(1), (e) => seen.push(e));
    detach(undefined, (e) => seen.push(e));
    detach(
      Promise.reject(new Error('x')),
      () => {
        throw new Error('logger broke');
      },
    );
    await new Promise((r) => setTimeout(r, 0));
    expect(seen).toEqual(['player left']);
  });
});
