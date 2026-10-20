// Block loot tables readable by BOTH supported Minecraft versions (MT, R3).
//
// 26.3 changed the loot predicate format and silently ignores the 26.2 one (unknown keys are skipped, the table still
// loads):
//   26.2  pool / entry  "conditions": [ { "condition": "minecraft:survives_explosion" }, … ]
//         state test    { "condition": "minecraft:block_state_property", "block": id, "properties": { … } }
//   26.3  pool / entry  "condition": { "type": "minecraft:survives_explosion" }   (one predicate; all_of for more)
//         state test    { "type": "minecraft:match_block", "blocks": id, "state": { … } }
// A table written in one format therefore drops on every explosion (and from every part) on the other version. Each
// version's codec ignores the other's keys, so every pool / entry here carries BOTH: `conditions` (26.2) and
// `condition` (26.3). Proven on both versions by the GameTest `multi_block_loot_*` (MultiBlockTableGameTests).
// Pure.

/** One controller item (part main / single only) that survives explosions with the vanilla 1 / radius chance. */
export function controllerLoot(name) {
  const id = `burmaldaholic:${name}`;
  const parts = ['main', 'single'];
  return {
    type: 'minecraft:block',
    pools: [
      {
        rolls: 1.0,
        conditions: [{ condition: 'minecraft:survives_explosion' }],
        condition: { type: 'minecraft:survives_explosion' },
        entries: [
          {
            type: 'minecraft:item',
            name: id,
            conditions: [
              { condition: 'minecraft:any_of', terms: parts.map((part) => ({ condition: 'minecraft:block_state_property', block: id, properties: { part } })) },
            ],
            condition: { type: 'minecraft:any_of', terms: parts.map((part) => ({ type: 'minecraft:match_block', blocks: id, state: { part } })) },
          },
        ],
      },
    ],
    random_sequence: `burmaldaholic:blocks/${name}`,
  };
}
