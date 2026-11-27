"""Process settings, applied before DeepEval is imported (from conftest.py)."""

import os
from pathlib import Path

EVALS_DIR = Path(__file__).resolve().parent.parent
REPO_DIR = EVALS_DIR.parent


def configure() -> None:
    """No telemetry, results next to the suite, and OPENAI_API_KEY from the repo's .env (real env vars win)."""
    os.environ.setdefault("DEEPEVAL_TELEMETRY_OPT_OUT", "1")
    os.environ.setdefault("DEEPEVAL_RESULTS_FOLDER", str(EVALS_DIR / "results"))
    _load_dotenv(REPO_DIR / ".env")


def _load_dotenv(path: Path) -> None:
    if not path.is_file():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))
