package dev.nezo.burmaldaholic.client.hud;

import dev.nezo.burmaldaholic.core.network.PlayerStatusPayload;
import net.minecraft.client.Minecraft;

/** What a {@link HudSegment} may read while building its lines. */
public record HudContext(Minecraft minecraft, PlayerStatusPayload status, long clientTicks, float partialTick) {}
