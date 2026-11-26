# AI support agent — implementation steps

Steps for the features in [ai-plan.md](ai-plan.md). Each step was built and verified on its own before the next.

## Status (2026-11-25)

| Step | Topic | State |
|---|---|---|
| 1 | Skeleton, dependencies, config | built |
| 2 | Contracts | built |
| 3 | Persistence schema | built |
| 4 | Usage ledger + trace | built |
| 5 | Event emission (SSE) | built |
| 6 | Mock tool layer | built |
| 7 | Action engine | built |
| 8 | Agent graph + conversation API | built |
| 9 | Action round-trip | built |
| 10 | Clarification, refusal, escalation | built |
| 11 | Rolling summary + checkpointing | built |
| 12 | RAG ingestion | built |
| 13 | RAG retrieval + search_policies | built |
| 14 | Eval harness | pending (after the UI is ready for testing) |

"Built" means verified against a scripted OpenAI stub. Behaviour with the real model still needs work before the UI phase.


Rules for every step: build only that step, compile and boot, show the result, stop for review before the next one. Feature ids (F1-F14) refer to the list above. Frontend untouched. The user starts Postgres (docker compose) when a step needs it.

Existing pieces to reuse:
- `security.CurrentUser` / `security.AccessScope` (identity, ownership, `visibleMinRoles`, `canSeeDocument/Section`). `AccessScope` reads the request thread's SecurityContext, so the ai layer captures `CurrentUser` into `AgentContext` on the request thread and never calls `AccessScope.current()` from async/streaming threads.
- `support.entity.Ticket` / `TicketMessage` (chat timeline), `Role.satisfies`, `SupportTier`.
- `docs` entities `Document` / `DocumentVersion` / `DocumentSection` (RAG source, already versioned with min_role).
- `common.exception` types; `app.jwt.secret`-style config for the action-token key.

## Step 1 — Skeleton, dependencies, config
- `pom.xml`: `langchain4j` core, `langchain4j-open-ai`, `langgraph4j-core` (+ its Postgres checkpoint module if usable), `pgvector` JDBC. Plain libraries, no starters. Pin versions after checking compatibility with Boot 4.1 / Java 25 / the Jackson version in use.
- Package layout under `com.rmrx.agenticshop.ai`: `config`, `web`, `contract`, `context`, `conversation`, `graph`, `tools` (`port`, `mock`), `action`, `rag`, `usage`, `trace`, `events`, `eval`, each with a `package-info.java`.
- `AiProperties` (`app.ai.*`): `OPENAI_API_KEY`, chat model, summary model, embedding model `text-embedding-3-small`, temperature, timeouts, step budget, history window, mock defaults, failure injection (off), enabled flag.
- `AiConfig`: manual beans for `OpenAiChatModel`, `OpenAiStreamingChatModel`, `OpenAiEmbeddingModel`, virtual-thread executor.
- Flyway `V8__ai_extension.sql`: `create extension if not exists vector`.
- Done when: app boots with and without the key; nothing else changes.

## Step 2 — Contracts (inputs/outputs, no logic)
- `AgentContext` (user id, role, seller id, support tier, ticket id, request id) + factory from `CurrentUser` and ticket.
- Requests: `StartConversationRequest`, `SendMessageRequest`, `ActionRequest`.
- Envelope (F3): `AgentResponse` (schemaVersion, status, text, blocks, citations, suggestedReplies, runId, messageId); sealed `Block` with records ORDER_LIST, ORDER_DETAIL, SHIPMENT_TRACKING, PRODUCT_LIST, PRODUCT_DETAIL, RETURN_STATUS, REFUND_RESULT, POLICY_ANSWER, CONFIRMATION_REQUEST, CHOICE_REQUEST, ESCALATION; `ItemAction` (token, type, label, requiresConfirmation, state); `Citation`; `SuggestedReply`. Jackson `type` discriminator.
- Events (F13): sealed `AgentEvent` — RunStarted, TokenDelta, ToolStarted, ToolFinished, BlockReady, Final, Error.
- Tool results (F7): sealed `ToolResult<T>` = `Ok(resultId, data)` | `Failure(code, message)`; `ToolErrorCode` NOT_FOUND, FORBIDDEN, INELIGIBLE, CONFLICT, LIMIT_EXCEEDED, UNAVAILABLE, INVALID_INPUT; compact views (OrderSummary, OrderDetail, ProductSummary, ...).
- Done when: compiles; sample JSON of each envelope/event shown for review.

## Step 3 — Persistence schema
- `V9__ai_core.sql`:
  - `ticket_message`: add `kind` (TEXT, ENVELOPE, ACTION, SYSTEM; default TEXT), `payload jsonb`, `schema_version`; `AuthorType.AI`.
  - `ai_run` (ticket, user, status, prompt version, model, timings, step count, error).
  - `ai_step` (run, seq, MODEL/TOOL, name, args/result summary, error code, latency, tokens) — trace, prunable.
  - `ai_model_price` (provider, model, token type, price per 1M, effective_from) + seed rows for the chosen OpenAI models.
  - `ai_usage` (append-only ledger: user nullable, ticket, run, purpose, provider, model, input / cached input / output tokens, cost, price row id, created_at).
  - `ai_action` (token hash, ticket, message, user, type, target type/id, params jsonb, requires_confirmation, state OPEN/USED/EXPIRED/SUPERSEDED, expires_at, outcome).
- Entities + repositories; minimal additive change in `support` (`AuthorType.AI`, `MessageKind`, new columns).
- Done when: migrations apply and `ddl-auto: validate` passes.

## Step 4 — Usage ledger + trace infrastructure (F11, F12)
- `UsageRecorder`: langchain4j `ChatModelListener` + embedding wrapper record every call with purpose and context (via a `RunScope`); cost from the price row effective at call time; streaming and aborted calls record final/partial usage.
- `TraceRecorder`: run start/finish, model and tool steps.
- Endpoints: `GET /api/ai/usage/me`, `GET /api/ai/tickets/{id}/usage` (incl. current context size), `GET /api/ai/usage/users/{id}` (FINANCE/ADMIN), `GET /api/ai/runs/{id}/trace` (staff); permissions in `RolePermissions`.
- Dev-profile endpoint doing one model call.
- Done when: one real OpenAI call produces a correct `ai_usage` row and aggregates show it.

## Step 5 — Event emission (F13)
- `AgentEventPublisher` + `RunEventBus` (per run, buffered so a late subscriber gets earlier events).
- `SseTransport`: `GET /api/ai/runs/{runId}/events`. A websocket transport later implements the same subscriber interface.
- Dev endpoint emits a fake sequence.
- Done when: `curl -N` shows the ordered stream ending with the final envelope.

## Step 6 — Mock tool layer (F7, F2)
- Ports: `OrderPort`, `ShipmentPort`, `CatalogPort`, `ReturnPort`, `RefundPort`, `SupportPort` (all take `AgentContext`).
- `MockWorld`: in-memory, mutable, built from JSON fixtures per scenario (happy, not-found, not-owner, ineligible, already-refunded, partial-shipment, empty); selected per user fixture or `X-Mock-Scenario` header (dev/eval only); ownership enforced; failure-injection hook off.
- Tools (`@Tool`, clear descriptions): reads `get_my_orders`, `get_order`, `track_shipment`, `search_products`, `get_product`, `get_return_status`, `get_ticket_history`; writes `cancel_order`, `create_return_request`, `issue_refund` (hard limit in code), `escalate_to_human`, `add_ticket_note` — writes run only through the confirmation path (step 7). `search_policies` comes in step 13.
- `TurnResultRegistry` gives each Ok result a `resultId`; `ToolCatalog` filters tools by role.
- Done when: a dev endpoint runs each tool per scenario; a customer cannot read another user's order.

## Step 7 — Action engine (F3 actions, F4)
- `ActionPolicy`: allowed actions per item from current state + role.
- `ActionTokenService`: HMAC-signed token bound to user + ticket + target + type, stored hashed with TTL.
- `WriteGateway`: `prepare` → CONFIRMATION_REQUEST (amount, item, reason) + pending action; `commit` re-checks real state, executes, stores outcome; supersedes older open confirmations for the same target.
- `BlockFactory`: blocks from registered tool results, with actions attached.
- Done when: prepare / commit / decline / expire / replay / forged token behave correctly via dev endpoints.

## Step 8 — Agent graph v1 + conversation API (F1, F8, F14)
- LangGraph4j `AgentState` (context, messages, results, step count, draft).
- Nodes: `loadContext` (identity summary + last N turns with compact block summaries) → `agent` (role-filtered tools) ⇄ `tools` (publish ToolStarted/Finished, register results; step budget) → `compose` (structured output: status, text, resultIds to show, suggestedReplies; code builds blocks) → `persist` (ENVELOPE message, actions, run finish).
- Prompt `resources/ai/prompts/support-agent.v1.md`; version recorded on the run.
- API: `POST /api/ai/conversations`, `POST /api/ai/conversations/{ticketId}/messages` (returns runId; stream via step 5), `GET /api/ai/conversations/{ticketId}/messages`, `POST /api/ai/conversations/{ticketId}/close`.
- Done when: "show my orders" returns ORDER_LIST with actions; ledger + trace rows exist; history reload shows the same envelope.

## Step 9 — Action round-trip (F4, F14)
- `POST /api/ai/actions/{token}` (confirm/decline): validate, store ACTION message, mark USED, resume the graph with a structured `ActionEvent`.
- Timeline shows USED/EXPIRED/SUPERSEDED actions disabled.
- Done when: cancel → confirm → mock state changed → block with new state; stale action on a since-shipped order is rejected with a fresh answer.

## Step 10 — Clarification, refusal, escalation (F5, F6)
- Ambiguous target → NEEDS_INFO + CHOICE_REQUEST; out-of-scope → REFUSED without tool calls; escalation triggers → `escalate_to_human`, ESCALATED block, SYSTEM message, agent stops acting on that ticket.
- Done when: each path reproduces with a mock scenario.

## Step 11 — Rolling summary + checkpointing (F1, F8)
- Summary of older turns (purpose SUMMARY in ledger) when history exceeds the window.
- Postgres checkpoint saver per ticket (library module or own `ai_checkpoint` table); interrupted run resumes.
- Done when: long thread keeps bounded context size; resume after kill works.

## Step 12 — RAG ingestion (F9)
- `V10__ai_policy_chunk.sql`: `ai_policy_chunk` (document, version, section, doc type, seller_id, min_role, effective_from/to, text, `embedding vector(1536)`, `tsv tsvector`), HNSW + GIN indexes.
- `PolicyIngestionService`: heading-aware chunking of sections, batch embedding (purpose INGESTION, no user), idempotent per version.
- `POST /api/ai/admin/rag/reindex` (all / per version), ADMIN only.
- Done when: seeded documents are chunked and embedded with sane counts per doc/role.

## Step 13 — RAG retrieval + `search_policies` (F9)
- `PolicyRetriever`: single SQL with ACL in WHERE (mirrors `AccessScope.canSeeDocument/Section`), time filter on the reference date, vector top-k + full-text top-k, RRF; `Reranker` interface with no-op default.
- Reference date = order date for order questions, else now.
- Tool returns chunks with citation data → POLICY_ANSWER + `citations[]`; below threshold → no-answer → refusal/escalation.
- Done when: same question as CUSTOMER vs SUPPORT_T2 gives different, correctly cited sources; T2-only sections never reach T1.

## Step 14 — Eval harness (F10)
- Scenario YAML (`src/test/resources/ai/evals/`): role/user, mock scenario, turns (text or action), expectations (tool trajectory, final status, block types, final MockWorld state, citations, forbidden content).
- Runner (`eval` profile command or JUnit tag `eval`) against the user's DB; resets MockWorld per scenario; report JSON + markdown (pass rate, tokens, cost, latency); baseline file committed.
- Suites: support (~15 to start), retrieval (recall@k, citation correctness), leakage (must be 0).
- Done when: report generated and baseline stored.

## Verification (every step)
- `./mvnw -q compile` and boot; DB steps after the user starts Postgres.
- HTTP calls per role with seeded logins; SSE with `curl -N`.
- From step 8: check ledger, trace and timeline per manual run; from step 14: eval report is the main check.
