package dev.nezo.burmaldaholic.pvp;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.pvp.Participant;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.PvpPresenter;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.Step;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.pvp.logic.RevealOrder;
import java.util.List;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The Java presenter (pvp-bots.md §3.6): {@link dev.nezo.burmaldaholic.pvp.net.PvpSyncPayload}s to the lobby /
 * mode / result screens, titles for the Final Reveal (PVP.md §3.11.4) and the vanilla sounds / particles of §12.
 * Players whose screen is closed see the HUD ticker (client) fed by the same payloads.
 */
public final class PvpScreensPresenter implements PvpPresenter {
	/**
	 * Lobby / invite changed. A new duel invite sends the target its clickable chat line at once (the engine
	 * only records the invite, PVP.md §3.3.1); the 10-tick scan in {@link PvpUi} is the safety net.
	 */
	@Override
	public void lobbyChanged(PvpMatch match) {
		MinecraftServer server = server(match);
		if (server == null) {
			return;
		}
		if (match.state() == MatchState.INVITED && match.invitee() != null) {
			ServerPlayer target = server.getPlayerList().getPlayer(match.invitee());
			if (target != null) {
				PvpUi.notifyInvite(server, target, match);
			}
			return;
		}
		PvpUi.pushAll(server, match, false);
	}

	@Override
	public void revealStep(PvpMatch match, Step step, List<ServerPlayer> viewers) {
		PvpUi.addStep(match.id, step);
		for (ServerPlayer p : viewers) {
			if (PvpMatchView.indexOf(match, p.getUUID()) < 0) {
				PvpUi.push(p, match, false); // spectator: HUD ticker line only
			}
		}
		MinecraftServer server = server(match);
		if (server != null) {
			PvpUi.pushAll(server, match, false);
		}
	}

	/**
	 * Final Reveal: {@code place} ≥ 2 → "#k: Name — points" under "Final results…" with a rising bell and
	 * angry-villager particles at that player; place ≤ 1 → "NAME WINS!" / "DEAD HEAT!" + payout, jingle, totem.
	 * Spectators in {@code viewers} only get the winner title.
	 */
	@Override
	public void finalReveal(PvpMatch match, int place, List<ServerPlayer> viewers) {
		PvpUi.setFinalPlace(match.id, place);
		Placed who = placed(match, place);
		int n = match.participants().size();
		boolean winnerCall = place <= 1;
		Component title;
		Component subtitle;
		if (!winnerCall) {
			title = Component.translatable("gui.burmaldaholic.pvp.match.final");
			subtitle = who == null ? null : Component.translatable("gui.burmaldaholic.pvp.match.place", Texts.number(place), who.name,
				Texts.plural("unit.burmaldaholic.point", who.points));
		} else {
			Outcome o = match.outcome();
			boolean deadHeat = o != null && o.winners().length > 1;
			if (deadHeat) {
				title = Component.translatable("gui.burmaldaholic.pvp.result.dead_heat_title");
			} else {
				title = Component.translatable("gui.burmaldaholic.pvp.result.winner_title", who == null ? Component.empty() : who.name);
			}
			long payout = o == null || who == null ? -1 : PvpMatchView.payouts(match, o)[who.index];
			subtitle = payout > 0 ? Texts.raw("+").append(Texts.chips(payout)) : null;
		}
		for (ServerPlayer p : viewers) {
			boolean participant = PvpMatchView.indexOf(match, p.getUUID()) >= 0;
			if (!participant && !winnerCall) {
				continue;
			}
			PvpUi.title(p, title, subtitle, winnerCall ? 60 : 30);
			if (!participant) {
				continue;
			}
			if (winnerCall) {
				boolean won = who != null && isWinner(match, PvpMatchView.indexOf(match, p.getUUID()), who.index);
				PvpUi.sound(p, won ? "burmaldaholic:win" : "burmaldaholic:lose", won ? 1.0f : 0.8f, 1.0f);
				if (won) {
					PvpUi.sound(p, "minecraft:entity.player.levelup", 1.0f, 1.0f);
				}
			} else {
				PvpUi.sound(p, "minecraft:block.note_block.bell", 0.7f, RevealOrder.bellPitch(n - place, Math.max(1, n - 1)));
			}
		}
		ServerPlayer at = who == null ? null : online(match, who.index);
		if (at != null) {
			ServerLevel level = at.level();
			PvpUi.particles(level, winnerCall ? "minecraft:totem_of_undying" : "minecraft:angry_villager", at.getX(), at.getY() + 1, at.getZ(),
				winnerCall ? 30 : 6);
		}
		MinecraftServer server = server(match);
		if (server != null) {
			PvpUi.pushAll(server, match, false);
		}
	}

	@Override
	public void result(PvpMatch match, List<ServerPlayer> participants) {
		for (ServerPlayer p : participants) {
			PvpUi.push(p, match, true);
		}
	}

	@Override
	public void sound(PvpMatch match, String soundId, int who) {
		MinecraftServer server = server(match);
		if (server == null) {
			return;
		}
		String id = soundId.contains(":") ? soundId : "minecraft:" + soundId;
		for (ServerPlayer p : PvpMatchView.onlineHumans(server, match)) {
			if (who < 0 || PvpMatchView.indexOf(match, p.getUUID()) == who) {
				PvpUi.sound(p, id, 1.0f, 1.0f);
			}
		}
	}

	@Override
	public void particles(PvpMatch match, String particleId, int who) {
		MinecraftServer server = server(match);
		if (server == null) {
			return;
		}
		String id = particleId.contains(":") ? particleId : "minecraft:" + particleId;
		ServerPlayer at = who >= 0 ? online(match, who) : null;
		if (at != null) {
			PvpUi.particles(at.level(), id, at.getX(), at.getY() + 1, at.getZ(), 12);
			return;
		}
		GlobalPos anchor = match.anchor;
		ServerLevel level = server.getLevel(anchor.dimension());
		if (level != null) {
			PvpUi.particles(level, id, anchor.pos().getX() + 0.5, anchor.pos().getY() + 1.2, anchor.pos().getZ() + 0.5, 12);
		}
	}

	// ---- helpers -----------------------------------------------------------------------------

	private record Placed(int index, Component name, long points) {}

	/** Who is revealed at {@code place}: from the outcome once known, else from a revealed Final Reveal step. */
	private static @Nullable Placed placed(PvpMatch match, int place) {
		// during the Final Reveal the engine exposes each place as it is revealed (never ahead of it)
		var shown = match.placing(Math.max(1, place));
		if (shown.isPresent()) {
			Participant p = PvpMatchView.participant(match, shown.get().participant());
			return p == null ? null : new Placed(shown.get().participant(), PvpMatchView.name(match, p), shown.get().points());
		}
		Outcome o = match.outcome();
		int pos = Math.max(0, place - 1);
		if (o != null && pos < o.rankOrder().length) {
			int i = o.rankOrder()[pos];
			Participant p = PvpMatchView.participant(match, i);
			return p == null ? null : new Placed(i, PvpMatchView.name(match, p), i < o.points().length ? o.points()[i] : 0);
		}
		List<JsonObject> steps = PvpUi.steps(match.id);
		for (int k = steps.size() - 1; k >= 0; k--) {
			JsonObject data = steps.get(k).getAsJsonObject("data");
			if (data != null && data.has("seat") && (!data.has("place") || data.get("place").getAsInt() == Math.max(1, place))) {
				int i = data.get("seat").getAsInt();
				Participant p = PvpMatchView.participant(match, i);
				long points = data.has("points") ? data.get("points").getAsLong() : 0;
				return p == null ? null : new Placed(i, PvpMatchView.name(match, p), points);
			}
		}
		return null;
	}

	private static boolean isWinner(PvpMatch match, int index, int shownWinner) {
		Outcome o = match.outcome();
		if (o != null) {
			for (int w : o.winners()) {
				if (w == index) {
					return true;
				}
			}
			return false;
		}
		return index == shownWinner;
	}

	private static @Nullable ServerPlayer online(PvpMatch match, int index) {
		Participant p = PvpMatchView.participant(match, index);
		MinecraftServer server = server(match);
		if (p == null || server == null || !(p.occupant instanceof SeatOccupant.Human h)) {
			return null;
		}
		ServerPlayer sp = server.getPlayerList().getPlayer(h.id());
		return sp == null || sp.hasDisconnected() ? null : sp;
	}

	static @Nullable MinecraftServer server(PvpMatch match) {
		return PvpModule.server();
	}
}
