package dev.nezo.burmaldaholic.bots;

import dev.nezo.burmaldaholic.bots.logic.HeatStage;
import dev.nezo.burmaldaholic.bots.logic.InviteBook;
import dev.nezo.burmaldaholic.bots.logic.SettingsRules;
import dev.nezo.burmaldaholic.bots.net.BotsActionPayload;
import dev.nezo.burmaldaholic.bots.net.BotsScreenPayload;
import dev.nezo.burmaldaholic.core.bots.BotLedger;
import dev.nezo.burmaldaholic.core.bots.TableBots;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.BotsMode;
import dev.nezo.burmaldaholic.core.bots.logic.OwnerControls;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Server side of the Table settings screen (BOTS.md §8.2) and of {@code /casino table …}: builds the
 * viewer's state, validates every change with {@link SettingsRules} and hands it to core's
 * {@link TableBots} (settings: pending until the safe point; access: at once). Server thread.
 */
public final class TableSettings {
	/** Who invited whom (members_only). */
	static final InviteBook INVITES = new InviteBook();
	/** Names of players seen (invite lists show offline invitees by name). */
	private static final Map<UUID, String> NAMES = new HashMap<>();

	private TableSettings() {}

	static void remember(ServerPlayer player) {
		NAMES.put(player.getUUID(), player.getGameProfile().name());
	}

	static String name(MinecraftServer server, UUID id) {
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		if (p != null) {
			remember(p);
			return p.getGameProfile().name();
		}
		return NAMES.getOrDefault(id, id.toString().substring(0, 8));
	}

	// ---- C2S ------------------------------------------------------------------------------------

	static void handle(BotsActionPayload payload, ServerPlayNetworking.Context context) {
		ServerPlayer player = context.player();
		if (!CasinoMode.isEnabled(player)) {
			return;
		}
		BlockPos pos = payload.pos();
		if (player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 64 * 64) {
			return;
		}
		Optional<BotTables.Found> found = BotTables.at(player.level(), pos);
		if (found.isEmpty()) {
			return;
		}
		BotTables.Found f = found.get();
		CompoundTag a = payload.args();
		boolean defaults = a.getBooleanOr("defaults", false);
		Component error = switch (payload.action()) {
			case "open" -> null;
			case "save" -> save(player, f, readSettings(a, f.bots().settings()), defaults);
			case "private" -> setPrivate(player, f, a.getBooleanOr("on", false));
			case "invite" -> parse(a.getStringOr("id", "")).map(id -> invite(player, f, id)).orElse(null);
			case "uninvite" -> parse(a.getStringOr("id", "")).map(id -> uninvite(player, f, id)).orElse(null);
			case "limits" -> limits(player, f, a);
			default -> null;
		};
		if (error == null && (payload.action().equals("limits") || (payload.action().equals("save") && defaults))) {
			player.sendOverlayMessage(Component.translatable(payload.action().equals("limits")
				? "msg.burmaldaholic.bots.limits_saved" : "msg.burmaldaholic.bots.defaults_saved"));
		}
		send(player, f, payload.action().equals("open"), a.getBooleanOr("screenDefaults", defaults), error);
	}

	private static Optional<UUID> parse(String s) {
		try {
			return Optional.of(UUID.fromString(s));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	static BotSettings readSettings(CompoundTag a, BotSettings fallback) {
		return new BotSettings(
			SeatPolicy.byId(a.getStringOr("policy", fallback.policy().id()), fallback.policy()),
			a.getIntOr("count", fallback.count()),
			BotDifficulty.byId(a.getStringOr("difficulty", fallback.difficulty().id()), fallback.difficulty()),
			a.getBooleanOr("keepFree", fallback.keepFree()),
			a.getBooleanOr("chatter", fallback.chatter()),
			speed(a.getStringOr("speed", fallback.speed().name()), fallback.speed()));
	}

	private static BotSpeed speed(String id, BotSpeed fallback) {
		for (BotSpeed s : BotSpeed.values()) {
			if (s.name().equalsIgnoreCase(id)) {
				return s;
			}
		}
		return fallback;
	}

	// ---- actions (also used by the commands) ----------------------------------------------------

	/** Settings change; null = ok (pending until the next safe point), else the error. */
	static @Nullable Component save(ServerPlayer player, BotTables.Found f, BotSettings wanted, boolean asDefaults) {
		SettingsRules.View v = SettingsRules.view(f.actor(player), f.facts(), asDefaults);
		BotSettings current = asDefaults ? f.bots().defaults() : currentOrPending(f.bots());
		SettingsRules.Checked c = SettingsRules.validate(wanted, current, v);
		if (!c.ok()) {
			return Component.translatable(c.error());
		}
		Result<BotSettings> r = f.bots().requestChange(player, c.settings(), asDefaults);
		if (!r.isOk()) {
			return r.error();
		}
		f.blockEntity().setChanged();
		if (!asDefaults) {
			Component summary = summary(f, r.value() != null ? r.value() : c.settings());
			Component line = Component.translatable("msg.burmaldaholic.bots.settings_pending", Texts.raw(player.getGameProfile().name()), summary);
			for (UUID id : f.table().seatedHumans()) {
				ServerPlayer p = player.level().getServer().getPlayerList().getPlayer(id);
				if (p != null) {
					p.sendSystemMessage(line);
				}
			}
		}
		return null;
	}

	static @Nullable Component setPrivate(ServerPlayer player, BotTables.Found f, boolean on) {
		SettingsRules.View v = SettingsRules.view(f.actor(player), f.facts(), false);
		if (!v.mayEditAccess()) {
			return Component.translatable(SettingsRules.NOT_HOST);
		}
		if (on && !v.privateAllowed()) {
			return Component.translatable(v.privateReason() != null ? v.privateReason() : SettingsRules.PRIVATE_FORBIDDEN);
		}
		f.bots().access().setPrivate(on, f.table().seatedHumans());
		f.blockEntity().setChanged();
		return null;
	}

	static @Nullable Component invite(ServerPlayer player, BotTables.Found f, UUID target) {
		SettingsRules.Actor actor = f.actor(player);
		SettingsRules.View v = SettingsRules.view(actor, f.facts(), false);
		if (!v.mayEditAccess()) {
			return Component.translatable(SettingsRules.NOT_HOST);
		}
		if (!v.privateAllowed()) {
			return Component.translatable(v.privateReason() != null ? v.privateReason() : SettingsRules.PRIVATE_FORBIDDEN);
		}
		if (target.equals(player.getUUID())) {
			return null;
		}
		int max = CasinoConfig.bots().privateTables.maxInvites;
		if (!f.bots().access().invite(target, SettingsRules.invitesAreSaved(actor), max)) {
			return Component.translatable("gui.burmaldaholic.bots.error.invites_full", Texts.number(max));
		}
		f.blockEntity().setChanged();
		INVITES.record(f.key(), player.getUUID(), target);
		MinecraftServer server = player.level().getServer();
		String targetName = name(server, target);
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.bots.invite_sent", Texts.raw(targetName)));
		ServerPlayer invitee = server.getPlayerList().getPlayer(target);
		if (invitee != null) {
			BlockPos p = f.pos();
			invitee.sendSystemMessage(Component.translatable("msg.burmaldaholic.bots.invited", Texts.raw(player.getGameProfile().name()), f.name(),
				Component.translatable("chat.coordinates", Texts.raw(Integer.toString(p.getX())), Texts.raw(Integer.toString(p.getY())),
					Texts.raw(Integer.toString(p.getZ())))));
		}
		return null;
	}

	static @Nullable Component uninvite(ServerPlayer player, BotTables.Found f, UUID target) {
		SettingsRules.View v = SettingsRules.view(f.actor(player), f.facts(), false);
		if (!v.mayEditAccess()) {
			return Component.translatable(SettingsRules.NOT_HOST);
		}
		f.bots().access().uninvite(target);
		INVITES.remove(f.key(), target);
		f.blockEntity().setChanged();
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.bots.uninvited", Texts.raw(name(player.level().getServer(), target))));
		return null;
	}

	/** BOTS_ONLY host lets another human in: the session switches to Humans + bots (and the human is invited if private). */
	static @Nullable Component letIn(ServerPlayer player, BotTables.Found f, UUID target) {
		BotSettings cur = currentOrPending(f.bots());
		if (!player.getUUID().equals(f.host())) {
			return Component.translatable(SettingsRules.NOT_HOST);
		}
		if (cur.policy() == SeatPolicy.BOTS_ONLY) {
			Result<BotSettings> r = f.bots().requestChange(player, cur.withPolicy(SeatPolicy.MIXED), false);
			if (!r.isOk()) {
				return r.error();
			}
		}
		if (f.bots().access().isPrivate()) {
			return invite(player, f, target);
		}
		f.blockEntity().setChanged();
		return null;
	}

	static @Nullable Component limits(ServerPlayer player, BotTables.Found f, CompoundTag a) {
		SettingsRules.View v = SettingsRules.view(f.actor(player), f.facts(), true);
		if (!v.mayEditLimits()) {
			return Component.translatable(SettingsRules.HOST_LOCKED);
		}
		OwnerControls cur = f.bots().limits();
		BotsMode mode = cur.botsMode();
		String m = a.getStringOr("mode", mode.name());
		for (BotsMode b : BotsMode.values()) {
			if (b.name().equalsIgnoreCase(m)) {
				mode = b;
			}
		}
		OwnerControls wanted = new OwnerControls(mode, a.getBooleanOr("hostMayChange", cur.hostMayChange()), a.getIntOr("maxBots", cur.maxBots()),
			a.getBooleanOr("allowPrivate", cur.allowPrivate()));
		OwnerControls next = SettingsRules.clampLimits(wanted, cur, f.table().botSeatCount(), f.owned(), v.mayEditBotsMode());
		f.bots().setLimits(next);
		if (f.owned() && next.botsMode() != cur.botsMode()) {
			// the charter's per-table "Bots" switch follows the owner's mode (BOTS.md §6.2)
			dev.nezo.burmaldaholic.core.service.CharterBots.set(f.level(), f.pos(), next.botsMode() != BotsMode.OFF);
		}
		if (!next.allowPrivate() && f.bots().access().isPrivate()) {
			f.bots().access().setPrivate(false, List.of());
		}
		f.blockEntity().setChanged();
		return null;
	}

	/** The settings a change is based on: the pending ones if any, else the session's. */
	static BotSettings currentOrPending(TableBots bots) {
		BotSettings p = bots.pending();
		return p != null ? p : bots.settings();
	}

	static MutableComponent summary(BotTables.Found f, BotSettings s) {
		UUID host = f.host();
		String hostName = host == null ? "" : name(f.level().getServer(), host);
		return BotTexts.summary(s, f.bots().access().isPrivate(), f.botsSeated(), hostName, "chemmy".equals(f.table().botGameId()));
	}

	// ---- S2C ------------------------------------------------------------------------------------

	/** Opens (or refreshes) the Table settings screen of {@code f} for {@code player}. */
	public static void send(ServerPlayer player, BotTables.Found f, boolean open, boolean defaults, @Nullable Component error) {
		if (BotsScreenPayload.TYPE == null || !ServerPlayNetworking.canSend(player, BotsScreenPayload.TYPE)) {
			if (error != null) {
				player.sendSystemMessage(error.copy().withStyle(ChatFormatting.RED));
			}
			return;
		}
		ServerPlayNetworking.send(player, new BotsScreenPayload("settings", open, state(player, f, defaults, error)));
	}

	static CompoundTag state(ServerPlayer player, BotTables.Found f, boolean defaultsMode, @Nullable Component error) {
		MinecraftServer server = player.level().getServer();
		SettingsRules.Actor actor = f.actor(player);
		SettingsRules.View v = SettingsRules.view(actor, f.facts(), defaultsMode && actor.manager());
		boolean defaults = defaultsMode && actor.manager();
		TableBots tb = f.bots();
		BotSettings shown = defaults ? tb.defaults() : currentOrPending(tb);
		CompoundTag t = new CompoundTag();
		t.putLong("pos", f.pos().asLong());
		t.putString("title", f.blockEntity().getBlockState().getBlock().getDescriptionId());
		t.putString("game", f.table().botGameId());
		t.putBoolean("defaults", defaults);
		UUID host = f.host();
		t.putString("host", host == null ? "" : name(server, host));
		t.putString("policy", shown.policy().id());
		t.putInt("count", shown.count());
		t.putString("difficulty", shown.difficulty().id());
		t.putBoolean("keepFree", shown.keepFree());
		t.putBoolean("chatter", shown.chatter());
		t.putString("speed", shown.speed().name());
		t.putBoolean("pending", !defaults && tb.pending() != null);
		t.putBoolean("mayEdit", v.mayEdit());
		t.putString("lock", v.lockReason() == null ? "" : v.lockReason());
		t.putBoolean("mayDefaults", v.mayEditDefaults());
		t.putBoolean("botsVisible", v.botsVisible());
		t.putString("botsReason", v.botsReason() == null ? "" : v.botsReason());
		t.putBoolean("botsOnly", v.botsOnlyAllowed());
		t.putInt("maxCount", v.maxCount());
		t.putString("diffMode", v.difficultyMode().name());
		t.putBoolean("heatHardOnly", "poker".equals(f.table().botGameId()) && heat(player) != HeatStage.NONE);
		t.putInt("botsSeated", f.botsSeated());
		// access
		t.putBoolean("private", tb.access().isPrivate());
		t.putBoolean("mayAccess", v.mayEditAccess());
		t.putBoolean("privateAllowed", v.privateAllowed());
		t.putString("privateReason", v.privateReason() == null ? "" : v.privateReason());
		Set<UUID> invited = new LinkedHashSet<>(tb.access().guests());
		invited.addAll(tb.access().sessionInvites());
		t.put("invited", people(server, invited));
		Set<UUID> nearby = new LinkedHashSet<>();
		int radius = CasinoConfig.bots().privateTables.inviteRadius;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p == player || invited.contains(p.getUUID()) || f.seated(p.getUUID())) {
				continue;
			}
			if (radius == 0 || (p.level() == f.level() && p.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(f.pos())) <= (double) radius * radius)) {
				nearby.add(p.getUUID());
			}
		}
		t.put("nearby", people(server, nearby));
		// limits
		OwnerControls l = tb.limits();
		t.putString("mode", l.botsMode().name());
		t.putBoolean("hostMayChange", l.hostMayChange());
		t.putInt("maxBots", l.maxBots());
		t.putBoolean("allowPrivate", l.allowPrivate());
		t.putBoolean("mayLimits", v.mayEditLimits());
		t.putBoolean("mayMode", v.mayEditBotsMode());
		t.putBoolean("owned", f.owned());
		t.putInt("seats", f.table().botSeatCount());
		// seated bots (read-only list with personality)
		ListTag bots = new ListTag();
		List<SeatOccupant> occ = f.table().occupants();
		for (int i = 0; i < occ.size(); i++) {
			if (occ.get(i) instanceof SeatOccupant.Bot b) {
				CompoundTag e = new CompoundTag();
				e.putInt("seat", i + 1);
				e.putString("name", b.profile().nameId());
				e.putString("level", b.profile().level().id());
				e.putString("personality", b.profile().personality().id());
				long stack = 0;
				for (TableBots.SeatedBot sb : tb.bots()) {
					if (sb.profile.id().equals(b.profile().id())) {
						stack = sb.stack;
					}
				}
				e.putLong("stack", stack);
				e.putBoolean("money", b.role() == dev.nezo.burmaldaholic.core.bots.logic.BotRole.MONEY);
				bots.add(e);
			}
		}
		t.put("bots", bots);
		if (error != null) {
			t.put("error", ComponentSerialization.CODEC.encodeStart(player.registryAccess().createSerializationContext(NbtOps.INSTANCE), error)
				.result().orElseGet(CompoundTag::new));
		}
		return t;
	}

	private static ListTag people(MinecraftServer server, Set<UUID> ids) {
		ListTag list = new ListTag();
		for (UUID id : ids) {
			CompoundTag e = new CompoundTag();
			e.putString("id", id.toString());
			e.putString("name", name(server, id));
			list.add(e);
		}
		return list;
	}

	/** The player's heat stage today (BOTS.md §5.4). */
	static HeatStage heat(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		return HeatStage.of(BotLedger.netToday(server, player.getUUID()), BotLedger.threshold(server, player.getUUID()),
			CasinoConfig.bots().sulkMultiplier);
	}

	/** Chat line with a clickable [Let them in] for the host of a BOTS_ONLY table (BOTS.md §2.1). */
	public static void askHost(ServerPlayer host, String requesterName) {
		MutableComponent click = Component.translatable("msg.burmaldaholic.bots.let_in_click").withStyle(s -> s.withColor(ChatFormatting.GREEN)
			.withClickEvent(new ClickEvent.RunCommand("/casino table letin " + requesterName)));
		host.sendSystemMessage(Component.translatable("msg.burmaldaholic.bots.let_in_request", Texts.raw(requesterName)).append(" ").append(click)); // literal-ok: separator space
	}
}
