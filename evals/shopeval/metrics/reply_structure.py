from ..matching import contains_any
from .base import TranscriptMetric

MAX_SUGGESTIONS = 3  # app.ai.reply.max-suggestions


class ReplyStructureMetric(TranscriptMetric):
    """The structured output of the replies: the envelope status, the block (`none` = text only), the tool whose
    result is displayed, text that must / must not appear, and the reply limits. `reply` checks the last turn,
    `replies` lists checks per `turn`. Every turn must end with a reply (no AGENT_ERROR) and no FAILED fallback
    unless expected."""

    metric_name = "Reply structure"

    def failures(self) -> list[str]:
        failed = []
        for turn in self.transcript.turns:
            if turn.get("error"):
                failed.append(f"turn {turn['index']}: run failed with {turn['error']}")
        checks = list(self.transcript.expect.get("replies", []))
        if "reply" in self.transcript.expect:
            checks.append(dict(self.transcript.expect["reply"], turn=self.transcript.turns[-1]["index"]))
        for check in checks:
            failed += self._check(check)
        return failed

    def _check(self, want: dict) -> list[str]:
        turn = want["turn"]
        reply = self.transcript.reply(turn)
        if reply is None:
            return [] if self._ended_paused(turn) else [f"turn {turn}: no reply"]
        failed = []
        expected_status = want.get("status")
        if expected_status and reply["status"] != expected_status:
            failed.append(f"turn {turn}: status {reply['status']}, expected {expected_status}")
        if not expected_status and reply["status"] == "FAILED":
            failed.append(f"turn {turn}: the run fell back to the FAILED reply")
        if "block" in want:
            got = reply.get("blockType") or "none"
            if got != want["block"]:
                failed.append(f"turn {turn}: block {got}, expected {want['block']}")
        if "displayedTool" in want and reply.get("displayedTool") != want["displayedTool"]:
            failed.append(f"turn {turn}: displays {reply.get('displayedTool')}, expected {want['displayedTool']}")
        text = reply.get("text") or ""
        failed += [f"turn {turn}: text lacks '{t}'" for t in want.get("mustContain", []) if not contains_any(text, t)]
        failed += [f"turn {turn}: text contains '{t}'" for t in want.get("mustNotContain", []) if contains_any(text, t)]
        if len(reply.get("suggestedReplies") or []) > MAX_SUGGESTIONS:
            failed.append(f"turn {turn}: more than {MAX_SUGGESTIONS} suggested replies")
        if reply["status"] in ("ANSWERED", "REFUSED") and not reply.get("response"):
            failed.append(f"turn {turn}: no structured AgentResponse stored")
        return failed

    def _ended_paused(self, turn: int) -> bool:
        t = next((x for x in self.transcript.turns if x["index"] == turn), None)
        return bool(t and t.get("endedPaused"))
