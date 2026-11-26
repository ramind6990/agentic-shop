# AI support agent — feature plan (phases 0-2)

Feature list (F1-F14), decisions and out-of-scope items for the first agentic stage of AgenticShop. Implementation order lives in [ai-implementation-steps.md](ai-implementation-steps.md). The long-term roadmap (phases 0-9) is in ShopOps_Agentic_Practice_Plan.pdf.

## Context
The base marketplace backend exists (identity/roles, catalog, orders, returns/refunds, support tickets, versioned documents with sections, risk flags, JWT auth, rich seed, pgvector image in docker-compose). Now the agentic part starts, scoped to phases 0-2, backend only, everything under a new package `com.rmrx.agenticshop.ai`. Tools are mocked (scenario-driven fakes) and will later be wired to the real services. Stack: LangChain4j + LangGraph4j + pgvector.

Goal of this step: not "an LLM with 4 tools", but a solid support-agent product whose responses are UI-ready, safe by construction, and measurable from day one.

## Features

### F1. Conversation (ticket chat) API
- Conversation = a support ticket thread. Start conversation, send message, list history, close.
- Agent replies persisted as ticket messages (author AGENT), so the future ticket chat page reads one timeline.
- Short-term memory: last N turns + rolling summary per conversation.
- Streaming (SSE): text tokens plus progress events ("Looking up your orders…", "Checking return policy…"), then the final structured envelope.

### F2. Identity-bound agent context
- Every turn builds an `AgentContext` from the JWT (user id, role, seller id, support tier). Code passes it to tools; the LLM never sees or chooses whose permissions apply.
- Toolset filtered by role before the model sees it (customer vs support tier-1 vs seller get different tools).
- Tools enforce ownership themselves (mock checks too): customer asking for someone else's order gets FORBIDDEN/NOT_FOUND.

### F3. Structured response envelope (UI blocks)
Every reply has one generic, versioned shape:
- `status`: ANSWERED | NEEDS_INFO | NEEDS_CONFIRMATION | ESCALATED | REFUSED | ERROR
- `text`: short markdown narrative
- `blocks[]`: typed payloads — ORDER_LIST, ORDER_DETAIL, SHIPMENT_TRACKING, PRODUCT_LIST, PRODUCT_DETAIL, RETURN_STATUS, REFUND_RESULT, POLICY_ANSWER, CONFIRMATION_REQUEST, CHOICE_REQUEST, ESCALATION
- `citations[]`: the policy sources that back the answer (doc / section / version / effective_from), shown as "source" chips so the user and evals can verify the claim
- `suggestedReplies[]`: optional quick-reply chips ("Yes, cancel it", "Show older orders") the user can tap instead of typing
- Key rule: block data comes from tool results, never from LLM text. The LLM selects which tool results to show (by result id) and writes the narrative; code builds the blocks. No hallucinated orders or prices.
- Each item carries `actions[]` computed by code from state + permissions (PENDING order: Cancel; DELIVERED within return window: Start return; SHIPPED: Track; product: View/Ask). The LLM cannot invent actions.

### F4. Action round-trip and confirmation
- UI clicks an action, `POST /actions/{actionToken}`. Token is signed and bound to user + conversation + target, so it cannot be forged or replayed by another user.
- The action enters the graph as a structured user event, not free text.
- Every write is two-step: agent proposes (CONFIRMATION_REQUEST with amount, item, policy reason), user confirms, tool executes. Writes return explicit success/failure plus the new state.

### F5. Clarification and slot filling
- Missing or ambiguous target (e.g. "refund my headphones", 2 matching orders): agent replies NEEDS_INFO with a CHOICE_REQUEST block (order picker) instead of guessing.
- Out-of-scope requests: polite REFUSED, with no tool calls.

### F6. Human escalation
- Triggers: user asks for a human, repeated tool failure, low confidence/no grounded answer, policy says "manual". Effect: ticket escalated (tier/status change via mock), ESCALATED block, agent stops acting.

### F7. Mocked tools, scenario-driven
- Read: `get_my_orders(filters)`, `get_order`, `track_shipment`, `search_products`, `get_product`, `get_return_status`, `get_ticket_history`, `search_policies` (RAG, real).
- Write: `cancel_order`, `create_return_request`, `issue_refund` (hard limit in code), `escalate_to_human`, `add_ticket_note`.
- Each domain sits behind a port interface; `mock` implementation now, real adapter later (Spring profile switch). No agent code changes.
- Mock state is in-memory per scenario and mutable (cancel really changes status), so evals can check the final state.
- Scenarios: happy, not found, not owner, ineligible (outside window), already refunded, partial shipment, empty result. Selected by seed fixture per user, or by header/eval config for determinism.
- Typed compact results (records/sealed types) with error codes the agent can reason about: NOT_FOUND, FORBIDDEN, INELIGIBLE, CONFLICT, LIMIT_EXCEEDED, UNAVAILABLE.
- Failure injection hook in the mock layer (failure rate, latency, malformed output), off by default. This is the phase-0 "chaos" switch, ready for phase 3.

### F8. Agent graph (LangGraph4j)
- Nodes: load context/memory, then agent (ReAct), then tools (loop), then compose response (structured), then persist.
- Step budget (max tool iterations per turn) and a graceful "couldn't finish" fallback.
- Checkpointing in Postgres per conversation (the base for resume/HITL in phases 3-4).
- Prompts versioned as resources and recorded on each run.

### F9. Permission-aware RAG (phase 2)
- Ingestion from existing document/version/section tables into chunks with metadata: doc type, min role/tier, seller id, version, effective_from/to.
- Hybrid retrieval: pgvector similarity + Postgres full-text, fused with RRF, optional reranker.
- ACL filter inside the SQL query, before ranking (no post-filter leakage via counts or rerank).
- Time-aware: when a question relates to an order, use the policy version effective at the order date.
- Grounded answers: POLICY_ANSWER block with clause citations; "no supporting policy" leads to refusal/escalation instead of a guess.
- Admin re-index endpoint (full and per document version).

### F10. Eval harness (starts in phase 2)
- Golden scenarios as files: persona/role, mock scenario, user turns, expectations (tool trajectory, final status, block types, final mock state, required citations, forbidden content).
- Runner reports pass rate, tokens, cost, latency per scenario and per suite.
- Suites: support tasks, retrieval (recall@k, citation correctness), leakage (restricted content per role, must be exactly 0).
- Baseline numbers stored, so later phases are compared against them.

### F11. Run trace (basic observability)
- Per turn: model calls, tool calls (args, result summary, latency, error), tokens, cost, prompt version, final status. Stored in ai tables, with a debug endpoint per conversation. Langfuse comes in phase 9.

### F12. Token usage ledger (billing-ready, from day one)
- Every model call (chat, embedding, any future sub-agent/summarizer/reranker) writes an append-only usage record: user, ticket, run, purpose (chat / summary / embedding-query / ingestion), provider, model, input tokens, cached input tokens, output tokens, cost computed with a price snapshot.
- Model price table (per model, per token type, effective_from), so historic costs stay correct when prices change.
- Ingestion/embedding of documents is recorded as system usage (no user), so it is never charged to a customer.
- Aggregates: per ticket (with current context size of the conversation), per user (totals and per period), per model. Endpoints for the user (own usage) and admin/finance (any user).
- Difference from F11: the trace is for debugging and may be pruned; the ledger is the billing source of truth and is never pruned or edited.
- Streaming calls still record usage at the end (and partial usage if the stream is aborted).

### F13. Event emission (SSE now, websocket later)
- The agent emits events through one internal publisher interface: token delta, progress (tool started/finished), block ready, final envelope, error.
- SSE endpoint is the first transport; a websocket transport can later subscribe to the same publisher without agent changes.

### F14. What gets stored in the chat timeline
- Stored as ticket messages (permanent, re-rendered on reload):
  - User text messages.
  - Agent final envelope: text + blocks + citations + actions + status, saved as one message (text column plus JSON payload, with schema version). Blocks are a snapshot of the data at that moment ("your order was SHIPPED at 10:42").
  - User action clicks, saved as their own message of kind ACTION ("Confirmed: cancel order #123"), so the timeline reads correctly and the model sees what the user chose.
  - System notices: escalated to human, action expired.
- Not stored as messages (transient): token deltas, progress events ("Looking up your orders…"), partial blocks. They only exist during streaming; their facts are kept in the run trace (F11) for debugging.
- Action lifecycle: each stored action has a state (OPEN, USED, EXPIRED, SUPERSEDED). Once clicked, or when a newer confirmation replaces it, the old buttons render disabled. On click the server always re-checks the current real state (an old "Cancel" on an order that has since shipped is rejected with a fresh answer), so a stale snapshot can never cause a wrong write.
- Confirmation requests store their outcome (CONFIRMED / DECLINED / EXPIRED, with TTL).
- What the model sees from history: the stored text plus a compact summary of blocks (ids and key fields), not the full JSON, to save tokens. Details are pulled again through tools when needed.

## Out of scope for this step (all needed later, keep the design open for them)
Frontend; real service wiring; retries/idempotency/fallback model (phase 3); tier-2 approval HITL, PII masking, injection defenses (phase 4); long-term memory (phase 5); context challenge (phase 6); multi-agent (phase 7); full eval/CI gate (phase 8); Langfuse, rate limits, dashboards (phase 9).

## Decisions
- Package: `com.rmrx.agenticshop.ai` (sub-package `agent` reserved for later).
- Chat model: OpenAI (configurable model name). Embeddings: OpenAI `text-embedding-3-small` (1536 dims, cheap, good enough for policy text; switch to `-large` only if retrieval evals demand it).
- LangChain4j / LangGraph4j as plain libraries, no Spring starters; beans wired manually in ai config.
- Conversation messages live in the existing ticket/ticket_message tables. The ai package adds its own tables (runs, steps, checkpoints, usage ledger, price table, policy chunks) that link to the ticket.
- Context strategy: only minimal context is injected each turn (identity summary, current ticket id, last few turns); older ticket history and policies are pulled by the model through tools (just-in-time).

## Verification (per feature, later)
- Postgres via user's docker compose (ask the user to start it).
- Golden-scenario eval run as the main check; manual SSE call per role (customer, support tier-1, seller) with curl.
- Leakage suite = 0 before RAG is considered done.

---
