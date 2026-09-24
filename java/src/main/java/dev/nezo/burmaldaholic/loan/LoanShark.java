package dev.nezo.burmaldaholic.loan;

import com.mojang.serialization.DynamicOps;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.loan.entity.LoanSharkEntity;
import dev.nezo.burmaldaholic.loan.logic.Dialogue;
import dev.nezo.burmaldaholic.loan.logic.LoanRecord;
import dev.nezo.burmaldaholic.loan.logic.LoanRecord.Status;
import dev.nezo.burmaldaholic.loan.logic.LoanRules;
import dev.nezo.burmaldaholic.loan.logic.LoanRules.Product;
import dev.nezo.burmaldaholic.loan.net.LoanActionPayload;
import dev.nezo.burmaldaholic.loan.net.LoanUiPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Loan Shark NPC service (GAME_DESIGN.md §5.1, UI.md §10): opens the server-driven Loan screen, keeps
 * track of open screens (the shark is invulnerable to players meanwhile), handles its buttons, and
 * respawns killed sharks at their home after {@code worldgen.loanSharkRespawnTicks}.
 */
public final class LoanShark {
	/** Max distance to the shark for screen actions. */
	private static final double REACH = 8.0;
	/** player → shark entity uuid (null key value = status screen without a shark). */
	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private record Session(@Nullable UUID shark, boolean piglin, String greeting) {}

	private LoanShark() {}

	// ---- screen ---------------------------------------------------------------------------------

	/** Right-click on a shark. */
	public static void open(ServerPlayer player, LoanSharkEntity shark) {
		MinecraftServer server = player.level().getServer();
		if (!CasinoMode.isEnabled(server)) {
			player.sendOverlayMessage(Component.translatable("gui.burmaldaholic.error.casino_off"));
			return;
		}
		if (!CasinoConfig.loan().enabled) {
			player.sendOverlayMessage(Component.translatable("gui.burmaldaholic.error.disabled"));
			return;
		}
		LoanService.tick(player);
		LoanRecord rec = LoanService.record(server, player.getUUID());
		boolean piglin = shark.isPiglin();
		String greeting = rec.status == Status.DEFAULT ? Dialogue.OVERDUE : piglin ? Dialogue.PIGLIN_GREETING : Dialogue.GREETING;
		Session s = new Session(shark.getUUID(), piglin, Dialogue.pick(greeting, player.getRandom()::nextInt));
		SESSIONS.put(player.getUUID(), s);
		send(player, true, null, false, shark.getId());
	}

	public static boolean isBusy(LoanSharkEntity shark) {
		UUID id = shark.getUUID();
		return SESSIONS.values().stream().anyMatch(s -> id.equals(s.shark()));
	}

	/** Builds and sends the screen state. */
	private static void send(ServerPlayer player, boolean open, @Nullable Component message, boolean error, int sharkEntityId) {
		Session s = SESSIONS.get(player.getUUID());
		if (s == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		LoanRecord rec = LoanService.record(server, player.getUUID());
		long now = LoanService.now(server);
		int tier = LoanService.tier(server, player.getUUID());
		double rate = LoanService.rateFor(server, player.getUUID());
		CompoundTag t = new CompoundTag();
		t.putBoolean("piglin", s.piglin());
		t.putInt("entity", sharkEntityId);
		putComponent(server, t, "greeting", speech(s.piglin(), Component.translatable(s.greeting())));
		long balance = Economies.get().balance(player);
		t.putLong("balance", balance);
		t.putLong("owed", rec.owed);
		String status = switch (rec.status) {
			case ACTIVE -> "active";
			case DEFAULT -> "default";
			case NONE -> now < rec.cooldownUntil ? "cooldown" : "none";
		};
		t.putString("status", status);
		putComponent(server, t, "status_line", statusLine(rec, now));
		boolean canTake = rec.status == Status.NONE && now >= rec.cooldownUntil;
		t.putBoolean("can_take", canTake);
		t.putString("rate", LoanRules.percent(rate));
		if (rec.goodStanding > 0) {
			int steps = Math.min(rec.goodStanding, CasinoConfig.loan().goodStandingMaxSteps);
			putComponent(server, t, "good_standing", Component.translatable("gui.burmaldaholic.loan.good_standing", Texts.number(rec.goodStanding),
				Texts.decimal(LoanRules.percent(steps * CasinoConfig.loan().goodStandingDiscount))));
		}
		List<Product> products = LoanService.products();
		ListTag list = new ListTag();
		for (Product p : products) {
			CompoundTag pt = new CompoundTag();
			pt.putInt("index", p.index());
			pt.putString("id", p.id());
			pt.putLong("principal", p.principal());
			pt.putInt("days", p.days());
			pt.putLong("due", LoanRules.due(p.principal(), rate));
			pt.putBoolean("locked", tier < p.minTier());
			pt.putInt("min_tier", p.minTier());
			list.add(pt);
		}
		t.put("products", list);
		int locked = LoanRules.lockedCount(products, tier);
		t.putInt("locked", locked);
		if (canTake && locked > 0) {
			putComponent(server, t, "refuse_line", speech(s.piglin(), Component.translatable(Dialogue.pick(Dialogue.REFUSE_VIP, player.getRandom()::nextInt))));
		}
		if (message != null) {
			putComponent(server, t, "message", message);
			t.putBoolean("error", error);
		}
		ServerPlayNetworking.send(player, new LoanUiPayload(LoanUiPayload.LOAN, open, t));
	}

	static MutableComponent statusLine(LoanRecord rec, long now) {
		return switch (rec.status) {
			case ACTIVE -> Component.translatable("gui.burmaldaholic.loan.status.active", Texts.chips(rec.owed), LoanService.dhm(rec.deadlineTick - now))
				.withStyle(ChatFormatting.YELLOW);
			case DEFAULT -> Component.translatable("gui.burmaldaholic.loan.status.default", Texts.chips(rec.owed)).withStyle(ChatFormatting.RED);
			case NONE -> now < rec.cooldownUntil
				? Component.translatable("gui.burmaldaholic.loan.status.cooldown", LoanService.days(rec.cooldownUntil - now)).withStyle(ChatFormatting.GRAY)
				: Component.translatable("gui.burmaldaholic.loan.status.none").withStyle(ChatFormatting.GREEN);
		};
	}

	/** "&lt;Loan Shark&gt; line" (vanilla chat format). */
	static MutableComponent speech(boolean piglin, Component line) {
		return speech(Component.translatable(piglin ? "entity.burmaldaholic.piglin_moneylender" : "entity.burmaldaholic.loan_shark"), line);
	}

	static MutableComponent speech(Component name, Component line) {
		return Component.translatable("chat.type.text", name.copy().withStyle(ChatFormatting.GOLD), line);
	}

	static void putComponent(MinecraftServer server, CompoundTag t, String key, Component c) {
		DynamicOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		ComponentSerialization.CODEC.encodeStart(ops, c).result().ifPresent(tag -> t.put(key, tag));
	}

	/** Loan screen buttons. */
	static void onAction(ServerPlayer player, LoanActionPayload a) {
		Session s = SESSIONS.get(player.getUUID());
		if (s == null) {
			return;
		}
		if ("close".equals(a.action())) {
			SESSIONS.remove(player.getUUID());
			return;
		}
		Entity shark = s.shark() == null ? null : ((ServerLevel) player.level()).getEntity(s.shark());
		if (s.shark() != null && (shark == null || !shark.isAlive() || shark.distanceTo(player) > REACH)) {
			SESSIONS.remove(player.getUUID());
			ServerPlayNetworking.send(player, new LoanUiPayload(LoanUiPayload.LOAN, false, new CompoundTag()));
			return;
		}
		int entityId = shark == null ? -1 : shark.getId();
		MinecraftServer server = player.level().getServer();
		switch (a.action()) {
			case "take" -> {
				Component err = LoanService.take(player, a.index());
				if (err != null) {
					send(player, false, err, true, entityId);
				} else {
					LoanService.sound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F);
					Component line = speech(s.piglin(), Component.translatable(Dialogue.pick(Dialogue.GIVEN, player.getRandom()::nextInt)));
					player.sendSystemMessage(line);
					send(player, false, line, false, entityId);
				}
			}
			case "pay", "pay_all" -> {
				LoanRecord rec = LoanService.record(server, player.getUUID());
				long amount = "pay_all".equals(a.action()) ? rec.owed : a.amount();
				if (rec.status == Status.NONE) {
					send(player, false, null, false, entityId);
					return;
				}
				if (amount <= 0 || amount > rec.owed) {
					send(player, false, Component.translatable("gui.burmaldaholic.error.invalid_amount"), true, entityId);
					return;
				}
				long paid = LoanService.pay(player, amount, true);
				if (paid <= 0) {
					send(player, false, Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(Economies.get().balance(player))), true,
						entityId);
				} else if (LoanService.record(server, player.getUUID()).status == Status.NONE) {
					Component line = speech(s.piglin(), Component.translatable(Dialogue.pick(Dialogue.REPAID, player.getRandom()::nextInt)));
					player.sendSystemMessage(line);
					send(player, false, line, false, entityId);
				} else {
					send(player, false, Component.translatable("msg.burmaldaholic.loan.paid_partial", Texts.chips(paid),
						Texts.chips(LoanService.record(server, player.getUUID()).owed)), false, entityId);
				}
			}
			default -> send(player, false, null, false, entityId);
		}
	}

	/** Drops sessions whose player left or walked away (a lost "close" packet must not keep a shark invulnerable). */
	static void prune(MinecraftServer server) {
		SESSIONS.entrySet().removeIf(e -> {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null) {
				return true;
			}
			if (e.getValue().shark() == null) {
				return false;
			}
			Entity shark = ((ServerLevel) p.level()).getEntity(e.getValue().shark());
			return shark == null || !shark.isAlive() || shark.distanceTo(p) > REACH * 2;
		});
	}

	static void onDisconnect(ServerPlayer player) {
		SESSIONS.remove(player.getUUID());
	}

	static void clear() {
		SESSIONS.clear();
	}

	// ---- death / respawn ------------------------------------------------------------------------

	public static void onDeath(ServerLevel level, LoanSharkEntity shark) {
		SESSIONS.values().removeIf(s -> shark.getUUID().equals(s.shark()));
		LoanData data = LoanData.get(level.getServer());
		long at = LoanService.now(level.getServer()) + CasinoConfig.worldgen().loanSharkRespawnTicks;
		data.respawns().add(new LoanData.Respawn(level.dimension().identifier().toString(), shark.home(), shark.isPiglin(), at));
		while (data.respawns().size() > 64) {
			data.respawns().remove(0);
		}
		data.setDirty();
	}

	/** Every 5 s while active: respawn due sharks whose home chunk is loaded. */
	static void respawnTick(MinecraftServer server) {
		LoanData data = LoanData.get(server);
		if (data.respawns().isEmpty()) {
			return;
		}
		long now = LoanService.now(server);
		boolean changed = false;
		for (Iterator<LoanData.Respawn> it = data.respawns().iterator(); it.hasNext();) {
			LoanData.Respawn r = it.next();
			if (now < r.at()) {
				continue;
			}
			Identifier dimId = Identifier.tryParse(r.dimension());
			ServerLevel level = dimId == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
			if (level == null) {
				it.remove();
				changed = true;
				continue;
			}
			if (!level.isLoaded(r.pos())) {
				continue;
			}
			var type = r.piglin() ? LoanContent.PIGLIN_MONEYLENDER : LoanContent.LOAN_SHARK;
			if (level.getEntities(type, new AABB(r.pos()).inflate(8), e -> true).isEmpty()) {
				LoanSharkEntity shark = type.create(level, EntitySpawnReason.EVENT);
				if (shark != null) {
					shark.snapTo(r.pos().getX() + 0.5, r.pos().getY(), r.pos().getZ() + 0.5);
					level.addFreshEntity(shark);
				}
			}
			it.remove();
			changed = true;
		}
		if (changed) {
			data.setDirty();
		}
	}
}
