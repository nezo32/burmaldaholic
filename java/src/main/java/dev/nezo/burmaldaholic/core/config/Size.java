package dev.nezo.burmaldaholic.core.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Allowed number of entries of a list/array config field; a wrong size resets the field to its default. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Size {
	int min();

	int max();
}
