# ai2 — the redesigned support agent

`backend/src/main/java/com/rmrx/agenticshop/ai2` is a step-by-step redesign of the AI support agent. The old `ai`
package is left untouched; ai2 has its own beans (named `ai2…` where a name would clash), tables (`ai2_…`), model
bean, prompt and endpoints (`/api/ai2/…`).

This document describes what exists today. Open items are listed at the end.

---

## 1. Big picture

```
WS   /api/ai2/ws?connectToken=…                 (chat socket: send / answer / cancel in, all the user's run events out)
POST /api/ai2/ws/connect-tokens                 (one-time connect token for the socket handshake)
GET  /api/ai2/conversations                     (the user's conversations, with titles)
GET  /api/ai2/conversations/{id}                (one conversation: active run + timeline)
POST /api/ai2/conversations/messages            (REST send, first message: creates the conversation — to be removed)
POST /api/ai2/conversations/{id}/messages       (REST send, next messages — to be removed)
POST /api/ai2/conversations/{id}/answers        (REST answer to a question / approval — to be removed)
GET  /api/ai2/conversations/{id}/messages       (timeline)
GET  /api/ai2/runs/{runId}/events               (live run events, SSE)
          │
          ▼
ConversationService ── conversation + ownership checks, one active run per conversation,
          │            stores the user's message
          ▼
RunLauncher ── runs the graph on a virtual thread; stores question/approval or reply, then emits INTERACTION / REPLY
          │
          ▼
GraphRunner ── the only class outside `graph` that talks to langgraph4j (start / resume / discard)
          │
          ▼
RunGraph (langgraph4j) ── modelCall → outputValidation → tool nodes → modelCall … → END
          │
          ├── ModelCallNode ── system prompt + conversation history (HistoryProvider) + this run's messages
          ├── ToolCallExecutor ── langchain4j tool execution, results kept under refs r1, r2, …
          └── JdbcCheckpointSaver ── graph state in Postgres after every node
```

A **run** is one execution of the graph for one user message. It either **completes** (with a reply), **pauses**
(waiting for the user's answer to a question or an approval) or is **abandoned** (the paused question was not
answered). A conversation has at most one active run at a time.

---

## 2. Packages

| Package | Contents |
|---|---|
| `ai2.graph` | `RunGraph` (nodes + edges), `RunLimits` |
| `ai2.graph.node` | `ModelCallNode`, `OutputValidationNode`, `ToolExecutionNode`, `AskUserNode`, `SensitiveToolExecutionNode` |
| `ai2.graph.edge` | `RunRoutes` (conditional edges) |
| `ai2.graph.state` | `RunState`, `RunContext`, `StepPlan`, `ValidationOutcome` |
| `ai2.graph.agent.response` | `AgentResponse`, `DisplayReference`, `SuggestionReply`, `ResponseStatus` (the model's final reply) |
| `ai2.graph.agent.pending` | `PendingInteraction` (what a paused run waits for) |
| `ai2.contract.interaction` | `HumanAnswer`, `InteractionJson` |
| `ai2.contract.domain` | ai2's own enums and values (`OrderStatus`, `ReturnReason`, `TicketStatus`, `Address`, `Verdict` …); never `app` classes |
| `ai2.contract.tool` | `ToolResponse`, `ToolResult`, `PageInfo`, `Texts` |
| `ai2.contract.tool.response.*` | responses per family: `product`, `order`, `shipment`, `returns`, `customer`, `support` |
| `ai2.contract.reply` | `ReplyEnvelope`, `ReplyStatus`, `ReplyEnvelopes` (agent response → envelope; failed / stopped) |
| `ai2.contract.reply.block` | `ReplyBlock` (sealed, JSON `type`) + one record per block, `ReplyBlocks` (tool result → block; `displayable`) |
| `ai2.tools` | `ToolRegistry` (one `ToolCatalog` per `Audience`), `ToolDefinitions`, `ToolCallExecutor` (runs as the user), `ToolCallTracker` (progress events + TOOL trace step), `ToolPaging`, `ToolDates`, annotations (`@ToolAudience`, `@ToolLabel`, `@Sensitive`, `@AskUser`) |
| `ai2.tools.ask` | `AskUserTools` (`ask_choice`, `ask_input`, `ask_yes_no`) + their argument records |
| `ai2.tools.{product,order,customer,shipment,returns,support}` | `*Tools` (thin `@Tool` methods) + MapStruct `*ToolMapper` (domain DTO → contract) |
| `ai2.model` | `Ai2ModelConfig` (the `ai2ChatModel` bean), `ModelGateway` (the only model caller: retries, trace, usage), `ModelCall`, `ModelRetryPolicy`, `ModelCallException`, `SystemPrompt`, `Ai2ModelProperties`, `Ai2PromptProperties`, `Ai2ReplyProperties`, `Ai2RunProperties` |
| `ai2.conversation` | `ConversationService`, `RunLauncher` (threads, cancel), `RunRecorder` (start / end of a run: message, active run, trace), `GraphRunner`, `RunOutcome`, `ConversationConflictException` |
| `ai2.events` | `ChatResponse` (the one outgoing message), `AgentEventPublisher`, `RunEvents` / `RunEventBus`, `InMemoryRunEventBus`, `Ai2EventProperties` |
| `ai2.events.sse` | `SseRunEventTransport` |
| `ai2.events.ws` | `WsConfig`, `Ai2WsProperties`, `WsConnectTokenStore`, `WsHandshakeInterceptor`, `WsUser`, `ChatSocketHandler`, `ClientSession`, `ClientMessage` |
| `ai2.conversation.store` | `Conversation`, `ConversationStore` |
| `ai2.conversation.message` | `ConversationMessage`, `ConversationMessageStore`, `MessageKind`, `InteractionStatus`, `ReplyDisplay` |
| `ai2.activerun` | `ActiveRun`, `ActiveRunRegistry` |
| `ai2.checkpointer` | `JdbcCheckpointSaver` |
| `ai2.memory` | `HistoryProvider` (also `memoryState`), `MemoryState`, `ConversationSummarizer`, `HistoryProjection`, `ConversationMemoryStore`, `Ai2MemoryProperties` |
| `ai2.trace` | `TraceRecorder` (writes `ai2_run` / `ai2_run_step`), `TraceStep`, `StepType`, `RunStatus`, `ErrorCodes`, `TraceQueries`, `TraceViews` |
| `ai2.usage` | `UsageRecorder` (writes `ai2_usage`), `UsageScope`, `UsageIssuer`, `UsagePurpose`, `TokenCounts`, `TokenTotals`, `UsageQueries`, `UsageService`, `UsageViews` |
| `ai2.web` | `ConversationController`, `UsageController` (usage + traces), `RunEventsController`, `WsConnectTokenController`, `DevToolsController` (dev only), `SendMessageRequest`, `AnswerRequest` |

---

## 3. The graph

```
START → modelCall → outputValidation ─┬─ RETRY ─────────► modelCall
                                      ├─ FAILED ────────► END   (fallback reply)
                                      ├─ FINAL_ANSWER ──► END
                                      └─ TOOL_CALLS ────► toolExecution? → askUser | sensitiveToolExecution → modelCall
```

- **Only `outputValidation` ends a run.** Edges skip tool nodes that have nothing to do in the current step.
- `askUser` and `sensitiveToolExecution` always go back to `modelCall`; their pauses happen inside the node.
- Routing is in `RunRoutes.afterValidation` / `afterToolExecution`; they only read state written by validation.

### Nodes

| Node | What it does |
|---|---|
| `ModelCallNode` | Builds the request: system prompt (rendered per call) + history + this run's messages + all tools + the `AgentResponse` JSON schema as response format, and sends it through `ModelGateway`. On the run's last allowed call, or once the run's tokens reach `app.ai2.run.max-tokens`, `toolChoice = NONE` and a "no more tools" note. |
| `OutputValidationNode` | Validates the model's output and decides how the step ends (see below). |
| `ToolExecutionNode` | Executes read-only tool calls; stores each successful result under a new ref. |
| `AskUserNode` | Pauses once per unanswered ask call; when all are answered, writes the answers as the calls' results. |
| `SensitiveToolExecutionNode` | Pauses once per sensitive call for approval; executes approved calls, a "denied" result for the others. |

### Output validation

For **tool calls**:
- unknown tool name (including a tool of another audience) → error result per call, retry;
- tool calls although the call was made with tools off (budget used up) → error results, retry while model calls are
  left (a used-up token budget never fails the run by itself);
- more than 5 calls in one step → the rest get "Skipped: more than 5 tool calls in one step…";
- a call with the same tool and arguments (JSON, key order ignored) as an earlier call of the run — counting only
  calls after the run's last sensitive call, since an action may change what a read returns — gets "Not executed:
  already called with the same arguments in this request; use its result (ref rN)";
- calls are sorted into a `StepPlan` (read / ask / sensitive / deferred) by tool category;
- **mixing rule**: when a step contains an ask, its sensitive calls are deferred with the result "Not executed: call it
  again after the user has answered your question".

For a **final answer** (text without tool calls): parse JSON into `AgentResponse`, then check status present, text
non-blank and ≤ `max-text-length`, ≤ `max-suggestions` suggestions with labels ≤ `max-suggestion-label-length`, and
that `finalReferenceToDisplay.ref` is a ref of this run whose `selectedItemIdentifiers` are items of that result, and
that the result can be shown at all (`ReplyBlocks.displayable`: everything except category filters).

Failure → feedback to the model (an error result per tool call, or a system message for a final answer) and RETRY;
after 2 consecutive failures, or when the model-call budget is used up, FAILED with a fallback reply.

Every rejection and every dropped call is a VALIDATION step in the run's trace. Stubs still marked TODO: argument
parsing against tool schemas, silent fixes.

### State (`RunState`)

Checkpointed after every node, so every value is `Serializable`.

| Key | Content | Written by |
|---|---|---|
| `context` | `RunContext(userId, conversationId, runId)` | caller |
| `messages` | this run's messages (append-only, duplicates kept) | several nodes |
| `modelCallCount` | model calls so far | modelCall |
| `tokensUsed`, `lastCallToolsOn` | input + output tokens of the run's model calls (token budget); whether the last call could use tools | modelCall |
| `validationOutcome`, `validationRetries`, `stepPlan` | how the last step ended | outputValidation (every pass) |
| `response` | the final `AgentResponse` | outputValidation |
| `humanAnswers` | callId → `HumanAnswer` (merge reducer) | caller via `updateState` before resuming |
| `toolResults` | ref → `ToolResult` (merge reducer) | tool nodes |

History of earlier runs is **not** in the state; it is loaded per model call (section 9).

### Limits (`RunLimits`)

8 model calls per run (the last with tools off), 2 validation retries, 5 calls per step; recursion limit derived
from these. Token budget per run: `app.ai2.run.max-tokens` (default 200 000, input + output of the run's own calls);
once reached, the next call is made with tools off.

---

## 4. The model's reply

```json
{
  "status": "ANSWERED | REFUSED",
  "text": "short text for the chat",
  "finalReferenceToDisplay": { "ref": "r2", "selectedItemIdentifiers": ["101", "103"] },
  "suggestedReplies": [ { "label": "Red running shoes", "text": "Do you have red running shoes?" } ]
}
```

- `finalReferenceToDisplay` names **one tool result of this run** to show the customer (a product list, an order…);
  `selectedItemIdentifiers` narrows it to some items (empty = all). Null when nothing is shown.
- The final answer arrives through the request's JSON response format; a model output with tool calls is a step, not
  a reply.

### Reply envelope (what the client gets)

A REPLY message (`ChatResponse.reply`, section 11) carries a `ReplyEnvelope`, built by `ReplyEnvelopes` from the
response and the tool result its `finalReferenceToDisplay.ref` names:

```json
{
  "status": "ANSWERED",
  "text": "Here are the **running shoes** I found.",
  "block": { "type": "PRODUCT_LIST", "items": [ { "id": 101, "title": "…", "price": 89.90, "thumbnailUrl": "…" } ] },
  "suggestedReplies": [ { "label": "…", "text": "…" } ]
}
```

- `status`: `ANSWERED | REFUSED` (from the agent), `FAILED | STOPPED` (stored by the system for a run that ended
  without a reply: an error, the user's stop, a restart). `text` is markdown (the UI renders lists, bold, italic,
  code, links — without innerHTML).
- `block` (absent for a text-only reply) is a `ReplyBlock` whose `type` names the card: `PRODUCT_LIST`,
  `PRODUCT_DETAIL`, `REVIEW_LIST`, `ORDER_LIST`, `ORDER_DETAIL`, `ORDER_CANCELLATION`, `ADDRESS_CHANGE`, `TRACKING`,
  `SHIPMENT_LIST`, `RETURN_LIST`, `REFUND_LIST`, `TICKET_LIST`, `TICKET_DETAIL`, `CUSTOMER_LIST`, `CUSTOMER_DETAIL`,
  `ADDRESS_LIST`. A single changed return, refund or ticket (after an action) shows as a one-item list. Only category
  filters have no block: the model reads "not shown to the user" on their ref line.
- List items are narrowed to `selectedItemIdentifiers` (empty = all), with the ids of `ToolResponse.itemIdentifiers()`
  (tracking: tracking numbers).
- The timeline still stores the raw response and `ReplyDisplay` (unchanged). Envelopes stored before V18 were
  migrated to the `block` shape.

---

## 5. Tools

Tools are langchain4j `@Tool` methods on Spring beans implementing `AgentToolSet`. `ToolRegistry` collects them at
startup; `ToolCallExecutor` runs them through langchain4j's `DefaultToolExecutor`.

### Categories and annotations

| Annotation | Category | Behaviour |
|---|---|---|
| (none) | READ | executed right away |
| `@Sensitive(approvalMessage, detail)` | SENSITIVE | the run pauses for the user's approval first |
| `@AskUser` | ASK | never executed; the run pauses for the user's answer |
| `@ToolLabel("… {param}")` | — | user-facing progress text (TOOL_STARTED event; default "Working on it…") |

**Startup checks** (the app does not start otherwise): `{param}` placeholders must be parameters of the tool, no
duplicate tool names, `@AskUser` and `@Sensitive` not together, approval message not blank, every executable tool
returns a `ToolResponse`.

### Results

- A tool returns a `ToolResponse` (Serializable) with the **full data**; it is stored in the state as a `ToolResult`
  under a run-scoped ref `r1, r2, …` (only successful executions get a ref).
- The model gets `ref: rN` followed by the JSON of `ToolResponse.toAgentSummary()` — a smaller view with only what it
  needs (ids kept so it can select items).
- `ToolResponse.itemIdentifiers()` lists the selectable items (product ids, order numbers) for validation.
- The executor never throws: unknown tool, ask tool or a failing tool becomes an error result.

### Audiences and identity

- Every tool has `@ToolAudience` (on the method or its class): `CUSTOMER` (role CUSTOMER) or `STAFF` (SUPPORT_T1,
  SUPPORT_T2, ADMIN). `ToolRegistry` builds one `ToolCatalog` per audience; a run sees only its user's catalog (offered
  specs, validation, execution). One name may have a customer and a staff variant with different parameters
  (`search_orders`, `search_returns`, `search_refunds`, `get_addresses`).
- `RunContext` carries the user's role and seller id. `ToolCallExecutor` runs each call inside
  `AccessScope.runAs(caller)`, so the shop's services apply that user's scope exactly as for their own requests
  (customers only ever see their own rows). Refund limits per role are enforced by the domain, not by tool selection.
- Tool methods read a per-call key (`ToolCallExecutor.CALL_KEY`, `runId:callId`) from an `InvocationParameters`
  parameter; `issue_refund` uses it as the refund's idempotency key.

### Paging

`ToolPaging`: pages of 10, `page` 1-based, at most 5. Page 6+ returns an error result telling the model to narrow the
search; on page 5 with more results, `PageInfo.note` says so. Every list response carries `PageInfo(total, page,
hasMore, note)`. The prompt states the rule.

### Contracts and mapping

Tool parameters and responses use only ai2 types: enums and values in `contract/domain`, responses in
`contract/tool/response/{product,order,shipment,returns,customer}`. Each tool family has a MapStruct
`<Family>ToolMapper` from the domain DTOs (an enum constant missing on either side fails the build). Business logic
lives in the domain (`app/*/service`): `OrderRules` (cancel, address change, return window), `OrderCommandService`,
`ReturnCommandService`, `RefundCommandService`, `RefundLedger`, `ShipmentService`, `AddressService`.

### Tools

| Tool | Audience | Category | What it does |
|---|---|---|---|
| `search_products` | both | READ | text + category, brands, price, rating, stock, attribute filters, sort; `refinements` (top categories/brands, price range) when the result is bigger than a page |
| `get_category_filters` | both | READ | category path, subcategories, filterable attributes (codes, options, ranges) |
| `get_product` | both | READ | details, attributes, stock, rating distribution; DRAFT = not found |
| `get_product_reviews` | both | READ | page of reviews (bodies cut to 300 chars for the model) |
| `search_orders` | C / S (+customerId, sellerId) | READ | number, statuses, dates, product words; newest first |
| `get_order` | both | READ | lines with itemId, payments, shipments, returns, refunds, `actions` (verdicts + return deadline + returnable quantities) |
| `cancel_order` | both | SENSITIVE | PENDING/PAID, not shipped; voids or refunds the rest, releases stock |
| `get_addresses` | C / S (customerId) | READ | saved addresses |
| `update_shipping_address` | both | SENSITIVE | not shipped yet; a saved address of the order's customer |
| `track_shipment` | both | READ | by order number or tracking number; carrier scans (model sees the last 5) |
| `search_shipments` | S | READ | carrier, statuses, dates, customer, seller, `overdueOnly` |
| `search_returns` | C / S (+customer, seller, reason, dates) | READ | return requests |
| `request_return` | both | SENSITIVE | delivered, inside the seller's window, quantity ≤ returnable |
| `cancel_return` | both | SENSITIVE | REQUESTED only |
| `decide_return` | S | SENSITIVE | approve/reject REQUESTED or DISPUTED, with a note |
| `search_refunds` | C / S (+customer, seller, dates) | READ | refunds |
| `issue_refund` | S | SENSITIVE | T1 ≤ 100, T2 ≤ 1000, ADMIN unlimited; ≤ what is left to refund; idempotent per call |
| `find_customers`, `get_customer` | S | READ | find by name/email; profile, addresses, counts |
| `escalate_to_human` | C | SENSITIVE (approval shows the summary) | hand-off: opens a T1 ticket linked to the order and the conversation, first message = the agent's summary (author AI); a second hand-off about the same order (or conversation) goes into the open ticket |
| `search_tickets` | C / S (+ assignee ME/UNASSIGNED/ANY, tier, priority, category, customer, query) | READ | own tickets / the support queue |
| `get_ticket` | both | READ | ticket + latest 10 messages; internal notes only for staff |
| `take_ticket`, `add_internal_note`, `escalate_ticket` | S | READ (internal, no approval) | assign to self; staff-only note; T1 → T2 (ESCALATED, unassigned, reason as note) |
| `reply_to_ticket`, `resolve_ticket` | S | SENSITIVE | customer-visible message; status PENDING (waiting on customer) / RESOLVED |
| `ask_choice`, `ask_input`, `ask_yes_no` | both | ASK | unchanged |

A product or order detail shows as a one-card PRODUCT_LIST / ORDER_LIST block until it has its own block. Any other
result cannot be shown: its ref line says "not shown to the user", and validation rejects a display reference to it,
so the model writes the facts in the text.

### Hand-off (human support)
Async tickets with a staff copilot. Scenario tested end to end through the model: a customer reports a parcel shown
delivered but never received → the agent checks tracking and the order, explains it cannot refund, and calls
`escalate_to_human` (customer approves the summary) → ticket `TCK-…` opens (OPEN, T1, linked to the conversation);
more details from the customer go into the same ticket → support T1 lists the unassigned queue, takes the ticket,
re-checks the facts, issues the refund (within its limit) and resolves the ticket with a message to the customer →
the customer asks the agent for news and gets the resolution. Over the T1 limit, the refund is refused and
`escalate_ticket` hands the ticket to T2. Domain: `TicketCommandService` (open with dedup, take, note, reply,
escalate, resolve), `TicketLookup`, `TicketSearch`; `ticket.conversation_id` (Flyway V17).

Dev-only: with `app.ai2.dev-endpoints=true` (launch config `backend-dev-tools`), `GET /api/ai2/dev/tools` lists the
caller's catalog and `POST /api/ai2/dev/tools/{name}` runs one tool as the caller (no model, no approval step).
Demo data (`V113`): customers `customer_1..3` each have a PENDING, a PAID, a SHIPPED (in transit) and a DELIVERED
order (`AS-<yymmdd>-9<n>000<1..4>`, dated when the migration ran) and a second saved address.

---

## 6. Human in the loop (questions and approvals)

- The model asks with an ask tool; approvals come from `@Sensitive` tools (the model never asks for permission).
- The pausing node puts a `PendingInteraction` in the langgraph4j interrupt metadata:
  `ChoiceQuestion | InputQuestion | YesNoQuestion | ApprovalRequest(title, detail)`, each with an `interactionId`
  (deterministic from run id + call id, so it stays stable when the node is re-entered) and the `callId`.
- The user answers with a `HumanAnswer`: `choice(values) | input(text) | yesNo(yes) | approval(approved) | dismissed`.
  `PendingInteraction.accepts(answer)` checks type and option values.
- langgraph4j calls `interrupt()` on every entry of the node, including after a resume, so the check is idempotent:
  it pauses on the first call without an answer. One pause per pending item; `apply()` runs once all are answered.
- Results the model gets: `{"selected":[…]}`, `{"text":…}`, `{"yes":true}`, `{"dismissed":true}`; a denied or dismissed
  approval gets "The user denied this action, so it was not executed. Do not retry it unless the user asks for it
  again."

### Rules (`ConversationService`)

| Input | No active run | RUNNING | PAUSED |
|---|---|---|---|
| new message | start a run | 409 | abandon the paused run, then start a run |
| answer | 409 | 409 | matching `interactionId` + accepted → resume; otherwise abandon (no new run) |

Abandoning marks the question NOT_ANSWERED and drops the run with its checkpoints.

---

## 7. Conversations and chat messages

- A first message without an id creates the conversation (`ai2_conversation`, owned by the user). Every other call
  checks ownership: another user's conversation is 404.
- A CLOSED conversation (memory over its hard limit) refuses new messages with 409 "This conversation is full; please
  start a new one".
- Everything the user sees is stored in `ai2_conversation_message`:

| Kind | When | Content |
|---|---|---|
| USER | a message is sent | the text |
| INTERACTION | a run pauses | the question / approval (`payload`), `interaction_status`, later the `answer` |
| ASSISTANT | a run completes | the `AgentResponse` (`payload`), the shown result (`display`: tool name, selected ids, full data, agent summary) and the `ReplyEnvelope` the UI renders (`reply`) |

A stopped (cancelled) or failed run leaves only the USER message.

Interaction status: PENDING → ANSWERED / DISMISSED (questions), APPROVED / DENIED (approvals), NOT_ANSWERED (abandoned).

---

## 8. Persistence (Postgres)

| Table | Purpose |
|---|---|
| `ai2_conversation` | conversations (user, OPEN/CLOSED) |
| `ai2_active_run` | the one active run per conversation — the conversation's lock; status RUNNING/PAUSED, pending interaction (JSON) |
| `ai2_checkpoint` | langgraph4j state after each node (thread = run id), serialized with `LC4jStateSerializer` |
| `ai2_conversation_message` | the chat timeline |
| `ai2_conversation_memory` | summary of older messages + the last message it covers |

- `ActiveRunRegistry` makes every change one atomic statement (`insert … on conflict do nothing`, a guarded update
  for resume, `delete … returning` for abandon), so concurrent requests cannot both win.
- Checkpoints are deleted with their active run (FK cascade); everything is deleted with its conversation.
- A failed checkpoint write fails the run (pausing and resuming depend on it).
- At startup, runs still RUNNING are dropped (cut off by the previous shutdown) and get a FAILED reply ("this reply
  was interrupted"); PAUSED runs stay and can still be answered — a paused approval survives a restart.
- `ai2_run` (one row per run, kept after the active run row is gone: status, prompt version, model, step count, error),
  `ai2_run_step` (the run's trace, see section 10) and `ai2_usage` (the token ledger) are written by `TraceRecorder`
  and `UsageRecorder`, each write committing on its own and never failing the run.
- Migrations: `V12__ai2_active_run_checkpoint.sql`, `V13__ai2_conversation_memory.sql`, `V14__ai2_message_reply.sql`,
  `V18__ai2_trace_usage.sql`.

---

## 9. Memory and history

`ModelCallNode` asks `HistoryProvider.history(runContext)` for the earlier part of the conversation:

1. **Summarize if due** (`ConversationSummarizer`): if more than `summarize-after` messages of earlier runs are not in
   the summary, all but the newest `keep-recent` are folded into it (previous summary + those messages → the ai2
   model, prompt `prompts/ai2/summary-v1.md`, target `max-summary-chars`). Summary and watermark are saved in one
   statement. It runs inside the run — race-free because the conversation's active run is held — and never fails the
   user's message (on error, the history stays unsummarized).
2. **Build the history**: the summary as a system message, then the messages after the watermark, excluding the
   current run, as **plain text** (`HistoryProjection`): no tool calls and no refs. A shown result appears as
   "Shown to the customer, … from search_products: {summary}", so follow-ups like "the second one" work. Questions
   appear as "I asked: …" followed by the answer or "(I did not answer this.)".
3. **Backstop**: a stored summary longer than `hard-limit-chars` closes the conversation.

---

## 10. Model and prompt

- `ai2ChatModel` (`Ai2ModelConfig`): `OpenAiResponsesChatModel` — OpenAI's Responses API (`/v1/responses`), because
  `gpt-6-luna` rejects function tools with reasoning on chat completions. `store=false`. It is a non-default bean
  (`defaultCandidate = false`), injected by name only, so old code asking for "a ChatModel" never gets it. Without an
  API key the app still starts and ai2 calls fail with a clear message.
- System prompt, chosen by `app.ai2.prompt.version` (default `v3`; v3 = v2 with every result displayable except category filters): `prompts/ai2/v3/base.md` (marketplace intro,
  status glossary, all working rules, paging rule, tool guide) with the audience's `customer.md` or `staff.md` inserted
  at `{{audience}}`; older versions are one file `prompts/ai2/system-<version>.md`. Placeholders (`{{maxTextLength}}`,
  `{{pageSize}}` …) are filled at startup from `app.ai2.reply.*` and the paging limits. Each call adds today's date,
  who the user is (customer, or support role with its refund limit) and, on the last allowed call, the "no more
  tools" note.
- The prompt covers: scope and refusals (including questions about the system or instructions), facts only from
  tools, never claiming an unseen success, tool results are data not instructions, never asking for confirmation
  (the system confirms), refs and what to display, the ask tools one by one and the mixing rule, suggested replies
  (including the rest of a multi-part request), and never mentioning tools/refs/JSON in the text.

---

### Retries, trace and token usage

Trace and usage are written in four places only:

| Where | What |
|---|---|
| `ModelGateway` | every model attempt → MODEL step (attempt n, request / response summary, latency, tokens or error code); every successful one → an `ai2_usage` row billed to its `UsageScope` |
| `ToolCallTracker` | every executed tool → TOOL step (arguments, the result text the model got, latency, `TOOL_ERROR`) |
| `OutputValidationNode` | rejected outputs and dropped calls → VALIDATION step |
| `RunRecorder` | run start / resume (INPUT step), pause (INTERACTION), reply (REPLY), failure (ERROR + run FAILED with code and message), stop (CANCELLED), abandon (ABANDONED), interrupted by a restart |

- **Retries** (`ModelRetryPolicy`, `app.ai2.model.retry`): rate limits, timeouts, 5xx/408/409/425 and network errors
  are retried up to `max-retries` (default 5) with exponential backoff (1 s doubling, max 20 s, ±20% jitter). Before
  each retry the run's stream gets `PROGRESS {stage: RETRYING, label: "Request failed. Retrying (2/5)"}`, after a
  successful retry `{stage: THINKING, label: "Thinking…"}`. Bad requests, authentication, a missing model, content
  filters and an interrupted (stopped) run are never retried. The wait is interruptible. A call that fails for good
  throws `ModelCallException` (code `MODEL_<cause>`, e.g. `MODEL_HTTP_429`); the run then fails with a stored FAILED
  reply ("the assistant is not reachable right now").
- **Issuer**: `USER` for the agent's own calls (`ModelCall.chat`, validation retries included), `SYSTEM` for work the
  system does itself (`ModelCall.summary`: the memory summarizer, not retried). Usage is tokens only: input (of which
  cached), output (of which reasoning).
- **Run failures** (anything thrown out of the graph) are not retried: `RunRecorder.failed` releases the run, stores
  the FAILED reply and traces the error; the history gives the next run "(I could not answer this message: an error
  occurred.)" (STOPPED: "(The customer stopped this request before I replied.)").

## 11. API

All endpoints need the `AI_CHAT` permission (customers and support staff).

| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/ai2/conversations` | — | `[{id, title, status, updatedAt}]`, most recently active first (≤ 100); title = the start of the first message |
| GET | `/api/ai2/conversations/{id}` | — | `{id, title, status, updatedAt, activeRun: {runId, status, pendingInteraction} \| null, messages}` |
| POST | `/api/ai2/conversations/messages` | `{"text": "…"}` | `RunOutcome` (new conversation) |
| POST | `/api/ai2/conversations/{id}/messages` | `{"text": "…"}` | `RunOutcome` |
| POST | `/api/ai2/conversations/{id}/answers` | `{"interactionId": "…", "answer": {"type": "choice", "values": ["WE45"]}}` | `RunOutcome` |
| GET | `/api/ai2/conversations/{id}/messages` | — | the timeline |

Usage and traces:

| Method | Path | Permission | Returns |
|---|---|---|---|
| GET | `/api/ai2/usage/me?from&to` | `AI_USAGE_READ_OWN` | `{userId, from, to, total, byIssuer: {USER, SYSTEM}}` (tokens; default the current UTC month) |
| GET | `/api/ai2/usage/users/{userId}?from&to` | `AI_USAGE_READ_ALL` | the same for any user |
| GET | `/api/ai2/conversations/{id}/usage` | `AI_CHAT` (owner, else 404) | `{conversationId, total, byIssuer, context: {lastCallInputTokens, lastCallAt, memory}}`; `memory` = `HistoryProvider.memoryState` (summary size, messages since the summary, the limits) |
| GET | `/api/ai2/conversations/{id}/runs` | `AI_TRACE_READ` | the conversation's runs, newest first |
| GET | `/api/ai2/runs/{runId}/trace` | `AI_TRACE_READ` | `{run, steps}` |

`TokenTotals` = `{calls, inputTokens, cachedInputTokens, outputTokens, reasoningTokens, totalTokens}` (total = input +
output).

The POST endpoints return at once with a `RunOutcome`: `running` (the run executes in the background) or `abandoned`
(`unanswered`); always `conversationId` and `runId`. How a running run ends comes as run events.

### What the client receives: `ChatResponse`

Every message to the UI (WebSocket frame, SSE event) has one shape, `ChatResponse`. `type` says what it is and
exactly the matching payload is set:

```json
{"type":"INPUT","requestId":"r1","conversationId":9,"runId":"…","seq":1,"at":"…","input":{"text":"show me running shoes"}}
{"type":"PROGRESS","conversationId":9,"runId":"…","seq":2,"at":"…","progress":{"stage":"TOOL_STARTED","label":"Searching products for running shoes","toolName":"search_products"}}
{"type":"REPLY","conversationId":9,"runId":"…","seq":4,"at":"…","reply":{"status":"ANSWERED","text":"…","block":{"type":"PRODUCT_LIST","items":[…]},"suggestedReplies":[]}}
{"type":"INTERACTION","conversationId":9,"runId":"…","seq":4,"at":"…","interaction":{"type":"approval","interactionId":"…","title":"Do you want to cancel order WE45?"}}
{"type":"ERROR","requestId":"r3","at":"…","error":{"code":"CONFLICT","message":"…","retryable":true}}
```

| type | Payload | When |
|---|---|---|
| `INPUT` | `input`: `{text}` (a message) or `{answered, answer}` (the question and the user's answer) | first event of a run, and of its resume |
| `PROGRESS` | `progress`: `stage` `TOOL_STARTED` (`label` = rendered `@ToolLabel`, `toolName`) · `TOOL_FINISHED` (`toolName`, `success`, `durationMs`) | while a run executes |
| `REPLY` | `reply`: the `ReplyEnvelope` (section 4) | the run completed; last event |
| `INTERACTION` | `interaction`: the `PendingInteraction` (question or approval) | the run waits for an answer; last event of this stream |
| `ERROR` | `error`: `code`, `message`, `retryable` | `AGENT_ERROR` (the run failed), `CANCELLED` (stopped by the user), `RUN_NOT_LIVE`, and for a refused WS request `BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`, `INTERACTION_CLOSED` (the answered question is no longer open: send a new message), `INTERNAL` |

- Run events carry `seq` (1, 2, 3 … per run, continuing across a pause and its resume) and `conversationId`. INPUT
  also carries the `requestId` of the WS request that sent it (only meaningful to that client; null over REST).
  Refusals carry the refused request's `requestId`, no `seq`, and go only to the requesting socket.
- A run's stream ends with its first REPLY, INTERACTION or ERROR.

### Run events (SSE)

`GET /api/ai2/runs/{runId}/events` (`text/event-stream`, owner only, otherwise 404). Frames: `id:` = seq,
`event:` = the `ChatResponse` type (`INPUT`, `PROGRESS`, `REPLY`, `INTERACTION`, `ERROR`), `data:` = the
`ChatResponse` JSON.

- The stream is opened before the POST returns, so subscribing after it replays the stream from its first event.
- REPLY and INTERACTION are emitted after the reply / interaction is stored and the active run updated, so the client
  can answer or send right away. Answering resumes the same `runId` with a new stream whose seq continues; the client
  subscribes again (with `Last-Event-ID` = the last seq it saw, or from scratch).
- Reconnect with `Last-Event-ID` (or `?after=`) to get only later events. Closed streams stay replayable for
  `retention`; an unknown or evicted run gets one `ERROR` `RUN_NOT_LIVE`.
- In memory, single instance (`InMemoryRunEventBus`, same design as the `ai` package's bus); never persisted.

Answer bodies by type: `{"type":"choice","values":[…]}`, `{"type":"input","text":"…"}`, `{"type":"yesNo","yes":true}`,
`{"type":"approval","approved":true}`, `{"type":"dismissed"}`.

Errors: 400 invalid body, 401/403 auth, 404 not the caller's conversation, 409 run in progress / no question to
answer / conversation full.

### WebSocket (the chat channel)

**Handshake.** Browsers cannot send an `Authorization` header on a WebSocket, and the JWT should not go into a URL:

1. `POST /api/ai2/ws/connect-tokens` with the bearer JWT (`AI_CHAT`) → `{"connectToken": "…", "expiresAt": "…"}`.
   The token is 32 random bytes, valid for `connect-token-ttl` (30s) and **single use**.
2. Open `ws://…/api/ai2/ws?connectToken=…`. The handshake consumes the token: missing / unknown / used / expired →
   **401**, a user without `AI_CHAT` → **403**, an Origin not in `allowed-origins` → **403** (a client without an
   Origin header, i.e. not a browser, is let through). The security chain permits only this GET path unauthenticated.
3. Every frame then acts as that user only. Ownership is checked as for REST: another user's conversation or run →
   `NOT_FOUND`.
4. The socket is closed (1008, "Session expired; reconnect with a new connect token") when the JWT the connect token
   was issued for expires.

**Client frames** (`ClientMessage`, `type` property; `requestId` is the client's correlation id, ≤ 64 chars):

```json
{"type":"chat","requestId":"r1","text":"show me running shoes"}
{"type":"chat","requestId":"r2","conversationId":9,"text":"and in red?"}
{"type":"chat","requestId":"r3","conversationId":9,"interactionId":"…","answer":{"type":"approval","approved":true}}
{"type":"cancel","requestId":"c1","conversationId":9}
```

`chat` is the merged send/answer contract (`ChatRequest`): either `text` (≤ 4000; without `conversationId` a new
conversation is created) or `conversationId + interactionId + answer`. `cancel` stops the conversation's RUNNING run:
it is interrupted, released with its checkpoints, nothing is stored for it, and its stream ends with ERROR `CANCELLED`
(409 CONFLICT when nothing runs).

**Server frames** are `ChatResponse`s (see above). **Every socket of a user gets every event of every run of that
user** (`RunEventBus.subscribeUser`, live only, from when the socket opened), so two devices or tabs show the same
conversation: one sends, both see the INPUT, the progress and the reply. The sender recognises its own INPUT by
`requestId` (and, for a first message, learns the new `conversationId` from it). Events carry `conversationId`, so a
client shows the open conversation's events and uses the rest for its list.
- A refused request gets an ERROR with its `requestId` on that socket only; the socket stays open.
- An answer that no longer matches the open question gets ERROR `INTERACTION_CLOSED` (the paused run was abandoned).
- A client that (re)connects or opens a conversation mid-run reads `GET /api/ai2/conversations/{id}`: the history
  (a reply or question is stored before it is emitted, so nothing is lost) and `activeRun` (RUNNING: a reply is on
  its way; PAUSED: the question to answer).

**Limits.** Client frames over `max-message-bytes` (16 KB) close the socket (1009). Sends go through a
`ConcurrentWebSocketSessionDecorator`: a client that blocks a send longer than `send-time-limit` or lets
`send-buffer-bytes` pile up is disconnected, never the run. The server pings every `ping-interval`; a client that
sends nothing (pongs included) for `idle-timeout` is closed. Both use Spring's 4500 ("session not reliable"), and so
does a socket the bus drops for falling too far behind. Closing a socket only stops delivery; runs go on.

### The chat page (frontend)

`frontend/src/app/features/chat` (route `/chat`, `/chat/:conversationId`, needs `AI_CHAT`; the "Support chat" button at
the bottom of the side nav). `ChatSocket` (connect token → socket, reconnect with backoff) and `ChatStore` are provided
by the page: the socket opens with the page and closes when it is left. The conversation list sits next to the app's
side nav; the open conversation comes from the URL and its history from the detail endpoint. A usage bar above the
conversation shows this chat's tokens (you / system), its context (the last call's input tokens, the memory: messages
kept word for word vs. the summarize threshold) and the user's tokens this month, reloaded when a run ends. Progress
is one transient line (`ProgressLine`): a new label erases the old one from its end and types in from its start
(shared beginning kept; instant under reduced motion); a reply shows its markdown text (`shared/ui/markdown-text`), its
block (`components/blocks`, one card per block type) and, on the last reply, its suggestions; FAILED / STOPPED replies
show as the error / "Stopped." notes;
questions and approvals are boxes with their buttons and become "asked / answered" boxes once answered. The send button
turns into stop while a run executes. Socket events of other conversations only refresh the list (busy dot).

---

## 12. Configuration (`application.yml`)

```yaml
app:
  ai2:
    dev-endpoints: ${AI2_DEV_ENDPOINTS:false}   # /api/ai2/dev/tools (never in production)
    model:          # OpenAI Responses API
      api-key: ${OPENAI_API_KEY:}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com/v1}
      model-name: ${AI2_CHAT_MODEL:gpt-6-luna}
      max-output-tokens: 4000
      reasoning-effort: ${AI2_REASONING_EFFORT:}   # none | minimal | low | medium | high
      timeout: 60s
      retry:        # transient model errors; the user sees "Request failed. Retrying (n/max)"
        max-retries: ${AI2_MODEL_MAX_RETRIES:5}
        initial-backoff: 1s
        max-backoff: 20s
    run:
      max-tokens: ${AI2_RUN_MAX_TOKENS:200000}   # then the next model call answers without tools
    prompt:
      version: ${AI2_PROMPT_VERSION:v3}   # prompts/ai2/v3/base.md + customer.md / staff.md
    reply:          # told to the model and enforced by validation
      max-text-length: 600
      max-suggestions: 3
      max-suggestion-label-length: 40
    memory:
      summarize-after: ${AI2_SUMMARIZE_AFTER:20}
      keep-recent: ${AI2_KEEP_RECENT:6}
      max-summary-chars: 3000
      hard-limit-chars: ${AI2_SUMMARY_HARD_LIMIT:6000}
      prompt-version: v1
    events:         # live run events, in memory
      retention: 5m       # closed streams stay replayable this long
      buffer-size: 2000
      heartbeat: 15s
      max-open: 10m       # a stream still open after this is closed (hung run)
    ws:             # chat WebSocket
      allowed-origins: ${AI2_WS_ALLOWED_ORIGINS:http://localhost:4200}
      connect-token-ttl: 30s
      max-message-bytes: 16384
      send-time-limit: 10s
      send-buffer-bytes: 524288
      ping-interval: 15s
      idle-timeout: 60s   # no incoming traffic (pongs included) for this long closes the socket
```

---

## 13. Trying it out

Launch configurations in `.claude/launch.json`: `backend` (normal), `backend-dev-tools` (with
`/api/ai2/dev/tools`), `backend-small-memory` (summarize after 4 messages), `backend-tiny-memory` (summarize after 2,
hard limit 50 characters — to see a conversation close).

Seeded demo users (password = username): `customer_1..3`, `support_t1_1..3`, `support_t2_1..3`, `admin_1..3`
(`db/seed/V111__seed_logins.sql`). Each demo customer has a PENDING, a PAID, an in-transit and a delivered order
(`AS-<yymmdd>-9<n>000<1..4>`, from `V113`), and a second saved address.

Try one tool without the model (dev endpoint, runs as the signed-in user, no approval step):

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/ai2/dev/tools/track_shipment -Headers $headers -ContentType 'application/json' -Body '{"trackingNumber":"1ZDEMO10003"}'
```

```powershell
$headers = @{ Authorization = "Bearer " + (Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login -ContentType 'application/json' -Body (@{ username = 'customer_1'; password = $password } | ConvertTo-Json)).token }
$r = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/ai2/conversations/messages -Headers $headers -ContentType 'application/json' -Body '{"text":"please cancel my pending order"}'
# follow the run until REPLY / INTERACTION / ERROR; the INTERACTION event carries interaction.interactionId
curl.exe -sN -H "Authorization: $($headers.Authorization)" "http://localhost:8080/api/ai2/runs/$($r.runId)/events"
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/ai2/conversations/$($r.conversationId)/answers" -Headers $headers -ContentType 'application/json' -Body (@{ interactionId = $interactionId; answer = @{ type = 'approval'; approved = $true } } | ConvertTo-Json -Depth 5)
```

Scenarios verified against the live app with the real model: first message creates a conversation; follow-ups use
history; question/approval lifecycle (PENDING → APPROVED / DISMISSED / NOT_ANSWERED); a paused approval answered after
a restart; double answer 409; wrong interaction id → abandoned; RUNNING lock and its cleanup at startup; another
user's conversation 404; delete cascades; summarization inside the run; hard limit closes the conversation; customer
and staff tool catalogs (20 / 29 tools); tracking, order actions, returns, cancellation and address change; refund
limits per tier; the hand-off scenario (section 5) from the customer's report to the resolved ticket.

---

## 14. Open items

- Item actions in the reply envelope (view, track, cancel…); token streaming of the reply text; shipments in the
  envelope once a shipment tool exists.
- Remove the REST send/answer endpoints now that the UI uses the WebSocket.
- WebSocket hardening: a cap on open sockets per user, and a rate limit on chat frames.
- Hand-off follow-ups: a staff reply/resolution posted into the customer's linked conversation (domain event → ai2
  listener → "Support team" chat message, needs a frontend card); code-forced escalation (a "Talk to a person" button,
  repeated tool failures, step budget used up); priority rules and response times per category.
- Pre-approval check for sensitive tools: today `cancel_order` on a delivered order asks for approval first and only
  then fails; check feasibility (and compute consequences for the approval detail) before pausing.
- Validation stubs: tool-argument checks, silent fixes.
- Several app instances: run events and connect tokens live in one JVM (needs a shared bus behind `RunEventBus` and a
  shared connect-token store).
- Cost (prices per model) on top of the token ledger; `RunLimits` into configuration.
- Several app instances: dropping RUNNING runs at startup assumes one instance (needs owner + heartbeat, or resuming
  from checkpoints).
- The approval text of a sensitive tool can only show its arguments (e.g. an address id, not the address).
- Spring wiring of the nodes (they are built by `RunGraph` today).
