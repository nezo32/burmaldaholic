// UV layouts of the vanilla entity models the casino NPCs wear (DL-SPEC §3). A box at texture offset (u, v) of size
// (w, h, d) unwraps into six faces (vanilla cube UV): top/bottom on the first row, then right side, front, left side
// and back. `layer: 'overlay'` boxes (hat, jacket, sleeves …) may stay partly transparent; every 'base' face must be
// fully opaque and every pixel outside all faces fully transparent (npc-art.test.mjs).

/** The six face rectangles of a cube unwrap. */
export const faces = (u, v, w, h, d) => ({
  top: { x: u + d, y: v, w, h: d },
  bottom: { x: u + d + w, y: v, w, h: d },
  right: { x: u, y: v + d, w: d, h },
  front: { x: u + d, y: v + d, w, h },
  left: { x: u + d + w, y: v + d, w: d, h },
  back: { x: u + 2 * d + w, y: v + d, w, h },
});

const box = (name, u, v, w, h, d, layer = 'base') => ({ name, u, v, w, h, d, layer, faces: faces(u, v, w, h, d) });

const byName = (boxes) => Object.fromEntries(boxes.map((b) => [b.name, b]));

/** PlayerModel, wide arms (64 × 64): base parts and their second-layer overlays. */
export const PLAYER = {
  w: 64,
  h: 64,
  boxes: [
    box('head', 0, 0, 8, 8, 8),
    box('hat', 32, 0, 8, 8, 8, 'overlay'),
    box('body', 16, 16, 8, 12, 4),
    box('jacket', 16, 32, 8, 12, 4, 'overlay'),
    box('rightArm', 40, 16, 4, 12, 4),
    box('rightSleeve', 40, 32, 4, 12, 4, 'overlay'),
    box('leftArm', 32, 48, 4, 12, 4),
    box('leftSleeve', 48, 48, 4, 12, 4, 'overlay'),
    box('rightLeg', 0, 16, 4, 12, 4),
    box('rightPants', 0, 32, 4, 12, 4, 'overlay'),
    box('leftLeg', 16, 48, 4, 12, 4),
    box('leftPants', 0, 48, 4, 12, 4, 'overlay'),
  ],
};

/** AdultPiglinModel (64 × 64): the player body with a 10 × 8 × 8 head, snout, tusks and ears; no hat layer. */
export const PIGLIN = {
  w: 64,
  h: 64,
  boxes: [
    box('head', 0, 0, 10, 8, 8),
    box('snout', 31, 1, 4, 4, 1),
    box('tuskRight', 2, 4, 1, 2, 1),
    box('tuskLeft', 2, 0, 1, 2, 1),
    box('rightEar', 39, 6, 1, 5, 4),
    box('leftEar', 51, 6, 1, 5, 4),
    ...PLAYER.boxes.filter((b) => b.name !== 'head' && b.name !== 'hat'),
  ],
};

/** VillagerModel (64 × 64): head + nose, robe body, crossed arms, legs; hat, hat rim and the long jacket are overlays. */
export const VILLAGER = {
  w: 64,
  h: 64,
  boxes: [
    box('head', 0, 0, 8, 10, 8),
    box('hat', 32, 0, 8, 10, 8, 'overlay'),
    box('hatRim', 30, 47, 16, 16, 1, 'overlay'),
    box('nose', 24, 0, 2, 4, 2),
    box('body', 16, 20, 8, 12, 6),
    box('jacket', 0, 38, 8, 20, 6, 'overlay'),
    box('arms', 44, 22, 4, 8, 4),
    box('armsMid', 40, 38, 8, 4, 4),
    box('leg', 0, 22, 4, 12, 4),
  ],
};

/** EndermanModel (64 × 32): arms and legs share one 2 × 30 × 2 limb; the headwear box is an overlay. */
export const ENDERMAN = {
  w: 64,
  h: 32,
  boxes: [
    box('head', 0, 0, 8, 8, 8),
    box('headwear', 0, 16, 8, 8, 8, 'overlay'),
    box('body', 32, 16, 8, 12, 4),
    box('limb', 56, 0, 2, 30, 2),
  ],
};

export const parts = (model) => byName(model.boxes);

/** Every face rectangle of a model with its layer. */
export const allFaces = (model) =>
  model.boxes.flatMap((b) => Object.entries(b.faces).map(([side, r]) => ({ box: b.name, side, layer: b.layer, ...r })));
