package com.composerai.api.shared.ledger;

import com.composerai.api.model.EmailMessage;

/**
 * Snapshot of an email that was relevant to the conversation. Reuses the existing
 * {@link EmailMessage} plus a storage key for downstream replay tools so ledger consumers can look
 * up the same markdown shown in the OpenAI request ({@code com.openai.models.chat.completions.
 * ChatCompletionCreateParams}).
 */
public record EmailObject(String emailId, String storageKey, EmailMessage snapshot) {}
