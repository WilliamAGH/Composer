package com.composerai.api.domain.model;

import com.composerai.api.util.StringUtils;

/**
 * Value object representing a message identifier.
 */
public record MessageId(String value) {
    public MessageId {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("MessageId cannot be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
