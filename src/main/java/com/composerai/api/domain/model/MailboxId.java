package com.composerai.api.domain.model;

import com.composerai.api.util.StringUtils;

/**
 * Value object representing a mailbox identifier.
 */
public record MailboxId(String value) {
    public MailboxId {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("MailboxId cannot be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
