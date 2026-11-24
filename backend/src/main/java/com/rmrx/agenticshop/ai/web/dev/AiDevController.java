package com.rmrx.agenticshop.ai.web.dev;

import com.rmrx.agenticshop.ai.config.AiProperties;
import com.rmrx.agenticshop.ai.trace.TraceRecorder;
import com.rmrx.agenticshop.ai.trace.entity.AiRunStatus;
import com.rmrx.agenticshop.ai.usage.RunScope;
import com.rmrx.agenticshop.ai.usage.RunScopes;
import com.rmrx.agenticshop.ai.usage.entity.AiUsagePurpose;
import com.rmrx.agenticshop.ai.usage.repository.AiUsageRepository;
import com.rmrx.agenticshop.ai.web.dto.AiUsageDtos.UsageRow;
import com.rmrx.agenticshop.common.exception.BadRequestException;
import com.rmrx.agenticshop.security.AccessScope;
import com.rmrx.agenticshop.security.CurrentUser;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Dev-only checks of the AI wiring, present only with {@code app.ai.dev-endpoints=true}. Any signed-in user may call
 * them; the ping costs real tokens when pointed at OpenAI.
 */
@RestController
@RequestMapping("/api/ai/dev")
@ConditionalOnBooleanProperty("app.ai.dev-endpoints")
public class AiDevController {

    private final ObjectProvider<ChatModel> chatModel;
    private final ObjectProvider<EmbeddingModel> embeddingModel;
    private final TraceRecorder trace;
    private final AiUsageRepository usages;
    private final AccessScope scope;
    private final AiProperties props;
    private final JsonRoundtripCheck jsonRoundtrip;

    public AiDevController(ObjectProvider<ChatModel> chatModel, ObjectProvider<EmbeddingModel> embeddingModel,
                           TraceRecorder trace, AiUsageRepository usages, AccessScope scope, AiProperties props,
                           JsonRoundtripCheck jsonRoundtrip) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.trace = trace;
        this.usages = usages;
        this.scope = scope;
        this.props = props;
        this.jsonRoundtrip = jsonRoundtrip;
    }

    public record PingRequest(@NotBlank String text) {
    }

    public record PingResponse(String reply, int embeddingDimensions, UUID runId, List<UsageRow> usage) {
    }

    /** One chat call and one embedding call in a traced run billed to the caller; returns the ledger rows. */
    @PostMapping("/ping")
    public PingResponse ping(@Valid @RequestBody PingRequest request) {
        ChatModel chat = chatModel.getIfAvailable();
        EmbeddingModel embedding = embeddingModel.getIfAvailable();
        if (chat == null || embedding == null) throw new BadRequestException("AI is disabled (app.ai.enabled=false)");

        CurrentUser user = scope.current();
        UUID runId = trace.startRun(user.id(), null, "dev-ping", props.chat().model());
        RunScope runScope = new RunScope(user.id(), null, runId, AiUsagePurpose.CHAT);
        try {
            String reply = RunScopes.get(runScope, () -> chat.chat(request.text()));
            int dims = RunScopes.get(runScope, () -> embedding.embed(request.text()).content().dimension());
            trace.finishRun(runId, AiRunStatus.COMPLETED, null, null);
            return new PingResponse(reply, dims, runId,
                    usages.findByRunIdOrderByIdAsc(runId).stream().map(UsageRow::of).toList());
        } catch (RuntimeException e) {
            trace.finishRun(runId, AiRunStatus.FAILED, e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    /** Writes and re-reads the Jackson 3 jsonb columns in a rolled-back transaction. */
    @PostMapping("/json-roundtrip")
    public JsonRoundtripCheck.Result jsonRoundtrip() {
        return jsonRoundtrip.run(scope.current().id());
    }
}
