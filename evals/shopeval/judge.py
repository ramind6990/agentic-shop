"""The LLM judge for the conversational metrics: an OpenAI model through the Responses API (as the agent uses),
with structured output when DeepEval passes a schema. Model: EVAL_JUDGE_MODEL (default: the agent's AI_CHAT_MODEL,
else gpt-6-luna); optional EVAL_JUDGE_REASONING (none | minimal | low | medium | high)."""

from __future__ import annotations

import os
from functools import lru_cache

from deepeval.models.base_model import DeepEvalBaseLLM
from openai import AsyncOpenAI, OpenAI

_TIMEOUT_SECONDS = 120


class OpenAIResponsesJudge(DeepEvalBaseLLM):

    def __init__(self, model_name: str, reasoning_effort: str | None = None):
        self.model_name = model_name
        self.reasoning_effort = reasoning_effort or None
        self.client = OpenAI(timeout=_TIMEOUT_SECONDS)
        self.async_client = AsyncOpenAI(timeout=_TIMEOUT_SECONDS)
        super().__init__(model_name)

    def load_model(self, *args, **kwargs):
        return self.client

    def generate(self, prompt: str, schema=None):
        if schema is not None:
            return self.client.responses.parse(text_format=schema, **self._request(prompt)).output_parsed
        return self.client.responses.create(**self._request(prompt)).output_text

    async def a_generate(self, prompt: str, schema=None):
        if schema is not None:
            response = await self.async_client.responses.parse(text_format=schema, **self._request(prompt))
            return response.output_parsed
        return (await self.async_client.responses.create(**self._request(prompt))).output_text

    def get_model_name(self) -> str:
        return self.model_name

    def _request(self, prompt: str) -> dict:
        request = {"model": self.model_name, "input": prompt, "store": False}
        if self.reasoning_effort:
            request["reasoning"] = {"effort": self.reasoning_effort}
        return request


@lru_cache(maxsize=1)
def judge() -> OpenAIResponsesJudge:
    model = os.environ.get("EVAL_JUDGE_MODEL") or os.environ.get("AI_CHAT_MODEL") or "gpt-6-luna"
    return OpenAIResponsesJudge(model, os.environ.get("EVAL_JUDGE_REASONING"))
