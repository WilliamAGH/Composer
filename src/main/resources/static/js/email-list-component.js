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
                    <svg class="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" style="color: #1e293b;">
                        <path d="M4 12l1.5 1.5a2 2 0 002.828 0L12 9.828a2 2 0 012.828 0L20 15" />
                        <path d="M3 7l9-4 9 4" />
                        <path d="M21 10v8a2 2 0 01-2 2H5a2 2 0 01-2-2v-8" />
                        <circle cx="17" cy="7" r="2.5" fill="#10b981" stroke="white" stroke-width="1.5"/>
                    </svg>
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
                <div class="email-dropdown-item" data-action="view-full" data-email-id="${emailId}">
                    View Full Content
                </div>
                <div class="email-dropdown-item" data-action="copy" data-email-id="${emailId}">
                    Copy to Clipboard
                </div>
                <div class="email-dropdown-item" data-action="insights" data-email-id="${emailId}">
                    Get AI Insights
                </div>
                <div class="email-dropdown-item" data-action="download" data-email-id="${emailId}">
                    Download
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
        const trigger = this.container.querySelector(`[data-email-id="${emailId}"].email-dropdown-trigger`);
        
        if (this.activeDropdown && this.activeDropdown !== dropdown) {
            this.activeDropdown.classList.remove('show');
        }
        
        if (dropdown && trigger) {
            const isShowing = dropdown.classList.toggle('show');
            this.activeDropdown = isShowing ? dropdown : null;
            
            // Position dropdown using fixed positioning for better overlay
            if (isShowing) {
                const rect = trigger.getBoundingClientRect();
                dropdown.style.position = 'fixed';
                dropdown.style.top = `${rect.bottom + 4}px`;
                dropdown.style.right = `${window.innerWidth - rect.right}px`;
                dropdown.style.left = 'auto';
            }
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
