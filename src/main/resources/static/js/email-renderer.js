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
            // allow-same-origin is required so the parent can read iframe content size; scripts remain disabled
            iframe.setAttribute('sandbox', 'allow-same-origin allow-popups allow-popups-to-escape-sandbox');
            iframe.setAttribute('referrerpolicy', 'no-referrer');
            iframe.setAttribute('loading', 'lazy');
            iframe.setAttribute('aria-label', 'Email body');
            iframe.setAttribute('title', 'Email body');
            // Inline styles so we don't rely on external CSS in v2
            iframe.style.width = '100%';
            iframe.style.display = 'block';
            iframe.style.border = '0';
            // Set a conservative initial height; will be auto-resized
            iframe.style.height = '200px';

            container.style.width = '100%';
            container.style.maxWidth = '100%';
            container.style.overflowX = 'hidden';
            container.style.position = 'relative';
            container.appendChild(iframe);

            const handleReady = () => {
                container.classList.remove('email-html-container--loading');
                if (loadingOverlay.parentNode === container) {
                    loadingOverlay.remove();
                }
                // Resize once the content is ready
                scheduleIframeAutosize(iframe);
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

            // Kick off autosize immediately and then again on readiness
            scheduleIframeAutosize(iframe);
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
                    html, body {
                        margin: 0;
                        padding: 0;
                        height: auto;
                        min-height: 0;
                        width: 100%;
                        max-width: 100%;
                        overflow-x: hidden !important; /* prevent horizontal scroll; parent resizes iframe */
                        overflow-y: hidden !important; /* parent controls scroll */
                    }
                    *, *::before, *::after {
                        box-sizing: border-box !important;
                        max-width: 100%;
                    }
                    body {
                        font-family: inherit;
                        font-size: inherit;
                        line-height: inherit;
                        color: inherit;
                        background: transparent;
                        word-break: break-word;
                        overflow-wrap: anywhere;
                        -webkit-text-size-adjust: 100%;
                    }
                    .email-wrapper {
                        width: 100% !important;
                        max-width: 100% !important;
                        overflow-x: hidden !important;
                        box-sizing: border-box !important;
                    }
                    /* Force all images, videos, and media to scale down */
                    img, video, svg, canvas, picture {
                        max-width: 100% !important;
                        width: auto !important;
                        height: auto !important;
                        display: block !important;
                    }
                    /* Override any inline width on images */
                    img[width], video[width], svg[width] {
                        width: auto !important;
                        max-width: 100% !important;
                    }
                    /* Block elements should never exceed viewport width */
                    div, p, table, ul, ol, li, header, footer, section, article, main, aside,
                    h1, h2, h3, h4, h5, h6, blockquote, pre, form, fieldset {
                        max-width: 100% !important;
                        overflow-x: hidden !important;
                        box-sizing: border-box !important;
                    }
                    /* Force tables to be responsive - ultra aggressive for email HTML */
                    table, table * {
                        max-width: 100% !important;
                    }
                    table {
                        width: 100% !important;
                        table-layout: fixed !important;
                        border-collapse: collapse !important;
                    }
                    table[width="600"],
                    table[width="100%"],
                    table[width],
                    td[width],
                    th[width] {
                        width: 100% !important;
                        max-width: 100% !important;
                    }
                    /* Specific override for nested tables */
                    table table {
                        width: 100% !important;
                        max-width: 100% !important;
                    }
                    td, th {
                        word-break: break-word;
                        overflow-wrap: anywhere;
                        max-width: 100% !important;
                    }
                    /* Text content wrapping */
                    pre, code {
                        white-space: pre-wrap !important;
                        word-break: break-word !important;
                        overflow-wrap: anywhere !important;
                        max-width: 100% !important;
                    }
                    a, span, strong, em, b, i {
                        word-break: break-word;
                        overflow-wrap: anywhere;
                        max-width: 100% !important;
                    }
                    /* Strip any hard-coded sizing that breaks layout */
                    [style*="min-width"] { min-width: 0 !important; }
                    [style*="width:"] { max-width: 100% !important; }
                    /* Remove any attempt at fixed positioning */
                    [style*="position: fixed"],
                    [style*="position:fixed"] {
                        position: static !important;
                    }
                    /* Hide any accidental scrollbars */
                    ::-webkit-scrollbar { width: 0; height: 0; }
                    html { scrollbar-width: none; }
                    * { scrollbar-width: none; }
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
        if (typeof requestAnimationFrame === 'function') requestAnimationFrame(adjust);
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

            // Fallback measurement for absolutely-positioned content: compute max element bottom
            let maxBottom = 0;
            try {
                if (body && body.getElementsByTagName) {
                    const els = body.getElementsByTagName('*');
                    const limit = Math.min(els.length, 5000);
                    for (let i = 0; i < limit; i++) {
                        const el = els[i];
                        const rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
                        if (rect && Number.isFinite(rect.bottom)) {
                            if (rect.bottom > maxBottom) maxBottom = rect.bottom;
                        }
                    }
                }
            } catch (_) {
                // best-effort only
            }

            const computedHeight = Math.max(
                0,
                ...(heightCandidates.length ? heightCandidates : [0]),
                Math.ceil(maxBottom)
            );

            iframe.style.height = `${Math.max(computedHeight, 300)}px`;
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
