package com.composerai.api.adapters.out.openai;

import com.openai.errors.OpenAIServiceException;
import org.springframework.http.HttpStatus;

/**
 * Detects the shared gateway's deterministic HTTP 422 {@code unpreservable_reasoning_intent}
 * rejection: the requested reasoning effort cannot be preserved by any candidate provider,
 * so retrying unchanged can never succeed.
 */
public final class ReasoningIntentRejection {

    private static final String UNPRESERVABLE_REASONING_INTENT_CODE = "unpreservable_reasoning_intent";

    private ReasoningIntentRejection() {}

    public static boolean isUnpreservable(Throwable error) {
        boolean sawUnprocessableEntity = false;
        boolean sawGatewayCode = false;
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof OpenAIServiceException serviceException
                    && serviceException.statusCode() == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
                sawUnprocessableEntity = true;
                sawGatewayCode |= serviceException
                        .code()
                        .map(UNPRESERVABLE_REASONING_INTENT_CODE::equals)
                        .orElse(false);
            }
            String message = current.getMessage();
            if (message != null && message.contains(UNPRESERVABLE_REASONING_INTENT_CODE)) {
                sawGatewayCode = true;
            }
        }
        return sawUnprocessableEntity && sawGatewayCode;
    }
}
