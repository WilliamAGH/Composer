/*
 * DEPRECATED: email compose window (v1). Superseded by Svelte ComposeWindow.svelte
 * in frontend/email-client/src/lib/ComposeWindow.svelte. Scheduled for deletion after v2 cutover.
 */
const DEFAULT_ATTACHMENT_EXTENSIONS = ['.eml', '.msg', '.txt', '.pdf', '.doc', '.docx', '.ppt', '.pptx', '.html', '.md'];

const noop = () => {};

export class ComposeManager {
    constructor({ container, template, onSend = noop, onRequestAi = noop } = {}) {
        if (!container || !template) {
            throw new Error('ComposeManager requires container and template elements');
        }

        this.container = container;
        this.template = template;
        this.onSend = onSend;
        this.onRequestAi = onRequestAi;
        this.windows = [];
        this.windowCounter = 0;
    }

    open(options = {}) {
        const windowId = `compose-window-${++this.windowCounter}`;
        const fragment = this.template.content.cloneNode(true);
        const element = fragment.querySelector('[data-compose-window]');
        if (!element) {
            throw new Error('Compose window template is missing required data attribute "data-compose-window"');
        }

        element.id = windowId;

        const state = {
            id: windowId,
            element,
            minimized: false,
            attachments: [],
            attachmentCounter: 0,
            isReply: Boolean(options.isReply),
            relatedEmailId: options.relatedEmailId || null,
            metadata: options.metadata ? { ...options.metadata } : {}
        };

        this.#configureWindow(state, options);
        this.container.appendChild(element);
        element.classList.add('active');
        this.windows.push(state);
        this.#realign();
        this.#focusInitialField(state);
        return state;
    }

    close(windowId) {
        const index = this.windows.findIndex((entry) => entry.id === windowId);
        if (index === -1) {
            return;
        }

        const [state] = this.windows.splice(index, 1);
        if (state?.element) {
            state.element.remove();
        }
        this.#realign();
    }

    getWindow(windowId) {
        return this.windows.find((entry) => entry.id === windowId) || null;
    }

    listWindows() {
        return [...this.windows];
    }

    collectWindowData(windowId) {
        const state = this.getWindow(windowId);
        if (!state) {
            return null;
        }
        const { refs } = state;
        if (!refs) {
            return null;
        }

        const to = refs.inputTo ? refs.inputTo.value.trim() : '';
        const subject = refs.inputSubject.value.trim();
        const message = refs.inputMessage.value.trim();
        const attachments = state.attachments.map(({ name, size }) => ({ name, size }));

        return { state, to, subject, message, attachments };
    }

    #configureWindow(state, options) {
        const { element } = state;
        const refs = {
            headerLabel: element.querySelector('[data-role="header-label"]'),
            toRow: element.querySelector('[data-role="to-row"]'),
            inputTo: element.querySelector('[data-role="input-to"]'),
            inputSubject: element.querySelector('[data-role="input-subject"]'),
            inputMessage: element.querySelector('[data-role="input-message"]'),
            aiButtons: element.querySelectorAll('[data-ai-command]'),
            attachmentsContainer: element.querySelector('[data-role="attachments"]'),
            attachmentsList: element.querySelector('[data-role="attachments-list"]'),
            attachmentsCount: element.querySelector('[data-role="attachments-count"]'),
            attachmentInput: element.querySelector('[data-role="input-attachment"]'),
            attachButton: element.querySelector('[data-role="attach-button"]'),
            sendButton: element.querySelector('[data-action="send"]'),
            minimizeButton: element.querySelector('[data-action="minimize"]'),
            closeButton: element.querySelector('[data-action="close"]')
        };

        state.refs = refs;

        const replyLabel = options.replyLabel || (options.replyToName ? `Reply to ${options.replyToName}` : 'Reply');
        refs.headerLabel.textContent = state.isReply ? replyLabel : 'New Message';

        if (refs.toRow) {
            refs.toRow.classList.toggle('hidden', state.isReply);
        }

        if (refs.inputTo && !state.isReply) {
            refs.inputTo.value = options.toAddress || '';
        }
        if (refs.inputSubject) {
            refs.inputSubject.value = options.subject || '';
        }
        if (refs.inputMessage) {
            refs.inputMessage.value = options.body || '';
            refs.inputMessage.setAttribute('rows', options.rows ? String(options.rows) : (state.isReply ? '6' : '8'));
            refs.inputMessage.placeholder = options.placeholder || (state.isReply ? 'Type your reply...' : 'Type your message...');
        }

        this.#bindWindowEvents(state);
        this.#attachFileHandler(state);
    }

    #bindWindowEvents(state) {
        const { element, refs } = state;
        if (!refs) return;

        refs.closeButton?.addEventListener('click', (event) => {
            event.stopPropagation();
            this.close(state.id);
        });

        refs.minimizeButton?.addEventListener('click', (event) => {
            event.stopPropagation();
            state.minimized = !state.minimized;
            element.classList.toggle('minimized', state.minimized);
        });

        refs.sendButton?.addEventListener('click', () => {
            const data = this.collectWindowData(state.id);
            if (!data) {
                return;
            }
            this.onSend(data);
        });

        if (refs.inputMessage) {
            refs.inputMessage.addEventListener('keydown', (event) => {
                if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
                    event.preventDefault();
                    const data = this.collectWindowData(state.id);
                    if (!data) {
                        return;
                    }
                    this.onSend(data);
                }
            });
        }

        if (refs.attachmentsList) {
            refs.attachmentsList.addEventListener('click', (event) => {
                const target = event.target instanceof HTMLElement ? event.target.closest('[data-remove-attachment]') : null;
                if (!target) {
                    return;
                }
                event.preventDefault();
                const attachmentId = target.getAttribute('data-remove-attachment');
                this.#removeAttachment(state, attachmentId);
            });
        }

        refs.aiButtons?.forEach((button) => {
            button.addEventListener('click', () => {
                const command = button.getAttribute('data-ai-command');
                if (!command) {
                    return;
                }
                this.onRequestAi({ windowId: state.id, command, state });
            });
        });
    }

    #attachFileHandler(state) {
        const { refs } = state;
        if (!refs?.attachmentInput || !refs.attachButton || !window.ComposerAI?.FileUploadHandler) {
            return;
        }

        try {
            new window.ComposerAI.FileUploadHandler({
                dropArea: refs.attachButton,
                fileInput: refs.attachmentInput,
                validExtensions: DEFAULT_ATTACHMENT_EXTENSIONS,
                onFileSelected: (file) => {
                    refs.attachmentInput.value = '';
                    this.#addAttachment(state, file);
                }
            });
        } catch (error) {
            console.error('Failed to initialize compose attachment handler', error);
        }
    }

    #addAttachment(state, file) {
        if (!file) {
            return;
        }

        state.attachmentCounter = (state.attachmentCounter || 0) + 1;
        state.attachments.push({
            id: `${state.id}-attachment-${state.attachmentCounter}`,
            name: file.name,
            size: typeof file.size === 'number' ? file.size : 0
        });

        this.#renderAttachments(state);
    }

    #removeAttachment(state, attachmentId) {
        if (!attachmentId) {
            return;
        }
        state.attachments = state.attachments.filter((attachment) => attachment.id !== attachmentId);
        this.#renderAttachments(state);
    }

    #renderAttachments(state) {
        const { refs } = state;
        if (!refs?.attachmentsContainer || !refs.attachmentsList) {
            return;
        }

        const attachments = state.attachments || [];
        if (attachments.length === 0) {
            refs.attachmentsContainer.classList.add('hidden');
            refs.attachmentsList.innerHTML = '';
            if (refs.attachmentsCount) {
                refs.attachmentsCount.textContent = '';
            }
            return;
        }

        const markup = attachments.map((attachment) => {
            const safeName = window.ComposerAI?.escapeHtml
                ? window.ComposerAI.escapeHtml(attachment.name || 'Attachment')
                : (attachment.name || 'Attachment');
            const sizeLabel = (typeof attachment.size === 'number' && window.ComposerAI?.formatFileSize)
                ? window.ComposerAI.formatFileSize(attachment.size)
                : '';
            return `
                <li class="compose-attachment-item flex items-center justify-between gap-3 rounded-lg border border-slate-200/80 bg-white/90 px-3 py-2 shadow-sm">
                    <div class="flex items-center gap-3 min-w-0">
                        <svg class="icon-sm flex-shrink-0 text-slate-700" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                            <path d="M18.375 12.739l-7.693 7.693a4.5 4.5 0 01-6.364-6.364l10.94-10.94A3 3 0 1119.5 7.372L8.552 18.32" stroke-linecap="round" stroke-linejoin="round"></path>
                        </svg>
                        <div class="min-w-0">
                            <p class="truncate font-semibold text-xs text-slate-700">${safeName}</p>
                            ${sizeLabel ? `<p class="text-slate-500" style="font-size: 0.65rem;">${sizeLabel}</p>` : ''}
                        </div>
                    </div>
                    <button type="button" class="text-slate-400 hover:text-slate-700 transition" data-remove-attachment="${attachment.id}" title="Remove attachment">
                        <svg class="icon-sm" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                            <path d="M6 18L18 6M6 6l12 12" stroke-linecap="round" stroke-linejoin="round" />
                        </svg>
                    </button>
                </li>
            `;
        }).join('');

        refs.attachmentsList.innerHTML = markup;
        refs.attachmentsContainer.classList.remove('hidden');
        if (refs.attachmentsCount) {
            refs.attachmentsCount.textContent = `${attachments.length} file${attachments.length === 1 ? '' : 's'}`;
        }
    }

    #realign() {
        this.windows.forEach((windowState, index) => {
            const { element } = windowState;
            if (!element) {
                return;
            }
            element.style.right = '1.5rem';
            element.style.bottom = `${index * 4}rem`;
            element.style.left = 'auto';
            element.style.top = 'auto';
        });
    }

    #focusInitialField(state) {
        const { refs } = state;
        if (!refs) {
            return;
        }
        const target = state.isReply ? refs.inputSubject : (refs.inputTo || refs.inputSubject);
        if (target) {
            setTimeout(() => target.focus(), 75);
        }
    }
}
