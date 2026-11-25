package com.rmrx.agenticshop.ai.tools;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.OrderDetailView;
import com.rmrx.agenticshop.ai.contract.tool.PolicyChunkView;
import com.rmrx.agenticshop.ai.contract.tool.ToolErrorCode;
import com.rmrx.agenticshop.ai.rag.PolicyRetriever;
import com.rmrx.agenticshop.ai.tools.port.OrderPort;
import com.rmrx.agenticshop.ai.tools.port.PortException;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Policy retrieval: the caller's visible policy sections in force on the relevant day. */
public class PolicyTools extends AbstractTools {

    public static final String SEARCH_POLICIES = "search_policies";
    static final int MAX_QUERY = 500;
    /** What the model is told when nothing supports an answer. */
    static final String NO_SUPPORTING_POLICY = "no supporting policy: no policy section available to you covers "
            + "this question. Do not answer from general knowledge or guess; say you could not find a policy that "
            + "covers it and offer to hand the conversation to a support agent.";

    private final OrderPort orders;
    private final PolicyRetriever retriever;
    private final int excerptChars;
    private final Clock clock;

    PolicyTools(AgentContext ctx, TurnResultRegistry registry, JsonMapper json, OrderPort orders,
                PolicyRetriever retriever, int excerptChars, Clock clock) {
        super(ctx, registry, json);
        this.orders = orders;
        this.retriever = retriever;
        this.excerptChars = excerptChars;
        this.clock = clock;
    }

    @Tool(name = SEARCH_POLICIES, value = "Searches the shop's policy documents you may see (returns, refunds, "
            + "shipping, warranty, fees; internal procedures for staff) and returns the relevant sections with "
            + "document, section, version and validity dates, each with a `ref` number. Call it for every question "
            + "about rules, eligibility, deadlines, fees or procedures, before answering. Pass orderId when the "
            + "question is about a specific order: the policy version in force on that order's date is used. "
            + "NOT_FOUND means no supporting policy (or an unknown order): do not guess.")
    @ToolLabel("Checking our policies…")
    public String searchPolicies(
            @P("The policy question in a few words, e.g. 'return window electronics' or 'return shipping fee'")
            String query,
            @P(value = "Numeric id of the order the question is about; omit for general questions", required = false)
            Long orderId) {
        return run(SEARCH_POLICIES, () -> {
            if (query == null || query.isBlank()) throw PortException.invalid("query is required");
            String q = query.strip();
            if (q.length() > MAX_QUERY) q = q.substring(0, MAX_QUERY);
            LocalDate day = LocalDate.now(clock);
            if (orderId != null) {
                requireId("orderId", orderId);
                // ownership-checked: an order outside the caller's scope is NOT_FOUND, like everywhere else
                OrderDetailView order = orders.getOrder(ctx, orderId);
                if (order.placedAt() != null) day = LocalDate.ofInstant(order.placedAt(), ZoneOffset.UTC);
            }
            PolicyRetriever.Result result = retriever.search(ctx, q, day, null, 0);
            if (result.noSupportingPolicy()) throw new PortException(ToolErrorCode.NOT_FOUND, NO_SUPPORTING_POLICY);
            List<PolicyChunkView> views = new ArrayList<>();
            int ref = 1;
            for (PolicyRetriever.Hit h : result.hits()) {
                views.add(new PolicyChunkView(ref++, h.chunkId(), h.docTitle(), h.docType(), h.sectionHeading(),
                        h.versionLabel(), h.effectiveFrom(), h.effectiveTo(), excerpt(h.text(), excerptChars),
                        h.documentId(), h.versionId(), h.sectionId(), h.fusedScore()));
            }
            return new ToolData.PolicySearch(q, day, orderId, views, views.size());
        });
    }

    /** The text cut at a word boundary to at most max chars, with an ellipsis when cut. */
    static String excerpt(String text, int max) {
        if (text == null) return "";
        String t = text.strip();
        if (t.length() <= max) return t;
        int cut = t.lastIndexOf(' ', max - 1);
        if (cut < max / 2) cut = max - 1;
        return t.substring(0, cut).stripTrailing() + "…";
    }
}
