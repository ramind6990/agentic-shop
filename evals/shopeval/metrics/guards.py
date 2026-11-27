from .base import TranscriptMetric


class GuardsMetric(TranscriptMetric):
    """Guardrail decisions: every name in `guards` appears among the runs' GUARD steps (substring, e.g.
    "input blocked", "tool text hidden"); with `guards: []`, no enforcing guard step at all (shadow ones are fine).
    `noAgentCall: true` additionally requires that the agent's own model was never called (an input refusal)."""

    metric_name = "Guards"

    def failures(self) -> list[str]:
        expected = self.transcript.expect.get("guards")
        steps = [g["name"] for g in self.transcript.guard_steps()]
        failed = []
        if expected is not None:
            for name in expected:
                if not any(name.lower() in s.lower() for s in steps):
                    failed.append(f"expected guard step '{name}', got {steps}")
            if not expected:
                enforced = [s for s in steps if "shadow" not in s.lower()]
                if enforced:
                    failed.append(f"unexpected guard steps {enforced}")
        if self.transcript.expect.get("noAgentCall") and self.transcript.data["usage"]["agentCalls"] > 0:
            failed.append(f"the agent's model was called {self.transcript.data['usage']['agentCalls']} time(s)")
        return failed
