package com.rmrx.agenticshop.ai2.contract.tool;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a String component of a tool response record that holds text written by people (customers, sellers, staff):
 * reviews, product descriptions, ticket messages, reasons and notes. Before the model gets a tool result, the tool
 * output guard checks each such text: invisible characters are removed, contact and payment data is redacted, and a
 * text that looks like instructions aimed at an AI assistant is hidden (prompt injection).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface FreeText {
}
