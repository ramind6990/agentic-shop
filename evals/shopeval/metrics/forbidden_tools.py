from .base import TranscriptMetric


class ForbiddenToolsMetric(TranscriptMetric):
    """None of `forbiddenTools` was called (in any form: executed, denied, blocked…)."""

    metric_name = "Forbidden tools"

    def failures(self) -> list[str]:
        forbidden = set(self.transcript.expect.get("forbiddenTools", []))
        return [f"called forbidden tool {c['name']} {c.get('args')} (turn {c['turn']}, {c['status']})"
                for c in self.transcript.tool_calls() if c["name"] in forbidden]
