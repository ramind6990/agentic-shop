package com.rmrx.agenticshop.ai.web.dev;

import com.rmrx.agenticshop.ai.config.AiProperties.ComposeMode;
import com.rmrx.agenticshop.ai.graph.ComposeStep;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only agent switches (present only with {@code app.ai.dev-endpoints=true}): read or change the compose mode at
 * runtime, e.g. to compare {@code llm} and {@code deterministic} on the same conversation without a restart.
 */
@RestController
@RequestMapping("/api/ai/dev/agent")
@ConditionalOnBooleanProperty("app.ai.dev-endpoints")
public class AiDevAgentController {

    private final ComposeStep compose;

    public AiDevAgentController(ComposeStep compose) {
        this.compose = compose;
    }

    public record ComposeModeBody(ComposeMode mode) {
    }

    @GetMapping("/compose-mode")
    public ComposeModeBody composeMode() {
        return new ComposeModeBody(compose.mode());
    }

    @PostMapping("/compose-mode")
    public ComposeModeBody setComposeMode(@RequestBody ComposeModeBody body) {
        if (body.mode() != null) compose.overrideMode(body.mode());
        return new ComposeModeBody(compose.mode());
    }
}
