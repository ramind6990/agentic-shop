package com.rmrx.agenticshop.ai2.model;

import com.rmrx.agenticshop.ai2.graph.state.RunContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ai2 agent's system prompt. The instructions are versioned files, {@code prompts/ai2/system-<version>.md}; the
 * version comes from {@code app.ai2.prompt.version}. The file is loaded once at startup and its
 * {@code {{placeholders}}} are filled from {@link Ai2ReplyProperties} (an unknown placeholder fails startup). Each
 * model call then adds what changes per call: today's date and, on the run's last allowed call, the note that tools
 * are off.
 */
@Component
public class SystemPrompt {

    private static final Logger log = LoggerFactory.getLogger(SystemPrompt.class);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private static final String TOOLS_OFF_NOTE = """
            # No more tools in this request
            You cannot call tools any more. Give your final reply now with what you already know; if something could \
            not be looked up, say so in the text.""";

    private final String version;
    private final String instructions;
    private final Clock clock;

    /** The constructor Spring uses (the class has two, so it must be marked). */
    @Autowired
    public SystemPrompt(Ai2PromptProperties prompt, Ai2ReplyProperties reply) {
        this(prompt, reply, Clock.systemDefaultZone());
    }

    SystemPrompt(Ai2PromptProperties prompt, Ai2ReplyProperties reply, Clock clock) {
        this.version = prompt.version();
        this.instructions = fill(load(location(version)), Map.of(
                "maxTextLength", reply.maxTextLength(),
                "maxSuggestions", reply.maxSuggestions(),
                "maxSuggestionLabelLength", reply.maxSuggestionLabelLength()));
        this.clock = clock;
        log.info("ai2 system prompt {} loaded", version);
    }

    /** The prompt version in use, e.g. {@code v1}. */
    public String version() {
        return version;
    }

    /** The system message text for one model call of the run. */
    public String render(RunContext context, boolean toolsOn) {
        StringBuilder prompt = new StringBuilder(instructions)
                .append("\n\n# Today\n").append(LocalDate.now(clock));
        // TODO the customer's name / role and other per-user facts (needs them in RunContext)
        if (!toolsOn) prompt.append("\n\n").append(TOOLS_OFF_NOTE);
        return prompt.toString();
    }

    static String location(String version) {
        return "prompts/ai2/system-" + version + ".md";
    }

    private static String fill(String template, Map<String, Object> values) {
        Matcher m = PLACEHOLDER.matcher(template);
        String filled = m.replaceAll(match -> {
            Object value = values.get(match.group(1));
            if (value == null) {
                throw new IllegalStateException("ai2 system prompt: unknown placeholder {{" + match.group(1) + "}}");
            }
            return Matcher.quoteReplacement(String.valueOf(value));
        });
        return filled.strip();
    }

    private static String load(String location) {
        try {
            return new ClassPathResource(location).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the ai2 system prompt " + location, e);
        }
    }
}
