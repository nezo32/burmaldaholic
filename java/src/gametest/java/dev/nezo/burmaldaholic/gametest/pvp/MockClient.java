package dev.nezo.burmaldaholic.gametest.pvp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.network.MenuPayload;
import dev.nezo.burmaldaholic.core.network.TableErrorPayload;
import dev.nezo.burmaldaholic.core.network.TableSyncPayload;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasErrorPayload;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasScreenPayload;
import dev.nezo.burmaldaholic.pvp.net.PvpSyncPayload;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.impl.networking.AbstractChanneledNetworkAddon;
import net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A test player that talks to the server the way a real client does: every client → server message is a
 * {@link CustomPacketPayload} encoded with its registered codec, decoded again and handed to the player's real
 * packet listener ({@code handleCustomPayload} → Fabric's receivers → the mod's C2S handlers). Every server → client
 * custom payload goes through the player's connection (an in-memory channel), is captured, round-tripped through
 * its codec and fed to a tiny model of the client's PvP screen routing ({@code ClientPvp.receive}): which PvP screen
 * would be showing ({@code lobby}, {@code match:<mode>}, {@code result}) — so a test can assert that the duel
 * screen really opens for BOTH players.
 *
 * <p>The mock player has no real client, so the channels a real client announces (minecraft:register) are
 * declared here for the mod's clientbound payloads; nothing else about the server is faked.
 */
final class MockClient {
	private static final Transaction TEST = Transaction.of("core", "gametest");

	final ServerPlayer player;
	private final EmbeddedChannel channel;
	/** Every clientbound custom payload received so far (decoded). */
	final List<CustomPacketPayload> inbox = new ArrayList<>();
	/** Chat lines received (system chat), as plain strings with click commands appended {@code [cmd]}. */
	final List<String> chat = new ArrayList<>();
	/** Which PvP screen the client would show: null, "lobby", "match:&lt;mode&gt;", "result". */
	@Nullable String screen;
	/** Last PvP state the client holds (as ClientPvp.state()). */
	@Nullable JsonObject state;
	String kind = "clear";
	/** Screens the PvP sync ever opened, in order. */
	final List<String> opened = new ArrayList<>();
	/** Last machine state per table (TableSyncPayload). */
	@Nullable CompoundTag table;
	final List<Component> errors = new ArrayList<>();

	private MockClient(net.minecraft.server.level.ServerLevel level, Vec3 abs, long balance) {
		this(newPlayer(level, java.util.UUID.randomUUID()), abs, balance, true);
	}

	private MockClient(ServerPlayer p, Vec3 abs, long balance, boolean setBalance) {
		player = p;
		player.setPos(abs.x, abs.y, abs.z);
		if (setBalance) {
			Economies.get().setBalance(p.level().getServer(), player.getUUID(), balance, TEST);
		}
		channel = channelOf(connectionOf(player));
		declareChannels();
		drain();
	}

	/** Like {@code GameTestHelper.makeMockServerPlayerInLevel}, but with a unique name (commands target players by name). */
	private static ServerPlayer newPlayer(net.minecraft.server.level.ServerLevel level, java.util.UUID id) {
		com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(id, "rf" + id.toString().substring(0, 8));
		net.minecraft.server.network.CommonListenerCookie cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
		ServerPlayer p = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
		return p;
	}

	/** A test player at {@code at} (relative to the test structure). */
	static MockClient join(GameTestHelper helper, Vec3 at, long balance) {
		return new MockClient(helper.getLevel(), helper.absoluteVec(at), balance);
	}

	/** A second player on any server (client GameTests: the rival of the real client player), at absolute {@code at}. */
	static MockClient spawn(net.minecraft.server.level.ServerLevel level, Vec3 at, long balance) {
		return new MockClient(level, at, balance);
	}

	/** The same player (UUID, name) joining again after a disconnect (tester T-E2); the balance is kept. */
	static MockClient rejoin(GameTestHelper helper, java.util.UUID id, Vec3 at) {
		return new MockClient(newPlayer(helper.getLevel(), id), helper.absoluteVec(at), 0, false);
	}

	/** Disconnects the test player. */
	void remove(MinecraftServer server) {
		server.getPlayerList().remove(player);
	}

	private static Connection connectionOf(ServerPlayer player) {
		try {
			Field f = net.minecraft.server.network.ServerCommonPacketListenerImpl.class.getDeclaredField("connection");
			f.setAccessible(true);
			return (Connection) f.get(player.connection);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static EmbeddedChannel channelOf(Connection connection) {
		try {
			Field f = Connection.class.getDeclaredField("channel");
			f.setAccessible(true);
			Channel c = (Channel) f.get(connection);
			return (EmbeddedChannel) c;
		} catch (ReflectiveOperationException | ClassCastException e) {
			throw new IllegalStateException("mock player connection has no embedded channel", e);
		}
	}

	/** What a real client announces after joining (Fabric's minecraft:register): the mod's clientbound channels. */
	@SuppressWarnings("unchecked")
	private void declareChannels() {
		List<Identifier> ids = new ArrayList<>();
		for (CustomPacketPayload.Type<?> t : clientboundTypes()) {
			if (t != null) {
				ids.add(t.id());
			}
		}
		try {
			AbstractChanneledNetworkAddon<?> addon = ServerNetworkingImpl.getAddon(player.connection);
			Field f = AbstractChanneledNetworkAddon.class.getDeclaredField("sendableChannels");
			f.setAccessible(true);
			((Set<Identifier>) f.get(addon)).addAll(ids);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static List<CustomPacketPayload.Type<?>> clientboundTypes() {
		List<CustomPacketPayload.Type<?>> out = new ArrayList<>();
		out.add(PvpSyncPayload.TYPE);
		out.add(MenuPayload.TYPE);
		out.add(TableSyncPayload.TYPE);
		out.add(TableErrorPayload.TYPE);
		out.add(ExtrasScreenPayload.TYPE);
		out.add(ExtrasErrorPayload.TYPE);
		out.add(dev.nezo.burmaldaholic.games.extras.pvp.coin.CoinDuelNet.Screen.TYPE);
		out.add(dev.nezo.burmaldaholic.games.extras.pvp.wheel.WheelPartyNet.Panel.TYPE);
		return out;
	}

	MinecraftServer server() {
		return player.level().getServer();
	}

	long balance() {
		return Economies.get().balance(player);
	}

	// ---- client -> server ------------------------------------------------------------------------------

	/** Sends a C2S payload exactly like the network does: encode → decode → the player's packet listener. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	void send(CustomPacketPayload payload) {
		var codec = (net.minecraft.network.codec.StreamCodec) ((net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl) PayloadTypeRegistry.serverboundPlay())
			.get(payload.type()).codec();
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server().registryAccess());
		codec.encode(buf, payload);
		CustomPacketPayload decoded = (CustomPacketPayload) codec.decode(buf);
		if (buf.readableBytes() != 0) {
			throw new AssertionError("C2S " + payload.type().id() + ": " + buf.readableBytes() + " bytes left after decode");
		}
		player.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(decoded));
		drain();
	}

	/** Runs a chat command as the player (what clicking a chat [Accept] does). */
	void command(String command) {
		server().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
		drain();
	}

	// ---- server -> client ------------------------------------------------------------------------------

	/** Reads everything the server sent since the last call and updates the client model. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	void drain() {
		Object o;
		while ((o = channel.readOutbound()) != null) {
			List<Packet<?>> packets = new ArrayList<>();
			if (o instanceof ClientboundBundlePacket b) {
				b.subPackets().forEach(packets::add);
			} else if (o instanceof Packet<?> p) {
				packets.add(p);
			}
			for (Packet<?> p : packets) {
				if (p instanceof ClientboundSystemChatPacket c && !c.overlay()) {
					chat.add(flatten(c.content()));
				}
				if (!(p instanceof ClientboundCustomPayloadPacket cp)) {
					continue;
				}
				CustomPacketPayload payload = cp.payload();
				var tc = ((net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl) PayloadTypeRegistry.clientboundPlay()).get(payload.type().id());
				if (tc != null) {
					RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server().registryAccess());
					((net.minecraft.network.codec.StreamCodec) tc.codec()).encode(buf, payload);
					payload = (CustomPacketPayload) ((net.minecraft.network.codec.StreamCodec) tc.codec()).decode(buf);
				}
				inbox.add(payload);
				receive(payload);
			}
		}
	}

	private static String flatten(Component c) {
		StringBuilder b = new StringBuilder();
		c.visit((style, text) -> {
			b.append(text);
			if (style.getClickEvent() instanceof net.minecraft.network.chat.ClickEvent.RunCommand rc) {
				b.append("[").append(rc.command()).append("]");
			}
			return java.util.Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
		return b.toString();
	}

	/** The client's routing of the PvP sync (mirrors ClientPvp.receive). */
	private void receive(CustomPacketPayload payload) {
		if (payload instanceof TableSyncPayload t) {
			table = t.state();
			return;
		}
		if (payload instanceof TableErrorPayload e) {
			errors.add(e.message());
			return;
		}
		if (payload instanceof dev.nezo.burmaldaholic.games.extras.pvp.wheel.WheelPartyNet.Panel panel && !panel.message().getString().isEmpty()) {
			errors.add(panel.message());
		}
		if (payload instanceof dev.nezo.burmaldaholic.games.extras.pvp.coin.CoinDuelNet.Screen cs && !cs.message().getString().isEmpty()) {
			errors.add(cs.message());
		}
		if (payload instanceof ExtrasErrorPayload ee) {
			errors.add(ee.message());
		}
		if (!(payload instanceof PvpSyncPayload p)) {
			return;
		}
		if (p.kind().equals("error")) {
			errors.add(Component.literal(p.json()));
			return;
		}
		boolean pvpScreen = screen != null;
		if (p.kind().equals("clear")) {
			state = null;
			kind = "clear";
			if (screen != null && !screen.equals("result")) {
				screen = null;
			}
			return;
		}
		JsonObject s = JsonParser.parseString(p.json()).getAsJsonObject();
		state = s;
		kind = p.kind();
		boolean mayOpen = p.open() || pvpScreen;
		String next = switch (p.kind()) {
			case "lobby" -> "lobby";
			case "match" -> s.has("spectator") && s.get("spectator").getAsBoolean() ? null : "match:" + s.get("mode").getAsString();
			case "result" -> screen != null && screen.startsWith("match:") && !p.open() ? screen : "result";
			default -> null;
		};
		if (next != null && mayOpen && !next.equals(screen)) {
			screen = next;
			opened.add(next);
		}
	}

	/** Closes whatever screen (Esc). */
	void closeScreen() {
		screen = null;
	}

	/** The id of the match in the client's PvP state, or "". */
	String matchId() {
		return state == null || !state.has("id") ? "" : state.get("id").getAsString();
	}

	/** The last clickable chat command containing {@code fragment}, or null. */
	@Nullable String chatCommand(String fragment) {
		for (int i = chat.size() - 1; i >= 0; i--) {
			String line = chat.get(i);
			int at = line.indexOf("[/" + fragment);
			if (at >= 0) {
				return line.substring(at + 2, line.indexOf(']', at));
			}
		}
		return null;
	}

	void remove(GameTestHelper helper) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	@Override
	public String toString() {
		return player.getName().getString() + "{screen=" + screen + ", kind=" + kind + ", opened=" + opened + ", errors=" + errors.stream()
			.map(Component::getString).toList() + ", chat=" + chat.subList(Math.max(0, chat.size() - 4), chat.size()) + ", got="
			+ inbox.subList(Math.max(0, inbox.size() - 6), inbox.size()).stream().map(x -> x.type().id().getPath()).toList() + "}";
	}

	static CompoundTag args(Object... kv) {
		CompoundTag t = new CompoundTag();
		for (int i = 0; i + 1 < kv.length; i += 2) {
			String k = (String) kv[i];
			Object v = kv[i + 1];
			if (v instanceof Integer n) {
				t.putInt(k, n);
			} else if (v instanceof Long n) {
				t.putLong(k, n);
			} else if (v instanceof Boolean b) {
				t.putBoolean(k, b);
			} else {
				t.putString(k, String.valueOf(v));
			}
		}
		return t;
	}

	static BlockPos above(BlockPos p) {
		return p.above();
	}
}
