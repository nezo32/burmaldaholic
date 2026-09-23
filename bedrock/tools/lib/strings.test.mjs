import { describe, expect, it } from 'vitest';
import { parseConfigMd, parseNum, parseRange } from './config-md.mjs';
import { MARKER, aliasesFor, ownerOf, parseStrings, renderFragment, routeEntries, splitManual, toBedrock } from './strings.mjs';

const MODULES = ['core', 'blackjack', 'loan', 'lastchance', 'multiplayer', 'vip', 'chaos'];

describe('placeholder conversion (LOCALIZATION.md §2)', () => {
  it('converts %N$s to %N and keeps %s / %%', () => {
    expect(toBedrock('Bet: %1$s · %2$s')).toBe('Bet: %1 · %2');
    expect(toBedrock('%1$s%% and %s')).toBe('%1%% and %s');
    expect(toBedrock('+%1$s %%')).toBe('+%1 %%');
    expect(toBedrock('x  ')).toBe('x');
  });
  it('never leaves a $ behind', () => expect(toBedrock('%10$s %2$s')).not.toContain('$'));
});

const MD = `
## core
### Items
| Key | EN | RU |
|-----|----|----|
| \`item.burmaldaholic.chip_1\` | White Chip | Белая фишка |
| \`entity.burmaldaholic.loan_shark\` | Loan Shark | Ростовщик |
| \`item.burmaldaholic.loan_shark_spawn_egg\` | Loan Shark Spawn Egg | Яйцо |
| \`modmenu.nameTranslation.burmaldaholic\` | Burmaldaholic | Бурмалдоголик |
| \`gui.burmaldaholic.vip.x\` | In core section | Ядро |
## blackjack
### Cards (shared with poker)
| \`gui.burmaldaholic.card.hidden\` | Hidden | Скрыта |
### Other
| \`gui.burmaldaholic.blackjack.hit\` | Hit %1$s | Ещё %1$s |
## config
| \`config.burmaldaholic.lastChance.enabled\` | LC | ПШ |
| \`config.burmaldaholic.ownership.enabled\` | Own | Влад |
| \`config.burmaldaholic.section.core\` | General | Общее |
`;

describe('STRINGS.md parsing and routing', () => {
  const { entries, errors } = parseStrings(MD);
  it('parses rows only', () => {
    expect(errors).toEqual([]);
    expect(entries.map((e) => e.key)).toHaveLength(10);
    expect(entries[0]).toMatchObject({ key: 'item.burmaldaholic.chip_1', en: 'White Chip', ru: 'Белая фишка', section: 'core', subsection: 'Items' });
  });
  it('routes by module segment, subsection and section', () => {
    const own = Object.fromEntries(entries.map((e) => [e.key, ownerOf(e, MODULES)]));
    expect(own['gui.burmaldaholic.vip.x']).toBe('vip');
    expect(own['gui.burmaldaholic.card.hidden']).toBe('core');
    expect(own['gui.burmaldaholic.blackjack.hit']).toBe('blackjack');
    expect(own['config.burmaldaholic.lastChance.enabled']).toBe('lastchance');
    expect(own['config.burmaldaholic.ownership.enabled']).toBe('multiplayer');
    expect(own['config.burmaldaholic.section.core']).toBe('core');
  });
  it('generates Bedrock aliases (§1.3)', () => {
    const keys = entries.flatMap((e) => aliasesFor(e).map((a) => a.key));
    expect(keys).toEqual(['entity.burmaldaholic:loan_shark.name', 'item.spawn_egg.entity.burmaldaholic:loan_shark.name', 'pack.name']);
  });
  it('detects duplicates', () => expect(parseStrings(MD + '| `gui.burmaldaholic.card.hidden` | a | b |\n').errors[0]).toMatch(/duplicate/));
  it('renders fragments and keeps the manual part', () => {
    const routed = routeEntries(entries, MODULES);
    const text = renderFragment('blackjack', 'ru', routed.get('blackjack'), 'msg.burmaldaholic.blackjack.extra=Доп\n');
    expect(text).toContain('gui.burmaldaholic.blackjack.hit=Ещё %1');
    expect(text).not.toContain('$');
    expect(text).toContain(MARKER);
    expect(splitManual(text).manual).toBe('msg.burmaldaholic.blackjack.extra=Доп\n');
    expect(splitManual('no marker').manual).toBe('');
  });
});

describe('CONFIG.md parsing', () => {
  it('numbers and ranges', () => {
    expect(parseNum('1 000')).toBe(1000);
    expect(parseNum('10⁶')).toBe(1e6);
    expect(parseNum('10¹²')).toBe(1e12);
    expect(parseRange('0.0–0.5')).toEqual({ min: 0, max: 0.5, each: false });
    expect(parseRange('each 0–100')).toEqual({ min: 0, max: 100, each: true });
    expect(parseRange('—')).toEqual({});
    expect(parseRange('')).toBeUndefined();
  });
  it('rows, inheritance, families, labels', () => {
    const md = `
| \`vip.threshold.silver\` | long | 5000 | 1–10¹² | x |
| \`vip.threshold.gold\` | long | 25000 | | |
| \`chaos.weight.<event>\` | int | §13.2 | 0–1000 | x |
| \`core.casinoMode\` | bool | true (on world creation) | — | x |
| \`core.hud.position\` | enum(TOP_LEFT, TOP_RIGHT) | TOP_LEFT | — | x |
`;
    const labels = new Set(['config.burmaldaholic.vip.threshold.silver', 'config.burmaldaholic.chaos.weight', 'config.burmaldaholic.core.hud.position', 'gui.burmaldaholic.menu.settings.corner.top_left', 'gui.burmaldaholic.menu.settings.corner.top_right']);
    const { defs, errors } = parseConfigMd(md, labels);
    expect(errors).toEqual([]);
    const by = Object.fromEntries(defs.map((d) => [d.key, d]));
    expect(by['vip.threshold.gold']).toMatchObject({ min: 1, max: 1e12, default: 25000, owner: 'vip', section: 'vip' });
    expect(by['chaos.weight.golden_hour']).toMatchObject({ default: 2, label: 'config.burmaldaholic.chaos.weight', labelArg: { key: 'gui.burmaldaholic.chaos.event.golden_hour' } });
    expect(by['core.casinoMode']).toBeUndefined();
    expect(by['core.hud.position'].optionLabels).toEqual(['gui.burmaldaholic.menu.settings.corner.top_left', 'gui.burmaldaholic.menu.settings.corner.top_right']);
  });
});
