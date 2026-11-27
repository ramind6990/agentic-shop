"""Base of the deterministic metrics: a list of failed checks over the transcript, scored 1 (none) or 0."""

from __future__ import annotations

from abc import abstractmethod

from deepeval.metrics import BaseMetric

from ..transcripts import Transcript


class TranscriptMetric(BaseMetric):
    """A pass/fail check of one transcript. The DeepEval test case only carries the run into the report; the facts
    come from the transcript the metric was built with."""

    metric_name = "Transcript check"

    def __init__(self, transcript: Transcript):
        self.transcript = transcript
        self.threshold = 1.0
        self.strict_mode = True
        self.async_mode = False
        self.include_reason = True
        self.evaluation_model = "deterministic"

    @abstractmethod
    def failures(self) -> list[str]:
        """What is wrong, one line each; empty when the check passes."""

    def passed_reason(self) -> str:
        return "all checks passed"

    def measure(self, test_case, *args, **kwargs) -> float:
        try:
            failed = self.failures()
        except Exception as e:  # a broken transcript or expectation must show up, not crash the run
            self.error = f"{type(e).__name__}: {e}"
            self.success = False
            self.score = 0.0
            self.reason = self.error
            raise
        self.score = 0.0 if failed else 1.0
        self.success = not failed
        self.reason = "; ".join(failed) if failed else self.passed_reason()
        return self.score

    async def a_measure(self, test_case, *args, **kwargs) -> float:
        return self.measure(test_case, *args, **kwargs)

    def is_successful(self) -> bool:
        if self.error is not None:
            self.success = False
        else:
            self.success = self.score is not None and self.score >= self.threshold
        return self.success

    @property
    def __name__(self) -> str:
        return self.metric_name
