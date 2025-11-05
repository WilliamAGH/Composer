package com.composerai.api.service.email;

import com.composerai.api.model.EmailMessage;

import java.util.List;

public interface EmailMessageProvider {

    List<EmailMessage> loadEmails();
}
