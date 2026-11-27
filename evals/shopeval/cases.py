"""Transcript → DeepEval test cases: an LLMTestCase for the tool trajectory, a ConversationalTestCase for the judge.

In the conversation, a pause becomes an assistant turn (the question or the approval card, as the user saw it) and the
user's reaction a user turn ("[approves]", "[chooses: …]"), so the judge sees the human-in-the-loop flow."""

from __future__ import annotations

from typing import Any

from deepeval.test_case import ConversationalTestCase, LLMTestCase, ToolCall, Turn

from .transcripts import Transcript

# Role Adherence only reads the messages, not the tool calls: the role describes persona and scope. Whether the facts
# match the tools is the rubric's job (ConversationalGEval sees the tool outputs).
_LOOKUPS = ("Its facts come from lookups in the shop's systems that are not shown in its messages, so stating order, "
            "delivery or refund details is in character.")
CUSTOMER_ROLE = (
    "The friendly, concise customer support assistant of an online marketplace, talking to a signed-in customer about "
    "their own orders, deliveries, returns, refunds and support tickets, and about the shop's products. Changes need "
    "the customer's approval on a card the app shows, so it does not ask for confirmation in its text. It can pass a "
    "case to the human support team, and it politely declines topics outside the shop. " + _LOOKUPS
)
STAFF_ROLE = (
    "The concise copilot of a marketplace support agent (staff): it works on customers, orders, shipments, returns, "
    "refunds and tickets within the staff member's limits; actions need the staff member's approval on a card the app "
    "shows. " + _LOOKUPS
)
_RESULT_CHARS = 1500


def tool_case(t: Transcript) -> LLMTestCase:
    """The whole scenario as one case: all user messages in, the last reply out, every tool call of the model."""
    expected = t.expect.get("tools") or []
    return LLMTestCase(
        name=t.id,
        input="\n".join(turn["user"] for turn in t.turns),
        actual_output=_final_text(t),
        tools_called=[_tool(c) for c in t.tool_calls()],
        expected_tools=[ToolCall(name=e["name"], input_parameters=e.get("args")) for e in expected],
        tags=t.data["scenario"].get("tags"),
    )


def conversation_case(t: Transcript) -> ConversationalTestCase:
    turns: list[Turn] = []
    for turn in t.turns:
        turns.append(Turn(role="user", content=turn["user"]))
        for segment in turn["segments"]:
            tools = [_tool(c) for c in segment.get("toolCalls", [])] or None
            interaction = segment.get("interaction")
            if interaction:
                turns.append(Turn(role="assistant", content=_asked(interaction), tools_called=tools))
                turns.append(Turn(role="user", content=_reaction(interaction)))
            else:
                turns.append(Turn(role="assistant", content=_turn_text(turn), tools_called=tools))
    return ConversationalTestCase(
        name=t.id,
        turns=turns,
        scenario=t.data["scenario"].get("description"),
        expected_outcome=t.expect.get("outcome"),
        chatbot_role=STAFF_ROLE if t.data["scenario"].get("actorRole") != "CUSTOMER" else CUSTOMER_ROLE,
        tags=t.data["scenario"].get("tags"),
    )


def _tool(call: dict[str, Any]) -> ToolCall:
    result = call.get("result")
    return ToolCall(
        name=call["name"],
        input_parameters=call.get("args") or {},
        output=None if result is None else result[:_RESULT_CHARS],
        description=f"status: {call.get('status')}",
    )


def _asked(interaction: dict[str, Any]) -> str:
    if interaction["type"] == "approval":
        detail = f" ({interaction['detail']})" if interaction.get("detail") else ""
        return f"[approval card] {interaction['prompt']}{detail}"
    if interaction["type"] == "choice":
        labels = ", ".join(o.get("label", o.get("value", "")) for o in interaction.get("options") or [])
        return f"[question] {interaction['prompt']} Options: {labels}"
    return f"[question] {interaction['prompt']}"


def _reaction(interaction: dict[str, Any]) -> str:
    answer = interaction.get("answer")
    if answer is None:
        return "[does not answer]"
    kind = answer.get("type")
    if kind == "approval":
        return "[approves]" if answer.get("approved") else "[denies]"
    if kind == "choice":
        chosen = answer.get("values", [])
        labels = [o.get("label") for o in interaction.get("options") or [] if o.get("value") in chosen]
        return f"[chooses: {', '.join(labels or chosen)}]"
    if kind == "input":
        return answer.get("text", "")
    if kind == "yesNo":
        return "[yes]" if answer.get("yes") else "[no]"
    return "[closes the question without answering]"


def _turn_text(turn: dict[str, Any]) -> str:
    if turn.get("reply"):
        return turn["reply"].get("text") or ""
    if turn.get("error"):
        return f"[the assistant failed: {turn['error'].get('code')}]"
    return "[no reply]"


def _final_text(t: Transcript) -> str:
    return _turn_text(t.turns[-1]) if t.turns else "[no reply]"
