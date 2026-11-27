from .base import TranscriptMetric


class InteractionsMetric(TranscriptMetric):
    """Human in the loop: the expected pauses happened in order (type, tool, turn), there were no others (unless
    `noOtherInteractions: false`), and every pause fitted the user's script."""

    metric_name = "Interactions"

    def failures(self) -> list[str]:
        expected = self.transcript.expect.get("interactions", [])
        actual = self.transcript.interactions()
        failed = [f"unexpected pause: {m}" for m in self.transcript.data.get("unexpectedInteractions", [])]
        remaining = list(actual)
        for want in expected:
            index = next((i for i, got in enumerate(remaining) if _fits(want, got)), None)
            if index is None:
                failed.append(f"expected pause {want} did not happen (pauses: {_brief(actual)})")
            else:
                remaining = remaining[index + 1:]
        if self.transcript.expect.get("noOtherInteractions", True) and len(actual) != len(expected):
            failed.append(f"expected {len(expected)} pause(s), got {len(actual)}: {_brief(actual)}")
        return failed


def _fits(want: dict, got: dict) -> bool:
    return (want.get("type") in (None, got.get("type"))
            and want.get("tool") in (None, got.get("tool"))
            and want.get("turn") in (None, got.get("turn")))


def _brief(interactions: list[dict]) -> list[str]:
    return [f"turn {i['turn']} {i['type']} {i.get('tool') or ''}".strip() for i in interactions]
