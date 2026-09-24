import { describe, expect, it, vi } from 'vitest';

vi.mock('@minecraft/server', () => {
  const events = new Proxy({}, { get: () => ({ subscribe: () => {} }) });
  return {
    world: { afterEvents: events, beforeEvents: events },
    system: { currentTick: 0, runInterval: () => 0, runTimeout: () => 0, run: () => 0, clearRun: () => {}, afterEvents: events, beforeEvents: events },
    EasingType: {},
    MolangVariableMap: class {},
    EntityComponentTypes: {},
    ItemComponentTypes: {},
    ItemStack: class {},
  };
});
vi.mock('@minecraft/server-ui', () => ({ ActionFormData: class {}, ModalFormData: class {}, MessageFormData: class {}, FormCancelationReason: {}, uiManager: {}, CustomForm: class {} }));

const s = await import('./settings');
const { presentation } = await import('../../../../core');
const FX_DEFAULTS = presentation.FX_DEFAULTS;

const fakePlayer = (props: Record<string, unknown>, extra: Record<string, unknown> = {}) => ({
  getDynamicProperty: (k: string) => props[k],
  setDynamicProperty: (k: string, v: unknown) => (props[k] = v),
  isSneaking: false,
  location: { x: 0, y: 64, z: 0 },
  ...extra,
});

describe('slot settings (BS10)', () => {
  it('turbo halves shared beats only for the spinner; reduce motion never changes shared timing', () => {
    const fx = { ...FX_DEFAULTS, reduceMotion: true, flashes: false };
    const st = s.resolveSlotSettings(fx, true, true);
    expect(s.sharedProfile(st)).toEqual({ speedPct: 200, reduceMotion: false, flashes: true });
    expect(s.slotLocalProfile(st).speedPct).toBe(200);
    expect(s.slotLocalProfile(st).reduceMotion).toBe(true);
    expect(st.cabinetView).toBe(false); // disabled with reduce motion (§6.5)
    const normal = s.resolveSlotSettings({ ...FX_DEFAULTS, speedPct: 150 }, false, true);
    expect(s.sharedProfile(normal).speedPct).toBe(100);
    expect(s.slotLocalProfile(normal).speedPct).toBe(150);
    expect(normal.cabinetView).toBe(true);
  });

  it('frame options, particle scaling, volume, celebrations', () => {
    const st = s.resolveSlotSettings({ ...FX_DEFAULTS, reduceMotion: true, volume: 40 }, false, false, { ddui: false, tinting: false });
    expect(s.frameOptions(st)).toEqual({ reduceMotion: true, flashes: false, tinting: false });
    expect(s.particleCount(st, 60)).toBe(18);
    expect(s.particleCount(s.resolveSlotSettings(FX_DEFAULTS, false, false), 60)).toBe(60);
    expect(s.soundVolume(st, 0.5)).toBeCloseTo(0.2);
    expect(s.showOverlay(s.resolveSlotSettings({ ...FX_DEFAULTS, celebrations: 'off' }, false, false))).toBe(false);
    expect(s.camerasAllowed(st)).toBe(false);
  });

  it('dynamic properties and sneak-to-skip within 4 blocks', () => {
    const props: Record<string, unknown> = {};
    const p = fakePlayer(props);
    expect(s.slotSettings(p as never).turbo).toBe(false);
    s.setTurbo(p as never, true);
    expect(s.slotSettings(p as never).turbo).toBe(true);
    s.SLOT_SETTING_ROWS.find((r) => r.id === 'cabinetView')!.set(p as never, true);
    expect(s.slotSettings(p as never).cabinetView).toBe(true);
    expect(s.withinSkipRange({ x: 3, y: 64, z: 2 }, { x: 0, y: 64, z: 0 })).toBe(true);
    expect(s.withinSkipRange({ x: 3, y: 64, z: 3 }, { x: 0, y: 64, z: 0 })).toBe(false);
    expect(s.wantsSneakSkip(fakePlayer({}, { isSneaking: true }) as never, { x: 1, y: 64, z: 0 })).toBe(true);
    expect(s.wantsSneakSkip(fakePlayer({}, { isSneaking: true, location: { x: 9, y: 64, z: 0 } }) as never, { x: 0, y: 64, z: 0 })).toBe(false);
    expect(s.wantsSneakSkip(fakePlayer({}) as never, { x: 0, y: 64, z: 0 })).toBe(false);
  });
});
