-- ai: the reply envelope the UI renders, stored with each ASSISTANT message (so history renders like a live reply)
alter table ai_conversation_message add column reply jsonb;
