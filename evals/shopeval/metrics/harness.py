from .base import TranscriptMetric


class HarnessMetric(TranscriptMetric):
    """The run itself is trustworthy: no timeout, no unstubbed domain call, no broken script."""

    metric_name = "Harness"

    def failures(self) -> list[str]:
        return [f"harness: {e}" for e in self.transcript.data.get("harnessErrors", [])]
