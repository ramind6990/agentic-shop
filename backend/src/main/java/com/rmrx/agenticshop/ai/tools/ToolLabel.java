package com.rmrx.agenticshop.ai.tools;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A short, user-facing description of what a tool call is doing, e.g. {@code "Looking up order {orderId}"}, used for
 * progress events. Placeholders must be parameter names of the tool; {@link ToolRegistry} checks them at startup.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ToolLabel {

    String value();
}
