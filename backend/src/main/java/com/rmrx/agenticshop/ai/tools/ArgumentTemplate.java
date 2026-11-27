package com.rmrx.agenticshop.ai.tools;

import com.rmrx.agenticshop.ai.contract.interaction.InteractionJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A text with {@code {name}} placeholders filled from a tool call's arguments, e.g.
 * {@code "Do you approve cancelling order {orderId}?"}. Every placeholder must be a parameter of the tool; that is
 * checked when the template is created, so a typo fails at startup rather than in a conversation.
 */
public final class ArgumentTemplate {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final String MISSING = "?";

    private final String template;

    private ArgumentTemplate(String template) {
        this.template = template;
    }

    /**
     * @throws IllegalStateException when a placeholder is not one of {@code parameterNames}
     */
    public static ArgumentTemplate of(String template, String toolName, Set<String> parameterNames) {
        for (String name : placeholders(template)) {
            if (!parameterNames.contains(name)) {
                throw new IllegalStateException("Tool '%s': placeholder {%s} in \"%s\" is not one of its parameters %s"
                        .formatted(toolName, name, template, parameterNames));
            }
        }
        return new ArgumentTemplate(template);
    }

    /** Fills the placeholders from the call's JSON arguments; a missing or null argument renders as "?". */
    public String render(String argumentsJson) {
        Map<?, ?> arguments = argumentsJson == null || argumentsJson.isBlank()
                ? Map.of() : InteractionJson.read(argumentsJson, Map.class);
        Matcher m = PLACEHOLDER.matcher(template);
        return m.replaceAll(match -> {
            Object value = arguments.get(match.group(1));
            return Matcher.quoteReplacement(value == null ? MISSING : String.valueOf(value));
        });
    }

    public String template() {
        return template;
    }

    private static List<String> placeholders(String template) {
        List<String> names = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(template);
        while (m.find()) names.add(m.group(1));
        return names;
    }
}
