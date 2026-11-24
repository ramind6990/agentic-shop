package com.rmrx.agenticshop.ai.tools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Short user-facing progress text shown while a tool runs, e.g. "Looking up your orders\u2026". */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ToolLabel {

    String value();
}
