package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.ui.v2.V2SceneScreen;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Base of the v2 item-driven extras screens (Coin Flip, Scratch Cards, Dice Duel; ui-v2.md D5, lane E) on the kit's
 * {@link V2SceneScreen}: server-driven like before — it renders the last {@code ExtrasScreenPayload} state and sends
 * {@code ExtrasActionPayload}s through the kit's watchdog ({@link #sendRaw}) — with the TopBar (title, balance), the Dock
 * and the PlayArea scene. The balance shown is held at its pre-result value while a reveal runs, so the number never
 * spoils the landing (extras-pvp.md §0.3 rule 4).
 */
abstract class ExtrasSceneScreen<V> extends V2SceneScreen<V> {
	private final String game;
	private CompoundTag state;
	private long heldBalance = -1;

	protected ExtrasSceneScreen(String game, Component title, CompoundTag state) {
		super(title);
		this.game = game;
		this.state = state;
	}

	String game() {
		return game;
	}

	CompoundTag state() {
		return state;
	}

	/** The game's view of the current state (pure mapping + the screen's local choices). */
	protected abstract V view(CompoundTag state);

	/** New state from the server: animations first, then the view. */
	void acceptState(CompoundTag newState) {
		CompoundTag old = state;
		state = newState;
		onStateChanged(old, newState);
		state(view(newState), -1);
	}

	protected void onStateChanged(CompoundTag oldState, CompoundTag newState) {}

	/** A local change (a chip, a toggle, a landing): recompute the view, keep the watchdog as is. */
	protected void local() {
		view = view(state);
		refresh();
	}

	@Override
	protected void init() {
		if (view == null) view = view(state);
		super.init();
	}

	@Override
	public void sendRaw(String action, CompoundTag args) {
		if (action.equals("resync")) action = "refresh"; // the extras games re-send their state on "refresh"
		ClientPlayNetworking.send(new ExtrasActionPayload(game, action, args));
	}

	/** Sends an action under the watchdog (any state or error from the server answers it). */
	protected void send(String action, CompoundTag args) {
		chrome.send(action, args, "", 0);
	}

	protected void holdBalance(long balance) {
		heldBalance = balance;
	}

	protected void releaseBalance() {
		heldBalance = -1;
	}

	protected long shownBalance() {
		return heldBalance >= 0 ? heldBalance : state.getLongOr("balance", 0);
	}

	@Override
	protected long balance() {
		return shownBalance();
	}

	@Override
	protected long minBet() {
		return state.getLongOr("min", 0);
	}

	@Override
	protected long maxBet() {
		return state.getLongOr("max", 0);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
