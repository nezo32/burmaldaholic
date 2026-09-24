package dev.nezo.burmaldaholic.client.pvp.kit;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Faces on plates, legend rows and the podium (visual/extras.md §7): players through {@link PlayerFaceExtractor}
 * (the connection's skin, else the default skin of the UUID); bots by name theme: the generated bot heads
 * ({@code core/cards/bot/*}), a drawn creeper face for creeper-themed names.
 */
public final class Faces {
	private Faces() {}

	/**
	 * Draws the face of a participant key ({@code UUID} for humans, {@code bot:…} for bots) at {@code size} px.
	 *
	 * @param nameKey the bot's name key or id (theme), ignored for humans
	 */
	public static void draw(GuiGraphicsExtractor g, String key, boolean bot, String nameKey, int x, int y, int size) {
		if (bot) {
			bot(g, nameKey, x, y, size);
			return;
		}
		PlayerSkin skin;
		UUID id = parse(key);
		PlayerInfo info = id == null || Minecraft.getInstance().getConnection() == null ? null
			: Minecraft.getInstance().getConnection().getPlayerInfo(id);
		if (info != null) skin = info.getSkin();
		else skin = id == null ? DefaultPlayerSkin.getDefaultSkin() : DefaultPlayerSkin.get(id);
		PlayerFaceExtractor.extractRenderState(g, skin, x, y, size);
	}

	private static UUID parse(String key) {
		try {
			return UUID.fromString(key);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Bot face by name theme. */
	public static void bot(GuiGraphicsExtractor g, String nameKey, int x, int y, int size) {
		String n = nameKey == null ? "" : nameKey.substring(nameKey.lastIndexOf('.') + 1);
		if (n.contains("creeper")) {
			creeper(g, x, y, size);
			return;
		}
		String sprite;
		if (n.contains("shulker")) sprite = "shulker";
		else if (n.contains("ender") || n.contains("void") || n.contains("pearl")) sprite = "enderman";
		else if (n.contains("tusk") || n.contains("boris")) sprite = "brute";
		else if (n.contains("piglin") || n.contains("nether") || n.contains("goldie") || n.contains("crimson")) sprite = "piglin";
		else if (n.contains("bella") || n.contains("zoya") || n.contains("valya") || n.contains("luckless")) sprite = "witch";
		else sprite = "villager";
		if (!Kit.sprite(g, Kit.core("cards/bot/" + sprite), x, y, size, size)) creeper(g, x, y, size);
	}

	/** The creeper face (8 × 8 grid). */
	public static void creeper(GuiGraphicsExtractor g, int x, int y, int size) {
		g.fill(x, y, x + size, y + size, 0xFF50BE46);
		int c = Math.max(1, size / 8);
		int ox = x + (size - 8 * c) / 2;
		int oy = y + (size - 8 * c) / 2;
		int dark = 0xFF32962F;
		g.fill(ox + 2 * c, oy, ox + 3 * c, oy + c, dark);
		g.fill(ox + 6 * c, oy + c, ox + 7 * c, oy + 2 * c, dark);
		g.fill(ox, oy + 4 * c, ox + c, oy + 5 * c, dark);
		int k = 0xFF101010;
		g.fill(ox + c, oy + 2 * c, ox + 3 * c, oy + 4 * c, k);
		g.fill(ox + 5 * c, oy + 2 * c, ox + 7 * c, oy + 4 * c, k);
		g.fill(ox + 3 * c, oy + 4 * c, ox + 5 * c, oy + 6 * c, k);
		g.fill(ox + 2 * c, oy + 5 * c, ox + 3 * c, oy + 7 * c, k);
		g.fill(ox + 5 * c, oy + 5 * c, ox + 6 * c, oy + 7 * c, k);
	}
}
