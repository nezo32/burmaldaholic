package dev.nezo.burmaldaholic.bots.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Bot nameplates (BOTS.md §7.2): sets a text display's text, background and style flags (private in vanilla). */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {
	@Invoker("setText")
	void burmaldaholic$setText(Component text);

	@Invoker("setBackgroundColor")
	void burmaldaholic$setBackgroundColor(int argb);

	@Invoker("setFlags")
	void burmaldaholic$setFlags(byte flags);
}
