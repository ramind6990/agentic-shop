package com.rmrx.agenticshop.eval;

import com.rmrx.agenticshop.ai.conversation.ConversationService;
import com.rmrx.agenticshop.ai.events.RunEventBus;
import com.rmrx.agenticshop.ai.model.AiModelConfig;
import com.rmrx.agenticshop.ai.trace.TraceQueries;
import com.rmrx.agenticshop.app.catalog.service.CategoryService;
import com.rmrx.agenticshop.app.catalog.service.ProductSearchCriteriaParser;
import com.rmrx.agenticshop.app.catalog.service.ProductService;
import com.rmrx.agenticshop.app.identity.service.AddressService;
import com.rmrx.agenticshop.app.identity.service.UserService;
import com.rmrx.agenticshop.app.order.service.OrderCommandService;
import com.rmrx.agenticshop.app.order.service.OrderService;
import com.rmrx.agenticshop.app.order.service.ShipmentService;
import com.rmrx.agenticshop.app.returns.service.RefundApprovalService;
import com.rmrx.agenticshop.app.returns.service.RefundCommandService;
import com.rmrx.agenticshop.app.returns.service.ReturnCommandService;
import com.rmrx.agenticshop.app.returns.service.ReturnService;
import com.rmrx.agenticshop.app.security.AccessScope;
import com.rmrx.agenticshop.app.support.service.TicketCommandService;
import com.rmrx.agenticshop.app.support.service.TicketService;
import com.rmrx.agenticshop.eval.harness.EvalActors;
import com.rmrx.agenticshop.eval.harness.EvalTestConfig;
import com.rmrx.agenticshop.eval.harness.RecordingChatModel;
import com.rmrx.agenticshop.eval.harness.RunAwaiter;
import com.rmrx.agenticshop.eval.harness.ScenarioRunner;
import com.rmrx.agenticshop.eval.scenario.ScenarioLoader;
import com.rmrx.agenticshop.eval.transcript.Transcript;
import com.rmrx.agenticshop.eval.transcript.TranscriptBuilder;
import com.rmrx.agenticshop.eval.transcript.TranscriptWriter;
import com.rmrx.agenticshop.eval.world.DomainMocks;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mock-tier agent evals: every scenario under {@code eval/scenarios} runs against the real agent (real model,
 * graph, guards, conversation service and ai tables) while the domain services the tools call are Mockito mocks
 * answering from the scenario's world. Each run writes a transcript; the DeepEval suite in {@code evals/} scores them.
 * A test fails only on harness errors, never on the agent's behaviour.
 * <p>
 * Calls the real model (costs money): excluded from {@code mvn test}; run with
 * {@code mvn -Peval test [-Deval.include=orders/*] [-Deval.repeats=3]}. Needs the {@code agenticshop_eval} database.
 */
@Tag("eval")
@ActiveProfiles("eval")
@Import(EvalTestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MockitoBean(types = {ProductService.class, CategoryService.class, ProductSearchCriteriaParser.class,
        OrderService.class, OrderCommandService.class, ShipmentService.class, ReturnService.class,
        ReturnCommandService.class, RefundCommandService.class, RefundApprovalService.class, AddressService.class,
        UserService.class, TicketService.class, TicketCommandService.class})
class MockDomainEvalTest {

    @Autowired
    ApplicationContext context;
    @Autowired
    Environment env;
    @Autowired
    ConversationService conversations;
    @Autowired
    RunEventBus events;
    @Autowired
    AccessScope scope;
    @Autowired
    TraceQueries traces;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    @Qualifier(AiModelConfig.CHAT_MODEL)
    ChatModel chatModel;

    @TestFactory
    Stream<DynamicTest> scenarios() {
        String include = System.getProperty("eval.include", "**");
        int repeats = Integer.getInteger("eval.repeats", 1);
        List<String> ids = ScenarioLoader.ids(include);
        assertThat(ids).as("scenarios matching eval.include=" + include).isNotEmpty();
        assertThat(DomainMocks.TYPES).as("@MockitoBean types").hasSize(14);

        ScenarioRunner runner = runner();
        TranscriptWriter writer = new TranscriptWriter(Path.of(env.getProperty("eval.output-dir", "target/eval-runs")),
                settings(include, repeats));
        return ids.stream().flatMap(id -> IntStream.rangeClosed(1, repeats).mapToObj(r ->
                DynamicTest.dynamicTest(id + (repeats > 1 ? " #" + r : ""), () -> {
                    Transcript transcript = runner.run(id, r);
                    Path file = writer.write(transcript);
                    assertThat(transcript.harnessErrors()).as("harness errors (transcript " + file + ")").isEmpty();
                })));
    }

    private ScenarioRunner runner() {
        if (!(chatModel instanceof RecordingChatModel recorder)) {
            throw new IllegalStateException("aiChatModel is not wrapped by RecordingChatModel");
        }
        Duration timeout = env.getProperty("eval.run-timeout", Duration.class, Duration.ofMinutes(4));
        int maxPauses = env.getProperty("eval.max-pauses-per-turn", Integer.class, 6);
        TranscriptBuilder builder = new TranscriptBuilder(traces, prompt(), model());
        return new ScenarioRunner(conversations, new RunAwaiter(events, timeout), recorder, new EvalActors(jdbc),
                new DomainMocks(context), scope, builder, maxPauses);
    }

    private Map<String, Object> settings(String include, int repeats) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("tier", "mock-domain");
        settings.put("include", include);
        settings.put("repeats", repeats);
        settings.put("promptVersion", prompt());
        settings.put("model", model());
        settings.put("reasoningEffort", env.getProperty("app.ai.model.reasoning-effort", ""));
        settings.put("guardModel", env.getProperty("app.ai.guard.model-name", ""));
        return settings;
    }

    private String prompt() {
        return env.getProperty("app.ai.prompt.version", "");
    }

    private String model() {
        return env.getProperty("app.ai.model.model-name", "");
    }
}
