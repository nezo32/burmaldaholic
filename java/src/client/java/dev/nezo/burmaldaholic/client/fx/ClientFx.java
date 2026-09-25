package dev.nezo.burmaldaholic.client.fx;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.fx.CoreParticles;
import dev.nezo.burmaldaholic.core.fx.ServerFx;
import dev.nezo.burmaldaholic.core.network.FxPayload;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the presentation core (lane J-L1): loads {@link FxSettings}, draws the
 * {@link CelebrationOverlay} (HUD layer above chat when no screen is open; on top of any open screen otherwise,
 * except {@link CelebrationOverlay.Host} screens that draw it themselves), routes skip input, registers the core
 * particle providers and receives the {@code fx} payload. Called once from {@code CoreClientModule}.
 *
 * <p>Extension points for other lanes: {@link #on} (handlers for CHAOS, GOLDEN_HOUR_*, VIP_UP, COLLECTORS, CASHIER),
 * {@link #balanceHold} (the J-L2 HUD's {@code holdBalanceDelta}), {@link CelebrationStyles#register} (game words).
 */
public final class ClientFx {
	/** Set by the HUD lane: hold the balance delta for {@code ms} after a celebration starts (global §4.1, F6). */
	public static volatile IntConsumer balanceHold = ms -> {};

	private static final Map<ServerFx.Kind, List<Consumer<FxPayload>>> HANDLERS = new EnumMap<>(ServerFx.Kind.class);
	private static boolean initialized;

	/**
	 * A screen that reveals a result the server has already settled (the extras games): while its own reveal of
	 * {@code game} still runs, the server's celebration for that game waits and plays at the landing, so the overlay
	 * never spoils the animation (extras-pvp.md §0.3 rule 4). Closing the screen, a skip or the reveal ending releases
	 * it at once (rule 6: interrupt = reveal); a held celebration never waits longer than {@link #HOLD_LIMIT_MS}.
	 */
	public interface CelebrationGate {
		boolean holdsCelebration(String game);
	}

	/** A held celebration plays at the latest this long after it arrived. */
	public static final int HOLD_LIMIT_MS = 6000;
	private static CelebrationRequest held;
	private static String heldGame = "";
	private static int heldBalanceMs;
	private static long heldAt;

	private ClientFx() {}

	public static synchronized void init() {
		if (initialized) return;
		initialized = true;
		FxSettings.load(FabricLoader.getInstance().getConfigDir().resolve("burmaldaholic-client.json"));
		CasinoParticle.registerCore();
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Burmaldaholic.id("celebration"), ClientFx::extractHud);
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, pt) -> {
				if (!(s instanceof CelebrationOverlay.Host)) CelebrationOverlay.get().extract(g, false);
			});
			ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> s instanceof CelebrationOverlay.Host || !CelebrationOverlay.get().onClick());
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, e) -> s instanceof CelebrationOverlay.Host || !CelebrationOverlay.get().onKey(e));
		});
		if (FxPayload.TYPE != null) ClientPlayNetworking.registerGlobalReceiver(FxPayload.TYPE, (payload, context) -> handle(payload));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			FxSprites.invalidate();
			CasinoParticle.resetBudget();
		});
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(ClientFx::tickHeld);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			held = null;
			CelebrationOverlay.get().clear();
			CasinoParticle.resetBudget();
		});
	}

	/** Adds a handler for a payload kind (other lanes: chaos, Golden Hour, VIP, collectors, cashier). */
	public static synchronized void on(ServerFx.Kind kind, Consumer<FxPayload> handler) {
		HANDLERS.computeIfAbsent(kind, k -> new CopyOnWriteArrayList<>()).add(handler);
	}

	private static void extractHud(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() != null) return; // drawn by the screen hook, on top of the screen
		// no blur on the HUD pass: a HUD-layer blur swallowed every later stratum in the 26.2 client game test
		// (EPIC drew nothing); the dim backdrop carries the beat
		CelebrationOverlay.get().extract(g, false);
	}

	/** Dispatches one {@code fx} payload (client thread). */
	static void handle(FxPayload p) {
		WinTier[] tiers = WinTier.values();
		WinTier tier = tiers[Math.max(0, Math.min(tiers.length - 1, p.tier()))];
		switch (p.kind()) {
			case WIN -> {
				CelebrationRequest request = CelebrationStyles.request(p.game(), tier, p.amount(), p.stake(), p.arg(), p.maxWin(), p.seed());
				if (Minecraft.getInstance().gui.screen() instanceof CelebrationGate gate && gate.holdsCelebration(p.game())) {
					releaseCelebration(); // an older held one first (never two queued)
					held = request;
					heldGame = p.game();
					heldBalanceMs = p.holdMs();
					heldAt = net.minecraft.util.Util.getMillis();
				} else {
					CelebrationOverlay.get().play(request);
					if (p.holdMs() > 0) balanceHold.accept(p.holdMs());
				}
			}
			case BIG_WIN_NEARBY -> {
				if (FxSettings.othersCelebrations() && p.pos().isPresent()) nearby(p.pos().get(), tier, p.seed());
			}
			case BROADCAST -> {
				if (FxSettings.othersCelebrations()) {
					boolean jp = tier == WinTier.JACKPOT;
					Component name = p.actorName().orElse(Component.empty());
					CasinoToast.show(Component.translatable(jp ? "toast.burmaldaholic.jackpot.title" : "toast.burmaldaholic.big_win.title"),
						Component.translatable(jp ? "toast.burmaldaholic.jackpot.body" : "toast.burmaldaholic.big_win.body", name, Texts.chipsAcc(p.amount())),
						jp ? ServerFx.ToastIcon.JACKPOT : ServerFx.ToastIcon.BIG_WIN);
				}
			}
			case TOAST -> {
				ServerFx.ToastIcon[] icons = ServerFx.ToastIcon.values();
				CasinoToast.show(Component.translatable(p.game()), p.text().orElse(Component.empty()),
					icons[Math.max(0, Math.min(icons.length - 1, p.arg()))]);
			}
			default -> {
			}
		}
		List<Consumer<FxPayload>> hs = HANDLERS.get(p.kind());
		if (hs != null) for (Consumer<FxPayload> h : hs) h.accept(p);
	}

	/** Plays the held celebration now (a gate screen's landing, skip or close). */
	public static void releaseCelebration() {
		CelebrationRequest r = held;
		if (r == null) return;
		held = null;
		CelebrationOverlay.get().play(r);
		if (heldBalanceMs > 0) balanceHold.accept(heldBalanceMs);
	}

	/** The celebration waiting for a screen's landing, or {@code null} (tests). */
	public static CelebrationRequest heldCelebration() {
		return held;
	}

	/** Client tick: a held celebration plays once its screen no longer holds it (landed, closed) or after the limit. */
	private static void tickHeld(Minecraft mc) {
		if (held == null) return;
		boolean holding = mc.gui.screen() instanceof CelebrationGate gate && gate.holdsCelebration(heldGame);
		if (!holding || net.minecraft.util.Util.getMillis() - heldAt > HOLD_LIMIT_MS) releaseCelebration();
	}

	/** Another player's BIG+ win near us (global §4.7): burst at the spot + positional fanfare at half volume. */
	private static void nearby(Vec3 pos, WinTier tier, int seed) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		boolean jp = tier == WinTier.JACKPOT;
		String particle = jp ? "jackpot_burst" : tier.ordinal() >= WinTier.EPIC.ordinal() ? "gold_burst" : "coin_burst";
		int count = switch (tier) {
			case BIG -> 20;
			case MEGA -> 30;
			case EPIC -> 40;
			default -> 60;
		};
		CasinoParticle.burst(mc.level, CoreParticles.get(particle), pos.x, pos.y + 1.2, pos.z, count, 0.12, jp, seed);
		FxSounds.playAt(jp ? "jackpot" : "win_big", pos.x, pos.y, pos.z, 0.5f, 1f);
	}
}
