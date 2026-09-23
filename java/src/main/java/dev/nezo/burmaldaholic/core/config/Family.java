package dev.nezo.burmaldaholic.core.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code Map<String, V>} field as a key family ({@code chaos.weight.<event>}): every map key
 * becomes its own config key ({@code chaos.weight.mob_wave}) with its own editor row. The label is
 * the family template key {@code config.burmaldaholic.<field path>} with the member's display name
 * as {@code %1$s}; {@link #member()} is a translation-key pattern for that name ({@code %s} = map key),
 * or {@code "item"} for item ids (the item's own name), or empty (the raw id).
 * Unknown map keys are dropped with a warning unless {@link #open()} is true.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Family {
	String member() default "";

	boolean open() default false;
}
