package com.rmrx.agenticshop.ai.graph.guard;

import com.rmrx.agenticshop.ai.contract.interaction.InteractionJson;
import com.rmrx.agenticshop.ai.graph.state.RunContext;
import com.rmrx.agenticshop.ai.model.ModelCall;
import com.rmrx.agenticshop.ai.model.ModelGateway;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.service.output.JsonSchemas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The guardrails' own model calls: instructions + one input, answered as JSON of a given record. Goes through
 * {@link ModelGateway} (traced, billed as SYSTEM / GUARD, not retried). Never throws: a failed or unparsable call is
 * empty, and the caller falls back to its deterministic checks.
 */
@Component
public class GuardModel {

    private static final Logger log = LoggerFactory.getLogger(GuardModel.class);

    private final ModelGateway model;
    private final AiGuardProperties props;

    public GuardModel(ModelGateway model, AiGuardProperties props) {
        this.model = model;
        this.props = props;
    }

    public <T> Optional<T> ask(RunContext context, String note, String instructions, String input, Class<T> type) {
        try {
            ChatRequest.Builder request = ChatRequest.builder()
                    .messages(SystemMessage.from(instructions), UserMessage.from(input))
                    .responseFormat(format(type));
            if (props.modelName() != null && !props.modelName().isBlank()) request.modelName(props.modelName());
            String text = model.chat(request.build(), ModelCall.guard(context, note)).aiMessage().text();
            return Optional.ofNullable(InteractionJson.read(text, type));
        } catch (RuntimeException e) {
            log.warn("ai guard model call failed ({}): {}", note, e.getMessage());
            return Optional.empty();
        }
    }

    private static ResponseFormat format(Class<?> type) {
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchemas.jsonSchemaFrom(type)
                        .orElseThrow(() -> new IllegalStateException("no JSON schema for " + type.getSimpleName())))
                .build();
    }
}
