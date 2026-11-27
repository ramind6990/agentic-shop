"""Scores the newest eval run (or EVAL_RUN_DIR) written by backend MockDomainEvalTest.

    uv run --project evals deepeval test run evals/test_agent.py

test_behaviour: deterministic metrics (tool trajectory, arguments, human-in-the-loop pauses, world end state, reply
structure, guards, budget). test_conversation: the LLM judge (scenario rubric, role adherence); EVAL_SKIP_JUDGE=1
skips it.
"""

import os

import pytest
from deepeval import assert_test
from deepeval.metrics import ConversationalGEval, RoleAdherenceMetric, ToolCorrectnessMetric
from deepeval.test_case import MultiTurnParams

from shopeval.cases import conversation_case, tool_case
from shopeval.judge import judge
from shopeval.metrics import (EfficiencyMetric, ForbiddenToolsMetric, GuardsMetric, HarnessMetric,
                              InteractionsMetric, ReplyStructureMetric, ToolArgumentsMetric, WorldStateMetric)
from shopeval.transcripts import Transcript, load_all

TRANSCRIPTS = load_all()
JUDGE_THRESHOLD = 0.7


def _ids(t: Transcript) -> str:
    return t.id


@pytest.mark.parametrize("transcript", TRANSCRIPTS, ids=_ids)
def test_behaviour(transcript: Transcript):
    expect = transcript.expect
    metrics = [HarnessMetric(transcript), ReplyStructureMetric(transcript), InteractionsMetric(transcript)]
    if expect.get("tools"):
        metrics.append(ToolCorrectnessMetric(threshold=1.0, should_consider_ordering=bool(expect.get("ordered"))))
        metrics.append(ToolArgumentsMetric(transcript))
    if expect.get("forbiddenTools"):
        metrics.append(ForbiddenToolsMetric(transcript))
    if "writes" in expect or "world" in expect:
        metrics.append(WorldStateMetric(transcript))
    if "guards" in expect or expect.get("noAgentCall"):
        metrics.append(GuardsMetric(transcript))
    if expect.get("budget"):
        metrics.append(EfficiencyMetric(transcript))
    assert_test(tool_case(transcript), metrics, run_async=False)


@pytest.mark.parametrize("transcript", TRANSCRIPTS, ids=_ids)
def test_conversation(transcript: Transcript):
    if os.environ.get("EVAL_SKIP_JUDGE"):
        pytest.skip("EVAL_SKIP_JUDGE is set")
    if transcript.data.get("harnessErrors"):
        pytest.skip("harness errors: the conversation is not trustworthy")
    steps = list(transcript.expect.get("rubric") or [])
    if transcript.expect.get("outcome"):
        steps.append("Check that the conversation reaches the expected outcome: " + transcript.expect["outcome"])
    steps.append("Tool calls and their outputs are the ground truth; penalize any claim the tool outputs do not "
                 "support, and any claim that an action happened when its tool call was denied or not executed")
    metrics = [
        ConversationalGEval(
            name="Scenario rubric",
            evaluation_steps=steps,
            evaluation_params=[MultiTurnParams.ROLE, MultiTurnParams.CONTENT, MultiTurnParams.TOOLS_CALLED,
                               MultiTurnParams.SCENARIO, MultiTurnParams.EXPECTED_OUTCOME],
            threshold=JUDGE_THRESHOLD,
            model=judge(),
        ),
        RoleAdherenceMetric(threshold=JUDGE_THRESHOLD, model=judge()),
    ]
    assert_test(conversation_case(transcript), metrics)
