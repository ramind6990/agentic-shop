from .base import TranscriptMetric


class EfficiencyMetric(TranscriptMetric):
    """Within `budget`: agent model calls (`maxAgentCalls`), validation rejections (`maxValidationRejections`: how
    often the structured output or the tool calls were sent back), tokens (`maxTokens`, input + output, guards
    included)."""

    metric_name = "Efficiency"

    def failures(self) -> list[str]:
        budget = self.transcript.expect.get("budget") or {}
        usage = self.transcript.data["usage"]
        rejections = len(self.transcript.validation_steps())
        tokens = usage["inputTokens"] + usage["outputTokens"]
        failed = []
        if "maxAgentCalls" in budget and usage["agentCalls"] > budget["maxAgentCalls"]:
            failed.append(f"{usage['agentCalls']} agent model calls > {budget['maxAgentCalls']}")
        if "maxValidationRejections" in budget and rejections > budget["maxValidationRejections"]:
            names = [v["name"] + ": " + (v.get("result") or "")[:120] for v in self.transcript.validation_steps()]
            failed.append(f"{rejections} validation rejection(s) > {budget['maxValidationRejections']}: {names}")
        if "maxTokens" in budget and tokens > budget["maxTokens"]:
            failed.append(f"{tokens} tokens > {budget['maxTokens']}")
        return failed

    def passed_reason(self) -> str:
        usage = self.transcript.data["usage"]
        return (f"{usage['agentCalls']} agent calls, {usage['guardCalls']} guard calls, "
                f"{usage['inputTokens'] + usage['outputTokens']} tokens")
