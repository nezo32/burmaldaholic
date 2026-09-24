package dev.nezo.burmaldaholic.client.config;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.nezo.burmaldaholic.core.config.ConfigBinder;
import dev.nezo.burmaldaholic.core.config.ConfigHandle;
import dev.nezo.burmaldaholic.core.config.ConfigManager;
import dev.nezo.burmaldaholic.core.config.Family;
import dev.nezo.burmaldaholic.core.config.Member;
import dev.nezo.burmaldaholic.core.config.Range;
import dev.nezo.burmaldaholic.core.config.TranslatableEnum;
import dev.nezo.burmaldaholic.core.text.Numbers;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * Generated editor for one config page. Rows are built by reflection over the section classes
 * ({@code core/config/sections}), so every CONFIG.md key appears automatically:
 * booleans → ON/OFF, enums → cycle button, numbers → validated text field (range in the tooltip),
 * lists / maps → JSON text field, families ({@code chaos.weight.<event>}) → one row per member.
 * Labels: {@code config.burmaldaholic.<key>}, falling back to the family template with the member
 * name as {@code %1$s}; {@code .tooltip} keys are shown when present.
 */
final class ConfigSectionScreen extends Screen {
	private final Screen parent;
	private final CasinoConfigScreen.Page page;
	private final JsonObject working;
	private final JsonObject defaults = ConfigManager.get().defaultsJson();
	private final HeaderAndFooterLayout layout;
	private final Set<Row> invalid = new HashSet<>();
	private @Nullable RowList list;
	private @Nullable Button doneButton;

	ConfigSectionScreen(Screen parent, CasinoConfigScreen.Page page, JsonObject working) {
		super(page.label());
		this.parent = parent;
		this.page = page;
		this.working = working;
		this.layout = new HeaderAndFooterLayout(this, 24, 33);
	}

	@Override
	protected void init() {
		layout.addToHeader(new StringWidget(title, font));
		list = layout.addToContents(new RowList());
		LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
		doneButton = footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).build());
		layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
	}

	@Override
	protected void repositionElements() {
		layout.arrangeElements();
		if (list != null) {
			list.updateSize(width, layout);
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	private void setValid(Row row, boolean valid) {
		if (valid) {
			invalid.remove(row);
		} else {
			invalid.add(row);
		}
		if (doneButton != null) {
			doneButton.active = invalid.isEmpty();
		}
	}

	// ---- row model ----------------------------------------------------------------------------

	private record MemberName(String segment, Component name) {}

	private List<Row> buildRows() {
		List<Row> rows = new ArrayList<>();
		for (String section : page.sections()) {
			ConfigManager.get().handle(section).ifPresent(handle -> {
				JsonElement json = working.get(section);
				collect(sectionType(handle), section, json != null && json.isJsonObject() ? json.getAsJsonObject() : new JsonObject(), List.of(), rows);
			});
		}
		return rows;
	}

	private static Class<?> sectionType(ConfigHandle<?> handle) {
		return handle.get().getClass();
	}

	private void collect(Class<?> type, String path, JsonObject json, List<MemberName> members, List<Row> rows) {
		for (Field f : ConfigBinder.fields(type)) {
			String key = path + "." + f.getName();
			JsonElement value = json.get(f.getName());
			Class<?> raw = f.getType();
			Family family = f.getAnnotation(Family.class);
			Member member = f.getAnnotation(Member.class);
			if (Map.class.isAssignableFrom(raw) && family != null && value != null && value.isJsonObject()) {
				Class<?> valueType = mapValueType(f.getGenericType());
				for (Map.Entry<String, JsonElement> e : value.getAsJsonObject().entrySet()) {
					List<MemberName> m = with(members, new MemberName(e.getKey(), memberName(family, e.getKey())));
					String sub = key + "." + e.getKey();
					if (ConfigBinder.isNested(valueType) && e.getValue().isJsonObject()) {
						collect(valueType, sub, e.getValue().getAsJsonObject(), m, rows);
					} else {
						rows.add(leaf(valueType, f, sub, m));
					}
				}
			} else if (ConfigBinder.isNested(raw) && value != null && value.isJsonObject()) {
				List<MemberName> m = member == null ? members : with(members, new MemberName(f.getName(), Component.translatable(member.value())));
				collect(raw, key, value.getAsJsonObject(), m, rows);
			} else {
				rows.add(leaf(raw, f, key, members));
			}
		}
	}

	private static List<MemberName> with(List<MemberName> list, MemberName m) {
		List<MemberName> copy = new ArrayList<>(list);
		copy.add(0, m);
		return copy;
	}

	private static Class<?> mapValueType(Type type) {
		if (type instanceof ParameterizedType p && p.getActualTypeArguments()[1] instanceof Class<?> c) {
			return c;
		}
		return Object.class;
	}

	private static Component memberName(Family family, String id) {
		if (family.member().equals("item")) {
			Identifier itemId = Identifier.tryParse(id);
			if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
				return Component.translatable(BuiltInRegistries.ITEM.getValue(itemId).getDescriptionId());
			}
		} else if (!family.member().isEmpty() && Language.getInstance().has(family.member().formatted(id))) {
			return Component.translatable(family.member().formatted(id));
		}
		return Texts.raw(id);
	}

	/** {@code config.burmaldaholic.<key>}, else the family template with the member's name. */
	private static Component label(String key, List<MemberName> members) {
		if (Language.getInstance().has("config.burmaldaholic." + key)) {
			return Component.translatable("config.burmaldaholic." + key);
		}
		for (MemberName m : members) {
			String template = strip(key, m.segment());
			if (template != null && Language.getInstance().has("config.burmaldaholic." + template)) {
				return Component.translatable("config.burmaldaholic." + template, m.name());
			}
		}
		return Texts.raw(key);
	}

	private static @Nullable String strip(String key, String segment) {
		if (key.endsWith("." + segment)) {
			return key.substring(0, key.length() - segment.length() - 1);
		}
		int i = key.indexOf("." + segment + ".");
		return i < 0 ? null : key.substring(0, i) + key.substring(i + segment.length() + 1);
	}

	private Row leaf(Class<?> raw, Field annotations, String key, List<MemberName> members) {
		Component label = label(key, members);
		List<FormattedCharSequence> tooltip = tooltip(key, raw, annotations, members);
		JsonElement current = ConfigManager.at(working, key);
		if (raw == boolean.class || raw == Boolean.class) {
			return new BoolRow(key, label, tooltip, current != null && current.isJsonPrimitive() && current.getAsBoolean());
		}
		if (raw.isEnum()) {
			return new EnumRow(key, label, tooltip, raw, current);
		}
		boolean integral = raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class;
		boolean numeric = integral || raw == double.class || raw == Double.class || raw == float.class || raw == Float.class;
		return new TextRow(key, label, tooltip, current, numeric, integral);
	}

	private List<FormattedCharSequence> tooltip(String key, Class<?> raw, Field f, List<MemberName> members) {
		List<Component> lines = new ArrayList<>();
		lines.add(Texts.raw(key).withStyle(ChatFormatting.YELLOW));
		String tip = "config.burmaldaholic." + key + ".tooltip";
		if (Language.getInstance().has(tip)) {
			lines.add(Component.translatable(tip));
		}
		Range range = f.getAnnotation(Range.class);
		if (range != null) {
			lines.add(Component.translatable("config.burmaldaholic.range", Texts.raw(num(range.min())), Texts.raw(num(range.max()))).withStyle(ChatFormatting.GRAY));
		}
		JsonElement def = ConfigManager.at(defaults, key);
		if (def != null) {
			Component shown = Texts.raw(def.toString());
			if (raw.isEnum() && def.isJsonPrimitive()) {
				for (Object v : raw.getEnumConstants()) {
					if (((Enum<?>) v).name().equalsIgnoreCase(def.getAsString())) {
						shown = enumName(key, v);
					}
				}
			}
			lines.add(Component.translatable("editGamerule.default", shown).withStyle(ChatFormatting.GRAY));
		}
		List<FormattedCharSequence> out = new ArrayList<>();
		for (Component c : lines) {
			out.addAll(font.split(c, 200));
		}
		return out;
	}

	private static String num(double d) {
		return d == Math.rint(d) && Math.abs(d) < 1e15 ? Numbers.format((long) d) : Double.toString(d);
	}

	private void put(String key, JsonElement value) {
		ConfigManager.put(working, key, value);
	}

	private JsonElement defaultOf(String key) {
		JsonElement def = ConfigManager.at(defaults, key);
		return def == null ? new JsonPrimitive(0) : def.deepCopy();
	}

	// ---- rows ---------------------------------------------------------------------------------

	abstract class Row extends ContainerObjectSelectionList.Entry<Row> {
		final String key;
		final List<FormattedCharSequence> labelLines;
		final List<FormattedCharSequence> tooltip;
		final List<AbstractWidget> widgets = new ArrayList<>();
		final Button reset;

		Row(String key, Component label, List<FormattedCharSequence> tooltip) {
			this.key = key;
			this.labelLines = font.split(label, 170);
			this.tooltip = tooltip;
			this.reset = Button.builder(Texts.raw("↺"), b -> resetToDefault()).bounds(0, 0, 20, 20).build();
			reset.setTooltip(Tooltip.create(Component.translatable("config.burmaldaholic.reset")));
		}

		abstract AbstractWidget control();

		abstract void resetToDefault();

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
			int y = getContentY();
			if (labelLines.size() == 1) {
				graphics.text(font, labelLines.get(0), getContentX(), y + 6, -1);
			} else if (!labelLines.isEmpty()) {
				graphics.text(font, labelLines.get(0), getContentX(), y, -1);
				graphics.text(font, labelLines.get(1), getContentX(), y + 10, -1);
			}
			AbstractWidget control = control();
			reset.setX(getContentRight() - 20);
			reset.setY(y);
			control.setX(getContentRight() - 24 - control.getWidth());
			control.setY(y);
			control.extractRenderState(graphics, mouseX, mouseY, a);
			reset.extractRenderState(graphics, mouseX, mouseY, a);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return ImmutableList.of(control(), reset);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return ImmutableList.of(control(), reset);
		}
	}

	final class BoolRow extends Row {
		private final CycleButton<Boolean> button;

		BoolRow(String key, Component label, List<FormattedCharSequence> tooltip, boolean value) {
			super(key, label, tooltip);
			button = CycleButton.booleanBuilder(Component.translatable("gui.burmaldaholic.common.on"), Component.translatable("gui.burmaldaholic.common.off"), value)
				.displayOnlyValue()
				.create(0, 0, 60, 20, label, (b, v) -> put(key, new JsonPrimitive(v)));
		}

		@Override
		AbstractWidget control() {
			return button;
		}

		@Override
		void resetToDefault() {
			JsonElement def = defaultOf(key);
			boolean v = def.isJsonPrimitive() && def.getAsBoolean();
			button.setValue(v);
			put(key, new JsonPrimitive(v));
		}
	}

	final class EnumRow extends Row {
		private final CycleButton<Object> button;
		private final Class<?> type;

		EnumRow(String key, Component label, List<FormattedCharSequence> tooltip, Class<?> type, @Nullable JsonElement current) {
			super(key, label, tooltip);
			this.type = type;
			Object initial = constant(current);
			button = CycleButton.<Object>builder(v -> enumName(key, v), initial)
				.withValues(List.of(type.getEnumConstants()))
				.displayOnlyValue()
				.create(0, 0, 110, 20, label, (b, v) -> put(key, new JsonPrimitive(((Enum<?>) v).name())));
		}

		private Object constant(@Nullable JsonElement json) {
			Object[] values = type.getEnumConstants();
			if (json != null && json.isJsonPrimitive()) {
				for (Object v : values) {
					if (((Enum<?>) v).name().equalsIgnoreCase(json.getAsString())) {
						return v;
					}
				}
			}
			return values[0];
		}

		@Override
		AbstractWidget control() {
			return button;
		}

		@Override
		void resetToDefault() {
			Object v = constant(defaultOf(key));
			button.setValue(v);
			put(key, new JsonPrimitive(((Enum<?>) v).name()));
		}
	}

	/** {@code config.burmaldaholic.<key>.<value>} (lower case), else the enum's own key, else its name. */
	static Component enumName(String key, Object value) {
		String name = ((Enum<?>) value).name();
		String own = "config.burmaldaholic." + key + "." + name.toLowerCase(Locale.ROOT);
		if (Language.getInstance().has(own)) {
			return Component.translatable(own);
		}
		if (value instanceof TranslatableEnum t) {
			return Component.translatable(t.translationKey());
		}
		return Texts.raw(name);
	}

	final class TextRow extends Row {
		private final EditBox box;
		private final boolean numeric;
		private final boolean integral;

		TextRow(String key, Component label, List<FormattedCharSequence> tooltip, @Nullable JsonElement current, boolean numeric, boolean integral) {
			super(key, label, tooltip);
			this.numeric = numeric;
			this.integral = integral;
			box = new EditBox(font, 0, 0, numeric ? 80 : 130, 20, label);
			box.setMaxLength(numeric ? 24 : 8192);
			box.setValue(current == null ? "" : text(current));
			box.setResponder(this::changed);
		}

		private String text(JsonElement e) {
			return numeric && e.isJsonPrimitive() ? e.getAsJsonPrimitive().getAsBigDecimal().stripTrailingZeros().toPlainString() : e.toString();
		}

		private void changed(String value) {
			JsonElement parsed = parse(value);
			boolean ok = parsed != null;
			box.setTextColor(ok ? 0xFFE0E0E0 : 0xFFFF0000);
			setValid(this, ok);
			if (ok) {
				put(key, parsed);
			}
		}

		private @Nullable JsonElement parse(String value) {
			try {
				if (numeric) {
					BigDecimal d = new BigDecimal(value.trim().replace(" ", ""));
					if (integral && d.stripTrailingZeros().scale() > 0) {
						return null;
					}
					return new JsonPrimitive(d);
				}
				return JsonParser.parseString(value);
			} catch (RuntimeException e) {
				return null;
			}
		}

		@Override
		AbstractWidget control() {
			return box;
		}

		@Override
		void resetToDefault() {
			box.setValue(text(defaultOf(key)));
		}
	}

	final class RowList extends ContainerObjectSelectionList<Row> {
		RowList() {
			super(Minecraft.getInstance(), ConfigSectionScreen.this.width, layout.getContentHeight(), layout.getHeaderHeight(), 24);
			buildRows().forEach(this::addEntry);
		}

		@Override
		public int getRowWidth() {
			return Math.min(360, ConfigSectionScreen.this.width - 40);
		}

		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
			Row hovered = getHovered();
			if (hovered != null && !hovered.tooltip.isEmpty()) {
				graphics.setTooltipForNextFrame(hovered.tooltip, mouseX, mouseY);
			}
		}
	}
}
