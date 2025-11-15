package com.composerai.api.domain.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable value object representing a normalized folder identifier within a mailbox.
 * Using a dedicated type keeps folder comparisons consistent across in-memory session state today
 * and future IMAP-backed implementations that will reuse the same identifier strings.
 */
public final class MailFolderIdentifier {

    private final String value;

    private MailFolderIdentifier(String value) {
        this.value = value;
    }

    public static MailFolderIdentifier of(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Folder identifier cannot be blank");
        }
        String normalized = rawValue.trim().toLowerCase(Locale.US);
        return new MailFolderIdentifier(normalized);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MailFolderIdentifier that)) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
