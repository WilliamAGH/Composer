package com.composerai.api.adapters.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.composerai.api.application.dto.ChatRequest;
import com.composerai.api.application.usecase.chat.ExecuteChatUseCase;
import com.composerai.api.application.usecase.chat.StoreDraftContextUseCase;
import com.composerai.api.domain.model.ReasoningEffortLevel;
import com.composerai.api.dto.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CatalogCommandControllerTest {

    private final ExecuteChatUseCase executeChatUseCase = mock(ExecuteChatUseCase.class);
    private final CatalogCommandController controller =
            new CatalogCommandController(executeChatUseCase, mock(StoreDraftContextUseCase.class));

    @Test
    void omittedReasoningForcesCanonicalDefaultBeforeDelegation() {
        ChatRequest request = new ChatRequest("Summarize", null, 5);
        ChatResponse expected = new ChatResponse("Summary", "conversation", List.of(), "summary", "<p>Summary</p>");
        when(executeChatUseCase.execute(request)).thenReturn(expected);

        ChatResponse actual =
                controller.executeCatalogCommand("summarize", request).getBody();

        assertSame(expected, actual);
        assertEquals("summarize", request.getAiCommand());
        assertEquals(ReasoningEffortLevel.DEFAULT, request.getThinkingLevel());
    }

    @Test
    void explicitFalseCannotDisableReasoningForCatalogCommand() {
        ChatRequest request = new ChatRequest("Summarize", null, 5);
        request.setThinkingEnabled(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> controller.executeCatalogCommand("summarize", request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(executeChatUseCase);
    }

    @Test
    void explicitNoneCannotDisableReasoningForCatalogCommand() {
        ChatRequest request = new ChatRequest("Summarize", null, 5);
        request.setThinkingLevel(ReasoningEffortLevel.NONE);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> controller.executeCatalogCommand("summarize", request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(executeChatUseCase);
    }
}
