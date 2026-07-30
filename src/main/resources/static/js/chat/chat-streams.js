import { resetReasoningState, updateStreamState } from './chat-interactions.js';
import { applyMarkdownEnhancements, scrollIfAtBottom } from './chat-rendering.js';
import { readSseEvents, SseEventRouter } from './chat-sse.js';

function requestHeaders(chat) {
    return {
        'Content-Type': 'application/json',
        'X-UI-Request': chat.config.uiNonce
    };
}

function updateTimeoutHint(chat, response) {
    const timeoutHint = response.headers.get('X-Stream-Timeout-Hint');
    if (!timeoutHint) {
        return;
    }
    const parsed = Number.parseInt(timeoutHint, 10);
    if (Number.isFinite(parsed) && parsed > 0) {
        chat.streamTimeoutMs = parsed;
    }
}

function renderJsonResult(chat, target, rawText) {
    const preformatted = document.createElement('pre');
    preformatted.className = chat.jsonPreClass;
    if (rawText) {
        try {
            preformatted.textContent = JSON.stringify(JSON.parse(rawText), null, 2);
        } catch (_) {
            preformatted.textContent = rawText;
        }
    } else {
        preformatted.textContent = '{ }';
    }
    target.innerHTML = '';
    target.appendChild(preformatted);
}

export async function sendChatRequest(chat, payload, target) {
    const response = await fetch('/api/chat', {
        method: 'POST',
        headers: requestHeaders(chat),
        body: JSON.stringify(payload)
    });
    if (!response.ok) {
        throw new Error('HTTP ' + response.status);
    }

    const chatResponse = await response.json();
    const renderedHtml =
        (chatResponse.sanitizedHtml ||
            chatResponse.sanitizedHTML ||
            chatResponse.renderedHtml ||
            chatResponse.renderedHTML ||
            '').trim();
    const rawText = (chatResponse.rawMarkdown || chatResponse.response || chatResponse.message || '').trim();
    if (payload.jsonOutput) {
        renderJsonResult(chat, target, rawText);
    } else {
        target.innerHTML = renderedHtml || window.Composer.renderMarkdown(
            rawText || 'I received your message about the email.'
        );
        applyMarkdownEnhancements(target);
    }
    if (chatResponse.conversationId) {
        chat.conversationId = chatResponse.conversationId;
    }
}

function finalizeStream(chat, payload, target, state) {
    if (state.isJsonMode) {
        if (state.jsonPre) {
            try {
                state.jsonPre.textContent = JSON.stringify(JSON.parse(state.jsonBuffer), null, 2);
            } catch (_) {
                state.jsonPre.textContent = state.jsonBuffer || '{ }';
            }
        } else if (state.jsonBuffer) {
            renderJsonResult(chat, target, state.jsonBuffer);
        } else {
            target.innerHTML = window.Composer.renderMarkdown('No response received.');
        }
    } else {
        target.innerHTML = state.html || window.Composer.renderMarkdown("I don't have a response to that.");
        applyMarkdownEnhancements(target);
    }
    chat.conversationId = state.conversationId || payload.conversationId || chat.conversationId;
    resetReasoningState(chat);
    state.finished = true;
}

function createRouter(chat, payload, target, state, allowJson) {
    const events = chat.config.sseEvents;
    return new SseEventRouter()
        .on(events.METADATA, (raw) => {
            if (!raw) {
                return;
            }
            try {
                const metadata = JSON.parse(raw);
                state.conversationId = metadata.conversationId || state.conversationId;
                if (allowJson && metadata.jsonOutput !== undefined) {
                    state.isJsonMode = Boolean(metadata.jsonOutput);
                }
            } catch (error) {
                throw new Error('Composer received malformed stream metadata', { cause: error });
            }
        })
        .on(events.RENDERED_HTML, (chunk) => {
            if (chunk == null || state.isJsonMode) {
                return;
            }
            if (state.firstToken) {
                target.innerHTML = '<div class="phase-badge phase-streaming">Streaming...</div>';
                state.firstToken = false;
            }
            state.html += chunk || '';
            target.innerHTML = state.html + '<span class="streaming-cursor">|</span>';
            scrollIfAtBottom(chat);
        })
        .on(events.RAW_JSON, (chunk) => {
            if (!allowJson || chunk == null || !state.isJsonMode) {
                return;
            }
            state.jsonBuffer += chunk;
            if (state.firstToken) {
                target.innerHTML =
                    '<div class="phase-badge phase-streaming">Streaming JSON...</div><pre class="' +
                    chat.jsonPreClass + '"></pre>';
                state.jsonPre = target.querySelector('pre');
                state.firstToken = false;
            }
            if (state.jsonPre) {
                state.jsonPre.textContent = state.jsonBuffer;
            }
            scrollIfAtBottom(chat);
        })
        .on(events.REASONING, (raw) => {
            if (!raw) {
                return;
            }
            try {
                updateStreamState(chat, JSON.parse(raw));
            } catch (error) {
                throw new Error('Composer received a malformed reasoning event', { cause: error });
            }
        })
        .on(events.ERROR, (message) => {
            throw new Error(message || 'Stream error occurred');
        })
        .on(events.DONE, () => finalizeStream(chat, payload, target, state));
}

export async function streamResponse(chat, endpoint, payload, target, signal, allowJson) {
    const response = await fetch(endpoint, {
        method: 'POST',
        headers: requestHeaders(chat),
        body: JSON.stringify(payload),
        signal
    });
    if (!response.ok) {
        throw new Error('HTTP ' + response.status);
    }
    updateTimeoutHint(chat, response);

    const state = {
        isJsonMode: allowJson && Boolean(payload.jsonOutput),
        html: '',
        jsonBuffer: '',
        firstToken: true,
        jsonPre: null,
        conversationId: null,
        finished: false
    };
    const router = createRouter(chat, payload, target, state, allowJson);
    await readSseEvents(response, router, () => state.finished);
    if (!state.finished) {
        finalizeStream(chat, payload, target, state);
    }
}
