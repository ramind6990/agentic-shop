package com.rmrx.agenticshop.ai2.tools;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The audiences a tool is offered to; on a tool method, or on its class for all its tools (the method wins). Every
 * tool must have one ({@link ToolRegistry} fails startup otherwise). One tool name may exist in several methods with
 * disjoint audiences, e.g. a customer variant and a staff variant with more parameters.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ToolAudience {

    Audience[] value();
}
