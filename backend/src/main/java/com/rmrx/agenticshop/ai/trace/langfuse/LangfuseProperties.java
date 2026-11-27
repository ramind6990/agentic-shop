package com.rmrx.agenticshop.ai.trace.langfuse;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Export of ai runs to Langfuse ({@code app.ai.langfuse}).
 *
 * @param enabled     export on/off; also needs both keys
 * @param baseUrl     the Langfuse host (EU cloud by default; {@code https://us.cloud.langfuse.com}, or a self-hosted one)
 * @param publicKey   the project's public key ({@code pk-lf-...})
 * @param secretKey   the project's secret key ({@code sk-lf-...})
 * @param environment Langfuse environment of every trace (lowercase letters, digits, {@code -} and {@code _})
 */
@ConfigurationProperties("app.ai.langfuse")
public record LangfuseProperties(
        boolean enabled,
        @DefaultValue("https://cloud.langfuse.com") String baseUrl,
        String publicKey,
        String secretKey,
        @DefaultValue("development") String environment) {

    private static final String TRACES_PATH = "/api/public/otel/v1/traces";

    /** Enabled and both keys set. */
    public boolean active() {
        return enabled && notBlank(publicKey) && notBlank(secretKey);
    }

    /** Langfuse's OTLP/HTTP traces endpoint (it does not accept gRPC). */
    public String tracesEndpoint() {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + TRACES_PATH;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
