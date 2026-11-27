from typing import Any

from ..matching import same
from .base import TranscriptMetric

# collection → the field that identifies a row in `world:` paths, e.g. orders.AS-1001.status
_KEYS = {"orders": "number", "tickets": "number", "addresses": "id"}


class WorldStateMetric(TranscriptMetric):
    """τ-bench style end state. `writes`: the successful domain writes, in order, each with its leading arguments
    (`noOtherWrites`, default true, forbids any other successful write). `world`: final field values by path
    `<collection>.<key>.<field>`."""

    metric_name = "World state"

    def failures(self) -> list[str]:
        return self._writes() + self._fields()

    def _writes(self) -> list[str]:
        if "writes" not in self.transcript.expect:
            return []
        expected = self.transcript.expect["writes"]
        actual = self.transcript.writes("ok")
        failed = []
        remaining = list(actual)
        for want in expected:
            index = next((i for i, got in enumerate(remaining) if _write_fits(want, got)), None)
            if index is None:
                failed.append(f"expected write {want['call']}{want.get('args', [])} did not happen")
            else:
                remaining = remaining[index + 1:]
        if self.transcript.expect.get("noOtherWrites", True) and len(actual) != len(expected):
            failed.append(f"expected {len(expected)} write(s), got {len(actual)}: "
                          f"{[w['call'] + str(w['args']) for w in actual]}")
        return failed

    def _fields(self) -> list[str]:
        state = self.transcript.world_state()
        failed = []
        for path, want in (self.transcript.expect.get("world") or {}).items():
            found, got = _lookup(state, path)
            if not found:
                failed.append(f"world {path}: no such row or field")
            elif not same(want, got):
                failed.append(f"world {path} = {got!r}, expected {want!r}")
        return failed


def _write_fits(want: dict, got: dict) -> bool:
    args = want.get("args") or []
    return want["call"] == got["call"] and len(got["args"]) >= len(args) and all(map(same, args, got["args"]))


def _lookup(state: dict, path: str) -> tuple[bool, Any]:
    collection, rest = path.split(".", 1)
    key, field = rest.rsplit(".", 1)
    id_field = _KEYS.get(collection)
    if id_field is None:
        raise ValueError(f"world path {path}: unknown collection {collection}")
    row = next((r for r in state.get(collection, []) if same(key, r.get(id_field))), None)
    if row is None or field not in row:
        return False, None
    return True, row[field]
