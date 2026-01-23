package com.composerai.api.domain.model;

import com.composerai.api.util.StringUtils;

/**
 * Value object representing a session identifier.
 */
public record SessionId(String value) {
    public SessionId {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("SessionId cannot be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
