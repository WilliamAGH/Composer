export class SseEventRouter {
    constructor() {
        this.handlers = new Map();
    }

    on(eventType, handler) {
        if (typeof handler === 'function') {
            this.handlers.set(eventType, handler);
        }
        return this;
    }

    route(eventType, eventPayload) {
        const handler = this.handlers.get(eventType);
        if (handler) {
            handler(eventPayload);
        }
    }
}

function routePendingEvent(router, eventType, dataLines) {
    if (!eventType) {
        return;
    }
    router.route(eventType, dataLines.length > 0 ? dataLines.join('\n') : '');
}

export async function readSseEvents(response, router, isFinished) {
    if (!response.body) {
        throw new Error('Streaming response did not include a body');
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let currentEvent = null;
    let currentEventData = [];

    while (!isFinished()) {
        const streamRead = await reader.read();
        if (streamRead.done) {
            break;
        }

        buffer += decoder.decode(streamRead.value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
            const cleanedLine = line.endsWith('\r') ? line.slice(0, -1) : line;

            if (cleanedLine.startsWith('event:')) {
                routePendingEvent(router, currentEvent, currentEventData);
                currentEvent = cleanedLine.substring(6).trim();
                currentEventData = [];
                continue;
            }

            if (cleanedLine.startsWith('data:')) {
                const eventLinePayload = cleanedLine.length >= 6 && cleanedLine.charAt(5) === ' '
                    ? cleanedLine.substring(6)
                    : cleanedLine.substring(5);
                currentEventData.push(eventLinePayload);
                continue;
            }

            if (cleanedLine === '') {
                routePendingEvent(router, currentEvent, currentEventData);
                currentEvent = null;
                currentEventData = [];
                if (isFinished()) {
                    break;
                }
            }
        }
    }

    if (!isFinished()) {
        routePendingEvent(router, currentEvent, currentEventData);
    }
}
