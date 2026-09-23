package dev.nezo.burmaldaholic.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Binds a JSON tree onto a config object field by field (pure Java, unit-tested):
 * missing keys keep the default, a wrong type resets only that field to its default
 * ({@link ConfigIssue.Kind#INVALID}), numbers outside {@link Range} are clamped
 * ({@link ConfigIssue.Kind#CLAMPED}), unknown keys are reported ({@link ConfigIssue.Kind#UNKNOWN})
 * and dropped, then {@link Validatable#validate} runs for cross-field rules.
 *
 * <p>Supported field types: boolean, int, long, double (and boxes), String, enums, nested config
 * objects (public no-arg class with public fields), {@code Map<String, V>} (V = any supported type),
 * arrays and {@code List}s (parsed with Gson as a whole).
 */
public final class ConfigBinder {
	private final Gson gson;

	public ConfigBinder(Gson gson) {
		this.gson = gson;
	}

	/** Binds {@code json} onto {@code target} (mutated in place and returned). */
	public <T> T bind(T target, JsonObject json, String path, List<ConfigIssue> issues) {
		bindObject(target, json, path, issues);
		return target;
	}

	public static List<Field> fields(Class<?> type) {
		List<Field> result = new ArrayList<>();
		for (Field f : type.getFields()) {
			int mod = f.getModifiers();
			if (!Modifier.isStatic(mod) && !Modifier.isTransient(mod) && !Modifier.isFinal(mod)) {
				result.add(f);
			}
		}
		return result;
	}

	/** True for classes treated as nested config objects (recursed into field by field). */
	public static boolean isNested(Class<?> raw) {
		return !raw.isPrimitive() && !raw.isArray() && !raw.isEnum() && !raw.isInterface()
			&& raw != String.class && !Number.class.isAssignableFrom(raw) && raw != Boolean.class
			&& !Collection.class.isAssignableFrom(raw) && !Map.class.isAssignableFrom(raw);
	}

	private void bindObject(Object target, JsonObject json, String path, List<ConfigIssue> issues) {
		Set<String> known = new HashSet<>();
		for (Field f : fields(target.getClass())) {
			known.add(f.getName());
			JsonElement je = json.get(f.getName());
			if (je == null || je.isJsonNull()) {
				continue;
			}
			try {
				Object def = f.get(target);
				f.set(target, bindValue(f.getGenericType(), f, def, je, path + "." + f.getName(), issues));
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
		for (String key : json.keySet()) {
			if (!known.contains(key)) {
				issues.add(new ConfigIssue(path + "." + key, ConfigIssue.Kind.UNKNOWN, json.get(key).toString(), ""));
			}
		}
		if (target instanceof Validatable v) {
			v.validate(new Validatable.Issues() {
				@Override
				public void clamped(String relativeKey, Object oldValue, Object newValue) {
					issues.add(new ConfigIssue(path + "." + relativeKey, ConfigIssue.Kind.CLAMPED, String.valueOf(oldValue), String.valueOf(newValue)));
				}

				@Override
				public void invalid(String relativeKey, Object oldValue, Object newValue) {
					issues.add(new ConfigIssue(path + "." + relativeKey, ConfigIssue.Kind.INVALID, String.valueOf(oldValue), String.valueOf(newValue)));
				}
			});
		}
	}

	private Object invalid(String key, JsonElement je, Object def, List<ConfigIssue> issues) {
		issues.add(new ConfigIssue(key, ConfigIssue.Kind.INVALID, je.toString(), gson.toJson(def)));
		return def;
	}

	private Object bindValue(Type type, Field annotations, Object def, JsonElement je, String key, List<ConfigIssue> issues) {
		Class<?> raw = rawClass(type);
		Range range = annotations == null ? null : annotations.getAnnotation(Range.class);
		if (raw == boolean.class || raw == Boolean.class) {
			if (je.isJsonPrimitive() && je.getAsJsonPrimitive().isBoolean()) {
				return je.getAsBoolean();
			}
			return invalid(key, je, def, issues);
		}
		if (raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class
			|| raw == double.class || raw == Double.class || raw == float.class || raw == Float.class) {
			if (!(je.isJsonPrimitive() && je.getAsJsonPrimitive().isNumber())) {
				return invalid(key, je, def, issues);
			}
			BigDecimal value;
			try {
				value = je.getAsBigDecimal();
			} catch (NumberFormatException e) {
				return invalid(key, je, def, issues);
			}
			boolean integral = raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class;
			if (integral && value.stripTrailingZeros().scale() > 0) {
				return invalid(key, je, def, issues);
			}
			return clampNumber(raw, value, range, key, issues);
		}
		if (raw == String.class) {
			if (je.isJsonPrimitive() && je.getAsJsonPrimitive().isString()) {
				return je.getAsString();
			}
			return invalid(key, je, def, issues);
		}
		if (raw.isEnum()) {
			if (je.isJsonPrimitive() && je.getAsJsonPrimitive().isString()) {
				for (Object constant : raw.getEnumConstants()) {
					if (((Enum<?>) constant).name().equalsIgnoreCase(je.getAsString())) {
						return constant;
					}
				}
			}
			return invalid(key, je, def, issues);
		}
		if (Map.class.isAssignableFrom(raw)) {
			if (!je.isJsonObject()) {
				return invalid(key, je, def, issues);
			}
			Type valueType = type instanceof ParameterizedType p ? p.getActualTypeArguments()[1] : Object.class;
			Family family = annotations == null ? null : annotations.getAnnotation(Family.class);
			boolean open = family != null && family.open();
			@SuppressWarnings("unchecked")
			Map<String, Object> result = new LinkedHashMap<>(def == null ? Map.of() : (Map<String, Object>) def);
			for (Map.Entry<String, JsonElement> e : je.getAsJsonObject().entrySet()) {
				String sub = key + "." + e.getKey();
				Object elemDef = result.get(e.getKey());
				if (elemDef == null && !open) {
					issues.add(new ConfigIssue(sub, ConfigIssue.Kind.UNKNOWN, e.getValue().toString(), ""));
					continue;
				}
				if (elemDef == null) {
					elemDef = newDefault(rawClass(valueType));
					if (elemDef == null) {
						issues.add(new ConfigIssue(sub, ConfigIssue.Kind.INVALID, e.getValue().toString(), ""));
						continue;
					}
				}
				result.put(e.getKey(), bindValue(valueType, annotations, elemDef, e.getValue(), sub, issues));
			}
			return result;
		}
		if (raw.isArray() || Collection.class.isAssignableFrom(raw)) {
			Object parsed;
			try {
				parsed = gson.fromJson(je, type);
			} catch (RuntimeException e) {
				return invalid(key, je, def, issues);
			}
			if (parsed == null || containsNull(parsed)) {
				return invalid(key, je, def, issues);
			}
			Size size = annotations == null ? null : annotations.getAnnotation(Size.class);
			int length = raw.isArray() ? Array.getLength(parsed) : ((Collection<?>) parsed).size();
			if (size != null && (length < size.min() || length > size.max())) {
				return invalid(key, je, def, issues);
			}
			return range == null ? parsed : clampElements(parsed, range, key, issues);
		}
		if (isNested(raw)) {
			if (!je.isJsonObject()) {
				return invalid(key, je, def, issues);
			}
			Object target = def != null ? def : newDefault(raw);
			bindObject(target, je.getAsJsonObject(), key, issues);
			return target;
		}
		return invalid(key, je, def, issues);
	}

	private static boolean containsNull(Object value) {
		if (value instanceof Collection<?> c) {
			for (Object o : c) {
				if (o == null || containsNull(o)) {
					return true;
				}
			}
		} else if (value instanceof Object[] arr) {
			for (Object o : arr) {
				if (o == null || containsNull(o)) {
					return true;
				}
			}
		}
		return false;
	}

	private static Object newDefault(Class<?> raw) {
		if (raw == Integer.class) {
			return 0;
		}
		if (raw == Long.class) {
			return 0L;
		}
		if (raw == Double.class) {
			return 0.0;
		}
		if (raw == Boolean.class) {
			return false;
		}
		if (raw == String.class) {
			return "";
		}
		try {
			return raw.getConstructor().newInstance();
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	private static Object clampNumber(Class<?> raw, BigDecimal value, Range range, String key, List<ConfigIssue> issues) {
		BigDecimal clamped = value;
		if (range != null) {
			BigDecimal min = BigDecimal.valueOf(range.min());
			BigDecimal max = BigDecimal.valueOf(range.max());
			if (clamped.compareTo(min) < 0) {
				clamped = min;
			} else if (clamped.compareTo(max) > 0) {
				clamped = max;
			}
		}
		Object result;
		if (raw == int.class || raw == Integer.class) {
			clamped = clamped.max(BigDecimal.valueOf(Integer.MIN_VALUE)).min(BigDecimal.valueOf(Integer.MAX_VALUE));
			result = clamped.intValue();
		} else if (raw == long.class || raw == Long.class) {
			clamped = clamped.max(BigDecimal.valueOf(Long.MIN_VALUE)).min(BigDecimal.valueOf(Long.MAX_VALUE));
			result = clamped.longValue();
		} else if (raw == float.class || raw == Float.class) {
			result = clamped.floatValue();
		} else {
			result = clamped.doubleValue();
		}
		if (clamped.compareTo(value) != 0) {
			issues.add(new ConfigIssue(key, ConfigIssue.Kind.CLAMPED, value.toPlainString(), String.valueOf(result)));
		}
		return result;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object clampElements(Object value, Range range, String key, List<ConfigIssue> issues) {
		if (value instanceof int[] a) {
			for (int i = 0; i < a.length; i++) {
				a[i] = (int) clampNumber(int.class, BigDecimal.valueOf(a[i]), range, key + "[" + i + "]", issues);
			}
		} else if (value instanceof long[] a) {
			for (int i = 0; i < a.length; i++) {
				a[i] = (long) clampNumber(long.class, BigDecimal.valueOf(a[i]), range, key + "[" + i + "]", issues);
			}
		} else if (value instanceof double[] a) {
			for (int i = 0; i < a.length; i++) {
				a[i] = (double) clampNumber(double.class, BigDecimal.valueOf(a[i]), range, key + "[" + i + "]", issues);
			}
		} else if (value instanceof Object[] a) {
			for (int i = 0; i < a.length; i++) {
				a[i] = clampElements(a[i], range, key + "[" + i + "]", issues);
			}
		} else if (value instanceof List list) {
			for (int i = 0; i < list.size(); i++) {
				Object e = list.get(i);
				if (e instanceof Number n) {
					Class<?> cls = e instanceof Integer ? int.class : e instanceof Long ? long.class : double.class;
					list.set(i, clampNumber(cls, new BigDecimal(n.toString()), range, key + "[" + i + "]", issues));
				} else {
					list.set(i, clampElements(e, range, key + "[" + i + "]", issues));
				}
			}
		}
		return value;
	}

	private static Class<?> rawClass(Type type) {
		if (type instanceof Class<?> c) {
			return c;
		}
		if (type instanceof ParameterizedType p) {
			return (Class<?>) p.getRawType();
		}
		if (type instanceof java.lang.reflect.GenericArrayType g) {
			return Array.newInstance(rawClass(g.getGenericComponentType()), 0).getClass();
		}
		return Object.class;
	}

	/** Parses a command-line value using the shape of the current value at that key. */
	public static JsonElement parseRaw(String raw, JsonElement current) {
		String trimmed = raw.trim();
		if (current != null && current.isJsonPrimitive()) {
			JsonPrimitive p = current.getAsJsonPrimitive();
			if (p.isBoolean()) {
				String v = trimmed.toLowerCase(Locale.ROOT);
				return v.equals("true") || v.equals("false") ? new JsonPrimitive(Boolean.parseBoolean(v)) : new JsonPrimitive(trimmed);
			}
			if (p.isNumber()) {
				try {
					return new JsonPrimitive(new BigDecimal(trimmed.replace("_", "")));
				} catch (NumberFormatException e) {
					return new JsonPrimitive(trimmed);
				}
			}
			if (p.isString()) {
				return new JsonPrimitive(trimmed);
			}
		}
		try {
			return com.google.gson.JsonParser.parseString(trimmed);
		} catch (RuntimeException e) {
			return new JsonPrimitive(trimmed);
		}
	}
}
