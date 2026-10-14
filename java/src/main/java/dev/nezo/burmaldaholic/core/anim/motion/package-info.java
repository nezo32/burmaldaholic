/**
 * Shared motion kit "MK" (PURE, common source set; PHASE2B §3): closed-form motion that the server and every client
 * evaluate identically, so a ball, a die or a wheel lands on the server's result at the same millisecond everywhere.
 *
 * <ul>
 *   <li>{@link dev.nezo.burmaldaholic.core.anim.motion.Track}: keyframed multi-channel segments with closed forms,
 *       allocation-free sampling, pure in time.</li>
 *   <li>{@link dev.nezo.burmaldaholic.core.anim.motion.Ballistic},
 *       {@link dev.nezo.burmaldaholic.core.anim.motion.DecaySolver},
 *       {@link dev.nezo.burmaldaholic.core.anim.motion.BounceChain}: solvers that hit exact targets
 *       (contacts first, closed-form segments between them).</li>
 *   <li>{@link dev.nezo.burmaldaholic.core.anim.motion.MotionEvent} +
 *       {@link dev.nezo.burmaldaholic.core.anim.motion.EventCursor}: fire-once events at any frame rate, with the
 *       hitch rule (late events fire silently).</li>
 *   <li>{@link dev.nezo.burmaldaholic.core.anim.motion.ArtScale},
 *       {@link dev.nezo.burmaldaholic.core.anim.motion.PixelSnap},
 *       {@link dev.nezo.burmaldaholic.core.anim.motion.StageMap},
 *       {@link dev.nezo.burmaldaholic.core.anim.motion.HeightShadow},
 *       {@link dev.nezo.burmaldaholic.core.anim.motion.Streak}: crisp drawing helpers (integer physical px per art px,
 *       moving things snapped to physical px, pseudo-3D height, shutter streaks).</li>
 *   <li>{@link dev.nezo.burmaldaholic.core.anim.motion.MotionRng} (cosmetic seeds only, never the game RNG),
 *       {@link dev.nezo.burmaldaholic.core.anim.motion.SpeedPolicy} (shared time is never slowed),
 *       {@link dev.nezo.burmaldaholic.core.anim.motion.SoundMixer} (merge windows, per-id limits, lead voicing),
 *       {@link dev.nezo.burmaldaholic.core.anim.motion.FxGate} (reduced motion / flashes gate + test recorder).</li>
 * </ul>
 *
 * <p>Units: time in ms unless a parameter says otherwise; rates (velocities, accelerations, angular speeds) per
 * SECOND. Transcendentals use {@code StrictMath}, so values are bit-identical on every CPU and JVM and discrete
 * choices derived from them agree between viewers. No Minecraft imports. Golden vectors: {@code src/test/resources/fx/vectors/core_motion.json}. The client
 * half lives in {@code dev.nezo.burmaldaholic.client.fx.motion}.
 */
package dev.nezo.burmaldaholic.core.anim.motion;
