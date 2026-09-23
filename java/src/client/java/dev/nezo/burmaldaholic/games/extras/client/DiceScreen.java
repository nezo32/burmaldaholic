package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

/**
 * Dice Duel screen (UI.md §9): duel the house (bet selector incl. pawn stakes, [Roll]); challenge a nearby
 * player (target toggle + chip amount); pending incoming challenges with [Accept] / [Decline]. The dice roll
 * animation (20 ticks) is cosmetic.
 */
final class DiceScreen extends ExtrasScreen {
	private static final int ROLL_TICKS = 20;
	private static final int DIE = 18;
	private static long lastAmount = 1;

	private record Line(Component text, int y, int color) {}

	private final BetSelector bet;
	private final List<Line> lines = new ArrayList<>();
	private String target = "";
	private int shownSeq;
	private int animStart = -1000;
	private int diceY;

	DiceScreen(CompoundTag state) {
		super("dice", Component.translatable("gui.burmaldaholic.extras.dice.title"), state, 260, 200);
		this.bet = new BetSelector(true, lastAmount, this::rebuildWidgets);
		this.target = state.getStringOr("target", "");
		this.shownSeq = state.getCompoundOrEmpty("result").getIntOr("seq", -1);
	}

	@Override
	protected void onStateChanged(CompoundTag oldState, CompoundTag newState) {
		int seq = newState.getCompoundOrEmpty("result").getIntOr("seq", -1);
		if (seq >= 0 && seq != shownSeq) {
			shownSeq = seq;
			animStart = ticks;
		}
		String t = newState.getStringOr("target", "");
		if (!t.isEmpty()) {
			target = t;
		}
	}

	private boolean rolling() {
		return ticks - animStart < ROLL_TICKS;
	}

	private int line(Component text, int y, int color) {
		lines.add(new Line(text, y, color));
		return y + wrappedHeight(text, panelWidth - 2 * PAD);
	}

	@Override
	protected void layout() {
		lines.clear();
		CompoundTag s = state();
		int y = top + 20;
		y = line(Component.translatable("gui.burmaldaholic.extras.dice.rules"), y, MUTED) + 4;
		diceY = y;
		y += DIE + 26;
		// vs house
		y = line(Component.translatable("gui.burmaldaholic.extras.dice.vs_house").withStyle(ChatFormatting.BOLD), y, GOLD) + 2;
		y = line(bet.line(s), y, TEXT) + 2;
		Flow flow = new Flow(font, this::addRenderableWidget, left + PAD, y, panelWidth - 2 * PAD);
		bet.build(flow, s);
		flow.button(Component.translatable("gui.burmaldaholic.extras.dice.roll"), 50, b -> {
			lastAmount = Math.max(1, bet.amount());
			send("roll", bet.args());
		}).active = !rolling();
		y = flow.bottom() + 4;
		if (s.getBooleanOr("pvp", false)) {
			y = line(Component.translatable("gui.burmaldaholic.extras.dice.challenge_player").withStyle(ChatFormatting.BOLD), y, GOLD) + 2;
			ListTag nearby = s.getListOrEmpty("nearby");
			CompoundTag outgoing = s.getCompoundOrEmpty("outgoing");
			if (!outgoing.isEmpty()) {
				y = line(Component.translatable("msg.burmaldaholic.extras.dice.challenge_sent", Texts.raw(outgoing.getStringOr("name", "")),
					Texts.chips(outgoing.getLongOr("stake", 0))), y, MUTED) + 2;
			} else if (nearby.isEmpty()) {
				y = line(Component.translatable("gui.burmaldaholic.extras.dice.no_targets"), y, MUTED) + 2;
			} else {
				int idx = indexOf(nearby, target);
				if (idx < 0) {
					idx = 0;
					target = nearby.getCompoundOrEmpty(0).getStringOr("uuid", "");
				}
				String name = nearby.getCompoundOrEmpty(idx).getStringOr("name", "");
				Flow pf = new Flow(font, this::addRenderableWidget, left + PAD, y, panelWidth - 2 * PAD);
				int next = (idx + 1) % nearby.size();
				pf.button(Component.translatable("gui.burmaldaholic.extras.labeled", Component.translatable("gui.burmaldaholic.extras.dice.target"), Texts.raw(name)), 60, b -> {
					target = nearby.getCompoundOrEmpty(next).getStringOr("uuid", "");
					rebuildWidgets();
				});
				pf.button(Component.translatable("gui.burmaldaholic.extras.dice.challenge_amount", Texts.chipsAcc(Math.max(1, bet.amount()))), 60, b -> {
					CompoundTag args = new CompoundTag();
					args.putString("target", target);
					args.putLong("amount", Math.max(1, bet.amount()));
					send("challenge", args);
				});
				y = pf.bottom() + 2;
			}
		}
		ListTag incoming = s.getListOrEmpty("incoming");
		if (!incoming.isEmpty()) {
			y = line(Component.translatable("gui.burmaldaholic.extras.dice.pending").withStyle(ChatFormatting.BOLD), y + 2, GOLD) + 2;
			for (int i = 0; i < incoming.size(); i++) {
				CompoundTag c = incoming.getCompoundOrEmpty(i);
				int id = c.getIntOr("id", -1);
				y = line(Component.translatable("gui.burmaldaholic.extras.dice.invite", Texts.raw(c.getStringOr("name", "")), Texts.chips(c.getLongOr("stake", 0))), y, TEXT) + 1;
				Flow cf = new Flow(font, this::addRenderableWidget, left + PAD, y, panelWidth - 2 * PAD);
				cf.button(Component.translatable("gui.burmaldaholic.extras.dice.accept").withStyle(ChatFormatting.GREEN), 50, b -> answer("accept", id));
				cf.button(Component.translatable("gui.burmaldaholic.extras.dice.decline"), 50, b -> answer("decline", id));
				y = cf.bottom() + 2;
			}
		}
		fitHeight(y);
	}

	private static int indexOf(ListTag list, String uuid) {
		for (int i = 0; i < list.size(); i++) {
			if (list.getCompoundOrEmpty(i).getStringOr("uuid", "").equals(uuid)) {
				return i;
			}
		}
		return -1;
	}

	private void answer(String action, int id) {
		CompoundTag args = new CompoundTag();
		args.putInt("id", id);
		send(action, args);
	}

	@Override
	public void tick() {
		super.tick();
		if (ticks - animStart == ROLL_TICKS) {
			rebuildWidgets();
		}
		if (ticks % 20 == 0 && !state().getListOrEmpty("incoming").isEmpty()) {
			send("refresh", new CompoundTag());
		}
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int w = panelWidth - 2 * PAD;
		for (Line l : lines) {
			Art.wrap(g, font, l.text(), left + PAD, l.y(), w, l.color());
		}
		CompoundTag r = state().getCompoundOrEmpty("result");
		int[] you = r.getIntArray("you").orElse(new int[] {0, 0});
		int[] them = r.getIntArray("them").orElse(new int[] {0, 0});
		boolean anim = rolling() && r.getIntOr("seq", -1) >= 0;
		if (anim) {
			java.util.Random rnd = new java.util.Random(ticks * 31L);
			you = new int[] {1 + rnd.nextInt(6), 1 + rnd.nextInt(6)};
			them = new int[] {1 + rnd.nextInt(6), 1 + rnd.nextInt(6)};
		}
		boolean pvp = "pvp".equals(r.getStringOr("mode", "house"));
		int half = panelWidth / 2;
		Component youLabel = Component.translatable("gui.burmaldaholic.common.you");
		Component themLabel = pvp ? Texts.raw(r.getStringOr("opponent", "")) : Component.translatable("gui.burmaldaholic.extras.dice.dealer");
		g.centeredText(font, youLabel, left + half / 2, diceY, MUTED);
		g.centeredText(font, themLabel, left + half + half / 2, diceY, MUTED);
		drawPair(g, left + half / 2, diceY + 10, you);
		drawPair(g, left + half + half / 2, diceY + 10, them);
		if (!anim && r.getIntOr("seq", -1) >= 0) {
			g.centeredText(font, outcomeLine(r), left + half, diceY + DIE + 13, TEXT);
		}
	}

	private static void drawPair(GuiGraphicsExtractor g, int cx, int y, int[] faces) {
		Art.die(g, cx - DIE - 2, y, DIE, faces.length > 0 ? faces[0] : 0);
		Art.die(g, cx + 2, y, DIE, faces.length > 1 ? faces[1] : 0);
	}

	private static Component outcomeLine(CompoundTag r) {
		long net = r.getLongOr("net", 0);
		return switch (r.getStringOr("outcome", "")) {
			case "win" -> Component.translatable("gui.burmaldaholic.extras.dice.win", Texts.chips(net)).withStyle(ChatFormatting.GREEN);
			case "lose" -> Component.translatable("gui.burmaldaholic.extras.dice.lose", Texts.chipsAcc(-net)).withStyle(ChatFormatting.RED);
			case "house_tie" -> Component.translatable("gui.burmaldaholic.extras.dice.tie_seven").withStyle(ChatFormatting.RED);
			case "push", "refund" -> Component.translatable("gui.burmaldaholic.extras.dice.tie").withStyle(ChatFormatting.GRAY);
			default -> Component.empty();
		};
	}
}
