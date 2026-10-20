package dev.nezo.burmaldaholic.gametest.core;

import dev.nezo.burmaldaholic.client.cashier.CounterShell;
import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Viewport;
import java.util.Optional;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Real-input helpers for the counter screen (EC-1): click a {@link CounterShell} target at its real window pixel (the
 * mouse goes through the viewport mixin and the shell's HitMap), optionally with Shift held, and press keys.
 */
public final class CounterFlow {
	public static final int TIMEOUT = 200;

	private CounterFlow() {}

	public static Optional<Box> box(ClientGameTestContext ctx, String id) {
		return ctx.computeOnClient(mc -> mc.gui.screen() instanceof CounterShell.Holder h ? h.shell().hits().box(id) : Optional.empty());
	}

	public static boolean enabled(ClientGameTestContext ctx, String id) {
		return ctx.computeOnClient(mc -> mc.gui.screen() instanceof CounterShell.Holder h
			&& h.shell().hits().targets().stream().anyMatch(t -> t.id().equals(id) && t.enabled()));
	}

	/** Waits until target {@code id} exists and is enabled. */
	public static void awaitEnabled(ClientGameTestContext ctx, String id) {
		awaitShell(ctx, s -> s.hits().targets().stream().anyMatch(t -> t.id().equals(id) && t.enabled()), "target '" + id + "' enabled");
	}

	public static void awaitShell(ClientGameTestContext ctx, Predicate<CounterShell> pred, String what) {
		for (int t = 0; t <= TIMEOUT; t += 2) {
			boolean ok = ctx.computeOnClient(mc -> mc.gui.screen() instanceof CounterShell.Holder h && pred.test(h.shell()));
			if (ok) return;
			ctx.waitTicks(2);
		}
		throw new AssertionError("timed out waiting for the counter: " + what);
	}

	/** Left click on target {@code id}; {@code shift} holds Left Shift during the click. */
	public static void click(ClientGameTestContext ctx, String id, boolean shift) {
		Box b = box(ctx, id).orElseThrow(() -> new AssertionError("no counter target '" + id + "'"));
		Viewport vp = ctx.computeOnClient(mc -> ((CounterShell.Holder) mc.gui.screen()).shell().viewport());
		ctx.getInput().setCursorPos(vp.toWindow(b.x() + b.w() / 2.0), vp.toWindow(b.y() + b.h() / 2.0));
		ctx.waitTick();
		if (shift) ctx.getInput().holdKey(Inputs.KEY_LSHIFT);
		ctx.getInput().pressMouse(Inputs.MOUSE_LEFT);
		if (shift) ctx.getInput().releaseKey(Inputs.KEY_LSHIFT);
		ctx.waitTicks(2);
		String pressed = ctx.computeOnClient(mc -> mc.gui.screen() instanceof CounterShell.Holder h ? h.shell().lastPressed() : "");
		if (!pressed.equals(id)) throw new AssertionError("clicked '" + id + "' but the counter pressed '" + pressed + "'");
	}

	public static void click(ClientGameTestContext ctx, String id) {
		click(ctx, id, false);
	}

	/** Presses {@code key} (an {@link Inputs} code) with optional Shift / Control held. */
	public static void key(ClientGameTestContext ctx, int key, boolean shift, boolean ctrl) {
		if (shift) ctx.getInput().holdKey(Inputs.KEY_LSHIFT);
		if (ctrl) ctx.getInput().holdKey(Inputs.KEY_LCONTROL);
		ctx.getInput().pressKey(key);
		if (ctrl) ctx.getInput().releaseKey(Inputs.KEY_LCONTROL);
		if (shift) ctx.getInput().releaseKey(Inputs.KEY_LSHIFT);
		ctx.waitTicks(2);
	}

	public static String lastPressed(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.gui.screen() instanceof CounterShell.Holder h ? h.shell().lastPressed() : "");
	}
}
