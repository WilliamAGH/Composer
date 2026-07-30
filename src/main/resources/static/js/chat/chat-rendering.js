export function applyMarkdownEnhancements(root) {
    if (!root) {
        return;
    }
    const blocks = root.classList?.contains('chat-markdown')
        ? [root]
        : Array.from(root.querySelectorAll('.chat-markdown'));

    blocks.forEach((block) => {
        block.querySelectorAll('table').forEach((table) => {
            table.classList.add('chat-table');
            if (!table.closest('.chat-table-wrapper')) {
                const wrapper = document.createElement('div');
                wrapper.className = 'chat-table-wrapper';
                table.parentNode?.insertBefore(wrapper, table);
                wrapper.appendChild(table);
            }
        });
        block.querySelectorAll('pre').forEach((pre) => pre.classList.add('chat-code-block'));
        block.querySelectorAll('code').forEach((code) => {
            if (code.parentElement?.tagName !== 'PRE') {
                code.classList.add('chat-inline-code');
            }
        });
        block.querySelectorAll('blockquote').forEach((quote) => quote.classList.add('chat-blockquote'));
    });
}

export function addMessage(chat, content, sender) {
    const message = document.createElement('div');
    if (sender === 'user') {
        message.className = 'message-user max-w-[60%] rounded-2xl bg-slate-900 text-white px-4 py-2.5 shadow-sm';
        message.textContent = content;
        const wrapper = document.createElement('div');
        wrapper.className = 'message-block w-full justify-end';
        wrapper.appendChild(message);
        chat.messages.appendChild(wrapper);
        chat.messages.scrollTop = chat.messages.scrollHeight;
        return message;
    }

    const surface = document.createElement('div');
    surface.className = 'assistant-surface';
    const markdownContainer = document.createElement('div');
    markdownContainer.className = 'chat-markdown';
    if (content?.trim()) {
        markdownContainer.innerHTML = window.Composer.renderMarkdown(content);
        applyMarkdownEnhancements(markdownContainer);
    }
    surface.appendChild(markdownContainer);
    const wrapper = document.createElement('div');
    wrapper.className = 'message-block w-full';
    wrapper.appendChild(surface);
    chat.messages.appendChild(wrapper);
    return markdownContainer;
}

export function scrollIfAtBottom(chat) {
    if (chat.messages.scrollTop + chat.messages.clientHeight >= chat.messages.scrollHeight - 50) {
        chat.messages.scrollTop = chat.messages.scrollHeight;
    }
}

export function localizeDate(element) {
    if (!element) {
        return;
    }
    const iso = element.getAttribute('data-date-iso');
    const original = element.getAttribute('data-original-label') || '';
    if (!iso) {
        if (!element.textContent?.trim()) {
            element.textContent = original;
        }
        return;
    }
    try {
        const localDate = new Date(iso);
        if (!Number.isNaN(localDate.getTime())) {
            element.textContent = localDate.toLocaleString(undefined, {
                dateStyle: 'medium',
                timeStyle: 'short'
            });
            element.setAttribute('title', 'Original: ' + original + '\nISO: ' + iso);
            return;
        }
    } catch (_) {
        // Keep the server-provided label when browser date parsing fails.
    }
    element.textContent = original;
}

export function addEmailContextMessage(chat, emailData) {
    const subject = window.Composer.escapeHtml(emailData.subject || 'No subject');
    const sender = window.Composer.escapeHtml(emailData.from || 'Unknown sender');
    const dateLabel = window.Composer.escapeHtml(emailData.date || 'Unknown date');
    const iso = (emailData.dateIso || '').trim();
    const previewMarkdown = (emailData.parsedMarkdown || emailData.parsedPlain || '').trim();
    const previewId = 'email-preview-' + (++chat.previewCounter);
    const previewHtml = previewMarkdown.length > 0
        ? window.Composer.renderMarkdown(previewMarkdown)
        : '<p class="text-xs italic text-slate-500">Email preview unavailable.</p>';

    const toggleWrapper = document.createElement('div');
    toggleWrapper.className = 'overflow-hidden rounded-2xl border border-blue-200/60 bg-blue-50/40';
    toggleWrapper.innerHTML =
        '<div class="flex items-center gap-2 px-3.5 py-2.5">' +
        '<button type="button" class="flex flex-1 items-center gap-3 rounded-xl px-2 py-1.5 text-left transition hover:bg-blue-100/70 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-200" data-role="preview-toggle" aria-expanded="false" aria-controls="' + previewId + '">' +
        '<span class="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-xl bg-blue-100 text-blue-700">' +
        '<svg class="h-5 w-5" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M2.25 6.75A3.75 3.75 0 016 3h12a3.75 3.75 0 013.75 3.75v10.5A3.75 3.75 0 0118 21H6a3.75 3.75 0 01-3.75-3.75V6.75zm3.82-.456a.75.75 0 00-.82 1.26l6 3.9a.75.75 0 00.82 0l6-3.9a.75.75 0 00-.82-1.26L12 9.694 6.07 6.294z"/></svg>' +
        '</span>' +
        '<span class="flex-1 truncate text-sm font-semibold text-slate-800">' + subject + '</span>' +
        '<span class="ml-auto flex flex-shrink-0 items-center gap-2">' +
        '<span class="rounded-md bg-white/70 px-2 py-1 text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-600" data-role="email-date" data-original-label="' + dateLabel + '" ' + (iso ? 'data-date-iso="' + window.Composer.escapeHtml(iso) + '"' : '') + '>' + dateLabel + '</span>' +
        '<svg class="h-4 w-4 text-slate-500 transition-transform" data-role="preview-chevron" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 10.94l3.71-3.71a.75.75 0 111.06 1.06l-4.24 4.24a.75.75 0 01-1.06 0L5.21 8.29a.75.75 0 01.02-1.08z" clip-rule="evenodd"/></svg>' +
        '</span>' +
        '</button>' +
        '<button type="button" data-role="quick-insights" class="group flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-violet-500 to-indigo-600 text-white shadow-sm transition-all hover:shadow-md hover:scale-110 focus:outline-none focus-visible:ring-2 focus-visible:ring-violet-300" title="Get AI insights about this email" aria-label="Analyze email with AI">' +
        '<svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">' +
        '<path d="M9.813 3.063l.75 3.75m3.937-3.75l-.75 3.75m-6 .937l3.75.75m9.375 0l-3.75.75m-9.375 6l3.75-.75m9.375 0l-3.75-.75m-6 5.063l.75-3.75m3.937 3.75l-.75-3.75M12 18.75a6.75 6.75 0 100-13.5 6.75 6.75 0 000 13.5z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>' +
        '</svg>' +
        '</button>' +
        '</div>' +
        '<div id="' + previewId + '" data-role="preview-body" class="hidden border-t border-blue-100/70 bg-white/90 px-3.5 py-3">' +
        '<div class="chat-markdown"><p><strong>From:</strong> ' + sender + '</p>' +
        '<div class="chat-markdown">' + previewHtml + '</div></div></div>';

    const toggleButton = toggleWrapper.querySelector('[data-role="preview-toggle"]');
    const previewBody = toggleWrapper.querySelector('[data-role="preview-body"]');
    const previewChevron = toggleWrapper.querySelector('[data-role="preview-chevron"]');
    toggleButton.addEventListener('click', () => {
        const nextExpanded = toggleButton.getAttribute('aria-expanded') !== 'true';
        toggleButton.setAttribute('aria-expanded', String(nextExpanded));
        previewBody.classList.toggle('hidden', !nextExpanded);
        previewChevron.style.transform = nextExpanded ? 'rotate(180deg)' : 'rotate(0)';
    });
    toggleWrapper.querySelector('[data-role="quick-insights"]').addEventListener('click', (event) => {
        event.stopPropagation();
        chat.triggerInsights();
    });

    const wrapper = document.createElement('div');
    wrapper.className = 'message-block w-full';
    const surface = document.createElement('div');
    surface.className = 'assistant-surface';
    surface.appendChild(toggleWrapper);
    wrapper.appendChild(surface);
    chat.messages.appendChild(wrapper);
    localizeDate(toggleWrapper.querySelector('[data-role="email-date"]'));
    applyMarkdownEnhancements(surface);
    chat.messages.scrollTop = chat.messages.scrollHeight;
}
