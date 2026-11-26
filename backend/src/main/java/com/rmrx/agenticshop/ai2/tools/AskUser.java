package com.rmrx.agenticshop.ai2.tools;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an ask tool: the model calls it to ask the user something, the run pauses, and the user's answer becomes the
 * call's result. Such a tool is never executed. Cannot be combined with {@link Sensitive}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AskUser {
}
