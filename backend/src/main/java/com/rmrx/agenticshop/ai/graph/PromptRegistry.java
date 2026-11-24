package com.rmrx.agenticshop.ai.graph;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Versioned prompts from {@code classpath:ai/prompts/<name>.<version>.md}, loaded once and cached. The id
 * ({@code support-agent.v1}) is what a run records as its prompt version, so a trace always names the exact text used.
 */
@Component
public class PromptRegistry {

    public static final String SUPPORT_AGENT = "support-agent";
    public static final String SUPPORT_AGENT_VERSION = "v1";

    /** A loaded prompt; id is {@code name.version}. */
    public record Prompt(String id, String text) {
    }

    private final Map<String, Prompt> cache = new ConcurrentHashMap<>();

    public Prompt get(String name, String version) {
        String id = name + "." + version;
        return cache.computeIfAbsent(id, PromptRegistry::load);
    }

    /** The current support agent prompt. */
    public Prompt supportAgent() {
        return get(SUPPORT_AGENT, SUPPORT_AGENT_VERSION);
    }

    private static Prompt load(String id) {
        ClassPathResource resource = new ClassPathResource("ai/prompts/" + id + ".md");
        if (!resource.exists()) throw new IllegalArgumentException("Unknown prompt " + id);
        try (InputStream in = resource.getInputStream()) {
            return new Prompt(id, new String(in.readAllBytes(), StandardCharsets.UTF_8).strip());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read prompt " + id, e);
        }
    }
}
