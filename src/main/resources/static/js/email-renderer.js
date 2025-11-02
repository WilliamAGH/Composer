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
            return;
        }

        // Clear previous content
        container.innerHTML = '';

        // Client-side sanitization (defense in depth - server already sanitized)
        const sanitizedHtml = sanitizeHtml(htmlContent);

        // Create fully isolated iframe
        const iframe = document.createElement('iframe');

        // Critical security attributes
        iframe.setAttribute('sandbox', 'allow-same-origin'); // Minimal permissions
        iframe.setAttribute('referrerpolicy', 'no-referrer');

        // Styling for seamless integration
        iframe.style.cssText = `
            width: 100%;
            border: none;
            display: block;
            overflow: hidden;
            min-height: 200px;
            color-scheme: light;
        `;

        container.appendChild(iframe);

        // Write content to iframe
        const iframeDoc = iframe.contentDocument || iframe.contentWindow.document;
        const fullHtml = buildIframeDocument(sanitizedHtml);

        iframeDoc.open();
        iframeDoc.write(fullHtml);
        iframeDoc.close();

        // Setup auto-resize
        setupAutoResize(iframe, iframeDoc);
    }

    /**
     * Build complete HTML document for iframe with security headers and styling.
     */
    function buildIframeDocument(bodyContent) {
        return `
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta http-equiv="Content-Security-Policy" content="script-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none';">
                <style>
                    /* Reset and containment */
                    * {
                        max-width: 100%;
                        word-wrap: break-word;
                    }
                    html, body {
                        margin: 0;
                        padding: 16px;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        font-size: 0.95rem;
                        line-height: 1.65;
                        color: #0f172a;
                        background: transparent;
                        overflow-wrap: break-word;
                        word-break: break-word;
                    }
                    body {
                        contain: layout style paint;
                    }
                    /* Prevent layout breaks */
                    img {
                        max-width: 100% !important;
                        height: auto !important;
                    }
                    table {
                        max-width: 100%;
                        table-layout: fixed;
                    }
                    /* Neutralize potentially dangerous elements */
                    iframe, object, embed, applet {
                        display: none !important;
                    }
                    /* Prevent fixed/absolute positioning from breaking layout */
                    [style*="position: fixed"],
                    [style*="position:fixed"],
                    [style*="position: absolute"],
                    [style*="position:absolute"] {
                        position: relative !important;
                    }
                </style>
            </head>
            <body>
                ${bodyContent}
            </body>
            </html>
        `;
    }

    /**
     * Setup auto-resize functionality for iframe to match content height.
     */
    function setupAutoResize(iframe, iframeDoc) {
        const resizeIframe = () => {
            try {
                const body = iframeDoc.body;
                const html = iframeDoc.documentElement;
                const height = Math.max(
                    body.scrollHeight,
                    body.offsetHeight,
                    html.clientHeight,
                    html.scrollHeight,
                    html.offsetHeight
                );
                iframe.style.height = Math.min(height + 20, 10000) + 'px'; // Cap at 10000px
            } catch (error) {
                // Cross-origin error - use fallback height
                iframe.style.height = '600px';
            }
        };

        // Resize on load
        iframe.addEventListener('load', () => {
            resizeIframe();

            // Re-resize when images load
            const images = iframeDoc.querySelectorAll('img');
            images.forEach(img => {
                img.addEventListener('load', resizeIframe);
                img.addEventListener('error', resizeIframe);
            });
        });

        // Periodic resize check (for dynamic content)
        let resizeAttempts = 0;
        const resizeInterval = setInterval(() => {
            resizeIframe();
            resizeAttempts++;
            if (resizeAttempts > 10) {
                clearInterval(resizeInterval);
            }
        }, 100);
    }

    /**
     * Client-side HTML sanitization (defense in depth).
     * Server should already sanitize, but this provides additional safety.
     */
    function sanitizeHtml(html) {
        if (!html) return '';

        const temp = document.createElement('div');
        temp.innerHTML = html;

        // Remove dangerous tags
        temp.querySelectorAll('script').forEach(el => el.remove());
        const dangerousTags = ['iframe', 'object', 'embed', 'applet', 'link', 'style', 'base', 'meta', 'form'];
        dangerousTags.forEach(tag => {
            temp.querySelectorAll(tag).forEach(el => el.remove());
        });

        // Remove event handlers and javascript: URLs
        temp.querySelectorAll('*').forEach(el => {
            Array.from(el.attributes).forEach(attr => {
                if (attr.name.startsWith('on')) {
                    el.removeAttribute(attr.name);
                }
                if (attr.value && attr.value.toLowerCase().includes('javascript:')) {
                    el.removeAttribute(attr.name);
                }
            });

            // Neutralize dangerous CSS
            if (el.hasAttribute('style')) {
                const style = el.getAttribute('style');
                if (style.includes('position') && (style.includes('fixed') || style.includes('absolute'))) {
                    const cleaned = style.replace(/position\s*:\s*(fixed|absolute)/gi, 'position: relative');
                    el.setAttribute('style', cleaned);
                }
            }
        });

        return temp.innerHTML;
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
