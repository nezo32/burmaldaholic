package dev.nezo.burmaldaholic.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.nezo.burmaldaholic.core.text.Plural.Form;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PluralTest {
	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({
		"0,P5", "1,P1", "2,P2", "3,P2", "4,P2", "5,P5", "10,P5",
		"11,P5", "12,P5", "13,P5", "14,P5", "15,P5", "20,P5",
		"21,P21", "22,P2", "24,P2", "25,P5", "31,P21", "101,P21", "111,P5", "112,P5", "114,P5",
		"121,P21", "122,P2", "1000,P5", "1001,P21", "1011,P5", "1000000,P5",
		"-1,P1", "-21,P21", "-5,P5",
	})
	void forms(long n, Form expected) {
		assertEquals(expected, Plural.form(n));
	}

	/** The RU examples from the spec: 1 фишка / 2 фишки / 5 фишек / 11 фишек / 21 фишка / 111 фишек. */
	@ParameterizedTest
	@CsvSource({
		"1,burmaldaholic.core.chips.p1",    // 1 фишка
		"2,burmaldaholic.core.chips.p2",    // 2 фишки
		"5,burmaldaholic.core.chips.p5",    // 5 фишек
		"11,burmaldaholic.core.chips.p5",   // 11 фишек
		"21,burmaldaholic.core.chips.p21",  // 21 фишка
		"111,burmaldaholic.core.chips.p5",  // 111 фишек
	})
	void keys(long n, String key) {
		assertEquals(key, Plural.key("burmaldaholic.core.chips", n));
	}
}
