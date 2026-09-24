package dev.nezo.burmaldaholic.games.extras.client.pvp.coin;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.pvp.coin.CoinDuelNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Coin Flip Duel set-up (PVP.md §4.5, 200 × 140 and growing): opponent (the player the coin was used on, or a bot
 * Style), stake box with quick buttons, side toggle Heads | Tails, head-to-head line, [Throw down the gauntlet].
 * The server validates everything again ({@code PvpService.challenge}); errors come back as a red line.
 */
public final class CoinDuelSetupScreen extends PvpPanel {
	private static final BotDifficulty[] STYLES = {BotDifficulty.EASY, BotDifficulty.NORMAL, BotDifficulty.HARD, BotDifficulty.MIXED};
	private static long lastStake;
	private static boolean lastHeads = true;
	private static int lastStyle = 1;

	private CompoundTag state;
	private EditBox stakeBox;
	private long stake;
	private boolean heads = lastHeads;
	private int style = lastStyle;
	private int infoTop;

	public CoinDuelSetupScreen(CompoundTag state) {
		super(title(state), 220, 150);
		this.state = state;
		long min = state.getLongOr("min", 1);
		stake = clamp(lastStake > 0 ? lastStake : min);
	}

	private static Component title(CompoundTag s) {
		String name = s.getStringOr("target_name", "");
		return name.isEmpty() ? Component.translatable("gui.burmaldaholic.pvp.coin.title")
			: Component.translatable("gui.burmaldaholic.pvp.coin.setup_title", Texts.raw(name));
	}

	void accept(CompoundTag newState) {
		state = newState;
		rebuild();
	}

	private boolean vsBot() {
		return state.getStringOr("target", "").isEmpty();
	}

	private long clamp(long v) {
		long min = state.getLongOr("min", 1);
		long max = Math.max(min, state.getLongOr("max", min));
		return Math.max(min, Math.min(max, v));
	}

	private Component info() {
		String name = state.getStringOr("target_name", "");
		Component record;
		if (vsBot()) {
			record = Component.translatable("gui.burmaldaholic.bots.rules.fair");
		} else if (state.getIntOr("h2h_wins", 0) + state.getIntOr("h2h_losses", 0) == 0) {
			record = Component.translatable("gui.burmaldaholic.pvp.invite.record_none", Texts.raw(name));
		} else {
			record = Component.translatable("gui.burmaldaholic.pvp.invite.record", Texts.raw(name), Texts.number(state.getIntOr("h2h_wins", 0)),
				Texts.number(state.getIntOr("h2h_losses", 0)));
		}
		return record;
	}

	private Component limits() {
		return Component.translatable("gui.burmaldaholic.pvp.lobby.rake", percent(state.getIntOr("rake_bp", 300)));
	}

	/** Basis points → "3" / "2.5" percent, with the translated decimal separator. */
	public static Component percent(long bp) {
		String plain = bp % 100 == 0 ? Long.toString(bp / 100) : String.format(java.util.Locale.ROOT, "%.2f", bp / 100.0).replaceAll("0+$", "");
		return Component.translatable("gui.burmaldaholic.pvp.percent", Texts.decimal(plain));
	}

	@Override
	protected int layout() {
		int w = panelWidth - 2 * PAD;
		int y = top + 20;
		infoTop = y;
		y += wrappedHeight(info(), w) + 2 + font.lineHeight + 4;
		Flow flow = new Flow(y);
		if (vsBot() && state.getBooleanOr("bots", false)) {
			flow.button(Component.translatable("gui.burmaldaholic.pvp.bots.opponent", Component.translatable(STYLES[style].styleKey())), 100, b -> {
				style = (style + 1) % STYLES.length;
				lastStyle = style;
				rebuild();
			});
			flow.newRow();
		}
		int bx = flow.reserve(80);
		stakeBox = amountBox(bx, flow.y(), 80, stake);
		stakeBox.setResponder(v -> {
			String digits = v.replaceAll("\\D", "");
			if (!digits.equals(v)) {
				stakeBox.setValue(digits);
			}
			stake = parse(stakeBox, stake);
		});
		flow.button(Texts.raw("½"), 20, b -> setStake(stake / 2));
		flow.button(Texts.raw("×2"), 24, b -> setStake(stake * 2));
		flow.button(Component.translatable("gui.burmaldaholic.common.max"), 30, b -> setStake(state.getLongOr("max", 1)));
		flow.newRow();
		Component side = Component.translatable(heads ? "gui.burmaldaholic.extras.coin.heads" : "gui.burmaldaholic.extras.coin.tails");
		flow.button(Component.translatable("gui.burmaldaholic.pvp.new.side").append(": ").append(side), 80, b -> {
			heads = !heads;
			lastHeads = heads;
			rebuild();
		});
		flow.newRow();
		flow.button(Component.translatable("gui.burmaldaholic.pvp.new.submit").withStyle(ChatFormatting.BOLD), 120, b -> submit());
		flow.button(Component.translatable("gui.burmaldaholic.common.close"), 50, b -> onClose());
		return flow.bottom();
	}

	private void setStake(long v) {
		stake = clamp(v);
		if (stakeBox != null) {
			stakeBox.setValue(Long.toString(stake));
		}
	}

	private void submit() {
		stake = clamp(parse(stakeBox, stake));
		lastStake = stake;
		CompoundTag args = new CompoundTag();
		args.putLong("stake", stake);
		args.putBoolean("heads", heads);
		if (vsBot()) {
			args.putString("bot", STYLES[style].id());
		} else {
			args.putString("target", state.getStringOr("target", ""));
		}
		ClientPlayNetworking.send(new CoinDuelNet.Action("challenge", args));
	}

	@Override
	protected void content(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int w = panelWidth - 2 * PAD;
		int y = wrap(g, info(), left + PAD, infoTop, w, MUTED);
		Component line = Component.translatable("gui.burmaldaholic.pvp.new.stake").append(" · ").append(limits()).append(" · ")
			.append(Component.translatable("gui.burmaldaholic.common.balance", Texts.number(state.getLongOr("balance", 0))));
		wrap(g, line, left + PAD, y + 2, w, TEXT);
	}
}
