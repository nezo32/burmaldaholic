// E1 private-use glyph map (docs/architecture/animation.md §6): the reservation table of the core glyph sheet
// `textures/font/core/glyph_e1.png` (256², 16 px cells). Code points stay in the drawing code (./glyphs.mjs);
// this table is the reservation authority. `spec: 'reserved'` ranges stay empty.
export const GLYPH_RANGES = [
  { from: 0xe100, to: 0xe100, owner: 'core', spec: 'UI.md', what: 'chip' },
  { from: 0xe110, to: 0xe14f, owner: 'core', spec: 'UI.md', what: 'cards (E144 back)' },
  { from: 0xe150, to: 0xe153, owner: 'core', spec: 'UI.md', what: 'suits' },
  { from: 0xe160, to: 0xe165, owner: 'core', spec: 'UI.md', what: 'dice faces' },
  { from: 0xe170, to: 0xe171, owner: 'core', spec: 'UI.md', what: 'streak flame / cloud' },
  { from: 0xe172, to: 0xe177, owner: 'core', spec: 'global', what: 'HUD pips, sun, loan bell, collectors' },
  { from: 0xe180, to: 0xe185, owner: 'vip', spec: 'UI.md', what: 'VIP badges' },
  { from: 0xe186, to: 0xe18b, owner: 'core', spec: 'global', what: 'coin spin frames, heads, tails' },
  { from: 0xe190, to: 0xe190, owner: 'bots', spec: 'BOTS.md', what: 'bot' },
  { from: 0xe191, to: 0xe19b, owner: 'core', spec: 'global', what: 'thinking dots, mini chips, emerald, gold ingot, sparkle' },
  { from: 0xe19c, to: 0xe19f, owner: 'core', spec: 'reserved', what: 'global reserve' },
  { from: 0xe1a0, to: 0xe1a1, owner: 'pvp', spec: 'PVP.md', what: "rabbit's foot, charred" },
  { from: 0xe1a2, to: 0xe1af, owner: 'extras', spec: 'extras-pvp', what: 'scratch symbols, foil, chain pips, Plinko' },
  { from: 0xe1b0, to: 0xe1b8, owner: 'extras', spec: 'extras-pvp', what: 'wheel mode icon + segment icons' },
  { from: 0xe1b9, to: 0xe1bf, owner: 'extras', spec: 'reserved', what: 'extras reserve' },
  { from: 0xe1c0, to: 0xe1cb, owner: 'core', spec: 'tables', what: 'tumbling die, pockets, ball, dolly, push, tick/cross' },
  { from: 0xe1cc, to: 0xe1cf, owner: 'core', spec: 'reserved', what: 'tables reserve' },
  { from: 0xe1d0, to: 0xe1db, owner: 'core', spec: 'cards', what: 'card flip/peel/slot/flight, button, pot, stacks, muck' },
  { from: 0xe1dc, to: 0xe1df, owner: 'core', spec: 'reserved', what: 'cards reserve' },
  { from: 0xe1e0, to: 0xe1ef, owner: 'extras', spec: 'extras-pvp (moved)', what: 'Wheel Party dye swatches' },
  { from: 0xe1f0, to: 0xe1ff, owner: 'core', spec: 'reserved', what: 'presentation core reserve (skip hint, spinners)' },
];
