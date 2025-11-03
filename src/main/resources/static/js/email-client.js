import { ComposeManager } from './email-compose.js';

const FALLBACK_COMMAND_TITLES = {
    summarize: 'AI Summary',
    translate: 'AI Translation',
    draft: 'AI Draft Reply',
    compose: 'AI Compose',
    tone: 'AI Tone Adjustment',
    explain: 'AI Explanation'
};

class EmailClientApp {
    constructor({ messages = [], commandDefaults = {}, commandTemplates = {}, uiNonce = null } = {}) {
        this.uiNonce = uiNonce;
        this.commandDefaults = commandDefaults;
        this.commandTemplates = commandTemplates;
        this.supportedCommands = new Set(['compose', 'draft', 'summarize', 'translate', 'tone']);

        this.originalMessages = Array.isArray(messages) ? messages : [];
        this.emails = this.originalMessages.map((message, index) => this.#mapEmailMessage(message, index));
        if (this.emails.length === 0) {
            this.emails = this.#fallbackEmails();
        }

        this.filteredEmails = [...this.emails];
        this.selectedEmail = null;

        this.#initializeElements();
        this.#initializeComposeManager();
        this.#bindEventListeners();

        this.#renderEmailList();
        if (this.emails.length > 0) {
            this.#selectEmail(this.emails[0].id);
        }
        this.#updateBadges();
    }

    #initializeElements() {
        this.container = document.querySelector('.email-client-container');
        this.sidebar = document.getElementById('sidebar');
        this.menuToggle = document.getElementById('menuToggle');
        this.composeBtn = document.getElementById('composeBtn');
        this.searchInput = document.getElementById('searchInput');
        this.emailList = document.getElementById('emailList');
        this.emailContent = document.getElementById('emailContent');
        this.composeWindowsContainer = document.getElementById('composeWindowsContainer');
        this.aiResponsePanel = document.getElementById('aiResponsePanel');
    }

    #initializeComposeManager() {
        const template = document.getElementById('composeWindowTemplate');
        if (!template || !this.composeWindowsContainer) {
            throw new Error('Compose window template or container missing');
        }

        this.composeManager = new ComposeManager({
            container: this.composeWindowsContainer,
            template,
            onSend: (payload) => this.#handleComposeSend(payload),
            onRequestAi: ({ windowId, command }) => this.#handleComposeAi(windowId, command)
        });
    }

    #bindEventListeners() {
        this.menuToggle?.addEventListener('click', () => this.#toggleSidebar());
        this.composeBtn?.addEventListener('click', () => this.#openComposeWindow());
        this.searchInput?.addEventListener('input', (event) => {
            const term = event.target.value || '';
            this.#handleSearch(term);
        });
    }

    #mapEmailMessage(message, index) {
        const safe = message || {};
        const preview = typeof safe.preview === 'string' && safe.preview.trim().length > 0
            ? safe.preview.trim()
            : this.#coalescePreview(safe);
        return {
            id: safe.id || safe.contextId || `email-${index + 1}`,
            contextId: safe.contextId || null,
            from: safe.senderName || safe.senderEmail || 'Unknown sender',
            fromEmail: safe.senderEmail || '',
            to: safe.recipientName || safe.recipientEmail || '',
            toEmail: safe.recipientEmail || '',
            subject: safe.subject || 'No subject',
            preview,
            contentText: safe.emailBodyTransformedText || '',
            contentMarkdown: safe.emailBodyTransformedMarkdown || '',
            contentHtml: typeof safe.emailBodyHtml === 'string' && safe.emailBodyHtml.trim().length > 0 ? safe.emailBodyHtml : null,
            timestamp: safe.receivedTimestampDisplay || '',
            timestampIso: safe.receivedTimestampIso || null,
            read: Boolean(safe.read),
            starred: Boolean(safe.starred),
            avatar: safe.avatarUrl || safe.companyLogoUrl || '',
            labels: Array.isArray(safe.labels) ? safe.labels : [],
            companyLogoUrl: safe.companyLogoUrl || null
        };
    }

    #coalescePreview(message) {
        const text = typeof message.emailBodyTransformedText === 'string' ? message.emailBodyTransformedText.trim() : '';
        if (text.length === 0) {
            return '';
        }
        const normalized = text.replace(/\s+/g, ' ');
        return normalized.length <= 180 ? normalized : `${normalized.slice(0, 177)}...`;
    }

    #fallbackEmails() {
        return [
            {
                id: 'sample-1',
                contextId: null,
                from: 'ComposerAI',
                fromEmail: 'hello@composerai.app',
                to: 'ComposerAI Team',
                toEmail: 'hello@composerai.app',
                subject: 'Welcome to ComposerAI',
                preview: 'Compose, summarize, and translate your inbox with AI-powered workflows.',
                contentText: 'Welcome to ComposerAI! Upload emails, connect your inbox, and unlock AI-powered compose, summarize, and translate workflows.',
                contentMarkdown: '',
                contentHtml: null,
                timestamp: 'Just now',
                timestampIso: null,
                read: false,
                starred: true,
                avatar: '',
                labels: ['Product']
            }
        ];
    }

    #toggleSidebar() {
        if (!this.sidebar || !this.container) {
            return;
        }
        const isCollapsed = this.sidebar.classList.toggle('closed');
        this.container.classList.toggle('sidebar-collapsed', isCollapsed);
        this.container.classList.toggle('sidebar-open', !isCollapsed);
    }

    #handleSearch(query) {
        const normalized = query.toLowerCase();
        if (!normalized) {
            this.filteredEmails = [...this.emails];
        } else {
            this.filteredEmails = this.emails.filter((email) => {
                const haystack = [email.subject, email.from, email.preview].join(' ').toLowerCase();
                return haystack.includes(normalized);
            });
        }
        this.#renderEmailList();
    }

    #renderEmailList() {
        if (!this.emailList) {
            return;
        }

        if (this.filteredEmails.length === 0) {
            this.emailList.innerHTML = '<div class="p-6 text-sm text-slate-500">No emails match your filter.</div>';
            return;
        }

        const markup = this.filteredEmails.map((email) => {
            const selectedClass = this.selectedEmail?.id === email.id ? 'selected' : '';
            const unreadClass = email.read ? '' : 'unread';
            const from = window.ComposerAI.escapeHtml(email.from);
            const subject = window.ComposerAI.escapeHtml(email.subject);
            const preview = window.ComposerAI.escapeHtml(email.preview || '');
            const avatarSrc = email.avatar || email.companyLogoUrl || 'https://i.pravatar.cc/100?u=' + encodeURIComponent(email.fromEmail || email.from);
            const starClass = email.starred ? 'starred' : '';
            const starColor = email.starred ? '#facc15' : '#94a3b8';

            const labelsMarkup = email.labels.length > 0
                ? `<div class="flex gap-1 mt-2">${email.labels.map((label) => `<span class="label-badge">${window.ComposerAI.escapeHtml(label)}</span>`).join('')}</div>`
                : '';

            return `
                <div class="email-item ${unreadClass} ${selectedClass}" data-email-id="${email.id}">
                    <div class="flex items-start gap-3">
                        <img src="${avatarSrc}" alt="${from}" class="avatar" loading="lazy">
                        <div class="flex-1 min-w-0">
                            <div class="flex items-center justify-between mb-1">
                                <span class="font-semibold truncate" style="color: ${email.read ? '#64748b' : '#0f172a'}; font-size: 0.875rem;">${from}</span>
                                <span class="text-xs ml-2" style="color: #94a3b8; flex-shrink: 0;">${window.ComposerAI.escapeHtml(email.timestamp || '')}</span>
                            </div>
                            <p class="text-sm truncate mb-1" style="color: ${email.read ? '#64748b' : '#0f172a'}; font-weight: ${email.read ? '400' : '500'};">${subject}</p>
                            <p class="text-xs truncate" style="color: #94a3b8;">${preview}</p>
                            ${labelsMarkup}
                        </div>
                        <button class="star-btn mt-1" data-email-id="${email.id}" data-starred="${email.starred}">
                            <svg class="icon-sm star-icon ${starClass}" viewBox="0 0 24 24" stroke="currentColor" style="color: ${starColor};">
                                <path d="M11.48 3.499a.562.562 0 011.04 0l2.125 5.111a.563.563 0 00.475.345l5.518.442c.499.04.701.663.321.988l-4.204 3.602a.563.563 0 00-.182.557l1.285 5.385a.562.562 0 01-.84.61l-4.725-2.885a.563.563 0 00-.586 0L6.982 20.54a.562.562 0 01-.84-.61l1.285-5.386a.563.563 0 00-.182-.557l-4.204-3.602a.563.563 0 01.321-.988l5.518-.442a.563.563 0 00.475-.345L11.48 3.5z" stroke-linecap="round" stroke-linejoin="round"/>
                            </svg>
                        </button>
                    </div>
                </div>
            `;
        }).join('');

        this.emailList.innerHTML = markup;
        this.emailList.querySelectorAll('.email-item').forEach((item) => {
            item.addEventListener('click', (event) => {
                if (event.target.closest('.star-btn')) {
                    return;
                }
                const emailId = item.getAttribute('data-email-id');
                this.#selectEmail(emailId);
            });
        });

        this.emailList.querySelectorAll('.star-btn').forEach((button) => {
            button.addEventListener('click', (event) => {
                event.stopPropagation();
                const emailId = button.getAttribute('data-email-id');
                this.#toggleStar(emailId);
            });
        });
    }

    #selectEmail(emailId) {
        const email = this.emails.find((entry) => entry.id === emailId);
        if (!email) {
            return;
        }
        email.read = true;
        this.selectedEmail = email;
        this.#renderEmailList();
        this.#renderEmailContent(email);
        this.#updateBadges();
    }

    #renderEmailContent(email) {
        if (!this.emailContent) {
            return;
        }

        const labelsMarkup = email.labels.length > 0
            ? `<div class="flex gap-2 mt-3">${email.labels.map((label) => `<span class="label-badge">${window.ComposerAI.escapeHtml(label)}</span>`).join('')}</div>`
            : '';

        const avatarSrc = email.avatar || email.companyLogoUrl || 'https://i.pravatar.cc/120?u=' + encodeURIComponent(email.fromEmail || email.from);
        const toLine = (email.to || email.toEmail)
            ? `<div class="flex items-center gap-2 text-xs mt-1 text-slate-400"><span>To:</span><span>${window.ComposerAI.escapeHtml(email.to || 'Unknown recipient')}</span>${email.toEmail ? `<span>&lt;${window.ComposerAI.escapeHtml(email.toEmail)}&gt;</span>` : ''}</div>`
            : '';

        const hasHtmlBody = typeof email.contentHtml === 'string' && email.contentHtml.trim().length > 0;
        const markdownBody = email.contentMarkdown || email.contentText || '';
        const contentBody = hasHtmlBody
            ? '<div class="email-body" data-role="email-html-container"></div>'
            : `<div class="email-body" data-role="email-text">${window.ComposerAI.renderMarkdown(markdownBody)}</div>`;

        this.emailContent.innerHTML = `
            <div class="flex-1 flex flex-col">
                <div class="p-6 border-b" style="border-color: rgba(226, 232, 240, 0.6);">
                    <div class="flex items-start justify-between mb-4">
                        <div class="flex items-start gap-4">
                            <img src="${avatarSrc}" alt="${window.ComposerAI.escapeHtml(email.from)}" class="avatar avatar-lg" loading="lazy">
                            <div>
                                <h2 class="text-xl font-semibold mb-1" style="color: #0f172a;">${window.ComposerAI.escapeHtml(email.subject)}</h2>
                                <div class="flex items-center gap-2 text-sm" style="color: #64748b;">
                                    <span class="font-medium">${window.ComposerAI.escapeHtml(email.from)}</span>
                                    ${email.fromEmail ? `<span>&lt;${window.ComposerAI.escapeHtml(email.fromEmail)}&gt;</span>` : ''}
                                </div>
                                ${toLine}
                                <p class="text-xs mt-1" style="color: #94a3b8;">${window.ComposerAI.escapeHtml(email.timestamp || '')}</p>
                                ${labelsMarkup}
                            </div>
                        </div>
                        <div class="flex gap-2" data-role="email-actions">
                            <button type="button" class="btn-secondary btn-icon h-9 w-9" data-action="reply" title="Reply">
                                <svg class="icon-sm" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                                    <path d="M9 15L3 9m0 0l6-6M3 9h12a6 6 0 010 12h-3" stroke-linecap="round" stroke-linejoin="round"/>
                                </svg>
                            </button>
                            <button type="button" class="btn-secondary btn-icon h-9 w-9" data-action="archive" title="Archive">
                                <svg class="icon-sm" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                                    <path d="M20.25 7.5l-.625 10.632a2.25 2.25 0 01-2.247 2.118H6.622a2.25 2.25 0 01-2.247-2.118L3.75 7.5m8.25 3v6.75m0 0l-3-3m3 3l3-3M3.375 7.5h17.25c.621 0 1.125-.504 1.125-1.125v-1.5c0-.621-.504-1.125-1.125-1.125H3.375c-.621 0-1.125.504-1.125 1.125v1.5c0 .621.504 1.125 1.125 1.125z" stroke-linecap="round" stroke-linejoin="round"/>
                                </svg>
                            </button>
                            <button type="button" class="btn-secondary btn-icon h-9 w-9" data-action="delete" title="Delete">
                                <svg class="icon-sm" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                                    <path d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" stroke-linecap="round" stroke-linejoin="round"/>
                                </svg>
                            </button>
                        </div>
                    </div>
                    <div class="mt-4 flex flex-wrap gap-2" data-role="ai-command-bar">
                        ${this.#renderAiCommandButtons()}
                    </div>
                </div>
                <div class="flex-1 overflow-y-auto">
                    <div class="p-6 space-y-6">
                        <div class="prose prose-sm max-w-none text-slate-700" data-role="email-body">
                            ${contentBody}
                        </div>
                        <div id="aiResponsePanel" class="hidden space-y-2" data-role="ai-response-panel">
                            <div class="glass-panel" data-role="ai-response-body"></div>
                        </div>
                    </div>
                </div>
            </div>
        `;

        this.aiResponsePanel = this.emailContent.querySelector('[data-role="ai-response-panel"]');
        if (hasHtmlBody) {
            const htmlContainer = this.emailContent.querySelector('[data-role="email-html-container"]');
            if (htmlContainer) {
                if (window.EmailRenderer?.renderInIframe) {
                    window.EmailRenderer.renderInIframe(htmlContainer, email.contentHtml);
                } else {
                    htmlContainer.innerHTML = window.ComposerAI.renderMarkdown(markdownBody);
                }
            }
        }
        this.#bindEmailContentActions();
    }

    #renderAiCommandButtons() {
        const commands = [
            { name: 'summarize', label: 'Summarize', icon: '<path d="M9.813 3.063l.75 3.75m3.937-3.75l-.75 3.75m-6 .937l3.75.75m9.375 0l-3.75.75m-9.375 6l3.75-.75m9.375 0l-3.75-.75m-6 5.063l.75-3.75m3.937 3.75l-.75-3.75M12 18.75a6.75 6.75 0 100-13.5 6.75 6.75 0 000 13.5z" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>' },
            { name: 'translate', label: 'Translate', icon: '<path d="M10.5 21l5.25-11.25L21 21m-9-3h7.5M3 5.621a48.474 48.474 0 016-.371m0 0c1.12 0 2.233.038 3.334.114M9 5.25V3m3.334 2.364C11.176 10.658 7.69 15.08 3 17.502m9.334-12.138c.896.061 1.785.147 2.666.257m-4.589 8.495a18.023 18.023 0 01-3.827-5.802" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>' },
            { name: 'draft', label: 'Draft Reply', icon: '<path d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931zm0 0L19.5 7.125M18 14v4.75A2.25 2.25 0 0115.75 21H5.25A2.25 2.25 0 013 18.75V8.25A2.25 2.25 0 015.25 6H10" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>' },
            { name: 'compose', label: 'Compose', icon: '<path d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931zm0 0L19.5 7.125M18 14v4.75A2.25 2.25 0 0115.75 21H5.25A2.25 2.25 0 013 18.75V8.25A2.25 2.25 0 015.25 6H10" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>' },
            { name: 'tone', label: 'Adjust Tone', icon: '<path d="M12 18v-5.25m0 0a6.01 6.01 0 001.5-.189m-1.5.189a6.01 6.01 0 01-1.5-.189m3.75 7.478a12.06 12.06 0 01-4.5 0m3.75 2.383a14.406 14.406 0 01-3 0M14.25 18v-.192c0-.983.658-1.823 1.508-2.316a7.5 7.5 0 10-7.517 0c.85.493 1.509 1.333 1.509 2.316V18" stroke-linecap="round" stroke-linejoin="round"/>' }
        ];

        return commands.map(({ name, label, icon }) => `
            <button type="button" class="ai-command-btn" data-ai-command="${name}">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
                    ${icon}
                </svg>
                ${window.ComposerAI.escapeHtml(label)}
            </button>
        `).join('');
    }

    #bindEmailContentActions() {
        const replyButton = this.emailContent.querySelector('[data-action="reply"]');
        replyButton?.addEventListener('click', () => this.#openReplyWindow());

        this.emailContent.querySelectorAll('[data-ai-command]').forEach((button) => {
            button.addEventListener('click', () => {
                const command = button.getAttribute('data-ai-command');
                if (command) {
                    this.#triggerAiCommand(command);
                }
            });
        });
    }

    #toggleStar(emailId) {
        const email = this.emails.find((entry) => entry.id === emailId);
        if (!email) {
            return;
        }
        email.starred = !email.starred;
        this.#renderEmailList();
        if (this.selectedEmail?.id === emailId) {
            this.#renderEmailContent(email);
        }
        this.#updateBadges();
    }

    #updateBadges() {
        const unreadCount = this.emails.filter((email) => !email.read).length;
        const starredCount = this.emails.filter((email) => email.starred).length;
        const inboxBadge = document.getElementById('inboxBadge');
        const starredBadge = document.getElementById('starredBadge');
        if (inboxBadge) inboxBadge.textContent = unreadCount;
        if (starredBadge) starredBadge.textContent = starredCount;
    }

    #openComposeWindow() {
        this.composeManager.open({
            isReply: false,
            placeholder: 'Type your message...',
            metadata: { type: 'new' }
        });
    }

    #openReplyWindow() {
        if (!this.selectedEmail) {
            return;
        }
        const existing = this.composeManager.listWindows().find((windowState) => windowState.isReply && windowState.relatedEmailId === this.selectedEmail.id);
        if (existing) {
            existing.minimized = false;
            existing.element?.classList.remove('minimized');
            existing.element?.classList.add('active');
            existing.refs?.inputMessage?.focus();
            return existing;
        }

        return this.composeManager.open({
            isReply: true,
            relatedEmailId: this.selectedEmail.id,
            replyToName: this.selectedEmail.from,
            subject: `Re: ${this.selectedEmail.subject}`,
            rows: 6,
            placeholder: 'Type your reply...',
            metadata: { type: 'reply', emailId: this.selectedEmail.id }
        });
    }

    #handleComposeSend({ state, to, subject, message, attachments }) {
        if (!state) {
            return;
        }

        if (state.isReply) {
            if (subject && message) {
                console.log('Sending reply:', { subject, message, attachments });
                alert('Reply sent successfully!');
                this.composeManager.close(state.id);
            } else {
                alert('Please enter subject and message');
            }
        } else {
            if (to && subject && message) {
                console.log('Sending email:', { to, subject, message, attachments });
                alert('Email sent successfully!');
                this.composeManager.close(state.id);
            } else {
                alert('Please fill in all fields');
            }
        }
    }

    #handleComposeAi(windowId, command) {
        const windowData = this.composeManager.collectWindowData(windowId);
        if (!windowData) {
            return;
        }
        const { state, message, subject } = windowData;
        const instruction = this.#buildComposeInstruction(command, message, state.isReply);
        this.#setComposeButtonsDisabled(state, true);

        this.#callAiCommand(command, instruction, { contextId: this.selectedEmail?.contextId, subject })
            .then((response) => {
                const draftText = this.#extractPlainTextFromResponse(response);
                if (!draftText) {
                    alert('AI returned an empty response. Try again or adjust the request.');
                    return;
                }
                const parsed = this.#parseSubjectAndBody(draftText);
                if (!state.refs) {
                    return;
                }
                if (!state.isReply && state.refs.inputTo && !state.refs.inputTo.value) {
                    state.refs.inputTo.value = this.selectedEmail?.fromEmail || '';
                }
                if (parsed.subject && state.refs.inputSubject) {
                    state.refs.inputSubject.value = parsed.subject;
                }
                if (state.refs.inputMessage) {
                    state.refs.inputMessage.value = parsed.body;
                    state.refs.inputMessage.focus();
                    state.refs.inputMessage.setSelectionRange(state.refs.inputMessage.value.length, state.refs.inputMessage.value.length);
                }
            })
            .catch((error) => {
                console.error('Compose AI error:', error);
                alert(error?.message || 'Unable to complete request. Please try again.');
            })
            .finally(() => {
                this.#setComposeButtonsDisabled(state, false);
            });
    }

    #setComposeButtonsDisabled(state, disabled) {
        if (!state) {
            return;
        }
        const root = state.element || null;
        const buttons = root?.querySelectorAll?.('.ai-command-btn') || state.refs?.aiButtons || [];
        buttons.forEach((btn) => {
            btn.disabled = disabled;
            btn.setAttribute('aria-busy', String(disabled));
            btn.style.opacity = disabled ? '0.6' : '1';
        });
    }

    #triggerAiCommand(command) {
        if (!this.selectedEmail) {
            alert('Select an email first.');
            return;
        }
        const title = this.#getTitleForCommand(command);
        const instruction = this.#buildInstructionForCommand(command);
        this.#setMainCommandButtonsDisabled(true);

        // Only show inline AI response for non-compose commands
        const isDraftCommand = command === 'draft' || command === 'compose';
        if (!isDraftCommand) {
            this.#setAiResponseState({ status: 'loading', title });
        }

        this.#callAiCommand(command, instruction, { contextId: this.selectedEmail.contextId })
            .then((data) => {
                if (isDraftCommand) {
                    const draftText = this.#extractPlainTextFromResponse(data);
                    this.#applyAiDraftToCompose(draftText, title);
                } else {
                    const html = this.#extractHtmlFromResponse(data);
                    this.#setAiResponseState({ status: 'success', title, html });
                }
            })
            .catch((error) => {
                const message = error?.message || 'Unable to complete request.';
                if (isDraftCommand) {
                    alert(message);
                } else {
                    this.#setAiResponseState({ status: 'error', title, message });
                }
            })
            .finally(() => this.#setMainCommandButtonsDisabled(false));
    }

    #setMainCommandButtonsDisabled(disabled) {
        const buttons = this.emailContent?.querySelectorAll('.ai-command-btn');
        buttons?.forEach((btn) => {
            btn.disabled = disabled;
            btn.setAttribute('aria-busy', String(disabled));
            btn.style.opacity = disabled ? '0.6' : '1';
        });
    }

    #setAiResponseState({ status, title, html, message }) {
        if (!this.aiResponsePanel) {
            return;
        }
        this.aiResponsePanel.classList.remove('hidden');
        const titleEl = this.aiResponsePanel.querySelector('[data-role="ai-response-title"]');
        const bodyEl = this.aiResponsePanel.querySelector('[data-role="ai-response-body"]');
        if (titleEl) {
            titleEl.textContent = title || 'AI Assistant';
        }
        if (!bodyEl) {
            return;
        }
        if (status === 'loading') {
            bodyEl.innerHTML = '<div class="text-sm text-slate-500">Thinking...</div>';
        } else if (status === 'error') {
            bodyEl.innerHTML = `<div class="text-sm text-rose-600">${window.ComposerAI.escapeHtml(message || 'Something went wrong.')}</div>`;
        } else {
            bodyEl.innerHTML = html || '<div class="text-sm text-slate-500">No response received.</div>';
        }
    }

    #callAiCommand(command, instruction, { contextId, subject } = {}) {
        const payload = {
            message: instruction,
            conversationId: this.conversationId,
            maxResults: 5,
            thinkingEnabled: false,
            jsonOutput: false
        };

        if (contextId) {
            payload.contextId = contextId;
        }
        if (this.supportedCommands.has(command)) {
            payload.aiCommand = command;
        }
        if (subject && subject.trim().length > 0) {
            payload.subject = subject.trim();
        }

        if (this.selectedEmail) {
            const contextString = this.#buildEmailContextString(this.selectedEmail);
            if (contextString && contextString.length > 0) {
                payload.emailContext = contextString;
            }
        }

        return fetch('/api/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-UI-Request': this.uiNonce || ''
            },
            body: JSON.stringify(payload)
        }).then(async (response) => {
            let data = null;
            try {
                data = await response.json();
            } catch (error) {
                data = null;
            }
            if (!response.ok) {
                const errorMessage = data?.message || data?.error || `Request failed with status ${response.status}`;
                throw new Error(errorMessage);
            }
            if (data?.conversationId) {
                this.conversationId = data.conversationId;
            }
            return data;
        });
    }

    #buildEmailContextString(email) {
        if (!email) {
            return '';
        }
        const lines = [];
        lines.push('=== Email Metadata ===');
        lines.push(`Subject: ${email.subject || 'No subject'}`);
        lines.push(`From: ${email.from}${email.fromEmail ? ` <${email.fromEmail}>` : ''}`);
        if (email.to || email.toEmail) {
            lines.push(`To: ${(email.to || 'Unknown recipient')}${email.toEmail ? ` <${email.toEmail}>` : ''}`);
        }
        if (email.timestamp) {
            lines.push(`Email sent on: ${email.timestamp}`);
        }
        if (email.timestampIso) {
            lines.push(`Email sent (ISO): ${email.timestampIso}`);
        }
        lines.push('');
        lines.push('=== Email Body ===');
        const body = email.contentMarkdown?.trim() || email.contentText?.trim() || '';
        lines.push(body.length > 0 ? body : '(Email body is empty)');
        return lines.join('\n');
    }

    #buildInstructionForCommand(command) {
        return this.commandDefaults[command] || 'Assist with the selected email.';
    }

    #buildComposeInstruction(command, currentDraft, isReply) {
        if (command === 'draft') {
            if (currentDraft && currentDraft.length > 0) {
                return `Improve this ${isReply ? 'reply' : 'draft'} while preserving the intent:\n\n${currentDraft}`;
            }
            return isReply
                ? 'Draft a courteous reply addressing the key points from the email above.'
                : 'Draft a helpful email based on the selected context.';
        }
        if (command === 'compose') {
            return currentDraft && currentDraft.length > 0
                ? `Polish this email draft and make it clear and concise:\n\n${currentDraft}`
                : 'Compose a professional reply using the email context above.';
        }
        if (command === 'tone') {
            return currentDraft && currentDraft.length > 0
                ? `Adjust the tone of this email to be friendly but professional:\n\n${currentDraft}`
                : 'Adjust the email to a friendly but professional tone.';
        }
        return this.commandDefaults[command] || 'Assist with the selected email.';
    }

    #applyAiDraftToCompose(draftText, title) {
        if (!draftText) {
            alert('AI returned an empty draft. Try again or refine the request.');
            return;
        }
        const composeWindow = this.#openReplyWindow();
        if (!composeWindow) {
            alert('Unable to open compose window for draft reply.');
            return;
        }
        const parsed = this.#parseSubjectAndBody(draftText);
        if (composeWindow.refs?.inputSubject && parsed.subject) {
            composeWindow.refs.inputSubject.value = parsed.subject;
        }
        if (composeWindow.refs?.inputMessage) {
            composeWindow.refs.inputMessage.value = parsed.body;
            composeWindow.refs.inputMessage.focus();
            composeWindow.refs.inputMessage.setSelectionRange(composeWindow.refs.inputMessage.value.length, composeWindow.refs.inputMessage.value.length);
        }
        // Hide the AI response panel since draft is now in compose window
        if (this.aiResponsePanel) {
            this.aiResponsePanel.classList.add('hidden');
        }
    }

    #getTitleForCommand(command) {
        return FALLBACK_COMMAND_TITLES[command] || 'AI Assistant';
    }

    #parseSubjectAndBody(text) {
        if (!text || text.trim().length === 0) {
            return { subject: '', body: '' };
        }
        const trimmed = text.trim();
        const subjectMatch = trimmed.match(/^Subject:\s*(.+?)$/m);
        if (subjectMatch) {
            const subject = subjectMatch[1].trim();
            const subjectEndIndex = trimmed.indexOf(subjectMatch[0]) + subjectMatch[0].length;
            const body = trimmed.substring(subjectEndIndex).replace(/^\s*\n+/, '').trim();
            return { subject, body };
        }
        return { subject: '', body: trimmed };
    }

    #extractHtmlFromResponse(data) {
        if (!data) {
            return '<div class="text-sm text-slate-500">No response received.</div>';
        }
        if (data.sanitizedHtml && data.sanitizedHtml.trim().length > 0) {
            return data.sanitizedHtml;
        }
        if (data.response && data.response.trim().length > 0) {
            return window.ComposerAI.renderMarkdown(data.response);
        }
        return '<div class="text-sm text-slate-500">No response received.</div>';
    }

    #extractPlainTextFromResponse(data) {
        if (!data) {
            return '';
        }
        if (data.response && data.response.trim().length > 0) {
            return data.response.trim();
        }
        if (data.sanitizedHtml && data.sanitizedHtml.trim().length > 0) {
            const temp = document.createElement('div');
            temp.innerHTML = data.sanitizedHtml;
            return temp.textContent.trim();
        }
        return '';
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const bootstrap = window.__EMAIL_CLIENT_BOOTSTRAP__ || {};
    window.emailClient = new EmailClientApp({
        messages: bootstrap.messages,
        commandDefaults: bootstrap.commandDefaults || {},
        commandTemplates: bootstrap.commandTemplates || {},
        uiNonce: bootstrap.uiNonce || null
    });
});
