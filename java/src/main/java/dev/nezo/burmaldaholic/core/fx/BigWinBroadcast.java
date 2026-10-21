package dev.nezo.burmaldaholic.core.fx;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.anim.WinTierTable;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.CoreConfig;
import dev.nezo.burmaldaholic.core.events.CasinoEvents.PlayResult;
import dev.nezo.burmaldaholic.core.text.Texts;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Java big-win broadcast (global.md §4.7, task J8): {@code core.announceBigWins} /
 * {@code core.bigWinThreshold} existed in the config but nothing used them. Rule (§2.4): a settlement is
 * announced when its tier by the DEFAULT table on the round's return / stake is EPIC (return ≥ 50 × stake and
 * net ≥ 500, or net ≥ {@code core.bigWinThreshold}) — the same rule as the chaos big-win buff, so the three
 * always agree. Rounds a module announces itself (slots jackpots: tag {@code jackpot}), PvP and
 * non-house-banked rounds, and deferred (offline) settlements are skipped.
 */
public final class BigWinBroadcast {
	/** Font of the E1 glyph sheet ({@code assets/burmaldaholic/font/default.json}). */
	public static final FontDescription GLYPHS = new FontDescription.Resource(Burmaldaholic.id("default"));
	/** Chip glyph U+E100 (UI.md §0.1). */
	public static final String CHIP_GLYPH = "";

	private BigWinBroadcast() {}

	/** Tier of a round for broadcasts (DEFAULT table, EPIC also at net ≥ threshold). */
	public static WinTier tierOf(long payout, long bet, int bigWinThreshold) {
		return WinTier.of(payout, bet, WinTierTable.DEFAULT.withEpicNet(bigWinThreshold));
	}

	/** True when this result is announced server-wide. */
	public static boolean announces(PlayResult r, boolean enabled, int threshold) {
		if (!enabled || r.deferred() || !r.houseBanked() || r.tags().contains("jackpot") || r.net() <= 0) return false;
		return tierOf(r.payout(), r.bet(), threshold).ordinal() >= WinTier.EPIC.ordinal();
	}

	/**
	 * The chat line: chip glyph, space, translated text. The glyph is a SIBLING under an empty root, never the
	 * parent: children inherit the parent's font, and {@link #GLYPHS} maps only U+E100–E1FF, so text under it
	 * renders as tofu in every language (owner playtest B2).
	 */
	static MutableComponent line(Component playerName, long net, String gameId) {
		return Component.empty()
			.append(Texts.raw(CHIP_GLYPH).withStyle(s -> s.withFont(GLYPHS))) // literal-ok: font glyph
			.append(Texts.raw(" ")) // literal-ok: separator
			.append(Component.translatable("msg.burmaldaholic.core.big_win", playerName, Texts.chipsAcc(net),
				Component.translatable("gui.burmaldaholic.common.game." + gameId)));
	}

	static void onResolved(ServerPlayer player, PlayResult r) {
		CoreConfig cfg = CasinoConfig.core();
		if (!announces(r, cfg.announceBigWins, cfg.bigWinThreshold)) return;
		MutableComponent line = line(player.getDisplayName(), r.net(), r.gameId());
		player.level().getServer().getPlayerList().broadcastSystemMessage(line, false);
		ServerFx.get().broadcast(player, WinTier.EPIC, r.net(), r.gameId());
	}
}
