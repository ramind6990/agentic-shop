package com.rmrx.agenticshop.ai2.model;

import com.rmrx.agenticshop.ai2.graph.state.RunContext;
import com.rmrx.agenticshop.ai2.tools.Audience;
import com.rmrx.agenticshop.ai2.tools.ToolPaging;
import com.rmrx.agenticshop.app.returns.service.RefundLimits;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ai2 agent's system prompt, one per {@link Audience}. The instructions are versioned files chosen by
 * {@code app.ai2.prompt.version}: {@code prompts/ai2/<version>/base.md} with the audience's
 * {@code customer.md} / {@code staff.md} inserted at its {@code {{audience}}} placeholder, or (older versions) one
 * file {@code prompts/ai2/system-<version>.md} for everyone. The files are loaded once at startup and their
 * {@code {{placeholders}}} filled from {@link Ai2ReplyProperties} and the paging limits (an unknown placeholder fails
 * startup). Each model call then adds what changes per call: today's date, who the user is and, on the run's last
 * allowed call, the note that tools are off.
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
    private final Map<Audience, String> instructions = new EnumMap<>(Audience.class);
    private final Clock clock;

    /** The constructor Spring uses (the class has two, so it must be marked). */
    @Autowired
    public SystemPrompt(Ai2PromptProperties prompt, Ai2ReplyProperties reply) {
        this(prompt, reply, Clock.systemDefaultZone());
    }

    SystemPrompt(Ai2PromptProperties prompt, Ai2ReplyProperties reply, Clock clock) {
        this.version = prompt.version();
        Map<String, Object> values = new HashMap<>(Map.of(
                "maxTextLength", reply.maxTextLength(),
                "maxSuggestions", reply.maxSuggestions(),
                "maxSuggestionLabelLength", reply.maxSuggestionLabelLength(),
                "pageSize", ToolPaging.PAGE_SIZE,
                "maxPage", ToolPaging.MAX_PAGE));
        for (Audience audience : Audience.values()) instructions.put(audience, compose(audience, values));
        this.clock = clock;
        log.info("ai2 system prompt {} loaded", version);
    }

    /** The prompt version in use, e.g. {@code v2}. */
    public String version() {
        return version;
    }

    /** The system message text for one model call of the run. */
    public String render(RunContext context, boolean toolsOn) {
        StringBuilder prompt = new StringBuilder(instructions.get(context.audience().orElse(Audience.CUSTOMER)))
                .append("\n\n# Today\n").append(LocalDate.now(clock))
                .append("\n\n# The user\n").append(userFacts(context));
        if (!toolsOn) prompt.append("\n\n").append(TOOLS_OFF_NOTE);
        return prompt.toString();
    }

    /** Who the user is (role, refund limits): part of every system prompt, and evidence for the reply audit. */
    public static String userFacts(RunContext context) {
        if (context.audience().orElse(Audience.CUSTOMER) == Audience.CUSTOMER) return "A signed-in customer.";
        if (!RefundLimits.mayIssue(context.role())) {
            return "A support agent with role " + context.role() + "; they cannot issue or approve refunds.";
        }
        return RefundLimits.capFor(context.role())
                .map(cap -> "A support agent with role " + context.role() + ". Their refund limit is " + cap
                        + " per refund: they may issue refunds up to " + cap + " themselves, and approve colleagues' "
                        + "refund requests up to " + cap + ". A larger refund needs request_refund_approval.")
                .orElse("A support agent with role " + context.role() + ". Their refunds are not limited by role, "
                        + "and they may approve colleagues' refund requests of any amount.");
    }

    private String compose(Audience audience, Map<String, Object> values) {
        String single = "prompts/ai2/system-" + version + ".md";
        if (new ClassPathResource(single).exists()) return fill(load(single), values);
        String dir = "prompts/ai2/" + version + "/";
        Map<String, Object> withAudience = new HashMap<>(values);
        withAudience.put("audience", fill(load(dir + audience.name().toLowerCase(Locale.ROOT) + ".md"), values));
        return fill(load(dir + "base.md"), withAudience);
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
