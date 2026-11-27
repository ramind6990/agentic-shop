"""Reading the transcripts the Java harness wrote, and views over them the metrics share."""

from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterator

from .env import REPO_DIR

SUPPORTED_VERSION = 1
RUNS_DIR = REPO_DIR / "backend" / "target" / "eval-runs"


@dataclass(frozen=True)
class Transcript:
    """One scenario run (one JSON file). `data` is the raw transcript; the helpers flatten it."""

    path: Path
    data: dict[str, Any]

    @property
    def id(self) -> str:
        return f"{self.data['scenario']['id']}#r{self.data['repetition']}"

    @property
    def scenario_id(self) -> str:
        return self.data["scenario"]["id"]

    @property
    def expect(self) -> dict[str, Any]:
        return self.data.get("expect") or {}

    @property
    def turns(self) -> list[dict[str, Any]]:
        return self.data.get("turns") or []

    def tool_calls(self) -> list[dict[str, Any]]:
        """Every tool call of the model, in order, each with its `turn` index."""
        return [dict(call, turn=t["index"]) for t in self.turns for s in t["segments"] for call in s.get("toolCalls", [])]

    def interactions(self) -> list[dict[str, Any]]:
        """Every pause, in order, each with its `turn` index."""
        return [dict(s["interaction"], turn=t["index"]) for t in self.turns for s in t["segments"] if s.get("interaction")]

    def writes(self, outcome: str | None = "ok") -> list[dict[str, Any]]:
        writes = (self.data.get("world") or {}).get("writes") or []
        return [w for w in writes if outcome is None or w.get("outcome") == outcome]

    def world_state(self) -> dict[str, Any]:
        return (self.data.get("world") or {}).get("state") or {}

    def reply(self, turn: int | None = None) -> dict[str, Any] | None:
        """The reply of a turn (default: the last turn)."""
        if not self.turns:
            return None
        t = self.turns[-1] if turn is None else next((x for x in self.turns if x["index"] == turn), None)
        return None if t is None else t.get("reply")

    def guard_steps(self) -> list[dict[str, Any]]:
        return [g for run in self.data.get("runs", []) for g in run.get("guards", [])]

    def validation_steps(self) -> list[dict[str, Any]]:
        return [v for run in self.data.get("runs", []) for v in run.get("validation", [])]


def run_dir() -> Path:
    """EVAL_RUN_DIR, or the newest folder under backend/target/eval-runs."""
    explicit = os.environ.get("EVAL_RUN_DIR")
    if explicit:
        return Path(explicit)
    if not RUNS_DIR.is_dir():
        raise FileNotFoundError(f"no eval runs in {RUNS_DIR}: run `mvn -f backend/pom.xml -Peval test` first")
    runs = sorted(p for p in RUNS_DIR.iterdir() if (p / "manifest.json").is_file())
    if not runs:
        raise FileNotFoundError(f"no eval run with a manifest.json in {RUNS_DIR}")
    return runs[-1]


def load_all(directory: Path | None = None) -> list[Transcript]:
    directory = directory or run_dir()
    manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
    return list(_load(directory, manifest))


def _load(directory: Path, manifest: dict[str, Any]) -> Iterator[Transcript]:
    for entry in manifest["transcripts"]:
        path = directory / entry["file"]
        data = json.loads(path.read_text(encoding="utf-8"))
        if data.get("version") != SUPPORTED_VERSION:
            raise ValueError(f"{path}: transcript version {data.get('version')}, expected {SUPPORTED_VERSION}")
        yield Transcript(path, data)
