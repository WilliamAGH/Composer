/**
 * Email List Component - shadcn/ui inspired
 * Vanilla JavaScript component for displaying emails with dropdown menus
 * Matches the ComposerAI UI design
 */

class EmailListComponent {
    constructor(containerId, options = {}) {
        this.container = document.getElementById(containerId);
        if (!this.container) {
            console.error(`Container with id "${containerId}" not found`);
            return;
        }
        
        this.emails = [];
        this.filteredEmails = [];
        this.expandedEmailId = null;
        this.activeDropdown = null;
        
        // Options
        this.options = {
            showFilter: options.showFilter !== false,
            onEmailClick: options.onEmailClick || null,
            onDropdownAction: options.onDropdownAction || null,
            ...options
        };
        
        this.init();
    }
    
    init() {
        this.render();
        this.attachGlobalListeners();
    }
    
    /**
     * Load emails into the component
     * @param {Array} emails - Array of email objects
     */
    loadEmails(emails) {
        this.emails = emails;
        this.filteredEmails = [...emails];
        this.renderEmailList();
    }
    
    /**
     * Render the initial container structure
     */
    render() {
        this.container.innerHTML = `
            <div class="email-list-container">
                ${this.options.showFilter ? this.renderFilterSection() : ''}
                <div class="email-list" id="${this.container.id}-list">
                    ${this.renderLoading()}
                </div>
            </div>
        `;
        
        if (this.options.showFilter) {
            const filterInput = this.container.querySelector('.email-filter-input');
            filterInput.addEventListener('input', (e) => this.handleFilter(e.target.value));
        }
    }
    
    /**
     * Render filter section
     */
    renderFilterSection() {
        return `
            <div class="email-filter-section">
                <input 
                    type="text" 
                    class="email-filter-input" 
                    placeholder="Filter emails by subject, sender..." 
                />
            </div>
        `;
    }
    
    /**
     * Render email list
     */
    renderEmailList() {
        const listContainer = document.getElementById(`${this.container.id}-list`);
        
        if (this.filteredEmails.length === 0) {
            listContainer.innerHTML = this.renderEmpty();
            return;
        }
        
        listContainer.innerHTML = this.filteredEmails.map(email => 
            this.renderEmailCard(email)
        ).join('');
        
        this.attachEmailListeners();
    }
    
    /**
     * Render individual email card
     */
    renderEmailCard(email) {
        const isExpanded = this.expandedEmailId === email.emailId;
        const unreadClass = email.unread ? 'unread' : '';
        const expandedClass = isExpanded ? 'expanded' : '';
        
        return `
            <div class="email-card ${unreadClass} ${expandedClass}" data-email-id="${email.emailId}">
                <div class="email-icon">
                    📧
                </div>
                
                <div class="email-content">
                    <div class="email-subject">${this.escapeHtml(email.subject || 'No Subject')}</div>
                    <div class="email-meta">
                        <span class="email-sender">${this.escapeHtml(email.sender || 'Unknown Sender')}</span>
                        ${email.relevanceScore ? this.renderRelevanceBadge(email.relevanceScore) : ''}
                    </div>
                    ${!isExpanded && email.snippet ? `<div class="email-snippet">${this.escapeHtml(email.snippet)}</div>` : ''}
                    
                    ${isExpanded ? this.renderExpandedContent(email) : ''}
                </div>
                
                <div class="email-date">
                    ${this.formatDate(email.emailDate)}
                    <button 
                        class="email-dropdown-trigger" 
                        data-email-id="${email.emailId}"
                        aria-label="Email actions"
                        type="button"
                    >
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                            <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" clip-rule="evenodd" />
                        </svg>
                    </button>
                </div>
                
                ${this.renderDropdownMenu(email.emailId)}
            </div>
        `;
    }
    
    /**
     * Render expanded email content
     */
    renderExpandedContent(email) {
        return `
            <div class="email-expanded-content">
                <div class="email-from">
                    <strong>From:</strong> ${this.escapeHtml(email.sender || 'Unknown')} ${email.senderEmail ? `&lt;${this.escapeHtml(email.senderEmail)}&gt;` : ''}
                </div>
                <div class="email-body">
                    ${this.escapeHtml(email.body || email.snippet || 'No content available')}
                </div>
            </div>
        `;
    }
    
    /**
     * Render dropdown menu
     */
    renderDropdownMenu(emailId) {
        return `
            <div class="email-dropdown-menu" data-dropdown-for="${emailId}">
                <div class="email-dropdown-label">Actions</div>
                <div class="email-dropdown-item" data-action="view" data-email-id="${emailId}">
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
                        <path d="M10 12.5a2.5 2.5 0 100-5 2.5 2.5 0 000 5z" />
                        <path fill-rule="evenodd" d="M.664 10.59a1.651 1.651 0 010-1.186A10.004 10.004 0 0110 3c4.257 0 7.893 2.66 9.336 6.41.147.381.146.804 0 1.186A10.004 10.004 0 0110 17c-4.257 0-7.893-2.66-9.336-6.41zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clip-rule="evenodd" />
                    </svg>
                    View Full Email
                </div>
                <div class="email-dropdown-item" data-action="reply" data-email-id="${emailId}">
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
                        <path fill-rule="evenodd" d="M7.793 2.232a.75.75 0 01-.025 1.06L3.622 7.25h10.003a5.375 5.375 0 010 10.75H10.75a.75.75 0 010-1.5h2.875a3.875 3.875 0 000-7.75H3.622l4.146 3.957a.75.75 0 01-1.036 1.085l-5.5-5.25a.75.75 0 010-1.085l5.5-5.25a.75.75 0 011.06.025z" clip-rule="evenodd" />
                    </svg>
                    Reply
                </div>
                <div class="email-dropdown-item" data-action="forward" data-email-id="${emailId}">
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
                        <path fill-rule="evenodd" d="M12.207 2.232a.75.75 0 00.025 1.06l4.146 3.958H6.375a5.375 5.375 0 000 10.75H9.25a.75.75 0 000-1.5H6.375a3.875 3.875 0 010-7.75h10.003l-4.146 3.957a.75.75 0 001.036 1.085l5.5-5.25a.75.75 0 000-1.085l-5.5-5.25a.75.75 0 00-1.06.025z" clip-rule="evenodd" />
                    </svg>
                    Forward
                </div>
                <div class="email-dropdown-separator"></div>
                <div class="email-dropdown-item" data-action="archive" data-email-id="${emailId}">
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
                        <path d="M2 3a1 1 0 00-1 1v1a1 1 0 001 1h16a1 1 0 001-1V4a1 1 0 00-1-1H2z" />
                        <path fill-rule="evenodd" d="M2 7.5h16l-.811 7.71a2 2 0 01-1.99 1.79H4.802a2 2 0 01-1.99-1.79L2 7.5zM7.5 10.5a.5.5 0 01.5-.5h4a.5.5 0 010 1H8a.5.5 0 01-.5-.5z" clip-rule="evenodd" />
                    </svg>
                    Archive
                </div>
                <div class="email-dropdown-item" data-action="delete" data-email-id="${emailId}">
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 20 20" fill="currentColor">
                        <path fill-rule="evenodd" d="M8.75 1A2.75 2.75 0 006 3.75v.443c-.795.077-1.584.176-2.365.298a.75.75 0 10.23 1.482l.149-.022.841 10.518A2.75 2.75 0 007.596 19h4.807a2.75 2.75 0 002.742-2.53l.841-10.52.149.023a.75.75 0 00.23-1.482A41.03 41.03 0 0014 4.193V3.75A2.75 2.75 0 0011.25 1h-2.5zM10 4c.84 0 1.673.025 2.5.075V3.75c0-.69-.56-1.25-1.25-1.25h-2.5c-.69 0-1.25.56-1.25 1.25v.325C8.327 4.025 9.16 4 10 4zM8.58 7.72a.75.75 0 00-1.5.06l.3 7.5a.75.75 0 101.5-.06l-.3-7.5zm4.34.06a.75.75 0 10-1.5-.06l-.3 7.5a.75.75 0 101.5.06l.3-7.5z" clip-rule="evenodd" />
                    </svg>
                    Delete
                </div>
            </div>
        `;
    }
    
    /**
     * Render relevance badge
     */
    renderRelevanceBadge(score) {
        let badgeClass = 'email-badge-low';
        let label = 'Low Match';
        
        if (score >= 0.8) {
            badgeClass = 'email-badge-high';
            label = 'High Match';
        } else if (score >= 0.5) {
            badgeClass = 'email-badge-medium';
            label = 'Good Match';
        }
        
        return `<span class="email-badge ${badgeClass}">${label}</span>`;
    }
    
    /**
     * Render loading state
     */
    renderLoading() {
        return `
            <div class="email-list-loading">
                <div class="loading-spinner"></div>
            </div>
        `;
    }
    
    /**
     * Render empty state
     */
    renderEmpty() {
        return `
            <div class="email-list-empty">
                <div class="email-list-empty-icon">📭</div>
                <div class="email-list-empty-text">No emails to display</div>
            </div>
        `;
    }
    
    /**
     * Attach event listeners to email cards
     */
    attachEmailListeners() {
        // Email card click to expand/collapse
        const emailCards = this.container.querySelectorAll('.email-card');
        emailCards.forEach(card => {
            card.addEventListener('click', (e) => {
                // Don't expand if clicking on dropdown trigger or dropdown menu
                if (e.target.closest('.email-dropdown-trigger') || e.target.closest('.email-dropdown-menu')) {
                    return;
                }
                
                const emailId = card.dataset.emailId;
                this.toggleExpand(emailId);
            });
        });
        
        // Dropdown trigger buttons
        const dropdownTriggers = this.container.querySelectorAll('.email-dropdown-trigger');
        dropdownTriggers.forEach(trigger => {
            trigger.addEventListener('click', (e) => {
                e.stopPropagation();
                const emailId = trigger.dataset.emailId;
                this.toggleDropdown(emailId);
            });
        });
        
        // Dropdown menu items
        const dropdownItems = this.container.querySelectorAll('.email-dropdown-item');
        dropdownItems.forEach(item => {
            item.addEventListener('click', (e) => {
                e.stopPropagation();
                const emailId = item.dataset.emailId;
                const action = item.dataset.action;
                this.handleDropdownAction(action, emailId);
                this.closeDropdown();
            });
        });
    }
    
    /**
     * Attach global event listeners
     */
    attachGlobalListeners() {
        // Close dropdown when clicking outside
        document.addEventListener('click', (e) => {
            if (!e.target.closest('.email-dropdown-trigger') && !e.target.closest('.email-dropdown-menu')) {
                this.closeDropdown();
            }
        });
    }
    
    /**
     * Toggle email card expansion
     */
    toggleExpand(emailId) {
        if (this.expandedEmailId === emailId) {
            this.expandedEmailId = null;
        } else {
            this.expandedEmailId = emailId;
        }
        
        this.renderEmailList();
        
        if (this.options.onEmailClick) {
            const email = this.emails.find(e => e.emailId === emailId);
            this.options.onEmailClick(email);
        }
    }
    
    /**
     * Toggle dropdown menu
     */
    toggleDropdown(emailId) {
        const dropdown = this.container.querySelector(`[data-dropdown-for="${emailId}"]`);
        
        if (this.activeDropdown && this.activeDropdown !== dropdown) {
            this.activeDropdown.classList.remove('show');
        }
        
        if (dropdown) {
            dropdown.classList.toggle('show');
            this.activeDropdown = dropdown.classList.contains('show') ? dropdown : null;
        }
    }
    
    /**
     * Close active dropdown
     */
    closeDropdown() {
        if (this.activeDropdown) {
            this.activeDropdown.classList.remove('show');
            this.activeDropdown = null;
        }
    }
    
    /**
     * Handle dropdown action
     */
    handleDropdownAction(action, emailId) {
        const email = this.emails.find(e => e.emailId === emailId);
        
        if (this.options.onDropdownAction) {
            this.options.onDropdownAction(action, email);
        } else {
            console.log(`Action "${action}" for email:`, email);
        }
    }
    
    /**
     * Handle filter input
     */
    handleFilter(query) {
        const lowerQuery = query.toLowerCase();
        this.filteredEmails = this.emails.filter(email => {
            return (
                (email.subject && email.subject.toLowerCase().includes(lowerQuery)) ||
                (email.sender && email.sender.toLowerCase().includes(lowerQuery)) ||
                (email.snippet && email.snippet.toLowerCase().includes(lowerQuery))
            );
        });
        this.renderEmailList();
    }
    
    /**
     * Format date for display
     */
    formatDate(dateString) {
        if (!dateString) return 'Unknown Date';
        
        const date = new Date(dateString);
        const now = new Date();
        const diff = now - date;
        const days = Math.floor(diff / (1000 * 60 * 60 * 24));
        
        if (days === 0) {
            return date.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
        } else if (days < 7) {
            return date.toLocaleDateString('en-US', { weekday: 'short', hour: 'numeric', minute: '2-digit' });
        } else {
            return date.toLocaleDateString('en-US', { 
                month: 'short', 
                day: 'numeric', 
                year: date.getFullYear() !== now.getFullYear() ? 'numeric' : undefined,
                hour: 'numeric',
                minute: '2-digit'
            });
        }
    }
    
    /**
     * Escape HTML to prevent XSS
     */
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

// Export for use in other scripts
if (typeof module !== 'undefined' && module.exports) {
    module.exports = EmailListComponent;
}
