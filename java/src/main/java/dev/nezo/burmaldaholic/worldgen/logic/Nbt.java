package dev.nezo.burmaldaholic.worldgen.logic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Minimal Named Binary Tag codec (PURE) for the structure template generator and its tests.
 * Values: {@link Byte}, {@link Integer}, {@link Long}, {@link Float}, {@link Double}, {@link String},
 * {@code Map<String, Object>} (compound, insertion order kept), {@link TagList}.
 */
public final class Nbt {
	public static final int END = 0, BYTE = 1, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6, STRING = 8, LIST = 9, COMPOUND = 10;

	private Nbt() {}

	/** A typed list; {@code elementType} matters for empty lists (use {@link #END}). */
	public record TagList(int elementType, List<Object> items) {
		public static TagList ints(int... values) {
			List<Object> l = new ArrayList<>();
			for (int v : values) {
				l.add(v);
			}
			return new TagList(INT, l);
		}

		public static TagList compounds(List<? extends Map<String, Object>> values) {
			return new TagList(values.isEmpty() ? END : COMPOUND, new ArrayList<>(values));
		}
	}

	public static Map<String, Object> compound() {
		return new LinkedHashMap<>();
	}

	/** Gzipped root compound (unnamed root, like vanilla structure files). Deterministic bytes. */
	public static byte[] writeGzip(Map<String, Object> root) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
			out.writeByte(COMPOUND);
			out.writeUTF("");
			writePayload(out, root);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return bytes.toByteArray();
	}

	public static Map<String, Object> readGzip(byte[] data) {
		try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(data)))) {
			int type = in.readByte();
			if (type != COMPOUND) {
				throw new IOException("root is not a compound");
			}
			in.readUTF();
			@SuppressWarnings("unchecked")
			Map<String, Object> root = (Map<String, Object>) readPayload(in, COMPOUND);
			return root;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static int typeOf(Object v) {
		if (v instanceof Byte) {
			return BYTE;
		} else if (v instanceof Integer) {
			return INT;
		} else if (v instanceof Long) {
			return LONG;
		} else if (v instanceof Float) {
			return FLOAT;
		} else if (v instanceof Double) {
			return DOUBLE;
		} else if (v instanceof String) {
			return STRING;
		} else if (v instanceof TagList) {
			return LIST;
		} else if (v instanceof Map<?, ?>) {
			return COMPOUND;
		}
		throw new IllegalArgumentException("unsupported NBT value " + v);
	}

	@SuppressWarnings("unchecked")
	private static void writePayload(DataOutputStream out, Object v) throws IOException {
		switch (typeOf(v)) {
			case BYTE -> out.writeByte((Byte) v);
			case INT -> out.writeInt((Integer) v);
			case LONG -> out.writeLong((Long) v);
			case FLOAT -> out.writeFloat((Float) v);
			case DOUBLE -> out.writeDouble((Double) v);
			case STRING -> out.writeUTF((String) v);
			case LIST -> {
				TagList list = (TagList) v;
				out.writeByte(list.elementType());
				out.writeInt(list.items().size());
				for (Object item : list.items()) {
					if (typeOf(item) != list.elementType()) {
						throw new IllegalArgumentException("mixed NBT list");
					}
					writePayload(out, item);
				}
			}
			case COMPOUND -> {
				for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
					out.writeByte(typeOf(e.getValue()));
					out.writeUTF(e.getKey());
					writePayload(out, e.getValue());
				}
				out.writeByte(END);
			}
			default -> throw new IllegalStateException();
		}
	}

	private static Object readPayload(DataInputStream in, int type) throws IOException {
		return switch (type) {
			case BYTE -> in.readByte();
			case INT -> in.readInt();
			case LONG -> in.readLong();
			case FLOAT -> in.readFloat();
			case DOUBLE -> in.readDouble();
			case STRING -> in.readUTF();
			case LIST -> {
				int elementType = in.readByte();
				int n = in.readInt();
				List<Object> items = new ArrayList<>(n);
				for (int i = 0; i < n; i++) {
					items.add(readPayload(in, elementType));
				}
				yield new TagList(elementType, items);
			}
			case COMPOUND -> {
				Map<String, Object> map = compound();
				for (int t = in.readByte(); t != END; t = in.readByte()) {
					String key = in.readUTF();
					map.put(key, readPayload(in, t));
				}
				yield map;
			}
			default -> throw new IOException("unsupported NBT tag type " + type);
		};
	}
}
