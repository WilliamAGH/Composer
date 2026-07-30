import { addEmailContextMessage } from './chat-rendering.js';

export function initializeEventListeners(chat) {
    chat.sendButton?.addEventListener('click', () => chat.sendMessage());
    chat.messageInput?.addEventListener('keypress', (event) => {
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            chat.sendMessage();
        }
    });

    if (chat.fileInput && window.Composer?.FileUploadHandler) {
        new window.Composer.FileUploadHandler({
            dropArea: chat.fileDropArea || document.getElementById('inlineUploadButton'),
            fileInput: chat.fileInput,
            onFileSelected: (file) => handleFileUpload(chat, file),
            validExtensions: ['.eml', '.msg', '.txt']
        });
    }

    initializeThinkingDropdown(chat);
    initializeJsonToggle(chat);
    updateThinkingControls(chat);
    resetReasoningState(chat);
}

function initializeThinkingDropdown(chat) {
    const button = document.getElementById('thinkingMenuButton');
    const menu = document.getElementById('thinkingMenu');
    const label = document.getElementById('thinkingMenuLabel');
    if (!button || !menu) {
        return;
    }

    const setOpen = (open) => {
        if (open) {
            const rect = button.getBoundingClientRect();
            const spaceBelow = window.innerHeight - rect.bottom;
            const menuHeight = 240;
            if (spaceBelow >= menuHeight || spaceBelow > rect.top) {
                menu.style.top = rect.bottom + 8 + 'px';
                menu.style.bottom = 'auto';
            } else {
                menu.style.bottom = window.innerHeight - rect.top + 8 + 'px';
                menu.style.top = 'auto';
            }
            menu.style.left = rect.left + 'px';
            menu.classList.remove('hidden');
        } else {
            menu.classList.add('hidden');
        }
        button.setAttribute('aria-expanded', String(open));
        button.querySelector('svg').style.transform = open ? 'rotate(180deg)' : 'rotate(0)';
    };

    button.addEventListener('click', (event) => {
        event.stopPropagation();
        setOpen(button.getAttribute('aria-expanded') !== 'true');
    });
    document.addEventListener('click', (event) => {
        if (!menu.contains(event.target) && !button.contains(event.target)) {
            setOpen(false);
        }
    });
    window.addEventListener('resize', () => {
        if (button.getAttribute('aria-expanded') === 'true') {
            setOpen(true);
        }
    });

    menu.querySelectorAll('button[data-reasoning-effort]').forEach((option) => {
        option.addEventListener('click', () => {
            updateThinkingMode(chat, option.getAttribute('data-reasoning-effort'));
            menu.querySelectorAll('button').forEach((menuOption) => menuOption.classList.remove('bg-slate-100'));
            option.classList.add('bg-slate-100');
            setOpen(false);
        });
    });

    chat.refreshThinkingMenuLabel = () => {
        if (!label) {
            return;
        }
        const effort = chat.thinkingLevel;
        if (!effort) {
            label.textContent = 'Thinking: Default';
            return;
        }
        label.textContent = 'Thinking: ' + effort.charAt(0).toUpperCase() + effort.slice(1);
    };
    chat.refreshThinkingMenuLabel();
}

function initializeJsonToggle(chat) {
    if (!chat.jsonToggleButton) {
        return;
    }
    chat.jsonToggleButton.addEventListener('click', () => {
        chat.jsonOutput = !chat.jsonOutput;
        updateJsonToggleButton(chat);
        if (chat.thinkingEnabled === false) {
            resetReasoningState(chat);
        }
    });
    updateJsonToggleButton(chat);
}

function updateJsonToggleButton(chat) {
    if (!chat.jsonToggleButton) {
        return;
    }
    const isOn = Boolean(chat.jsonOutput);
    chat.jsonToggleButton.textContent = isOn ? 'JSON Output: On' : 'JSON Output: Off';
    const enabledClasses = ['bg-slate-900', 'text-white', 'border-slate-900'];
    const disabledClasses = ['bg-white', 'text-slate-700', 'border-slate-300'];
    chat.jsonToggleButton.classList.toggle(enabledClasses[0], isOn);
    chat.jsonToggleButton.classList.toggle(enabledClasses[1], isOn);
    chat.jsonToggleButton.classList.toggle(enabledClasses[2], isOn);
    chat.jsonToggleButton.classList.toggle(disabledClasses[0], !isOn);
    chat.jsonToggleButton.classList.toggle(disabledClasses[1], !isOn);
    chat.jsonToggleButton.classList.toggle(disabledClasses[2], !isOn);
}

async function handleFileUpload(chat, file) {
    showUploadStatus(chat, '<div class="upload-status upload-status--info">Uploading and parsing file...</div>');
    try {
        const parsedEmail = await window.Composer.parseEmailFile(file, chat.config.uiNonce);
        if (!parsedEmail.contextId) {
            throw new Error('Server did not provide a contextId for the uploaded file');
        }
        chat.currentEmailContext = parsedEmail;
        showUploadStatus(chat, '<div class="upload-status upload-status--success">Context uploaded and parsed successfully!</div>');
        enableChat(chat);
        addEmailContextMessage(chat, parsedEmail);
    } catch (error) {
        const message = window.Composer.escapeHtml(String(error?.message || 'Unknown error'));
        showUploadStatus(chat, '<div class="upload-status upload-status--error">Failed to upload file: ' + message + '</div>');
    }
    enableChat(chat);
}

function showUploadStatus(chat, html) {
    if (!chat.uploadStatus) {
        return;
    }
    chat.uploadStatus.innerHTML = html;
    chat.uploadStatus.classList.toggle('hidden', !html);
}

function updateThinkingMode(chat, selectedEffort) {
    const disabledEffort = chat.config.disabledReasoningEffort;
    const normalizedEffort = (selectedEffort || disabledEffort).toLowerCase();
    chat.thinkingEnabled = normalizedEffort !== disabledEffort;
    chat.thinkingLevel = normalizedEffort;
    updateThinkingControls(chat);
    chat.refreshThinkingMenuLabel();
    if (chat.thinkingEnabled === false) {
        resetReasoningState(chat);
    }
}

export function updateThinkingControls(chat) {
    const enabled = !chat.messageInput?.disabled;
    chat.refreshThinkingMenuLabel();
    if (chat.thinkingStatus) {
        chat.thinkingStatus.classList.toggle('hidden', !enabled);
        if (enabled) {
            renderThinkingStatus(chat);
        }
    }
}

function enableChat(chat) {
    chat.messageInput.disabled = false;
    chat.sendButton.disabled = false;
    chat.messageInput.placeholder = 'Ask me anything about this email...';
    updateThinkingControls(chat);
}

export function buildChatPayload(chat, message, jsonOutput) {
    const contextId = chat.currentEmailContext?.contextId || null;
    const payload = {
        message,
        conversationId: chat.conversationId,
        maxResults: chat.defaultMaxResults,
        contextId,
        jsonOutput: Boolean(jsonOutput)
    };
    if (typeof chat.thinkingEnabled === 'boolean') {
        payload.thinkingEnabled = chat.thinkingEnabled;
    }
    if (chat.thinkingLevel) {
        payload.thinkingLevel = chat.thinkingLevel;
    }
    const rawContext = chat.currentEmailContext?.contextForAI;
    if (contextId && rawContext) {
        payload.emailContext = rawContext.length > chat.maxEmailContextChars
            ? rawContext.slice(0, chat.maxEmailContextChars)
            : rawContext;
    }
    return payload;
}

export function updateStreamState(chat, message) {
    if (!message?.type || !message.phase) {
        return;
    }
    chat.streamState.thinking = {
        active: true,
        phase: String(message.phase).toLowerCase(),
        lastType: String(message.type).toUpperCase(),
        message
    };
    renderThinkingStatus(chat, message);
}

function renderThinkingStatus(chat, message = {}) {
    if (!chat.thinkingStatus) {
        return;
    }
    if (!chat.streamState.thinking?.active) {
        chat.thinkingStatus.textContent = chat.thinkingEnabled === false
            ? 'Thinking disabled for faster responses.'
            : 'Using the default reasoning effort.';
        return;
    }
    chat.thinkingStatus.textContent = (message.displayLabel || 'Reasoning…').trim();
}

export function resetReasoningState(chat) {
    chat.streamState.thinking = { active: false, phase: 'idle', lastType: null, message: null };
    if (chat.thinkingStatus) {
        chat.thinkingStatus.textContent = chat.thinkingEnabled === false
            ? 'Thinking disabled for faster responses.'
            : 'Using the default reasoning effort.';
    }
}
