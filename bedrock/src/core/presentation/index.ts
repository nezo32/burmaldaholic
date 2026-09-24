/**
 * Bedrock presentation runtime (docs/architecture/animation.md §2.7–§2.10). Games import it through
 * `core/index.ts` (`presentation`); the pure part (timeline format, easing, tiers, roll-ups, seeds, celebration plans,
 * sound plans) is `core/logic/anim` (`anim`). The FX front door is `ctx.fx` (`FxService`).
 */
export { type FxSettings, type Celebrations, FX_DEFAULTS, FX_PROP, fxSettings, localProfile } from './settings';
export { type TimelineSink, type PlayOptions, type PresentationSession, playTimeline } from './scheduler';
export { type LiveForm, type LiveFormOptions, type LiveText, type LiveButtonOptions, type LiveCloseReason, createLiveForm, dduiHealthy } from './live-form';
export { type FadeSpec, fade, shake } from './camera';
export { type RollUpTitle, titleRollUp } from './rollup-hud';
export { type PropValue, writeProps, playPropAnimation, propTag, findProp, ensureProp, removeProp } from './props';
export { type SoundOpts, SOUNDS_PER_SECOND, playCasinoSound, playAt, viewersNear } from './sound';
export { type BurstOpts, ParticleBudget, MAX_PARTICLES_PER_BURST, MAX_CALLS_PER_EVENT, burst, acceptsCelebration } from './particles';
export { type SoundTimelineOptions, type SoundTimelineHandle, soundTimeline, revealAfter } from './table-fx';
export { type FxHud, type CelebrateOptions, type CelebrationHandle, type ChaosMood, FX_CHANNEL, FX_SCRIPT_EVENT, FxService } from './fx';
