package com.composerai.api.domain.port;

import com.composerai.api.domain.model.MailboxSnapshot;

/**
 * Port that knows how to produce a mailbox snapshot from the current data source (eml files today,
 * remote mailboxes later). Use cases never access the file system directly; they only depend on
 * this abstraction.
 */
public interface MailboxSnapshotPort {

    MailboxSnapshot loadSnapshot(String mailboxId);
}
