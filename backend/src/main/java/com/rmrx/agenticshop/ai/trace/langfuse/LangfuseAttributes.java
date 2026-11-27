package com.rmrx.agenticshop.ai.trace.langfuse;

import io.opentelemetry.api.common.AttributeKey;

import java.util.List;

/**
 * The span attributes Langfuse reads (its OpenTelemetry mapping). Trace attributes go on every span: Langfuse filters
 * on them only when each span carries them. Unprefixed attributes would land in unfilterable metadata.
 */
final class LangfuseAttributes {

    static final AttributeKey<String> TRACE_NAME = AttributeKey.stringKey("langfuse.trace.name");
    static final AttributeKey<String> SESSION_ID = AttributeKey.stringKey("langfuse.session.id");
    static final AttributeKey<String> USER_ID = AttributeKey.stringKey("langfuse.user.id");
    static final AttributeKey<String> VERSION = AttributeKey.stringKey("langfuse.version");
    static final AttributeKey<String> ENVIRONMENT = AttributeKey.stringKey("langfuse.environment");
    static final AttributeKey<List<String>> TAGS = AttributeKey.stringArrayKey("langfuse.trace.tags");

    static final AttributeKey<String> TYPE = AttributeKey.stringKey("langfuse.observation.type");
    /** DEBUG | DEFAULT | WARNING | ERROR; an ERROR span status sets ERROR and the status message too. */
    static final AttributeKey<String> LEVEL = AttributeKey.stringKey("langfuse.observation.level");
    static final AttributeKey<String> INPUT = AttributeKey.stringKey("langfuse.observation.input");
    static final AttributeKey<String> OUTPUT = AttributeKey.stringKey("langfuse.observation.output");
    static final AttributeKey<String> MODEL = AttributeKey.stringKey("langfuse.observation.model.name");
    static final AttributeKey<String> MODEL_PARAMETERS = AttributeKey.stringKey("langfuse.observation.model.parameters");
    static final AttributeKey<String> USAGE = AttributeKey.stringKey("langfuse.observation.usage_details");

    /** Observation types. */
    static final String AGENT = "agent";
    static final String GENERATION = "generation";
    static final String TOOL = "tool";
    static final String GUARDRAIL = "guardrail";
    static final String SPAN = "span";
    static final String EVENT = "event";

    /** Observation levels. */
    static final String WARNING = "WARNING";
    static final String ERROR = "ERROR";

    private static final String METADATA_PREFIX = "langfuse.observation.metadata.";

    private LangfuseAttributes() {
    }

    /** A filterable metadata key of the observation. */
    static AttributeKey<String> metadata(String key) {
        return AttributeKey.stringKey(METADATA_PREFIX + key);
    }
}
