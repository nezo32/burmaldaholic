package dev.nezo.burmaldaholic.pvp;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.pvp.Participant;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
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
		try {
			hints(match, step.data(), viewers);
		} catch (RuntimeException e) {
			dev.nezo.burmaldaholic.Burmaldaholic.LOGGER.warn("PvP: bad presentation hints in {} step {}", match.mode, step.kind(), e);
		}
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

	// ---- mode presentation hints (generic; e.g. games.slots.pvp.ShowdownTimeline) ----------------------

	/**
	 * Renders the mode's hints of a revealed step: {@code msgs[]} = chat lines {@code {key, args[]}} to the viewers,
	 * {@code titles[]} = {@code {seat, key, sub?}} (seat -1 = every participant; otherwise that participant, with
	 * the {@code sub} subtitle), {@code sounds[]} = {@code {id, seat, volume, pitch}} (seat -1 = everyone). An arg is
	 * {@code {"seat":i}} (display name), {@code {"n":x}} (number) or {@code {"key":k}} (translated).
	 */
	static void hints(PvpMatch match, @Nullable JsonObject data, List<ServerPlayer> viewers) {
		if (data == null) {
			return;
		}
		if (data.has("msgs") && data.get("msgs").isJsonArray()) {
			for (var e : data.getAsJsonArray("msgs")) {
				if (!e.isJsonObject() || !e.getAsJsonObject().has("key")) {
					continue;
				}
				JsonObject m = e.getAsJsonObject();
				List<Object> args = new java.util.ArrayList<>();
				if (m.has("args") && m.get("args").isJsonArray()) {
					for (var a : m.getAsJsonArray("args")) {
						args.add(arg(match, a.isJsonObject() ? a.getAsJsonObject() : new JsonObject()));
					}
				}
				Component line = Component.translatable(m.get("key").getAsString(), args.toArray());
				for (ServerPlayer p : viewers) {
					p.sendSystemMessage(line);
				}
			}
		}
		if (data.has("titles") && data.get("titles").isJsonArray()) {
			for (var e : data.getAsJsonArray("titles")) {
				if (!e.isJsonObject() || !e.getAsJsonObject().has("key")) {
					continue;
				}
				JsonObject t = e.getAsJsonObject();
				int seat = t.has("seat") ? t.get("seat").getAsInt() : -1;
				Participant who = seat >= 0 ? PvpMatchView.participant(match, seat) : null;
				Component title = Component.translatable(t.get("key").getAsString(), who == null ? Component.empty() : PvpMatchView.name(match, who));
				Component sub = t.has("sub") ? Component.translatable(t.get("sub").getAsString()) : null;
				for (ServerPlayer p : viewers) {
					int idx = PvpMatchView.indexOf(match, p.getUUID());
					if (idx >= 0 && (seat < 0 || idx == seat)) {
						PvpUi.title(p, title, idx == seat ? sub : null, 40);
					}
				}
			}
		}
		if (data.has("sounds") && data.get("sounds").isJsonArray()) {
			for (var e : data.getAsJsonArray("sounds")) {
				if (!e.isJsonObject() || !e.getAsJsonObject().has("id")) {
					continue;
				}
				JsonObject s = e.getAsJsonObject();
				String id = s.get("id").getAsString();
				String full = id.contains(":") ? id : "minecraft:" + id;
				int seat = s.has("seat") ? s.get("seat").getAsInt() : -1;
				float volume = s.has("volume") ? s.get("volume").getAsFloat() : 1.0f;
				float pitch = s.has("pitch") ? s.get("pitch").getAsFloat() : 1.0f;
				for (ServerPlayer p : viewers) {
					int idx = PvpMatchView.indexOf(match, p.getUUID());
					if (seat < 0 || idx == seat) {
						PvpUi.sound(p, full, volume, pitch);
					}
				}
			}
		}
	}

	private static Component arg(PvpMatch match, JsonObject a) {
		if (a.has("seat")) {
			Participant p = PvpMatchView.participant(match, a.get("seat").getAsInt());
			return p == null ? Texts.raw("?") : PvpMatchView.name(match, p);
		}
		if (a.has("n")) {
			return Texts.number(a.get("n").getAsLong());
		}
		if (a.has("key")) {
			return Component.translatable(a.get("key").getAsString());
		}
		return Component.empty();
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
