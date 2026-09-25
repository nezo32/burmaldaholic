# Research: Animation, game feel and entertaining slots

Status: research input for the designer and the architect (2026-09-24). Nothing here is normative.
Decisions go into `GAME_DESIGN.md`, `UI.md`, `CONFIG.md` and `STRINGS.md` once they are approved.

Goal from the user: **beautiful animations for every game** and **very entertaining slots**, in
both editions.

API names were checked against the local Minecraft **26.2** sources (loom cache,
`minecraft-clientOnly-…-26.2-sources.jar`), Fabric API 0.161 jars (`fabric-rendering-v1`
27.0.x, `fabric-particles-v1` 5.0.x) and the installed `@minecraft/server` **2.8.0** and
`@minecraft/server-ui` **2.1.0** typings. Items marked **(verify)** come from memory or
community practice and were not checked against a jar or typings.

Contents
1. Principles that apply to both editions
2. Java (Fabric 26.2–26.3): capabilities and how to use them
3. Bedrock (Script API 2.x and resource packs): capabilities and how to use them
4. Current implementation audit and gaps
5. Modern video-slot design, mapped to both editions
6. Per-game animation plans
7. Performance budgets
8. Accessibility and localization
9. Suggested config keys, strings and ordering of work
10. Open questions

---

## 1. Principles that apply to both editions

1. **The server decides first, then animates.** This is already the rule (`GAME_DESIGN.md` §4.1,
   `SpinAnimation`, `spinFrames`). Every animation replays an outcome that has already been
   drawn and settled, or is about to be settled. The client never predicts an outcome.
   The client may *predict presentation*: it can start spinning reels the moment the button is
   pressed, before the result packet arrives, and then ease onto the result when the packet
   lands. See §2.10.
2. **Time comes from a clock, not from frames.** In Java, use `Util.getMillis()` or
   `gameTime + partialTick`. In Bedrock, use `system.currentTick`. Never use "+1 per render call".
3. **One timeline per round.** Describe each animation as data: a list of `(tStart, duration,
   easing, target)` tracks built from the outcome. The same timeline drives visuals, sounds and
   the "result revealed" moment, and a **skip** sets `t = end`. Keep the timeline builders pure
   so they can be tested, like `SpinAnimation.java` and `animation.ts` today.
4. **Anticipation is honest.** Slowing down a reel, adding suspense sounds or teasing a bonus
   is allowed only when the *real* outcome warrants it, for example when two scatters are
   already visible. Never show fake near-misses that the RNG did not produce, and never change
   the odds to create them. See §5.9.
5. **Every flash, shake or strobe has a reduce-motion path.** See §8.
6. **No text baked into textures.** Numbers and words are rendered as text or glyphs from
   lang keys. See §8.

Shared easing helpers. Write them once per edition, in pure logic packages, with unit tests:

```java
// java: core/anim/Ease.java (pure, testable)
public final class Ease {
    public static double outCubic(double t) { return 1 - Math.pow(1 - t, 3); }
    public static double inOutSine(double t) { return -(Math.cos(Math.PI * t) - 1) / 2; }
    /** overshoot then settle: the "clunk" of a stopping reel */
    public static double outBack(double t, double s) { double u = t - 1; return 1 + (s + 1) * u * u * u + s * u * u; }
    public static double outElastic(double t) { return t == 0 || t == 1 ? t : Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * Math.PI / 3)) + 1; }
    public static double clamp01(double t) { return t < 0 ? 0 : t > 1 ? 1 : t; }
}
```

---

## 2. Java (Fabric, Minecraft 26.x, Mojang names)

### 2.1 Screen rendering model in 26.x

In 26.x the GUI uses an **extract-then-draw** model. Screens do not draw immediately. They
*extract* render state into a `GuiRenderState` through `GuiGraphicsExtractor`
(`net.minecraft.client.gui.GuiGraphicsExtractor`, the successor of `GuiGraphics`), and the
engine batches and draws afterwards.

- Override points: `Screen.extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY,
  float a)` and `Screen.extractBackground(...)`. The last argument `a` is the partial tick.
  The project's `CasinoTableScreen` subclasses already use `extractBackground` and
  `extractContent`.
- Transform: `g.pose()` is a `Matrix3x2fStack` (2D affine: translate, rotate, scale, and shear
  through `mul`). `pushMatrix()` and `popMatrix()` bracket each element. `WheelScreen` already
  rotates fills this way.
- Clipping: `g.enableScissor(x0, y0, x1, y1)` and `g.disableScissor()`. These nest as a stack
  (`ScreenRectangle` push/pop). Use one scissor per reel window.
- Layers: `g.nextStratum()` starts a new draw stratum, for example a big-win overlay above the
  widgets. `g.blurBeforeThisStratum()` blurs everything underneath, which gives a "big win"
  focus effect cheaply.
- Primitives: `fill`, `fillGradient`, `fill(RenderPipeline, …)`, `outline`,
  `horizontalLine`/`verticalLine`, `text`/`centeredText`/`textWithWordWrap`,
  `item(ItemStack, x, y)`, `entity(...)`, `blit(RenderPipeline, Identifier, x, y, u, v, w, h,
  …)`, and `blitSprite(RenderPipeline, Identifier sprite, x, y, w, h[, int argbColor | float
  alpha])`.
- The standard textured pipeline is `RenderPipelines.GUI_TEXTURED`.

**Frame rate.** Screens are drawn every frame (60–240 fps). The server ticks at 20 tps. Compute
positions from `Util.getMillis()` for purely visual time, or from `ticks + a` when the animation
must line up with server ticks (`WheelScreen` and `PlinkoScreen` already do `ticks - animStart +
partial`). Only discrete events (reel stop sound, reveal) are raised from `tick()` or from a
clock-threshold check. Never assume one render per tick.

### 2.2 Sprites, atlases, nine-slice, animated GUI textures

- **GUI sprites** live at `assets/burmaldaholic/textures/gui/sprites/<path>.png` and are
  stitched automatically into the GUI atlas. Draw them with
  `g.blitSprite(RenderPipelines.GUI_TEXTURED, Burmaldaholic.id("slots/reel_frame"), x, y, w, h)`.
- **Nine-slice or tile scaling** comes from a `.png.mcmeta` next to the sprite. The vanilla
  button uses exactly this:
  ```json
  { "gui": { "scaling": { "type": "nine_slice", "width": 200, "height": 20, "border": 3 } } }
  ```
  Other types are `"stretch"` (the default) and `"tile"`. Use nine-slice for machine cabinets,
  panels, win banners and buttons, so that one texture serves every screen size.
- **Animated GUI sprites**: the same `.mcmeta` accepts an `"animation"` block. Vanilla uses it
  in `gui/sprites/realm_status/expires_soon.png.mcmeta`: `{"animation":{"frametime":10,
  "height":28}}`. Frames are stacked vertically. Optional `"interpolate": true` blends between
  frames, and `"frames": [...]` sets a custom order. Good uses: chasing marquee bulbs around the
  cabinet, a glinting WILD symbol, a pulsing SPIN button, a burning jackpot meter. These cost
  nothing in code because the atlas ticks them.
- **Symbol sheet**: draw reel symbols as GUI sprites
  (`gui/sprites/slots/symbol/seven.png`, 32×32 or 48×48) instead of scaled items
  (`g.item(...)` at 2×, which is what `SlotMachineScreen.drawSymbol` does today). Sprites allow
  colour tint (`blitSprite(..., argb)` for dimming non-winning symbols), alpha, a blur-frame
  variant for fast spin (`seven_blur.png`), and animated win frames (`seven_win.png` plus
  `.mcmeta`).
- **Motion blur** without shaders: while a reel runs at full speed, draw the `_blur` variant
  stretched vertically by 1.3× at 70% alpha. Swap back to the sharp sprite during deceleration.

### 2.3 Custom shaders and RenderPipelines (26.x)

- `RenderPipelines.register(RenderPipeline)` is **public** in 26.2, and the GUI snippets
  (`RenderPipelines.GUI_SNIPPET`, `GUI_TEXTURED_SNIPPET`) are public too. A mod can therefore
  declare its own GUI pipeline:
  ```java
  public static final RenderPipeline GUI_SHIMMER = RenderPipelines.register(
      RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
          .withLocation(Burmaldaholic.id("pipeline/gui_shimmer"))
          .withFragmentShader(Burmaldaholic.id("core/gui_shimmer"))   // assets/burmaldaholic/shaders/core/gui_shimmer.fsh
          .build());
  // use: g.blitSprite(GUI_SHIMMER, sprite, x, y, w, h);
  ```
  The shared `Globals` uniform block (`shaders/include/globals.glsl`) exposes `GameTime`
  (0..1 over a day cycle) and `ScreenSize`, so a fragment shader can animate a gold shimmer
  sweep, a rainbow jackpot border or a CRT glow **with no extra uniforms**. Fabric's
  `FabricRenderPipeline.usePipelineDrawModeForGui()` exists for non-quad GUI pipelines.
- Risk: the Blaze3D pipeline API changes almost every minor version, and the project supports
  26.2 and 26.3 from one jar (`checkLinkage`). Keep shader use **optional and cosmetic**, behind
  a single class, with a sprite-only fallback. Run `checkLinkage` on every version. Recommended
  as polish only (phase 3).
- **3D inside a GUI**: `PictureInPictureRendererRegistry.register(...)` (Fabric) together with
  vanilla `net.minecraft.client.gui.render.pip.PictureInPictureRenderer` renders a 3D scene into
  a GUI rectangle. This is how vanilla draws entities and banners in screens. It could show a
  **real 3D roulette wheel or slot cabinet** inside the screen. It is heavy to maintain. Prefer
  2D sprites with rotation. **(verify factory signature before use)**

### 2.4 Reel-strip rendering recipe (Java)

```java
// per reel c: a strip of symbol ids (client-side cosmetic filler) whose last 3 entries are the
// server result for that column. Position p is in "cells scrolled".
record ReelTrack(int[] strip, long startMs, long stopMs, double vMax) {}

double reelPos(ReelTrack r, long now) {
    double t = (now - r.startMs()) / 1000.0;
    double accel = 0.12;                       // s to reach full speed (with a small back-kick)
    double total = (r.stopMs() - r.startMs()) / 1000.0;
    double decel = 0.35;                       // s of ease-out into the stop
    if (t < accel) return -0.15 * Math.sin(Math.PI * t / accel)          // wind-up kick upwards
                           + r.vMax() * t * t / (2 * accel);
    if (t < total - decel) return r.vMax() * (t - accel / 2);
    // final segment: land exactly on target index with outBack overshoot (bounce)
    double target = r.strip().length - 3;       // integer cell where the result shows
    double from = r.vMax() * (total - decel - accel / 2);
    double u = Ease.clamp01((t - (total - decel)) / decel);
    return from + (target - from) * Ease.outBack(u, 1.2);
}
// extract: g.enableScissor(win); for k in -1..3: blitSprite(symbol(strip[floor(p)+k]), x, y0 + (k - frac(p))*CELL); disableScissor()
```

The strip is built at spin start: random filler symbols chosen *from the machine's actual reel
distribution*, and the last three cells are the result grid. The landing distance is therefore
chosen so that the result arrives exactly at `stopMs`, with no visible snap. Reel stop times
are staggered (for example 600 / 850 / 1100 ms, and more for 5 reels). Anticipation stretches a
reel's `stopMs` (§5.9). A stop sound plays when `u` crosses about 0.8, which lines up with the
visible "clunk".

Extras that make reels feel good:
- A dark gradient at the top and bottom of each window (`fillGradient` from 0xC0000000 to
  0x00000000), which reads as a curved drum.
- Win presentation after all reels stop: dim non-winning cells (tint 0xFF606060), then, one
  line at a time, draw the payline path, pulse the winning symbols (scale
  `1 + 0.08*sin(t*2π*2)` about the cell centre using `pose()`), and show a small per-line
  amount label.
- A win counter roll-up (§5.10) and a big-win overlay on a new stratum with blur.

### 2.5 Animated block textures (.mcmeta)

Block and item textures accept the same `animation` mcmeta (`frametime`, `interpolate`,
`frames`). Good for marquee lights on `slot_machine_*_front.png`, a glowing roulette-table rim,
plinko pegs that twinkle, and a scratch-card foil shimmer. There is no code and no per-tick
cost; the texture atlas animates on the client. The limitation is that an animated block
texture loops **unconditionally**. For "lights only while spinning" or "gold while jackpot",
use a blockstate property (`lit`, `win=none|small|big`) that swaps between models or textures.
Server-side, this is `level.setBlock(pos, state.setValue(WIN, BIG), Block.UPDATE_CLIENTS)`,
which costs one block-update packet per change.

### 2.6 BlockEntityRenderer: in-world animated machines and tables

For a Fabric mod whose client also has the mod installed (true here: the screens need the
client mod), a **BlockEntityRenderer (BER)** is the best way to show spinning reels, a roulette
wheel with a ball, a plinko ball or dealt cards on felt **to spectators**. It draws at frame
rate, adds no entity overhead, and needs only one small synced state per block entity.

26.x BER API (from the sources):

```java
public interface BlockEntityRenderer<T extends BlockEntity, S extends BlockEntityRenderState> {
    S createRenderState();
    default void extractRenderState(T be, S state, float partialTicks, Vec3 cameraPos,
                                    ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling);
    void submit(S state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera);
    default int getViewDistance() { return 64; }
}
```

Pattern (following vanilla `CampfireRenderer`):

```java
public final class RouletteWheelRenderer implements BlockEntityRenderer<RouletteTableBlockEntity, RouletteWheelRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        float wheelDeg; float ballDeg; float ballRadius; final ItemStackRenderState ball = new ItemStackRenderState();
    }
    private final ItemModelResolver items;
    public RouletteWheelRenderer(BlockEntityRendererProvider.Context ctx) { items = ctx.itemModelResolver(); }
    public State createRenderState() { return new State(); }

    @Override public void extractRenderState(RouletteTableBlockEntity be, State s, float pt, Vec3 cam, ModelFeatureRenderer.CrumblingOverlay c) {
        BlockEntityRenderer.super.extractRenderState(be, s, pt, cam, c);
        long now = be.getLevel().getGameTime();                  // synced clock, ticks
        SpinSync sync = be.spinSync();                          // {startTick, durationTicks, result, startIndex}
        double t = sync == null ? 1 : Ease.clamp01((now - sync.startTick() + pt) / sync.durationTicks());
        double pos = SpinAnimation.position(sync.result(), t, sync.startIndex(), 3);   // existing pure code
        s.ballDeg = (float) (pos / Wheel.POCKETS * 360.0);
        s.wheelDeg = (float) (-(now + pt) * 1.5);                // wheel slowly counter-rotates
        s.ballRadius = (float) (0.42 - 0.08 * Ease.outCubic(Ease.clamp01((t - 0.7) / 0.3))); // ball drops in
        items.updateForTopItem(s.ball, BALL_STACK, ItemDisplayContext.FIXED, be.getLevel(), null, 0);
    }
    @Override public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState cam) {
        pose.pushPose();
        pose.translate(0.5, 1.02, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(s.wheelDeg));
        // wheel disc: submitCustomGeometry(pose, RenderTypes.entityCutout(WHEEL_TEX), (p, vc) -> quad(...))
        pose.popPose();
        pose.pushPose();
        pose.translate(0.5, 1.06, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(s.ballDeg));
        pose.translate(s.ballRadius, 0, 0);
        pose.scale(0.12f, 0.12f, 0.12f);
        s.ball.submit(pose, out, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
// client init: BlockEntityRenderers.register(TYPE, RouletteWheelRenderer::new)
//           or Fabric BlockEntityRendererRegistry.register(TYPE, RouletteWheelRenderer::new)
```

`SubmitNodeCollector` / `OrderedSubmitNodeCollector` provide `submitItem`, `submitModelPart`,
`submitBlockModel`, `submitCustomGeometry(pose, RenderType, renderer)`, `submitText(...)` for
in-world numbers, `submitShadow`, and so on. Use **item models** for symbols, cards, chips and
the ball, because they are cheap and resource-pack friendly. Use `submitCustomGeometry` for the
wheel disc and reel drums (a textured cylinder of N quads with a scrolling V offset).

**Sync.** The block entity keeps a small immutable `SpinSync` record (`startTick`, `duration`,
the result or grid, and the seed of the cosmetic filler strip). It sends it with
`getUpdatePacket()` / `getUpdateTag()` and
`level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)` **once per spin**. Every
client then animates locally at full frame rate from `gameTime + partialTick`. That is one
packet per spin per tracked chunk, which is very cheap. This works for slot machines too:
spectators see the cabinet's reels spin and land on the same result the player sees in the GUI.
Privacy note: for card games with hidden cards, sync only what is public (face-down cards
render as card backs).

**Model layers** (`ModelLayerRegistry` in Fabric plus vanilla `ModelPart`) are the alternative
for hinged parts (a card shoe, a dealer paddle, a lever arm on the slot cabinet). Animate them
with `ModelPart.xRot` and similar values set in `submit` from state.

### 2.7 Display entities (server-driven, vanilla-client compatible)

`net.minecraft.world.entity.Display` and its subclasses `ItemDisplay`, `BlockDisplay` and
`TextDisplay` interpolate their **transformation** (translation, left rotation, scale, right
rotation) on the client over `interpolation_duration` ticks, starting `start_interpolation`
ticks after the update. Position and rotation interpolate over `teleport_duration`
(`DATA_POS_ROT_INTERPOLATION_DURATION_ID`). All clients see a smooth frame-rate animation from
one entity-data packet per keyframe.

Caveats found in the 26.2 bytecode:
- The setters `setTransformation`, `setTransformationInterpolationDuration` and
  `setTransformationInterpolationDelay` are **private**. Use an accessor mixin
  (`@Invoker`/`@Accessor`, and the project already uses mixins) or write the NBT and call
  `load`. The accessor route is cleaner.
- Rotation interpolation is a quaternion slerp, so it takes the **shortest path**. A wheel that
  must turn 720° needs keyframes of at most about 120° each. For example, send a keyframe
  every 4 ticks with `interpolation_duration = 4`, and have the server compute the eased angle
  per keyframe from the existing `SpinAnimation`. A 5-second spin is about 25 packets per
  display, sent to every tracking player.
- `TextDisplay` supports background colour, `see_through`, `billboard` and line width, and in
  26.x its text can contain **sprite object components**
  (`net.minecraft.network.chat.contents.objects.AtlasSprite`, via
  `Component.object(...)` **(verify factory name)**). That allows floating symbol icons and
  "+1,250" win pop-ups that rise and fade using a translation plus scale-to-zero interpolation.
- `ItemDisplay` with an item that uses a 26.x item-model definition
  (`assets/burmaldaholic/items/slot_symbol.json` with `minecraft:select` on
  `custom_model_data` strings) shows any symbol with one item id.

**Recommendation:** use a BER for the permanent, high-frequency visuals (reels, wheel, ball,
cards), because the client mod is required anyway. Use Display entities only for **transient
celebrations** that are cheap and one-shot (a floating "BIG WIN" TextDisplay that pops, rises
and vanishes; a burst of coin ItemDisplays arcing outwards). Also use them if a future
"server-only, vanilla clients allowed" mode is ever wanted.

### 2.8 Particles

- Custom types: `FabricParticleTypes.simple()` (or `complex(codec, streamCodec)` for parameters
  such as colour or target), registered in `BuiltInRegistries.PARTICLE_TYPE`. Client:
  `ParticleProviderRegistry.getInstance().register(TYPE, SpriteSet-based provider)`. Sprites go
  in `assets/burmaldaholic/particles/<name>.json` (`{"textures":["burmaldaholic:coin_0",…]}`)
  with PNGs in `textures/particle/`.
- Useful base classes in 26.2: `SingleQuadParticle`, `SimpleAnimatedParticle` (sprite flipbook
  plus colour fade, like fireworks), `TrailParticle`, `FlyTowardsPositionParticle` (chips
  flying into the pot or back to the player).
- Server spawn: `serverLevel.sendParticles(type, x, y, z, count, dx, dy, dz, speed)`. This
  sends one packet per call per nearby player. Batch calls with `count` rather than looping.
- Casino set: `coin` (spinning gold coin, 8-frame flipbook, gravity, bounce), `chip` (tinted by
  denomination), `sparkle` (win glint), `confetti` (coloured, tumbling), `jackpot_star`
  (large, slow, glow) and `suit` (♠♥♦♣ sprites for card tables). Vanilla fallbacks exist
  (`ParticleTypes.TOTEM_OF_UNDYING`, `HAPPY_VILLAGER`, `FIREWORK`, `END_ROD`) if custom sprites
  are late.

### 2.9 Sound

- Today every sound in `java/src/main/sounds/*/sounds.json` is an alias of a vanilla event
  (`"type":"event"`). That is fine for placeholders. For entertainment, ship **custom .ogg
  files** (mono, 44.1 kHz, Vorbis q4, under 100 KB each) in
  `assets/burmaldaholic/sounds/slots/*.ogg` and reference them with `"name":"burmaldaholic:slots/reel_stop_1"`.
- Pitch ramps: `SimpleSoundInstance.forUI(event, pitch, volume)` for one-shots with a rising
  pitch (reel stops 1.0→1.12→1.26, which is a musical third per reel; scatter lands step up a
  scale). For a *continuous* ramp (the anticipation whirr, the roll-up tick), subclass
  `AbstractTickableSoundInstance` and change `pitch` and `volume` in `tick()`. Vanilla
  `MinecartSoundInstance` does this.
- Use `"stream": true` only for music loops longer than about 10 s. Use `"weight"` variants to
  avoid repetition fatigue. Put sounds in category `SoundSource.MASTER` or a dedicated category
  so players can control them; see §8.
- In-world sounds for spectators: `level.playSound(null, pos, event, SoundSource.BLOCKS, vol,
  pitch)` from the server, which the vanilla sound settings then attenuate by distance.

### 2.10 Screen shake, flash, titles, toasts, HUD

- **GUI shake**: offset `g.pose().translate(dx, dy)` for the whole screen content during
  `shakeUntil`, with `dx = A * decay * sin(t*47)` and `dy = A * decay * cos(t*39)`. Use this
  for KABOOM (PVP.md) and TNT or creeper symbols.
- **World camera shake** (for example a jackpot while the player is not in a GUI): inject at the
  tail of `GameRenderer.bobHurt(CameraRenderState, PoseStack)` and add a small rotation. It is
  client-only, respects the reduce-motion toggle, and stays tiny.
- **Flash**: a full-screen `g.fill(0,0,w,h, argb(alpha,255,240,180))` on a top stratum with
  alpha decaying over 250 ms. Capped and toggleable.
- **Titles**: `minecraft.gui.hud.setTitle(Component)`, `.setSubtitle(...)` and
  `.setTimes(fadeIn, stay, fadeOut)` (26.x moved these to `Hud`), or from the server with
  `ClientboundSetTitleTextPacket` and `ClientboundSetTitlesAnimationPacket`.
- **Toasts**: implement `Toast` (`getWantedVisibility`, `update(ToastManager, long)`,
  `extractRenderState(GuiGraphicsExtractor, Font, long)`) and add it with
  `minecraft.gui.toastManager().addToast(...)`. Good for "Jackpot won by X at Copper Row" and
  achievements.
- **HUD**: `HudElementRegistry.attachElementAfter(VanillaHudElements.X, id, element)` (Fabric).
  The project already has `CasinoHud.register(...)` segments; add an "active spin mini-reel"
  or "win roll-up" segment for when the player closes the screen mid-spin.

### 2.11 Presentation prediction vs server authority (Java)

Today `SlotMachineScreen` waits for the state packet that carries `grid` before it starts
animating. That usually arrives within one round trip (50–150 ms).
Improvement: start the **spin-up** immediately on click (acceleration and blur only, with no
result), and when the result arrives, compute the landing so it ends exactly `stopMs` after the
*result* arrived. If the result has not arrived by the time reel 1 would stop, keep spinning at
full speed. This is invisible and hides latency. If the action is rejected, stop the reels
with a soft bounce back to the old grid. The outcome is never predicted, only the motion.

---

## 3. Bedrock (Script API 2.x, resource packs, 1.26.30+)

The project targets `@minecraft/server` **2.8.0** and `@minecraft/server-ui` **2.1.0**, both
stable. The installed typings contain **no `@beta` tags**, so everything cited below from these
modules is stable.

### 3.1 What can move on Bedrock, at a glance

| Tool | Where it runs | Smoothness | Visible to | Good for |
|---|---|---|---|---|
| Entity with Blockbench geometry + animations + animation controllers | client, Molang | 60 fps (client-interpolated) | everyone nearby | reels, wheels, ball, dice, cards, cabinets, lever |
| Entity properties (`setProperty`, `client_sync`) → Molang `q.property` | server sets, client renders | instant state; client animates between | everyone (or one player via `setPropertyOverrideForEntity`) | which symbol, target angle, state of machine |
| `entity.playAnimation(anim, {controller, nextState, blendOutTime, stopExpression, players})` | server triggers | 60 fps | chosen players or all | one-shot moves (lever pull, card flip, celebration) |
| Custom particles (RP `particles/*.json`) with `MolangVariableMap` | client | 60 fps | `dimension.spawnParticle` = all, `player.spawnParticle` = one | coins, confetti, sparkles, ball trail |
| Flipbook textures (RP `textures/flipbook_textures.json`) | client | frame-based | everyone | marquee lights, glowing blocks |
| Block permutations (`block.setPermutation`) | server | instant swap | everyone | lit/unlit machine, win state |
| Sounds (`player.playSound(id,{pitch,volume})`, `dimension.playSound`) | client | n/a | one or all | stops, ticks, fanfares |
| `onScreenDisplay.setTitle/updateSubtitle/setActionBar` | server → one player | text updates, rate-limited | one player | counters, reel glyph strip |
| Camera: `player.camera.fade`, `setCamera(... easeOptions)`, `playAnimation(spline)`, `clear` | server → one player | 60 fps eased | one player | jackpot fly-in, fade to black |
| `/camerashake add` via `runCommand` | server → one player | client | one player | explosions, big wins |
| server-ui `CustomForm`/`MessageBox` with `Observable*` (DDUI) | server → one player | updates on `setData` | one player | live reels inside a form |
| JSON UI (RP `ui/*.json`) restyle of `server_form`, `hud_screen` | client | JSON UI anims (flipbook, alpha, offset) | one player | styled forms, custom HUD panels |
| Font glyphs (RP `font/glyph_XX.png`) | client | static glyphs, swapped per frame | text anywhere | symbols in text, forms, actionbar, titles |

### 3.2 Entities: the only way to get true 3D animation

Bedrock has **no display entities and no block-entity renderers**. Every animated 3D object on
a table is a custom **dummy entity**.

BP `entities/slot_reels.json` (sketch):
```json
{ "format_version": "1.21.0", "minecraft:entity": {
  "description": { "identifier": "burmaldaholic:slot_reels", "is_spawnable": false, "is_summonable": true,
    "properties": {
      "burmaldaholic:r0": { "type": "int", "range": [0, 11], "default": 0, "client_sync": true },
      "burmaldaholic:r1": { "type": "int", "range": [0, 11], "default": 0, "client_sync": true },
      "burmaldaholic:r2": { "type": "int", "range": [0, 11], "default": 0, "client_sync": true },
      "burmaldaholic:state": { "type": "enum", "values": ["idle","spin","win","big"], "default": "idle", "client_sync": true }
    } },
  "components": {
    "minecraft:physics": { "has_gravity": false, "has_collision": false },
    "minecraft:collision_box": { "width": 0.01, "height": 0.01 },
    "minecraft:damage_sensor": { "triggers": { "deals_damage": "no" } },
    "minecraft:pushable": { "is_pushable": false, "is_pushable_by_piston": false },
    "minecraft:persistent": {}, "minecraft:tick_world": { "never_despawn": true, "radius": 2 } } } }
```
Keep AI and behaviour components out, so the server tick cost is close to zero. Remove
`tick_world` if the entity may unload with its chunk; respawn or re-link from the block on
chunk load (as the worldgen croupiers do).

RP `entity/slot_reels.entity.json` plus `animations/slot_reels.animation.json` plus
`animation_controllers/slot_reels.ac.json`:
- **Reel geometry**: each reel is a 12-sided prism (one face per symbol) in Blockbench, a bone
  `reel0/1/2` with its pivot on the axle.
- **Spin**: animation `spin` loops `rotation: ["-q.anim_time * 720", 0, 0]` on each reel bone.
  Animation `land_N` rotates from the current angle to `-(v.target * 30)` with Blockbench
  easing, or in Molang
  `math.lerp(v.from, v.to, 1 - math.pow(1 - math.min(q.anim_time/0.4,1), 3))`, followed by an
  overshoot keyframe. The target comes from `q.property('burmaldaholic:r0')`.
- **Controller**: `idle → spinning` when
  `q.property('burmaldaholic:state') == 'spin'`, `spinning → landed` per reel, staggered with
  `q.anim_time > 0.6 + 0.25 * reelIndex`, or driven by the server with `playAnimation(...,
  { controller: 'controller.animation.slot_reels.r0', nextState: 'landed' })`.
- **Alternative with no bone rotation**: a flat reel window with **UV scrolling**. The render
  controller `uv_anim: { "offset": [0, "math.mod(q.anim_time * 4, 1)"], "scale": [1, 1] }` on a
  vertical symbol-strip texture, and a stopped state with
  `offset [0, "q.property('burmaldaholic:r0') / 12"]`. This is cheaper and pixel-perfect, and
  it looks like a real reel window. **(verify uv_anim property names in the current render
  controller schema)**

Server side (TypeScript):
```ts
// decide first (engine), then animate
const outcome = engine.spin(rng, table, bet);
reels.setProperty('burmaldaholic:state', 'spin');            // all nearby clients start spinning
outcome.grid[1].forEach((sym, c) => reels.setProperty(`burmaldaholic:r${c}`, SYMBOLS.indexOf(sym)));
// staggered stops (the controller reads the properties when landing); anticipation = extra delay
const stops = anticipationPlan(outcome);                      // pure, honest (§5.9)
stops.forEach((at, c) => system.runTimeout(() => {
  reels.playAnimation(`animation.burmaldaholic.slot_reels.land${c}`, { controller: `controller.r${c}`, nextState: 'landed' });
  player.playSound('burmaldaholic.slots.reel_stop', { pitch: 1 + 0.12 * c });
}, at));
```

`Player.setPropertyOverrideForEntity(entity, id, value)` sets a property value **only for that
player's view**. That allows a private reel result on a shared cabinet, for example to avoid
spoiling another player's reveal, or per-player highlight glow. It is present in the 2.8.0
typings.

Limits (Bedrock rules, **(verify current numbers)**): at most about 32 properties per entity
type; an int property range is the only way to pass numbers, and floats are allowed with a
range; enum values must be at most 16 or 32 chars. Molang `q.property` is available in RP
animations, controllers and render controllers.

Other in-world objects:
- **Roulette wheel**: an entity with a `wheel` bone (constant slow rotation) and a `ball` bone
  parented to an `orbit` bone. Animation `spin` sets `orbit.rotation.y =
  v.ball_start + (v.ball_end - v.ball_start) * ease(q.anim_time / v.dur)`, with a `ball.position.x`
  radius shrink at the end. The server sets `burmaldaholic:result_pocket` (int 0–36) and the
  animation derives the end angle from `WHEEL_ORDER` using a Molang array
  (`array.pocket_angles[...]` in the render controller, or a precomputed angle property).
- **Wheel of fortune**: a single `wheel` bone. The eased end angle is sent as a float property
  (`range [0, 3600]`) or as the int segment index.
- **Plinko**: a `ball` bone whose position keyframes are the drawn path. Either keep 2^12 = 4096
  paths as properties (a 12-bit int `path` property plus a Molang
  `math.mod(math.floor(v.path / math.pow(2, row)), 2)` per row), or ship 12 one-row animations
  chained by a controller. The 12-bit int is compact and deterministic.
- **Cards**: one entity per seat with 1–6 `card_N` bones. Faces come from a card atlas texture
  and each bone's UV comes from a `card_N` int property (0–52, where 52 is the back) through
  **render controller** `part_visibility` plus `uv_anim` or through separate geometry per
  card. Animations: `deal_N` (slide from the shoe, with an arc), `flip_N` (rotate Z 180° with
  a raise).
- **Dice (craps)**: two entities, or one entity with two bones. Tumble animation, then a land
  keyframe selected by `face` properties. Pair it with the `/camerashake` command at the moment
  the dice hit the back wall.

### 3.3 Attachables

Attachables (RP `attachables/*.json`) render an item held in hand with custom geometry and
animations. Candidates: a held scratch card with a foil shimmer, a spinning coin in the
coin-flip hand, and a dealer's card fan. These are cosmetic and low priority.

### 3.4 Particles (Bedrock)

RP `particles/coin_burst.json`, `format_version` "1.10.0", using `minecraft:emitter_rate_instant`
(`num_particles: "v.count"`), `minecraft:emitter_shape_point`,
`minecraft:particle_initial_speed`, `minecraft:particle_motion_dynamic` (gravity),
`minecraft:particle_appearance_billboard` with `"flipbook": { "base_UV": [0,0], "size_UV":
[8,8], "step_UV": [8,0], "frames_per_second": 12, "max_frame": 8, "loop": true }`, and
`minecraft:particle_appearance_tinting`. Parameters are passed from script:

```ts
const vars = new MolangVariableMap();
vars.setFloat('variable.count', tier === 'mega' ? 60 : 20);
vars.setColorRGB('variable.tint', { red: 1, green: 0.85, blue: 0.2 });
player.dimension.spawnParticle('burmaldaholic:coin_burst', at, vars);   // all nearby
player.spawnParticle('burmaldaholic:sparkle', at, vars);                 // only this player
```
Particles are client-simulated, so one call equals one packet regardless of particle count.
Budget the count per player (§7).

### 3.5 Sounds (Bedrock)

RP `sounds/sound_definitions.json` (format "1.20.20", already used by `lastchance`) points to
`.ogg` files in `RP/sounds/burmaldaholic/...`. Use `"category": "ui"` or `"player"`,
`"load_on_low_memory": true` for short effects, and `"stream": true` for loops.
`player.playSound(id, { pitch, volume })` is per player; `dimension.playSound(id, loc, {
pitch, volume })` is heard by everyone nearby. Pitch ramps are several calls at increasing
pitch. There is no continuous pitch control, so for a whirr use a looped ambient sound and
stop it with `player.runCommand('stopsound @s burmaldaholic.slots.whirr')`. Keep Bedrock sound
ids identical to the Java event names to share the sound design.

### 3.6 Titles, subtitles, actionbar

`player.onScreenDisplay.setTitle(title, { fadeInDuration, stayDuration, fadeOutDuration,
subtitle })` (all ticks), `updateSubtitle(...)` (change the subtitle of the current title
without re-fading, which makes a good roll-up counter), and `setActionBar(...)`. These are
text-only, but combined with **font glyph sprites** they carry symbols and even pixel art.
Existing rule: `PVP.md` says to keep actionbar updates at least 2 ticks apart per player.

Roll-up via `updateSubtitle`: set the title "BIG WIN" once, then call `updateSubtitle` every 2
ticks with the rolled-up amount (`fmt(n)`) for about 2 s. That is about 20 packets. It is cheap
and very effective.

### 3.7 Camera (Bedrock)

Stable in 2.8.0 typings: `player.camera.fade({ fadeTime: { fadeInTime, holdTime, fadeOutTime },
fadeColor })`, `setCamera('minecraft:free', { location, facingLocation | facingEntity,
easeOptions: { easeTime, easeType: EasingType.InOutSine } })`, `playAnimation(CatmullRomSpline |
LinearSpline, options)` (a spline fly-through), and `clear()`. Shake is only available through
the command: `player.runCommand('camerashake add @s 0.25 0.6 positional')` (intensity,
seconds, type `positional` or `rotational`).

Uses:
- Jackpot: fade to gold-white at 30% for 4 ticks, a short eased `minecraft:free` push-in towards
  the cabinet for 20 ticks, then `clear()`.
- Craps: shake for 0.3 s when the dice hit.
- Roulette: an optional "ball cam" looking down at the wheel entity during the last 2 s.

Always provide an opt-out (§8), because forced camera moves are the most motion-sickness-prone
effect. Never move the camera while a form is open, because it is hidden behind the form anyway.

### 3.8 Forms: classic, DDUI, JSON UI

- **Classic** `ActionFormData` gives `title`, `body`, `button(text, iconPath)`, `header`,
  `label` and `divider`. `iconPath` accepts any RP texture path
  (`textures/ui/burmaldaholic/spin`), so image buttons work without JSON UI. A form is a
  snapshot and **cannot animate**; it must be closed and reopened to update, which flickers.
- **DDUI `CustomForm` / `MessageBox`** (server-ui 2.1.0, stable; already documented in
  `docs/architecture/bedrock.md` §"Live-updating UI"). Components are bound to `ObservableString`,
  `ObservableUIRawMessage`, `ObservableNumber` or `ObservableBoolean`. `setData()` updates the
  open form in place, so **animated reels inside a form are possible**: a label whose raw
  message is three rows of symbol glyphs, updated every 2–3 ticks from `system.runInterval`,
  plus a button bound to an Observable label ("SPIN" → "STOP"). This is the best Bedrock answer
  for a slot "screen". Unknowns: the maximum update rate before the client coalesces updates,
  and whether DDUI respects resource-pack JSON UI styling. It is Ore UI based, so probably
  **not** **(verify)**. Build a spike first.
- **JSON UI** (RP `ui/server_form.json`, `ui/hud_screen.json`, and new files registered in
  `ui/_ui_defs.json`). It is unofficial and undocumented, and Mojang has changed it
  between versions (and it is being replaced by Ore UI), but it is still widely used in
  1.21–1.26 packs. What is feasible:
  - **Custom form skins**: branch in `server_form.json` on the form title. The common trick is
    a sentinel prefix in the title (`§b§u§r` or a private-use glyph) that selects a custom
    `long_form` panel with a nine-slice background (`"nineslice_size"` in
    `textures/ui/*.json`), custom button templates with textures for default, hover and
    pressed, and the body text shown at a larger scale. The project already plans this pattern
    for the HUD (`UI.md` §HUD: sentinel-prefixed titles).
  - **JSON UI animations**: `"anims"` on a control with `anim_type` of `alpha`, `offset`,
    `size`, `color`, `flip_book` (`fps`, `frame_count`, `frame_step`, `reversible`,
    `looping`), `uv`, `clip` and `wait`, each with `easing` such as `in_out_sine` or
    `out_bounce`. These are client-side and loop continuously. Uses include marquee bulbs
    around a styled form, a pulsing SPIN button, and a flipbook shimmer on a "BIG WIN" banner.
    They cannot be triggered from script except by the form's content (title or body
    bindings), so drive them by *which form* or *which title text* is shown.
  - **HUD panel**: `hud_screen.json` hooks title text with a sentinel prefix to render a
    persistent custom panel (the balance HUD in `UI.md`). The same technique can show a
    mini-reel strip or a jackpot ticker with JSON UI animations.
  - Risk: JSON UI can break on any game update. Keep every JSON UI enhancement **optional**,
    with the plain form or actionbar as the functional baseline. The project already requires
    this for the HUD.
- **Font glyph sprites**: `RP/font/glyph_E2.png` is already used for slot symbols (U+E200…).
  The sheet is a 16×16 grid; HD sheets (512 or 1024 px) give 32–64 px glyphs. Glyphs cannot
  animate by themselves, but **alternate glyph sets** do: `U+E2xx` for normal, `U+E3xx` for
  "glowing/win", and `U+E4xx` for "motion-blur" versions. Swapping codepoints per frame makes
  symbols blink or blur in forms, the actionbar and titles. Add a separate sheet for reel frame
  pieces so a text line can draw a cabinet border.

### 3.9 Blocks and item frames

- **Flipbook block textures** (RP `textures/flipbook_textures.json`: `flipbook_texture`,
  `atlas_tile`, `ticks_per_frame`, `blend_frames`) for always-on marquee lights.
- **Permutations**: add a state such as `burmaldaholic:lit` (bool) or `burmaldaholic:win`
  (enum) to the machine blocks, with `permutations` switching `minecraft:material_instances`
  or geometry `bone_visibility`. Use `block.setPermutation(block.permutation.withState(...))`
  from script. This changes state, not motion, and costs about one packet per change.
- Block geometry `bone_visibility` can use block state Molang, but block bones **cannot rotate
  over time**. Motion needs an entity.
- **Item frames / glow item frames** can show a symbol item; rotation steps are 45° with no
  interpolation. Not recommended for animation.

### 3.10 Stable vs experimental (1.26.30)

- **Stable and usable now** (typings 2.8.0 / 2.1.0 with no `@beta`): entity `playAnimation`,
  `setProperty`, `Player.setPropertyOverrideForEntity`, `spawnParticle` with
  `MolangVariableMap`, `playSound`, `onScreenDisplay.*` including `setHudVisibility` and
  `hideAllExcept`, `camera.fade`, `setCamera`, `playAnimation`, `clear`, server-ui
  `CustomForm`, `MessageBox` and Observables, and `ActionFormData.header`, `label` and
  `divider`.
  Resource pack entities, animations, controllers, render controllers, particles, sounds,
  flipbooks, fonts and attachables are long-stable formats.
- **Not an official API / fragile**: JSON UI restyling, and hijacking HUD titles.
- **Experimental / beta** (avoid unless an experiment toggle is accepted): anything only in
  `@minecraft/server` `-beta` packages, "Upcoming Creator Features" items, and custom camera
  presets in BP `cameras/presets` beyond `minecraft:free`, `first_person`, `third_person`,
  `third_person_front` and `fixed_boom` **(verify per release notes of 1.26.30)**. Use the
  built-in presets only.

---

## 4. Current implementation audit (2026-09-24)

| Game | Java today | Bedrock today | Gaps |
|---|---|---|---|
| Slots (3×3, 5 lines) | `SlotMachineScreen`: scissored reels, items drawn at 2×, random filler strip, **constant 25 cells/s scroll with no accel or decel**, **hard snap** to result at fixed 50/75/100% times, `slot_spin` sound pitch rising per stop, flashing payline rails, vanilla sounds for wins | `slots/index.ts`: actionbar glyph grid, **noise redrawn every 3 ticks** (no strip continuity: symbols teleport instead of scrolling), `random.click` on stops, `random.levelup`/`orb` on wins | No easing or bounce; no win-cell highlight or pulse; no roll-up; no big-win tiers; no anticipation; no skip; no in-world reels for spectators; no custom audio; Bedrock has no form-level animation (DDUI unused) |
| Roulette | `RouletteScreen`: wheel **strip** animation using `SpinAnimation` (cubic ease-out) | actionbar number strip in wheel order (`spinFrames`, quadratic ease-out), `wheelWindow` | No circular wheel or ball in either edition; no in-world wheel on the table block; no ball-bounce sound (clack-clack slowing) |
| Wheel of fortune | `WheelScreen`: rotated fills, cubic ease-out, pointer | actionbar segment names | No pointer "tick" bounce against pegs; no tick sounds; no in-world wheel; no win flash |
| Plinko | `PlinkoScreen`: ball follows drawn path with a sin hop per row | actionbar arrows "◀ ▶" | No peg flash or sounds per bounce; no slot highlight; no in-world board |
| Cards (BJ, baccarat, poker, UTH) | Screens draw cards with `fill`; `BlackjackDealerRenderer` and `BaccaratDealerRenderer` are dealer NPCs | text and actionbar; `baccarat.revealTicks` squeeze timing exists | No deal slide, flip or squeeze animation; no cards on felt in-world |
| Craps / dice | `CrapsScreen`, `DiceScreen` | actionbar | No tumble; no shake on impact |
| Sounds | all `sounds.json` entries alias vanilla events; **no .ogg in repo** | `lastchance` sound defs reuse vanilla orb | No casino sound palette |
| Blocks | static textures, no `.mcmeta` animation anywhere | static; no flipbooks | Marquee lights are an easy win |
| Particles | used only by chaos, VIP, loan and last-chance (vanilla types) | same | No casino particle set |

---

## 5. Modern video-slot design, mapped to both editions

Current machine: 3 reels × 3 rows, up to 5 lines, 12 symbols including WILD and chaos
specials (creeper, TNT, pearl, clock, star or jackpot). The features below are listed in the
order that most improves entertainment for the effort involved. **Each feature changes the
math**, so RTP must be re-simulated in `SlotRtp` / `rtp.ts` (already in place) and kept at or
below the configured target.

### 5.1 Layouts: 3×3 lines → 5×3 "243 ways"
- *Design*: 5 reels × 3 rows; a win is any left-to-right run of 3+ matching symbols on adjacent
  reels, in any row (3^5 = 243 ways). Pay = symbol pay × number of ways. This reads as
  "something always almost happens" and gives frequent small wins, which is the modern norm.
- *Recommendation*: keep the 3×3 copper machine (classic fruit), and make gold and netherite
  **5×3, 243 ways**. That gives variety and keeps the retro option.
- *Java*: the screen widens to 5 reels (sprites at 32 px give 160 px of reels, which fits the
  GUI width). Win presentation cycles through winning *symbols* (all positions) rather than
  lines.
- *Bedrock*: 5 glyphs per row in a DDUI label (fine); in-world entity with 5 reel bones and 5
  int properties.

### 5.2 Wilds and expanding wilds
- *Design*: WILD substitutes for regular symbols. **Expanding wild**: a wild landing on reel 2,
  3 or 4 grows to cover the whole reel before evaluation (applied by the engine, so it is part
  of the outcome, not a visual trick).
- *Animation*: the reel stops, the wild pulses once, then a vertical "grow" (scale Y from 1/3 to
  1 over 300 ms with outBack) covers the column, with a whoosh and sparkle particles.
- *Java*: a sprite-scaled column drawn on a higher stratum. *Bedrock*: a glyph swap of the
  column to the wild glyph, 2 frames in; in-world, a `wild_expand_N` bone animation or column
  overlay bone visibility.

### 5.3 Scatters and free spins with multipliers and retriggers
- *Design*: the scatter (reuse ★ star, or add a "casino chip" scatter) pays anywhere. 3+
  scatters trigger 8/12/20 free spins at the current bet, with a multiplier (×2, or a
  progressive ×1→×2→×3 per win). 3 scatters during free spins retrigger +5. The free-spin
  session is a **server state machine** (the engine draws each spin; the client animates each
  one like a normal spin, with a special theme).
- *Animation*: scatter lands get an escalating pitch sting (note 1, 2, 3); on trigger, a
  "FREE SPINS" banner, counter plates ("Spins left 7", "Multiplier ×3"), a different reel
  background (night, gold), and a music loop change. The end summary shows a total win roll-up.
- *Java*: theme swap = a different sprite set and background with a nine-slice frame colour;
  banner = a stratum overlay with blur behind. *Bedrock*: a DDUI title and labels bound to
  Observables (`spinsLeft`, `mult`); JSON UI skin variant by title sentinel; `setTitle` banner;
  a looped `burmaldaholic.slots.fs_music` (stream) stopped with `stopsound`.
- *Economics*: free spins must be paid from the same RTP budget, and auto-spin through free
  spins should be allowed (skip-friendly).

### 5.4 Pick bonus
- *Design*: 3 "bonus" symbols on reels 1, 3 and 5 open a pick game (pick 3 of 12 chests; each
  reveals a credit prize, "+1 pick" or "collect"). **All prizes are drawn server-side at
  trigger time** (a pre-shuffled board). The pick only reveals and must not change the EV. This
  is a regulatory norm, and the project's "draw first" rule already requires it.
- *Java*: a grid of chest sprites; the pick plays an open animation (sprite flipbook, 6 frames),
  a coin particle pop and a prize roll-up. Unpicked prizes are revealed dimmed at the end.
- *Bedrock*: an `ActionFormData` with image buttons (`button(label, 'textures/ui/burmaldaholic/chest')`),
  reopened after each pick; or DDUI with observable button labels (no reopen flicker). This is
  a natural fit for forms.

### 5.5 Hold & Spin (respins)
- *Design*: 6+ "coin" symbols (each carrying a credit value or MINI/MINOR/MAJOR/GRAND) trigger
  3 respins. Coins lock; only empty cells spin; each new coin resets the counter to 3. Fill all
  15 cells for GRAND. This is extremely engaging (the "Lightning Link" style), and the whole
  sequence is drawn up front or per respin on the server.
- *Java*: per-cell mini-reels (each cell is a scissored 1-cell reel); locked coins get a glow
  sprite with a `.mcmeta` animation; the counter plate shows 3 dots; a "reset" flash on a new
  coin.
- *Bedrock*: DDUI label grid (glyphs, with coin-value glyphs or short numbers under them); an
  in-world entity with per-cell visibility driven by a 15-bit int property (`locked_mask`).
- *Jackpot tie-in*: map MINI/MINOR/MAJOR to fixed multiples and GRAND to the existing
  progressive `JackpotPool`.

### 5.6 Cascading (tumbling) reels
- *Design*: winning symbols explode and disappear, symbols above fall into place, new ones drop
  in, and wins re-evaluate; often with a rising multiplier per cascade (×1, ×2, ×3, ×5). The
  server draws the whole cascade chain at spin time (a list of grids) and the client plays the
  steps.
- *Java*: per-cell y-offset tween with gravity (`y = y0 + ½gt²` clamped, then an outBounce
  land); an explode sprite flipbook plus particles.
- *Bedrock*: in forms, "falls" are glyph rows shifting down one row per 2-tick frame, which is
  acceptable; in-world this needs per-cell bones, and it is **hard, not recommended** for the
  entity version.
- Best candidate for a *separate* netherite-tier machine ("Nether Tumble").

### 5.7 Big-win celebration tiers
Tiers are measured as total win ÷ total bet:
| Tier | Threshold | Java | Bedrock |
|---|---|---|---|
| Win | >0 | cells pulse, roll-up 0.6–1.2 s, coin tick | glyph blink, actionbar amount, `orb` sound |
| Big Win | ≥10× | banner overlay + blur, 3 s roll-up, coin particles, fanfare | `setTitle("BIG WIN")` + `updateSubtitle` roll-up, `coin_burst` particle, fanfare |
| Mega Win | ≥25× | + screen flash, confetti, longer roll-up (5 s), tier "level-up" as the counter passes 25× | + `camera.fade` gold flash, confetti particles, `dimension.playSound` so neighbours hear it |
| Epic / Jackpot | ≥50× / progressive | + world shake (reduce-motion aware), server-wide toast, fireworks at the cabinet | + `camerashake`, world broadcast, firework particles, optional camera push-in |

The roll-up passes each tier *during* the count (the "BIG → MEGA → EPIC" upgrade moment), which
is the key excitement beat. Tiers are text from lang keys, rendered with styled fonts (§8).

### 5.8 Win counters and roll-ups
- Duration scales with the win: `d = clamp(0.6 s + 0.9 s × log10(1 + win/bet), 0.6, 8 s)`.
- Value: `shown = win × Ease.outCubic(t)` (fast start, gentle end), always ending exactly at
  `win`. Ticks: play a "coin tick" every time the shown value crosses a step, with pitch rising
  by 1% per step and capped. The tick rate is capped at about 15/s.
- Click, Space or Enter **skips to the end** (see §5.10).
- Bedrock: `updateSubtitle` every 2 ticks, or a DDUI Observable label every 2 ticks.

### 5.9 Near-miss anticipation (honest)
- *Allowed*: if the **actual** outcome already has 2 scatters (or bonus symbols) visible on
  earlier reels, the remaining reels **slow down** (extra 0.8–1.5 s each), get a glow frame and
  a tension whirr (a rising-pitch loop), and the last scatter lands with a sting *if it is
  really there*. The slowdown is triggered by what has already landed, which is information the
  player can see, and the result was fixed before any of it.
- *Not allowed*: engineering the RNG or reel strips so that near-misses happen more often than
  chance ("stops just above the payline" weighting); showing a scatter scrolling past the
  window at the moment of stopping when the real reel data would not put it there; any
  animation that shows a *different* result from the one that is paid. Filler symbols in the
  blur strip must come from the real reel distribution, and the last 3 cells must be the real
  outcome.
- Implementation: `anticipationPlan(outcome) → stopTimes[]` is a pure function of the
  already-decided outcome. Unit-test it: for random outcomes, the paid grid equals the shown
  grid, and anticipation triggers only when the scatter count on earlier reels is at least 2.
  Note this in `GAME_DESIGN.md` as policy. (Several jurisdictions, such as UKGC RTS 14 and
  Australian standards, restrict misleading near-miss displays; this mod is not real-money, but
  the ethic still applies.)

### 5.10 Skip and turbo
- **Skip / slam stop**: pressing SPIN again (or Space) during a spin brings all reels to a stop
  in 200 ms, keeping the bounce. During a roll-up it jumps to the final amount. It never skips
  the *result*; it only compresses time.
- **Turbo** toggle: a spin time multiplier of 0.5 (config `slots.turboAllowed`, and a per-player
  preference).
- Auto-spin already exists; show a spins-left counter and stop on feature or big win.
- Bedrock: DDUI "STOP" button bound during the spin (the observable label changes), or sneak to
  skip when the in-world machine is used.

### 5.11 Sound design pattern for slots
- A **pentatonic or major-scale ladder**: reel stops play notes 1-3-5 (on 5 reels: 1-2-3-5-8),
  scatter stings climb a separate higher ladder, and wins resolve on the tonic. Consistent
  pitches make the whole machine feel musical.
- Layers: spin loop (a quiet mechanical whirr), per-reel stop clunk plus note, win "chime" whose
  length scales with the tier, a coin-tick roll-up, a big-win fanfare (a 3-stage stem that
  upgrades with the tier), and free-spin music (separate stream).
- Loss is **silent**, with no sad sound. Never celebrate wins smaller than the bet as wins (the
  "loss disguised as win" problem): if total win < total bet, use a muted "return" tick, no
  fanfare, and roll-up label text "Returned 40" rather than "WIN 40". This is an ethical design
  choice and recommended as policy.
- Mix: mono, peaks at -3 dBFS, short tails. Everything is under the mod's sound category
  (§8).

### 5.12 Other entertaining touches
- **Lever pull** on in-world cabinets (sneak-use or right-click the lever face): a lever bone
  animation plus a clunk.
- **Symbol landing personality**: the creeper symbol hisses and swells when it lands, TNT
  flickers white, pearl sparkles purple, the clock ticks. These are 200–400 ms micro-animations
  per special symbol.
- **Attract mode**: idle cabinets cycle marquee lights (a free `.mcmeta` or flipbook) and
  occasionally show a demo "win flash" when no one is playing within 8 blocks. Cosmetic only;
  no fake win amounts.
- **Jackpot ticker**: an in-world `TextDisplay` (Java) or name-tag or entity (Bedrock) above
  progressive machines showing the live pool, updated at most once per second.

---

## 6. Per-game animation plans (both editions)

| Game | Java screen | Java in-world (BER) | Bedrock form or HUD | Bedrock in-world (entity) |
|---|---|---|---|---|
| Slots | §2.4 reels, win pulse, roll-up, tiers, anticipation | cabinet reels via BER from `SpinSync` | DDUI glyph reels + observable counter; JSON UI skin optional | `slot_reels` entity (bones or UV scroll), properties, lever |
| Roulette | circular wheel (rotated sprite wedges or one rotating 256 px wheel sprite drawn with `pose().rotate`), ball orbit with radius shrink and bounce "hops" in the last 20% (`abs(sin)` × decaying amplitude), pocket highlight | wheel + ball on the table block | actionbar strip (keep) + title result; DDUI live strip | wheel entity (orbit bone) |
| Wheel of fortune | peg ticks: when a segment boundary passes the pointer, deflect the pointer by 12° with a spring back (`outElastic`) and play a tick with pitch rising as it slows; win segment glow | wheel on block face | DDUI segment name + tick sounds | wheel bone, `result` property |
| Plinko | peg flash on contact, a small squash of the ball on each bounce, a slot "light-up" at the bottom, and a multiplier label pop | board with ball | DDUI row-by-row glyph board (12 rows of dots) | ball bone via path bits |
| Blackjack / baccarat / UTH / poker | cards as sprites: deal slide from the shoe (outCubic, 180 ms, rotation −8°→0°), flip via X-scale 1→0→1 with face swap at 0, baccarat squeeze (reveal the face from the corner with a scissor that grows), chip stacks sliding to the winner | cards on felt, dealer hand bone (existing dealer renderers) | glyph cards (face glyph sheet: 52 + back) in DDUI labels, flip = back glyph → face glyph after 3 ticks | per-seat card entity, `deal_N`/`flip_N` animations |
| Craps / dice | 3D-looking dice: 6-frame tumble sprite flipbook, shake on impact | dice ItemDisplay or BER | glyph dice faces cycling then landing | dice entity tumble + `camerashake` |
| Scratch cards | scratch with mouse: a per-cell mask texture (`DynamicTexture` updated on drag), foil particles, 70% auto-reveal | – | form buttons reveal cells one by one (image buttons with a foil texture) | – |
| Coin flip | vertical flip via Y-scale oscillation with speed decay, landing bounce | – | glyph heads/tails alternating, slowing | coin attachable spin |

---

## 7. Performance budgets

**Java client**
- Screens: under 300 blits per frame is trivially fine. Avoid `g.item()` for reels (a full item
  render per symbol); sprites are far cheaper. No allocation per frame in `extractRenderState`
  (reuse arrays).
- BER: `getViewDistance()` of 32 for slots and 48 for roulette. Skip the animation math when
  `sync == null || now > end + 40`, and draw the static result instead. Target at most 20
  animated BEs visible in a casino hall, with a per-BE cost under 0.05 ms.
- Particles: at most 60 per big-win burst and at most 150 for a jackpot, client-side. Vanilla
  caps at 16,384 in total.

**Java server / network**
- BER sync: 1 block-update packet per spin (plus 1 at the end if the state flag changes). This
  is negligible.
- Display entities: each keyframe is one `ClientboundSetEntityDataPacket` per tracking player.
  Budget at most 5 display entities per celebration, keyframes at least 3 ticks apart, and
  despawn after at most 3 s. Never keep a permanent spinning display on a timer when a BER can
  do it.
- `sendParticles`: at most 3 calls per event (use `count`).

**Bedrock server (Script API)**
- Script tick budget: the watchdog warns at about 100 ms of script time. Keep per-tick casino
  work under 2 ms. Animation loops use one `runInterval` per *table*, not per player per frame.
- Entities: **at most 1 animated entity per machine or table** (plus 1 per seat for cards). Keep
  them AI-free. At most about 40 casino entities per loaded area. Despawn or disable spectator
  visuals beyond 24 blocks (animation controllers can check `q.distance_from_camera` for LOD, so
  a far entity can skip bone animation).
- Properties: `setProperty` sends an actor-data update. Batch updates in the same tick (the
  engine coalesces changes per tick **(verify)**), and set at most about 5 properties per spin.
- Actionbar/title: at least 2 ticks apart per player (existing rule), and at most 1 channel per
  player at a time (the existing `ctx.hud` priority system).
- DDUI Observables: update at most every 2 ticks, and measure. Stop the interval as soon as the
  form closes (`isShowing()`).
- Particles: 1 `spawnParticle` call per effect. Count is Molang-driven, with at most 60
  particles per burst and a player-local LOD via `player.spawnParticle` for private effects.

**Bedrock client**
- Entity geometry: at most about 400 cubes per model, and texture at most 256×256 per entity
  (card atlas 512×512). Render-controller `uv_anim` is cheaper than bone rotation.

---

## 8. Accessibility and localization

**Settings** (Java: `CasinoConfigScreen` client section; Bedrock: per-player dynamic property
edited through the settings form):
- `anim.reduceMotion` (off by default): disables camera moves or shake, screen shake, the
  blur-behind, bounce overshoot and the wheel ball hop; replaces spins with a short 300 ms
  cross-fade; the roll-up becomes instant.
- `anim.flashes` (on by default; forced off by reduceMotion): full-screen flashes are capped at
  3 per second and 30% alpha maximum, even when on (the WCAG 2.3.1 "three flashes" guideline).
  No red strobe.
- `anim.speed` (0.5 / 1 / 1.5 = turbo) and a **Skip** button everywhere (§5.10).
- Sound: all casino sounds go through **one category** so players can mix them. Java: the vanilla
  `SoundSource` enum is closed, so use `SoundSource.BLOCKS` for in-world sounds and
  `SoundSource.MASTER` or `UI` for screen sounds **(verify a `UI` source exists in 26.x)**, plus
  a mod-level `anim.volume` multiplier applied to `SimpleSoundInstance` volume. Bedrock: category
  `ui` or `player` in `sound_definitions.json`, plus an `anim.volume` factor passed in
  `playSound` options.
- Colour: never rely only on red/green for win or lose; add icons and text. Payline colours in
  `LINE_COLORS` should be distinguishable under deuteranopia (add a dash pattern or line number
  labels).
- Subtitles: every new sound event gets a `subtitle` key (Java already does this).

**Localization**
- **No text in textures.** "BIG WIN", "FREE SPINS", "JACKPOT" and "×3" are rendered from lang
  keys (both RU and EN are required by `LOCALIZATION.md` / `STRINGS.md`). For fancy banner
  typography use a **custom font**: Java `assets/burmaldaholic/font/banner.json` bitmap provider
  (Latin + Cyrillic glyph sheet) applied through `Style.withFont(...)`; Bedrock via glyph
  sprites for digits and symbols only, with words in the default font. Numbers use the existing
  `Texts.chips` / `fmt` helpers.
- Textures may contain only symbols, digits (as glyph sheets) and decoration.
- Leave room for long words: Russian strings are about 30% longer. Banners should be
  nine-slice panels sized to the text width, never fixed-width art.
- Glyph codepoint map: document `U+E2xx` (slots) and new sheets (cards `U+E5xx`, dice `U+E6xx`,
  UI pieces `U+E7xx`) in `UI.md`, and keep them in sync with Java sprite ids.

---

## 9. Suggested config keys, strings and order of work

Config (`CONFIG.md` candidates): `slots.turboAllowed` (bool, true), `slots.anticipation` (bool,
true), `slots.bigWinTiers` (list, [10,25,50]), `anim.inWorld` (bool, true: spectator
entities/BER), `anim.inWorldRadius` (int, 24), and client/per-player `anim.reduceMotion`,
`anim.flashes`, `anim.speed` and `anim.volume`.

Order of work (most value first):
1. **Slots feel pass in both editions**: easing, bounce, stagger, win pulse, roll-up, skip,
   big-win tiers, a custom sound palette (placeholder .ogg set), and a honest anticipation
   plan. Bedrock: switch the slot screen to a DDUI `CustomForm` with Observables (spike first)
   and continuous strip scrolling instead of noise.
2. **Sprites instead of item renders** (Java) and **HD glyph sheets with win and blur
   variants** (Bedrock); marquee `.mcmeta` and flipbooks on all machine blocks.
3. **In-world spectator visuals**: a Java BER for slot cabinets, the roulette wheel and the
   wheel of fortune; Bedrock entities with properties and animations for the same.
4. **New slot features**: 5×3 / 243 ways on gold, free spins with multipliers, then hold &
   spin on netherite (each with an RTP simulation and tests).
5. Cards (deal, flip, squeeze), dice tumble, plinko peg effects.
6. Optional polish: a GUI shimmer shader (Java), JSON UI form skins (Bedrock), camera
   jackpot moves.

---

## 10. Open questions

1. DDUI `CustomForm` update-rate limits and whether RP JSON UI can style it. Needs a spike on
   1.26.30.
2. Should spectators see *other players'* slot results in-world before the player's own GUI
   reveals them? Recommend syncing the BER or entity state so it lands at the same time as the
   GUI, or using `setPropertyOverrideForEntity` on Bedrock.
3. Is the "loss disguised as win" policy (§5.11) accepted by the designer?
4. Custom audio: who produces the .ogg set (license: CC0 or commissioned)? Until then, keep the
   vanilla aliases, but keep the event names final.
5. Do 5×3 machines fit the Java GUI at GUI scale 4 on 1080p? Needs a layout check (the reel area
   is about 170 px wide).
