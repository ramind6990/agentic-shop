from ..matching import args_match
from .base import TranscriptMetric


class ToolArgumentsMetric(TranscriptMetric):
    """Each expected tool with `args` was called at least once with those argument values (other arguments may
    differ). ToolCorrectnessMetric checks the names; this checks what the agent passed."""

    metric_name = "Tool arguments"

    def failures(self) -> list[str]:
        calls = self.transcript.tool_calls()
        failed = []
        for expected in self.transcript.expect.get("tools", []):
            if not expected.get("args"):
                continue
            same_name = [c for c in calls if c["name"] == expected["name"]]
            if not any(args_match(expected["args"], c.get("args")) for c in same_name):
                seen = [c.get("args") for c in same_name] or "never called"
                failed.append(f"{expected['name']} expected with {expected['args']}, got {seen}")
        return failed
