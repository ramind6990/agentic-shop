package com.rmrx.agenticshop.ai.trace.langfuse;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * The OpenTelemetry SDK that carries ai runs to Langfuse. It is ai's own: not registered globally and not Spring's
 * observability, so only the spans {@link LangfuseExporter} creates are sent (no HTTP or JDBC spans). Export is
 * batched in the background; closing the provider on shutdown flushes what is left. Without
 * {@link LangfuseProperties#active()} the provider has no exporter and the exporter skips all work.
 */
@Configuration
@EnableConfigurationProperties(LangfuseProperties.class)
public class LangfuseConfig {

    static final String TRACER_PROVIDER = "aiLangfuseTracerProvider";

    private static final Logger log = LoggerFactory.getLogger(LangfuseConfig.class);
    private static final String SERVICE_NAME = "agenticshop";
    /** Spans per OTLP request: generations carry full prompts, so keep requests small. */
    private static final int MAX_EXPORT_BATCH = 50;
    private static final Duration EXPORT_TIMEOUT = Duration.ofSeconds(15);

    @Bean(name = TRACER_PROVIDER, destroyMethod = "close", defaultCandidate = false)
    public SdkTracerProvider aiLangfuseTracerProvider(LangfuseProperties props) {
        SdkTracerProviderBuilder provider = SdkTracerProvider.builder()
                .setIdGenerator(new RunTraceIds())
                .setResource(Resource.getDefault().merge(Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), SERVICE_NAME,
                        AttributeKey.stringKey("deployment.environment.name"), props.environment()))));
        if (props.active()) {
            provider.addSpanProcessor(BatchSpanProcessor.builder(exporter(props))
                    .setMaxExportBatchSize(MAX_EXPORT_BATCH)
                    .build());
            log.info("ai Langfuse export: on ({}, environment {})", props.tracesEndpoint(), props.environment());
        } else {
            log.info("ai Langfuse export: off (app.ai.langfuse.enabled and both keys needed)");
        }
        return provider.build();
    }

    private static OtlpHttpSpanExporter exporter(LangfuseProperties props) {
        String credentials = props.publicKey() + ":" + props.secretKey();
        return OtlpHttpSpanExporter.builder()
                .setEndpoint(props.tracesEndpoint())
                .addHeader("Authorization",
                        "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                // real-time ingestion; without it new traces can take minutes to show up
                .addHeader("x-langfuse-ingestion-version", "4")
                .setTimeout(EXPORT_TIMEOUT)
                .build();
    }
}
