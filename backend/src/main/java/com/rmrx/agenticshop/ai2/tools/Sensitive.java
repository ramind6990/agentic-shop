package com.rmrx.agenticshop.ai2.tools;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a tool that changes state: the run pauses for the user's approval before it is executed. The approval shows
 * {@link #approvalMessage()} and {@link #detail()}, rendered from the call's arguments. Placeholders must be parameter
 * names of the tool; {@link ToolRegistry} checks them at startup.
 * <pre>
 * &#64;Sensitive(approvalMessage = "Do you approve cancelling order {orderId}?")
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Sensitive {

    /** The approval question, e.g. {@code "Do you approve cancelling order {orderId}?"}. Required. */
    String approvalMessage();

    /** Optional extra line under the question. */
    String detail() default "";
}
