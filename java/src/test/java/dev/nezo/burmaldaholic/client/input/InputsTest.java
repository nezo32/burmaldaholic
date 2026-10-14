package dev.nezo.burmaldaholic.client.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * {@link Inputs} must hand out the numbers of the Minecraft version on the classpath (the build runs this on 26.2 and
 * 26.3, which number buttons and keys differently). Every field is checked against the same-named
 * {@link InputConstants} field, read reflectively so nothing here is inlined either.
 */
class InputsTest {
	@Test
	void mouseButtonsMatchTheRunningVersion() throws ReflectiveOperationException {
		assertEquals(constant("MOUSE_BUTTON_LEFT"), Inputs.MOUSE_LEFT);
		assertEquals(constant("MOUSE_BUTTON_RIGHT"), Inputs.MOUSE_RIGHT);
		assertEquals(constant("MOUSE_BUTTON_MIDDLE"), Inputs.MOUSE_MIDDLE);
		assertNotEquals(Inputs.MOUSE_LEFT, Inputs.MOUSE_RIGHT);
		assertNotEquals(Inputs.MOUSE_LEFT, Inputs.MOUSE_MIDDLE);
		assertNotEquals(Inputs.MOUSE_RIGHT, Inputs.MOUSE_MIDDLE);
	}

	@Test
	void buttonPredicates() {
		assertTrue(Inputs.isLeft(Inputs.MOUSE_LEFT));
		assertFalse(Inputs.isLeft(Inputs.MOUSE_RIGHT));
		assertTrue(Inputs.isRight(Inputs.MOUSE_RIGHT));
		assertFalse(Inputs.isRight(Inputs.MOUSE_LEFT));
		assertTrue(Inputs.isMiddle(Inputs.MOUSE_MIDDLE));
		assertFalse(Inputs.isMiddle(Inputs.MOUSE_LEFT));
	}

	@Test
	void everyKeyMatchesInputConstants() throws ReflectiveOperationException {
		int keys = 0;
		for (Field f : Inputs.class.getDeclaredFields()) {
			if (!f.getName().startsWith("KEY_")) {
				continue;
			}
			assertTrue(Modifier.isPublic(f.getModifiers()) && Modifier.isStatic(f.getModifiers()), f.getName());
			assertEquals(constant(f.getName()), f.getInt(null), "Inputs." + f.getName());
			keys++;
		}
		assertTrue(keys > 100, "Inputs exposes the full key table, got " + keys);
	}

	/** A {@code ConstantValue} attribute would let javac inline the field into callers: the 26.2 number again. */
	@Test
	void noFieldIsACompileTimeConstant() throws IOException {
		byte[] bytes;
		try (InputStream in = Inputs.class.getResourceAsStream("Inputs.class")) {
			assertNotNull(in, "Inputs.class on the test classpath");
			bytes = in.readAllBytes();
		}
		assertFalse(new String(bytes, StandardCharsets.ISO_8859_1).contains("ConstantValue"),
			"Inputs fields must be resolved at runtime, not compile-time constants");
	}

	private static int constant(String name) throws ReflectiveOperationException {
		return InputConstants.class.getField(name).getInt(null);
	}
}
