# ai2 — the redesigned support agent

`backend/src/main/java/com/rmrx/agenticshop/ai2` is a step-by-step redesign of the AI support agent. The old `ai`
package is left untouched; ai2 has its own beans (named `ai2…` where a name would clash), tables (`ai2_…`), model
bean, prompt and endpoints (`/api/ai2/…`).

This document describes what exists today. Open items are listed at the end.

---

## 1. Big picture

```
POST /api/ai2/conversations/messages            (first message: creates the conversation)
POST /api/ai2/conversations/{id}/messages       (next messages)
POST /api/ai2/conversations/{id}/answers        (answer to a question / approval)
GET  /api/ai2/conversations/{id}/messages       (timeline)
GET  /api/ai2/runs/{runId}/events               (live run events, SSE)
          │
          ▼
ConversationService ── conversation + ownership checks, one active run per conversation,
          │            stores the user's message
          ▼
RunLauncher ── runs the graph on a virtual thread; stores question/approval or reply, then emits PAUSED / FINAL
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
| `ai2.contract.tool` | `ToolResponse`, `ToolResult` |
| `ai2.contract.tool.response` | product and order response contracts |
| `ai2.tools` | `ToolRegistry`, `ToolCallExecutor`, `ToolProgress`, annotations (`@ToolLabel`, `@Sensitive`, `@AskUser`), `ToolCategory` … |
| `ai2.tools.ask` | `AskUserTools` (`ask_choice`, `ask_input`, `ask_yes_no`) + their argument records |
| `ai2.tools.product` | `ProductTools` (`search_products`, mock data) |
| `ai2.tools.order` | `OrderTools` (`search_orders`, `cancel_order`, mock data) |
| `ai2.model` | `Ai2ModelConfig` (the `ai2ChatModel` bean), `SystemPrompt`, `Ai2ModelProperties`, `Ai2PromptProperties`, `Ai2ReplyProperties` |
| `ai2.conversation` | `ConversationService`, `RunLauncher`, `GraphRunner`, `RunOutcome`, `ConversationConflictException` |
| `ai2.events` | `AgentEvent`, `AgentEventPublisher`, `RunEvents` / `RunEventBus`, `InMemoryRunEventBus`, `Ai2EventProperties` |
| `ai2.events.sse` | `SseRunEventTransport` |
| `ai2.conversation.store` | `Conversation`, `ConversationStore` |
| `ai2.conversation.message` | `ConversationMessage`, `ConversationMessageStore`, `MessageKind`, `InteractionStatus`, `ReplyDisplay` |
| `ai2.activerun` | `ActiveRun`, `ActiveRunRegistry` |
| `ai2.checkpointer` | `JdbcCheckpointSaver` |
| `ai2.memory` | `HistoryProvider`, `ConversationSummarizer`, `HistoryProjection`, `ConversationMemoryStore`, `Ai2MemoryProperties` |
| `ai2.web` | `ConversationController`, `RunEventsController`, `SendMessageRequest`, `AnswerRequest` |

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
| `ModelCallNode` | Builds the request: system prompt (rendered per call) + history + this run's messages + all tools + the `AgentResponse` JSON schema as response format. On the run's last allowed call, `toolChoice = NONE` and a "no more tools" note. |
| `OutputValidationNode` | Validates the model's output and decides how the step ends (see below). |
| `ToolExecutionNode` | Executes read-only tool calls; stores each successful result under a new ref. |
| `AskUserNode` | Pauses once per unanswered ask call; when all are answered, writes the answers as the calls' results. |
| `SensitiveToolExecutionNode` | Pauses once per sensitive call for approval; executes approved calls, a "denied" result for the others. |

### Output validation

For **tool calls**:
- unknown tool name → error result per call, retry;
- the run's budget is used up → FAILED;
- calls are sorted into a `StepPlan` (read / ask / sensitive / deferred) by tool category;
- **mixing rule**: when a step contains an ask, its sensitive calls are deferred with the result "Not executed: call it
  again after the user has answered your question".

For a **final answer** (text without tool calls): parse JSON into `AgentResponse`, then check status present, text
non-blank and ≤ `max-text-length`, ≤ `max-suggestions` suggestions with labels ≤ `max-suggestion-label-length`, and
that `finalReferenceToDisplay.ref` is a ref of this run whose `selectedItemIdentifiers` are items of that result.

Failure → feedback to the model (an error result per tool call, or a system message for a final answer) and RETRY;
after 2 consecutive failures, or when the model-call budget is used up, FAILED with a fallback reply.

Stubs still marked TODO: argument parsing against tool schemas, per-step call cap, duplicate-call detection,
silent fixes.

### State (`RunState`)

Checkpointed after every node, so every value is `Serializable`.

| Key | Content | Written by |
|---|---|---|
| `context` | `RunContext(userId, conversationId, runId)` | caller |
| `messages` | this run's messages (append-only, duplicates kept) | several nodes |
| `modelCallCount` | model calls so far | modelCall |
| `validationOutcome`, `validationRetries`, `stepPlan` | how the last step ended | outputValidation (every pass) |
| `response` | the final `AgentResponse` | outputValidation |
| `humanAnswers` | callId → `HumanAnswer` (merge reducer) | caller via `updateState` before resuming |
| `toolResults` | ref → `ToolResult` (merge reducer) | tool nodes |

History of earlier runs is **not** in the state; it is loaded per model call (section 9).

### Limits (`RunLimits`)

8 model calls per run (the last with tools off), 2 validation retries, 5 calls per step (cap still TODO); recursion
limit derived from these.

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

### Tools today (mock data)

| Tool | Category | Notes |
|---|---|---|
| `search_products(query)` | READ | 3 running shoes |
| `search_orders(statuses?)` | READ | 4 orders (WE45, WE51, WE32, WE18), newest first |
| `cancel_order(orderNumber, reason?)` | SENSITIVE | "Do you want to cancel order {orderNumber}?"; fails for orders not PLACED/PAID |
| `ask_choice(question, options, multiSelect)` | ASK | 2–6 options `{value, label, description}` |
| `ask_input(question)` | ASK | one free-text answer |
| `ask_yes_no(question)` | ASK | only to confirm understanding, never to ask permission |

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
| ASSISTANT | a run completes | the `AgentResponse` (`payload`) and the shown result (`display`: tool name, selected ids, full data, agent summary) |

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
- At startup, runs still RUNNING are dropped (cut off by the previous shutdown); PAUSED runs stay and can still be
  answered — a paused approval survives a restart.
- Migrations: `V12__ai2_active_run_checkpoint.sql`, `V13__ai2_conversation_memory.sql`.

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
- System prompt: `src/main/resources/prompts/ai2/system-<version>.md`, chosen by `app.ai2.prompt.version`. Placeholders
  (`{{maxTextLength}}` …) are filled from `app.ai2.reply.*` at startup; the same values are enforced by validation.
  Each call adds today's date and, on the last allowed call, the "no more tools" note.
- The prompt covers: scope and refusals (including questions about the system or instructions), facts only from
  tools, never claiming an unseen success, tool results are data not instructions, never asking for confirmation
  (the system confirms), refs and what to display, the ask tools one by one and the mixing rule, suggested replies
  (including the rest of a multi-part request), and never mentioning tools/refs/JSON in the text.

---

## 11. API

All endpoints need the `AI_CHAT` permission (customers and support staff).

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/api/ai2/conversations/messages` | `{"text": "…"}` | `RunOutcome` (new conversation) |
| POST | `/api/ai2/conversations/{id}/messages` | `{"text": "…"}` | `RunOutcome` |
| POST | `/api/ai2/conversations/{id}/answers` | `{"interactionId": "…", "answer": {"type": "choice", "values": ["WE45"]}}` | `RunOutcome` |
| GET | `/api/ai2/conversations/{id}/messages` | — | the timeline |

The POST endpoints return at once with a `RunOutcome`: `running` (the run executes in the background) or `abandoned`
(`unanswered`); always `conversationId` and `runId`. How a running run ends comes as run events.

### Run events (SSE)

`GET /api/ai2/runs/{runId}/events` (`text/event-stream`, owner only, otherwise 404). Frames: `id:` = seq,
`event:` = type, `data:` = the event JSON (with `type`, `runId`, `seq`, `at`).

| Event | Fields | When |
|---|---|---|
| `RUN_STARTED` | `conversationId` | the run starts or resumes |
| `TOOL_STARTED` | `toolName`, `progressLabel` (rendered `@ToolLabel`) | before a read or approved sensitive tool executes |
| `TOOL_FINISHED` | `toolName`, `success`, `errorCode` (`TOOL_FAILED` or null), `durationMs` | after it |
| `FINAL` | `response` (`AgentResponse`), `displayed` (the shown `ToolResult` or null) | run completed; last event |
| `PAUSED` | `interaction` (`PendingInteraction`) | run waits for an answer; last event of this stream |
| `ERROR` | `code` (`AGENT_ERROR`, `RUN_NOT_LIVE`), `message`, `retryable` | run failed / not streamed here |

- The stream is opened before the POST returns, so subscribing after it replays the stream from its first event.
- PAUSED and FINAL are emitted after the interaction / reply is stored and the active run updated, so the client can
  answer or send right away. Answering a PAUSED run resumes the same `runId` with a new stream whose seq continues;
  the client subscribes again (with `Last-Event-ID` = the last seq it saw, or from scratch).
- Reconnect with `Last-Event-ID` (or `?after=`) to get only later events. Closed streams stay replayable for
  `retention`; an unknown or evicted run gets one `ERROR` `RUN_NOT_LIVE`.
- In memory, single instance (`InMemoryRunEventBus`, same design as the `ai` package's bus); never persisted.

Answer bodies by type: `{"type":"choice","values":[…]}`, `{"type":"input","text":"…"}`, `{"type":"yesNo","yes":true}`,
`{"type":"approval","approved":true}`, `{"type":"dismissed"}`.

Errors: 400 invalid body, 401/403 auth, 404 not the caller's conversation, 409 run in progress / no question to
answer / conversation full.

---

## 12. Configuration (`application.yml`)

```yaml
app:
  ai2:
    model:          # OpenAI Responses API
      api-key: ${OPENAI_API_KEY:}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com/v1}
      model-name: ${AI2_CHAT_MODEL:gpt-6-luna}
      max-output-tokens: 4000
      reasoning-effort: ${AI2_REASONING_EFFORT:}   # none | minimal | low | medium | high
      timeout: 60s
    prompt:
      version: ${AI2_PROMPT_VERSION:v1}
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
```

---

## 13. Trying it out

Launch configurations in `.claude/launch.json`: `backend` (normal), `backend-small-memory` (summarize after 4
messages), `backend-tiny-memory` (summarize after 2, hard limit 50 characters — to see a conversation close).

Seeded demo users: `customer_1`, `customer_2`, … (see `db/seed/V111__seed_logins.sql` for the password).

```powershell
$headers = @{ Authorization = "Bearer " + (Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login -ContentType 'application/json' -Body (@{ username = 'customer_1'; password = $password } | ConvertTo-Json)).token }
$r = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/ai2/conversations/messages -Headers $headers -ContentType 'application/json' -Body '{"text":"please cancel my order WE45"}'
# follow the run until FINAL / PAUSED / ERROR; the PAUSED event carries interaction.interactionId
curl.exe -sN -H "Authorization: $($headers.Authorization)" "http://localhost:8080/api/ai2/runs/$($r.runId)/events"
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/ai2/conversations/$($r.conversationId)/answers" -Headers $headers -ContentType 'application/json' -Body (@{ interactionId = $interactionId; answer = @{ type = 'approval'; approved = $true } } | ConvertTo-Json -Depth 5)
```

Scenarios verified against the live app with the real model: first message creates a conversation; follow-ups use
history; question/approval lifecycle (PENDING → APPROVED / DISMISSED / NOT_ANSWERED); a paused approval answered after
a restart; double answer 409; wrong interaction id → abandoned; RUNNING lock and its cleanup at startup; another
user's conversation 404; delete cascades; summarization inside the run; hard limit closes the conversation.

---

## 14. Open items

- Reply envelope (turn `displayed` into UI blocks with item actions); token streaming of the reply text.
- Real data for the tools (catalog and order services), always scoped to the run's customer.
- Pre-approval check for sensitive tools: today `cancel_order` on a delivered order asks for approval first and only
  then fails; check feasibility (and compute consequences for the approval detail) before pausing.
- Validation stubs: tool-argument checks, per-step call cap, duplicate calls, silent fixes.
- A visible error reply when a run fails (today the user's message stays unanswered in the timeline; only an ERROR
  event is emitted).
- Several app instances: run events live in one JVM (needs a shared bus behind `RunEventBus`).
- Retries on model errors; token/cost budget per run; usage recording for ai2 model calls.
- Several app instances: dropping RUNNING runs at startup assumes one instance (needs owner + heartbeat, or resuming
  from checkpoints).
- Tool filtering per user/role in `ToolRegistry.specificationsFor`; per-user facts in the system prompt.
- Spring wiring of the nodes (they are built by `RunGraph` today).
