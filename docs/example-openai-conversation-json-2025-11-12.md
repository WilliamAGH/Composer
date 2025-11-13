```json
{
  "conversation_id": "conv-001",
  "created_at": "2025-01-16T10:00:00Z",
  "version": 2,
  "metadata": {
    "journey_scope": "mailbox-panel",
    "mailbox_id": "demo-mailbox"
  },

  "events": [
    {
      "event_id": "evt-001",
      "index": 1,
      "parent_event_id": null,
      "timestamp": "2025-01-16T10:00:05Z",
      "type": "system_message",
      "role": "system",
      "content": "You are ComposerAI. Cite email IDs when referencing messages.",
      "context_refs": [],
      "meta": {}
    },
    {
      "event_id": "evt-002",
      "index": 2,
      "parent_event_id": null,
      "timestamp": "2025-01-16T10:00:12Z",
      "type": "user_message",
      "role": "user",
      "content": "Summarize the latest hiring update email.",
      "context_refs": [
        { "ref_type": "email", "ref_id": "msg-001", "purpose": "primary" }
      ],
      "meta": {}
    },
    {
      "event_id": "evt-003",
      "index": 3,
      "parent_event_id": null,
      "timestamp": "2025-01-16T10:00:13Z",
      "type": "llm_call",
      "role": null,
      "content": null,
      "context_refs": [
        { "ref_type": "email", "ref_id": "msg-001", "purpose": "primary" }
      ],
      "meta": {},
      "llm": {
        "provider": "openai",
        "endpoint": "chat.completions",
        "sdk_type": "ChatCompletionCreateParams",
        "request_json": "{\"model\":\"gpt-4.1-mini\",\"messages\":[{\"role\":\"system\",\"content\":\"You are ComposerAI. Cite email IDs when referencing messages.\"},{\"role\":\"user\",\"content\":\"Summarize the latest hiring update email. Email msg-001: === Email Metadata === ...\"}],\"temperature\":0.2}",
        "response_json": "{\"id\":\"chatcmpl-abc\",\"created\":1737024013,\"choices\":[{\"index\":0,\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"Email msg-001 highlights: offer accepted, VP interviews next week, CFO approval pending.\"}}],\"usage\":{\"prompt_tokens\":180,\"completion_tokens\":60,\"total_tokens\":240}}",
        "usage": {
          "prompt_tokens": 180,
          "completion_tokens": 60,
          "total_tokens": 240,
          "latency_ms": 420
        }
      }
    },
    {
      "event_id": "evt-004",
      "index": 4,
      "parent_event_id": "evt-003",
      "timestamp": "2025-01-16T10:00:14Z",
      "type": "assistant_message",
      "role": "assistant",
      "content": "Email msg-001: Offer accepted for the Staff Engineer, VP finalist interviews next week, CFO approval still needed.",
      "context_refs": [
        { "ref_type": "email", "ref_id": "msg-001", "purpose": "quoted" }
      ],
      "meta": { "llm_call_ref": "evt-003" }
    },
    {
      "event_id": "evt-005",
      "index": 5,
      "parent_event_id": null,
      "timestamp": "2025-01-16T10:00:20Z",
      "type": "user_message",
      "role": "user",
      "content": "Compare that with the vendor invoice email.",
      "context_refs": [
        { "ref_type": \"email\", \"ref_id\": \"msg-001\", \"purpose\": \"primary\" },
        { "ref_type": \"email\", \"ref_id\": \"msg-002\", \"purpose\": \"primary\" }
      ],
      "meta": {}
    }
  ],

  "objects": {
    "emails": [
      {
        "email_id": "msg-001",
        "subject": "Weekly Hiring Update",
        "from": "HR Lead <hr@example.com>",
        "to": ["CTO <cto@example.com>"],
        "received_at": "2025-01-15T14:32:00Z",
        "storage_key": "emails/msg-001/contextForAi",
        "body_markdown": "=== Email Metadata ===\nSubject: Weekly Hiring Update\nFrom: HR Lead <hr@example.com>\n…",
        "meta": { "thread_id": "thread-hiring-2025" }
      },
      {
        "email_id": "msg-002",
        "subject": "Vendor Invoice #9876",
        "from": "Finance Ops <finance@example.com>",
        "to": ["CTO <cto@example.com>"],
        "received_at": "2025-01-16T09:04:00Z",
        "storage_key": "emails/msg-002/contextForAi",
        "body_markdown": "=== Email Metadata ===\nSubject: Vendor Invoice #9876\nFrom: Finance Ops <finance@example.com>\n…",
        "meta": { "thread_id": "thread-finance-2025" }
      }
    ]
  }
}
```
