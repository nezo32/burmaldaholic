package dev.nezo.burmaldaholic.core.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allowed range of a numeric config field (docs/design/CONFIG.md "Range" column). Out-of-range
 * values are clamped and reported. On arrays/lists/maps it applies to every numeric element.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Range {
	double min();

	double max();
}
