package com.rmrx.agenticshop.ai.web.dev;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.context.AgentContextFactory;
import com.rmrx.agenticshop.ai.contract.tool.OrderDetailView;
import com.rmrx.agenticshop.ai.rag.PolicyRetriever;
import com.rmrx.agenticshop.ai.tools.port.OrderPort;
import com.rmrx.agenticshop.ai.tools.port.PortException;
import com.rmrx.agenticshop.ai.usage.RunScope;
import com.rmrx.agenticshop.ai.usage.RunScopes;
import com.rmrx.agenticshop.ai.usage.entity.AiUsagePurpose;
import com.rmrx.agenticshop.app.common.exception.BadRequestException;
import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import com.rmrx.agenticshop.app.docs.entity.DocumentType;
import com.rmrx.agenticshop.app.identity.entity.AppUser;
import com.rmrx.agenticshop.app.identity.entity.Role;
import com.rmrx.agenticshop.app.identity.repository.AppUserRepository;
import com.rmrx.agenticshop.app.security.AccessScope;
import com.rmrx.agenticshop.app.security.CurrentUser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/**
 * Dev-only retrieval check (present only with {@code app.ai.dev-endpoints=true}): runs {@link PolicyRetriever} as a
 * seeded user and returns every fused candidate with its vector rank, keyword rank, fused score and whether it
 * passed the relevance rule. An ADMIN may run it as any user ({@code username}); everyone else only as themselves.
 * The query embedding is billed to the caller.
 */
@RestController
@RequestMapping("/api/ai/dev/rag")
@ConditionalOnBooleanProperty("app.ai.dev-endpoints")
public class AiDevRagController {

    private final PolicyRetriever retriever;
    private final AgentContextFactory contexts;
    private final AccessScope scope;
    private final AppUserRepository users;
    private final OrderPort orders;

    public AiDevRagController(PolicyRetriever retriever, AgentContextFactory contexts, AccessScope scope,
                              AppUserRepository users, OrderPort orders) {
        this.retriever = retriever;
        this.contexts = contexts;
        this.scope = scope;
        this.users = users;
        this.orders = orders;
    }

    /**
     * date overrides the reference day; otherwise orderId's placed date (ownership-checked as the user), else
     * today. docTypes empty = the role's default corpus. explain=true adds the EXPLAIN ANALYZE plan.
     */
    public record SearchRequest(String username, String query, Long orderId, LocalDate date,
                                Set<DocumentType> docTypes, Integer k, Boolean explain) {
    }

    public record Candidate(int position, long chunkId, String docCode, String docType, String versionLabel,
                            LocalDate effectiveFrom, LocalDate effectiveTo, String headingPath, Long sellerId,
                            String docMinRole, String sectionMinRole, Integer vectorRank, Double vectorSimilarity,
                            Integer keywordRank, Double keywordScore, double keywordCoverage, double fusedScore,
                            boolean supporting, boolean returned, String text) {
    }

    public record SearchResponse(String username, Role role, Long sellerId, List<String> visibleMinRoles,
                                 String query, LocalDate referenceDate, String referenceDateSource,
                                 Set<DocumentType> docTypes, boolean noSupportingPolicy, int returned,
                                 int candidatesTotal, List<Candidate> candidates, List<String> plan) {
    }

    @PostMapping("/search")
    public SearchResponse search(@RequestBody SearchRequest req) {
        if (req.query() == null || req.query().isBlank()) throw new BadRequestException("query is required");
        CurrentUser caller = scope.current();
        CurrentUser subject = subject(caller, req.username());
        AgentContext ctx = contexts.from(subject, null);
        AgentContext callerCtx = contexts.from(caller, null);

        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        String source = "today";
        if (req.date() != null) {
            day = req.date();
            source = "date";
        } else if (req.orderId() != null) {
            try {
                OrderDetailView o = orders.getOrder(ctx, req.orderId());
                if (o.placedAt() != null) day = LocalDate.ofInstant(o.placedAt(), ZoneOffset.UTC);
                source = "order " + req.orderId() + " placed date";
            } catch (PortException e) {
                throw new NotFoundException("Order", req.orderId());
            }
        }
        LocalDate refDay = day;
        RunScope billing = RunScope.of(callerCtx, null, AiUsagePurpose.EMBEDDING_QUERY);
        PolicyRetriever.Result r = RunScopes.get(billing, () -> retriever.search(ctx, req.query().strip(), refDay,
                req.docTypes(), req.k() == null ? 0 : req.k()));
        List<String> plan = Boolean.TRUE.equals(req.explain())
                ? RunScopes.get(billing, () -> retriever.explain(ctx, req.query().strip(), refDay, req.docTypes()))
                : null;
        Set<Long> returned = new java.util.HashSet<>();
        r.hits().forEach(h -> returned.add(h.chunkId()));
        List<Candidate> candidates = new java.util.ArrayList<>();
        int pos = 1;
        for (PolicyRetriever.Hit h : r.candidates()) {
            String text = h.text().length() <= 160 ? h.text() : h.text().substring(0, 159) + "…";
            candidates.add(new Candidate(pos++, h.chunkId(), h.docCode(), h.docType(), h.versionLabel(),
                    h.effectiveFrom(), h.effectiveTo(), h.headingPath(), h.sellerId(), h.docMinRole(),
                    h.sectionMinRole(), h.vectorRank(), h.vectorSimilarity(), h.keywordRank(), h.keywordScore(),
                    h.keywordCoverage(), h.fusedScore(), h.supporting(), returned.contains(h.chunkId()), text));
        }
        return new SearchResponse(subject.username(), subject.role(), subject.sellerId(), r.visibleMinRoles(),
                r.query(), r.referenceDate(), source, r.docTypes(), r.noSupportingPolicy(), r.hits().size(),
                candidates.size(), candidates, plan);
    }

    private CurrentUser subject(CurrentUser caller, String username) {
        if (username == null || username.isBlank() || username.equalsIgnoreCase(caller.username())) return caller;
        if (caller.role() != Role.ADMIN) throw new AccessDeniedException("Only ADMIN may search as another user");
        AppUser u = users.findByUsername(username.strip().toLowerCase())
                .orElseThrow(() -> new NotFoundException("User", username));
        return new CurrentUser(u.getId(), u.getUsername(), u.getRole(),
                u.getSeller() == null ? null : u.getSeller().getId());
    }
}
