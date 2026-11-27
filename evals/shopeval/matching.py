"""Loose value comparison shared by the deterministic metrics."""

from __future__ import annotations

from typing import Any


def norm(value: Any) -> str:
    """Case- and whitespace-insensitive text form; numbers compare by value (501 == "501", 89.9 == "89.90")."""
    if isinstance(value, bool):
        return str(value).lower()
    if isinstance(value, (int, float)):
        return f"{float(value):g}"
    text = str(value).strip().lower()
    try:
        return f"{float(text):g}"
    except ValueError:
        return text


def same(expected: Any, actual: Any) -> bool:
    if isinstance(expected, list):
        return isinstance(actual, list) and len(expected) == len(actual) and all(map(same, expected, actual))
    if expected is None or actual is None:
        return expected is None and actual is None
    return norm(expected) == norm(actual)


def args_match(expected: dict[str, Any] | None, actual: dict[str, Any] | None) -> bool:
    """Every listed expected argument has the same value in the actual call; extra actual arguments are fine."""
    actual = actual or {}
    return all(same(v, actual.get(k)) for k, v in (expected or {}).items())


def contains_any(text: str, alternatives: str) -> bool:
    """`a|b` alternatives, case-insensitive substring."""
    lowered = (text or "").lower()
    return any(part.strip().lower() in lowered for part in alternatives.split("|") if part.strip())
