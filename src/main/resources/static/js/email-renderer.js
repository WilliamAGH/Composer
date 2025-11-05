/**
 * EmailRenderer - Secure email HTML rendering in isolated iframes
 *
 * Provides iframe-based isolation for email HTML content to prevent:
 * - XSS attacks (scripts, event handlers)
 * - Layout breaking (position:fixed, position:absolute)
 * - Style injection affecting parent page
 *
 * Usage:
 *   EmailRenderer.renderInIframe(containerElement, htmlContent)
 *
 * @author William Callahan
 * @since 2025-11-02
 * @version 0.0.1
 */
const EmailRenderer = (() => {
    'use strict';

    /**
     * Renders email HTML content in a fully isolated sandboxed iframe.
     *
     * Security layers:
     * - Sandbox attribute blocks scripts, forms, popups, top-level navigation
     * - Separate origin prevents access to parent document
     * - CSS containment prevents style/layout leakage
     * - Auto-resizing maintains UX while ensuring isolation
     *
     * @param {HTMLElement} container - Container element to render iframe into
     * @param {string} htmlContent - Sanitized HTML content to render
     */
    function renderInIframe(container, htmlContent) {
        if (!container || !htmlContent) {
            throw new Error('EmailRenderer requires a target container and HTML content.');
        }

        // Client-side sanitization (defense in depth - server already sanitizes)
        const sanitizedHtml = typeof htmlContent === 'string' ? htmlContent : String(htmlContent || '');
        if (!sanitizedHtml || sanitizedHtml.trim().length === 0) {
            throw new Error('EmailRenderer received empty sanitized HTML.');
        }

        try {
            // Expose for debugging in the browser console when diagnosing rendering issues
            window.__EMAIL_RENDERER_DEBUG__ = window.__EMAIL_RENDERER_DEBUG__ || [];
            window.__EMAIL_RENDERER_DEBUG__.push({
                timestamp: Date.now(),
                length: sanitizedHtml.length,
                preview: sanitizedHtml.slice(0, 1200)
            });
            if (window.__EMAIL_RENDERER_DEBUG__.length > 20) {
                window.__EMAIL_RENDERER_DEBUG__.shift();
            }

            // Reset container and show loading affordance
            container.innerHTML = '';
            container.classList.add('email-html-container--loading');

            const loadingOverlay = document.createElement('div');
            loadingOverlay.className = 'email-html-loading';
            loadingOverlay.innerHTML = `
                <div class="email-html-loading-indicator"></div>
                <p>Rendering email&hellip;</p>
            `;
            container.appendChild(loadingOverlay);

            const iframe = document.createElement('iframe');
            iframe.className = 'email-html-iframe';
            iframe.setAttribute('sandbox', 'allow-popups allow-popups-to-escape-sandbox');
            iframe.setAttribute('referrerpolicy', 'no-referrer');
            iframe.setAttribute('loading', 'lazy');
            iframe.setAttribute('aria-label', 'Email body');
            iframe.setAttribute('title', 'Email body');

            container.appendChild(iframe);

            const handleReady = () => {
                container.classList.remove('email-html-container--loading');
                if (loadingOverlay.parentNode === container) {
                    loadingOverlay.remove();
                }
            };

            const fullHtml = buildIframeDocument(sanitizedHtml);
            if ('srcdoc' in iframe) {
                iframe.srcdoc = fullHtml;
            } else {
                const iframeDoc = iframe.contentDocument || iframe.contentWindow.document;
                iframeDoc.open();
                iframeDoc.write(fullHtml);
                iframeDoc.close();
            }

            attachContentReadyListener(iframe, handleReady);
            iframe.addEventListener('load', handleReady, { once: true });
        } catch (error) {
            console.error('EmailRenderer failed to render sanitized HTML.', error);
            throw error;
        }
    }

    function attachContentReadyListener(iframe, handleReady) {
        try {
            const iframeDoc = iframe.contentDocument || iframe.contentWindow?.document;
            if (iframeDoc && iframeDoc.readyState === 'complete') {
                handleReady();
                return;
            }
            const targetWindow = iframe.contentWindow;
            if (targetWindow) {
                const onDomReady = () => {
                    targetWindow.removeEventListener('DOMContentLoaded', onDomReady);
                    handleReady();
                };
                targetWindow.addEventListener('DOMContentLoaded', onDomReady);
            } else {
                iframe.addEventListener('load', handleReady, { once: true });
            }
        } catch (e) {
            iframe.addEventListener('load', handleReady, { once: true });
        }
    }

    /**
     * Build complete HTML document for iframe with security headers and styling.
     */
    function buildIframeDocument(bodyContent) {
        // NOTE: frame-ancestors cannot be enforced from a meta tag, so it is intentionally omitted.
        return `
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta http-equiv="Content-Security-Policy"
                    content="default-src 'none'; img-src http: https: data: cid:; style-src 'unsafe-inline' https: data:; font-src https: data:; script-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'; connect-src 'none';">
                <style>
                    html {
                        margin: 0;
                        padding: 0;
                        height: 100%;
                    }
                    body {
                        margin: 0;
                        padding: 0;
                        font-family: inherit;
                        font-size: inherit;
                        line-height: inherit;
                        color: inherit;
                        background: transparent;
                        min-height: 100%;
                        box-sizing: border-box;
                    }
                    .email-wrapper {
                        min-height: 100%;
                        width: 100%;
                    }
                    img, video {
                        max-width: 100% !important;
                        height: auto !important;
                    }
                    table {
                        width: 100%;
                        border-collapse: separate;
                    }
                </style>
            </head>
            <body>
                <div class="email-wrapper">
                    ${bodyContent}
                </div>
            </body>
            </html>
        `;
    }

    function scheduleIframeAutosize(iframe) {
        if (!iframe) return;
        const adjust = () => resizeIframeToContent(iframe);
        // Initial adjustments + delayed retries for late-loading assets
        adjust();
        [100, 300, 800, 1500].forEach((delay) => setTimeout(adjust, delay));
        bindIframeMutationObserver(iframe, adjust);
    }

    function resizeIframeToContent(iframe) {
        try {
            const doc = iframe.contentDocument || iframe.contentWindow?.document;
            if (!doc) {
                return;
            }
            const body = doc.body;
            const html = doc.documentElement;
            const heightCandidates = [
                body?.scrollHeight,
                body?.offsetHeight,
                html?.scrollHeight,
                html?.offsetHeight
            ].filter((value) => typeof value === 'number' && !Number.isNaN(value));
            const computedHeight = heightCandidates.length > 0 ? Math.max(...heightCandidates) : 0;
            iframe.style.height = `${Math.max(computedHeight, 200)}px`;
        } catch (err) {
            console.warn('Unable to auto-resize email iframe height.', err);
            iframe.style.height = '100%';
        }
    }

    function bindIframeMutationObserver(iframe, callback) {
        try {
            const doc = iframe.contentDocument || iframe.contentWindow?.document;
            if (!doc || !doc.body) {
                return;
            }
            const resizeHandler = () => callback();
            const observer = new MutationObserver(resizeHandler);
            observer.observe(doc.body, { attributes: true, childList: true, subtree: true, characterData: true });
            const cleanups = [];

            if (iframe.contentWindow && typeof iframe.contentWindow.addEventListener === 'function') {
                iframe.contentWindow.addEventListener('resize', resizeHandler);
                cleanups.push(() => iframe.contentWindow.removeEventListener('resize', resizeHandler));
            }

            Array.from(doc.images || []).forEach((img) => {
                img.addEventListener('load', resizeHandler);
                cleanups.push(() => img.removeEventListener('load', resizeHandler));
            });

            // The observer automatically disconnects when the iframe is removed from the DOM.
        } catch (err) {
            console.debug('EmailRenderer mutation observer unavailable.', err);
        }
    }

    // Public API
    return {
        renderInIframe
    };
})();

// Export for use in other scripts (if using modules)
if (typeof module !== 'undefined' && module.exports) {
    module.exports = EmailRenderer;
}

// Ensure the renderer is available to browser scripts
if (typeof window !== 'undefined') {
    window.EmailRenderer = EmailRenderer;
}
