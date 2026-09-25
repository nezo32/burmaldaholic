package dev.nezo.burmaldaholic.core.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a nested object that is one member of a family ({@code slots.<tier>.weights}): labels of
 * its fields fall back to the template without this segment ({@code config.burmaldaholic.slots.weights})
 * with {@link #value()} (a translation key) as {@code %1$s}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Member {
	String value();
}
