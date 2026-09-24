#!/usr/bin/env python3
"""Generate the data-driven advancements of GAME_DESIGN.md §19 (Java edition).

Writes java/src/main/resources/data/burmaldaholic/advancement/core/<id>.json for every id. Every
advancement has a single `minecraft:impossible` criterion "granted": core's CasinoAdvancements.grant
(dev.nezo.burmaldaholic.core.advancement) awards it from module hooks. Keep ADVANCEMENTS in sync with
CasinoAdvancements.IDS and Bedrock's core/logic/achievements.ts (same ids, same lang keys).
"""
import json
import pathlib

OUT = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/data/burmaldaholic/advancement/core"

# id, parent, frame, icon
ADVANCEMENTS = [
    ("root", None, "task", "burmaldaholic:casino_card"),
    ("first_bet", "root", "task", "burmaldaholic:chip_1"),
    ("beginners_luck", "first_bet", "task", "burmaldaholic:chip_25"),
    ("natural", "beginners_luck", "task", "burmaldaholic:blackjack_table"),
    ("split_personality", "natural", "goal", "minecraft:shears"),
    ("royal_flush", "beginners_luck", "challenge", "burmaldaholic:poker_table"),
    ("shark_hunter", "beginners_luck", "goal", "minecraft:cod"),
    ("three_sevens", "beginners_luck", "goal", "minecraft:redstone"),
    ("jackpot", "three_sevens", "challenge", "burmaldaholic:slot_machine_netherite"),
    ("zero_hero", "beginners_luck", "goal", "burmaldaholic:roulette_table"),
    ("hot_shooter", "beginners_luck", "goal", "burmaldaholic:dice"),
    ("plinko_edge", "beginners_luck", "challenge", "burmaldaholic:plinko_machine"),
    ("scratch_top", "first_bet", "challenge", "burmaldaholic:scratch_card_gold"),
    ("on_fire", "beginners_luck", "goal", "minecraft:blaze_powder"),
    ("black_cat", "first_bet", "goal", "minecraft:black_dye"),
    ("loan_taken", "root", "task", "burmaldaholic:loan_ledger_page"),
    ("knock_knock", "loan_taken", "task", "burmaldaholic:loan_overdue_notice"),
    ("hostile_takeover", "knock_knock", "goal", "minecraft:iron_sword"),
    ("clean_slate", "loan_taken", "task", "minecraft:paper"),
    ("not_today", "root", "goal", "burmaldaholic:lucky_coin"),
    ("scarred", "not_today", "challenge", "minecraft:totem_of_undying"),
    ("heart_on_the_line", "first_bet", "task", "minecraft:apple"),
    ("devils_deal", "heart_on_the_line", "challenge", "minecraft:wither_skeleton_skull"),
    ("golden_hour", "root", "task", "minecraft:clock"),
    ("beam_me_up", "root", "task", "minecraft:ender_pearl"),
    ("vip_silver", "root", "task", "minecraft:iron_ingot"),
    ("vip_gold", "vip_silver", "task", "minecraft:gold_ingot"),
    ("vip_platinum", "vip_gold", "goal", "minecraft:quartz"),
    ("vip_diamond", "vip_platinum", "goal", "minecraft:diamond"),
    ("vip_netherite", "vip_diamond", "challenge", "minecraft:netherite_ingot"),
    ("the_house", "root", "goal", "burmaldaholic:casino_charter"),
    ("house_always_wins", "the_house", "challenge", "minecraft:gold_block"),
    ("bankrupt", "the_house", "task", "minecraft:barrier"),
    ("piglin_parlor", "root", "task", "minecraft:gilded_blackstone"),
    ("high_roller", "piglin_parlor", "goal", "minecraft:end_rod"),
    # 2026-09 games (§20 baccarat, §21 Ultimate Texas Hold'em)
    ("baccarat_natural", "beginners_luck", "task", "burmaldaholic:baccarat_table"),
    ("tie_streak", "baccarat_natural", "goal", "minecraft:lead"),
    ("uth_four_x", "beginners_luck", "task", "burmaldaholic:uth_table"),
    ("banco", "baccarat_natural", "goal", "burmaldaholic:baccarat_table_player_banked"),
    ("bank_holder", "banco", "challenge", "minecraft:gold_block"),
    ("uth_house_seat", "uth_four_x", "goal", "burmaldaholic:uth_table_player_banked"),
    ("uth_royal", "uth_four_x", "challenge", "burmaldaholic:uth_table_high_roller"),
    # Seats & Bots (BOTS.md §10): granted by poker / baccarat (bots module: members_only, no_robots, word_got_around)
    ("man_vs_machine", "beginners_luck", "task", "minecraft:observer"),
    ("clean_sweep", "shark_hunter", "challenge", "minecraft:brush"),
    ("short_circuit", "banco", "goal", "minecraft:lightning_rod"),
]


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for adv_id, parent, frame, icon in ADVANCEMENTS:
        display = {
            "icon": {"id": icon},
            "title": {"translate": f"advancement.burmaldaholic.{adv_id}.title"},
            "description": {"translate": f"advancement.burmaldaholic.{adv_id}.description"},
            "frame": frame,
            "show_toast": True,
            "announce_to_chat": adv_id != "root",
            "hidden": False,
        }
        data = {}
        if parent is None:
            display["background"] = "minecraft:block/green_wool"
        else:
            data["parent"] = f"burmaldaholic:core/{parent}"
        data["display"] = display
        data["criteria"] = {"granted": {"trigger": "minecraft:impossible"}}
        (OUT / f"{adv_id}.json").write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {len(ADVANCEMENTS)} advancements to {OUT}")


if __name__ == "__main__":
    main()
