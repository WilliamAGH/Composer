import {
    buildChatPayload,
    initializeEventListeners,
    updateThinkingControls
} from './chat-interactions.js';
import {
    addMessage,
    applyMarkdownEnhancements
} from './chat-rendering.js';
import {
    sendChatRequest,
    streamResponse
} from './chat-streams.js';

class ComposerChat {
    constructor(config) {
        this.config = config;
        this.messages = document.getElementById('messages');
        this.messageInput = document.getElementById('messageInput');
        this.sendButton = document.getElementById('sendButton');
        this.fileDropArea = document.getElementById('fileDropArea');
        this.fileInput = document.getElementById('fileInput') || document.getElementById('inlineFileInput');
        this.uploadStatus = document.getElementById('uploadStatus');
        this.currentEmailContext = null;
        this.conversationId = null;
        this.activeStreamController = null;
        this.isStreaming = false;
        this.streamState = { thinking: { active: false, phase: 'idle', lastType: null, message: null } };
        this.thinkingStatus = document.getElementById('thinkingStatus');
        this.thinkingEnabled = null;
        this.thinkingLevel = null;
        this.jsonToggleButton = document.getElementById('jsonToggleButton');
        this.jsonOutput = false;
        this.jsonPreClass =
            'w-full whitespace-pre-wrap font-mono text-sm leading-relaxed bg-slate-900/90 text-slate-100 rounded-2xl border border-slate-800 p-4 shadow-ultra overflow-x-auto';
        this.previewCounter = 0;
        this.streamTimeoutMs = 120000;
        this.maxEmailContextChars = 20000;
        this.defaultMaxResults = 5;
        this.insightsTrigger = '__INSIGHTS_TRIGGER__';
        this.refreshThinkingMenuLabel = () => {};
        initializeEventListeners(this);
        applyMarkdownEnhancements(this.messages);
    }

    addMessage(content, sender) {
        return addMessage(this, content, sender);
    }

    async sendMessage() {
        const message = this.messageInput.value.trim();
        if (!message || this.isStreaming) {
            return;
        }

        this.abortActiveStream();
        this.addMessage(message, 'user');
        this.messageInput.value = '';
        const isJsonMode = this.jsonOutput;
        const assistant = this.addMessage('', 'assistant');
        if (isJsonMode) {
            assistant.innerHTML =
                '<div class="phase-badge phase-streaming">Preparing JSON...</div><pre class="' +
                this.jsonPreClass + '"></pre>';
        } else {
            const phaseLabel = this.thinkingEnabled === false ? 'Parsing your email...' : 'Thinking...';
            assistant.innerHTML =
                '<div class="assistant-thinking relative"><div class="phase-badge phase-thinking">' +
                phaseLabel +
                '</div><div class="thinking-skeleton rounded-xl border border-slate-200 bg-slate-50/80 p-3"><div class="thinking-line"></div><div class="thinking-line"></div><div class="thinking-line short"></div><div class="thinking-cursor-bar"></div></div></div>';
        }

        const payload = buildChatPayload(this, message, isJsonMode);
        await this.runStreamingRequest(
            (signal) => streamResponse(this, '/api/chat/stream', payload, assistant, signal, true),
            async (error) => {
                if (error.name === 'AbortError') {
                    assistant.innerHTML = window.Composer.renderMarkdown('Request timed out. Please try again.');
                    return;
                }
                try {
                    await sendChatRequest(this, payload, assistant);
                } catch (_) {
                    assistant.innerHTML =
                        window.Composer.renderMarkdown('Sorry, I encountered an error. Please try again.');
                }
            }
        );
    }

    async triggerInsights() {
        if (this.isStreaming) {
            return;
        }
        this.abortActiveStream();
        const assistant = this.addMessage('', 'assistant');
        assistant.innerHTML =
            '<div class="assistant-thinking relative"><div class="phase-badge phase-thinking">Analyzing email...</div><div class="thinking-skeleton rounded-xl border border-slate-200 bg-slate-50/80 p-3"><div class="thinking-line"></div><div class="thinking-line"></div><div class="thinking-line short"></div><div class="thinking-cursor-bar"></div></div></div>';
        const payload = buildChatPayload(this, this.insightsTrigger, false);
        await this.runStreamingRequest(
            (signal) => streamResponse(this, '/api/chat/insights/stream', payload, assistant, signal, false),
            (error) => {
                const message = error.name === 'AbortError'
                    ? 'Request timed out. Please try again.'
                    : 'Sorry, I encountered an error. Please try again.';
                assistant.innerHTML = window.Composer.renderMarkdown(message);
            }
        );
    }

    abortActiveStream() {
        try {
            this.activeStreamController?.abort();
        } catch (_) {
            // An already-completed browser stream does not need further cleanup.
        }
    }

    async runStreamingRequest(request, onError) {
        this.messageInput.disabled = true;
        this.sendButton.disabled = true;
        updateThinkingControls(this);
        this.isStreaming = true;
        this.activeStreamController = new AbortController();
        const timeoutId = setTimeout(() => this.activeStreamController?.abort(), this.streamTimeoutMs);
        try {
            await request(this.activeStreamController.signal);
        } catch (error) {
            await onError(error);
        } finally {
            clearTimeout(timeoutId);
            this.isStreaming = false;
            this.activeStreamController = null;
            this.messageInput.disabled = false;
            this.sendButton.disabled = false;
            updateThinkingControls(this);
            this.messages.scrollTop = this.messages.scrollHeight;
        }
    }
}

document.addEventListener('DOMContentLoaded', () => {
    new ComposerChat(window.ComposerChatConfig);
});
