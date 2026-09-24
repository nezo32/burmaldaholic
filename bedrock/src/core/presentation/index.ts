/**
 * Bedrock presentation runtime (docs/architecture/animation.md §2.7–§2.10). Games import it through
 * `core/index.ts`; the pure part (timeline format, easing, tiers, roll-ups, seeds) is `core/logic/anim`.
 */
export { type FxSettings, type Celebrations, FX_DEFAULTS, FX_PROP, fxSettings, localProfile } from './settings';
export { type TimelineSink, type PlayOptions, type PresentationSession, playTimeline } from './scheduler';
export { type LiveForm, type LiveFormOptions, type LiveText, createLiveForm } from './live-form';
export { type FadeSpec, fade, shake } from './camera';
export { type RollUpTitle, titleRollUp } from './rollup-hud';
export { type PropValue, writeProps, playPropAnimation } from './props';
