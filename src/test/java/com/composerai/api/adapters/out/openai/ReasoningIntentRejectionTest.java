package com.composerai.api.adapters.out.openai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openai.core.http.Headers;
import com.openai.errors.UnprocessableEntityException;
import com.openai.models.ErrorObject;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReasoningIntentRejectionTest {

    private static final String GATEWAY_CODE = "unpreservable_reasoning_intent";

    @Test
    void matchesWrappedGatewayRejection() {
        UnprocessableEntityException upstream = UnprocessableEntityException.builder()
                .headers(Headers.builder().build())
                .error(ErrorObject.builder()
                        .message("no candidate provider can preserve the requested reasoning effort")
                        .code(GATEWAY_CODE)
                        .param(Optional.empty())
                        .type("invalid_request_error")
                        .build())
                .build();
        RuntimeException wrapped = new RuntimeException(upstream.getMessage(), upstream);

        assertTrue(ReasoningIntentRejection.isUnpreservable(wrapped));
    }

    @Test
    void matchesGatewayCodeCarriedOnlyInMessage() {
        UnprocessableEntityException upstream = UnprocessableEntityException.builder()
                .headers(Headers.builder().build())
                .build();
        RuntimeException wrapped =
                new RuntimeException("422: {\"detail\":{\"code\":\"" + GATEWAY_CODE + "\"}}", upstream);

        assertTrue(ReasoningIntentRejection.isUnpreservable(wrapped));
    }

    @Test
    void ignoresUnprocessableEntityWithoutGatewayCode() {
        UnprocessableEntityException upstream = UnprocessableEntityException.builder()
                .headers(Headers.builder().build())
                .error(ErrorObject.builder()
                        .message("validation failed")
                        .code(Optional.empty())
                        .param(Optional.empty())
                        .type("invalid_request_error")
                        .build())
                .build();

        assertFalse(ReasoningIntentRejection.isUnpreservable(new RuntimeException(upstream)));
    }

    @Test
    void ignoresGatewayCodeWithoutUnprocessableEntity() {
        assertFalse(ReasoningIntentRejection.isUnpreservable(new RuntimeException("boom: " + GATEWAY_CODE)));
    }

    @Test
    void ignoresGenericFailures() {
        assertFalse(ReasoningIntentRejection.isUnpreservable(new RuntimeException("connection reset")));
    }
}
