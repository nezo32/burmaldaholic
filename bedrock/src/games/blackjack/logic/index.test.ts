import { describe, expect, it } from 'vitest';
import { handValue } from './index';

describe('blackjack handValue', () => {
  it('counts soft 21', () => expect(handValue(['A', 'K'])).toEqual({ total: 21, soft: true }));
  it('demotes aces', () => expect(handValue(['A', 'A', '9'])).toEqual({ total: 21, soft: true }));
  it('hard total', () => expect(handValue(['K', 'Q', '5'])).toEqual({ total: 25, soft: false }));
});
