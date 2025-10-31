/**
 * Email Data Table Component - shadcn/ui Data Table Implementation
 * Vanilla JavaScript implementation of shadcn/ui Data Table for emails
 */

class EmailDataTable {
    constructor(containerId, options = {}) {
        this.container = document.getElementById(containerId);
        if (!this.container) {
            console.error(`Container with id "${containerId}" not found`);
            return;
        }
        
        this.emails = [];
        this.filteredEmails = [];
        this.sortConfig = { key: null, direction: 'asc' };
        this.currentPage = 0;
        this.pageSize = 10;
        this.selectedRows = new Set();
        this.filterValue = '';
        
        // Options
        this.options = {
            onRowAction: options.onRowAction || null,
            showSelection: options.showSelection !== false,
            showPagination: options.showPagination !== false,
            ...options
        };
        
        this.init();
    }
    
    init() {
        this.render();
        this.attachEventListeners();
    }
    
    /**
     * Load emails data into the table
     */
    loadEmails(emails) {
        this.emails = emails.map((email, index) => ({
            id: email.id || `email-${index}`,
            subject: email.subject || 'No Subject',
            sender: email.from || email.sender || 'Unknown Sender',
            date: email.date || new Date().toISOString(),
            status: email.status || 'success',
            ...email
        }));
        this.filteredEmails = [...this.emails];
        this.applyFilter();
    }
    
    /**
     * Render the complete data table structure
     */
    render() {
        this.container.innerHTML = `
            <div class="email-data-table">
                <!-- Table Controls -->
                <div class="table-controls">
                    <input 
                        type="text" 
                        class="table-filter-input" 
                        placeholder="Filter emails..." 
                        value="${this.filterValue}"
                    />
                </div>
                
                <!-- Data Table -->
                <div class="data-table-wrapper">
                    <table class="data-table">
                        <thead class="data-table-header">
                            <tr class="data-table-header-row">
                                ${this.options.showSelection ? `
                                    <th class="data-table-head">
                                        <input type="checkbox" class="checkbox" id="select-all" />
                                    </th>
                                ` : ''}
                                <th class="data-table-head">
                                    <button class="btn btn-ghost btn-sm sortable-header" data-sort="subject">
                                        Subject
                                        <svg class="sort-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                            <path d="m7 15 5 5 5-5M7 9l5-5 5 5"/>
                                        </svg>
                                    </button>
                                </th>
                                <th class="data-table-head">
                                    <button class="btn btn-ghost btn-sm sortable-header" data-sort="sender">
                                        Sender
                                        <svg class="sort-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                            <path d="m7 15 5 5 5-5M7 9l5-5 5 5"/>
                                        </svg>
                                    </button>
                                </th>
                                <th class="data-table-head">Status</th>
                                <th class="data-table-head" data-align="right">Date</th>
                                <th class="data-table-head">
                                    <span class="sr-only">Actions</span>
                                </th>
                            </tr>
                        </thead>
                        <tbody class="data-table-body" id="table-body">
                            ${this.renderTableRows()}
                        </tbody>
                    </table>
                </div>
                
                <!-- Pagination -->
                ${this.options.showPagination ? this.renderPagination() : ''}
            </div>
        `;
    }
    
    /**
     * Render table rows
     */
    renderTableRows() {
        if (this.filteredEmails.length === 0) {
            return `
                <tr class="data-table-body-row">
                    <td class="data-table-cell data-table-no-results" colspan="6">
                        No results.
                    </td>
                </tr>
            `;
        }
        
        const startIndex = this.currentPage * this.pageSize;
        const endIndex = startIndex + this.pageSize;
        const pageEmails = this.filteredEmails.slice(startIndex, endIndex);
        
        return pageEmails.map(email => `
            <tr class="data-table-body-row" data-email-id="${email.id}" ${this.selectedRows.has(email.id) ? 'data-state="selected"' : ''}>
                ${this.options.showSelection ? `
                    <td class="data-table-cell">
                        <input type="checkbox" class="checkbox row-select" data-email-id="${email.id}" ${this.selectedRows.has(email.id) ? 'checked' : ''} />
                    </td>
                ` : ''}
                <td class="data-table-cell">
                    <div class="font-medium">${this.escapeHtml(email.subject)}</div>
                </td>
                <td class="data-table-cell">
                    <div class="lowercase">${this.escapeHtml(email.sender)}</div>
                </td>
                <td class="data-table-cell">
                    <div class="status-badge status-badge-${email.status}">${email.status}</div>
                </td>
                <td class="data-table-cell" data-align="right">
                    ${this.formatDate(email.date)}
                </td>
                <td class="data-table-cell">
                    <div class="dropdown-container" style="position: relative;">
                        <button class="btn btn-ghost btn-icon dropdown-trigger" data-email-id="${email.id}">
                            <span class="sr-only">Open menu</span>
                            <svg class="icon" viewBox="0 0 24 24" fill="currentColor">
                                <path d="M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"/>
                            </svg>
                        </button>
                        <div class="dropdown-menu" data-dropdown-for="${email.id}">
                            <div class="dropdown-menu-label">Actions</div>
                            <button class="dropdown-menu-item" data-action="view-full" data-email-id="${email.id}">
                                View Full Content
                            </button>
                            <button class="dropdown-menu-item" data-action="copy" data-email-id="${email.id}">
                                Copy to Clipboard
                            </button>
                            <div class="dropdown-menu-separator"></div>
                            <button class="dropdown-menu-item" data-action="insights" data-email-id="${email.id}">
                                Get AI Insights
                            </button>
                            <button class="dropdown-menu-item" data-action="download" data-email-id="${email.id}">
                                Download
                            </button>
                        </div>
                    </div>
                </td>
            </tr>
        `).join('');
    }
    
    /**
     * Render pagination controls
     */
    renderPagination() {
        const totalPages = Math.ceil(this.filteredEmails.length / this.pageSize);
        const startIndex = this.currentPage * this.pageSize;
        const endIndex = Math.min(startIndex + this.pageSize, this.filteredEmails.length);
        
        return `
            <div class="table-pagination">
                <div class="pagination-info">
                    ${this.selectedRows.size} of ${this.filteredEmails.length} row(s) selected.
                </div>
                <div class="pagination-controls">
                    <button class="btn btn-outline btn-sm" id="prev-page" ${this.currentPage === 0 ? 'disabled' : ''}>
                        Previous
                    </button>
                    <div class="pagination-info">
                        Page ${this.currentPage + 1} of ${totalPages}
                    </div>
                    <button class="btn btn-outline btn-sm" id="next-page" ${this.currentPage >= totalPages - 1 ? 'disabled' : ''}>
                        Next
                    </button>
                </div>
            </div>
        `;
    }
    
    /**
     * Attach event listeners
     */
    attachEventListeners() {
        // Filter input
        const filterInput = this.container.querySelector('.table-filter-input');
        if (filterInput) {
            filterInput.addEventListener('input', (e) => {
                this.filterValue = e.target.value;
                this.applyFilter();
            });
        }
        
        // Sort buttons
        this.container.addEventListener('click', (e) => {
            if (e.target.closest('[data-sort]')) {
                const sortKey = e.target.closest('[data-sort]').dataset.sort;
                this.handleSort(sortKey);
            }
        });
        
        // Select all checkbox
        const selectAllCheckbox = this.container.querySelector('#select-all');
        if (selectAllCheckbox) {
            selectAllCheckbox.addEventListener('change', (e) => {
                this.handleSelectAll(e.target.checked);
            });
        }
        
        // Row selection and actions
        this.container.addEventListener('click', (e) => {
            // Row selection
            if (e.target.classList.contains('row-select')) {
                const emailId = e.target.dataset.emailId;
                this.handleRowSelection(emailId, e.target.checked);
            }
            
            // Dropdown triggers
            if (e.target.closest('.dropdown-trigger')) {
                e.stopPropagation();
                const emailId = e.target.closest('.dropdown-trigger').dataset.emailId;
                this.toggleDropdown(emailId);
            }
            
            // Dropdown actions
            if (e.target.closest('.dropdown-menu-item')) {
                const action = e.target.closest('.dropdown-menu-item').dataset.action;
                const emailId = e.target.closest('.dropdown-menu-item').dataset.emailId;
                this.handleAction(action, emailId);
            }
            
            // Pagination
            if (e.target.id === 'prev-page') {
                this.previousPage();
            }
            if (e.target.id === 'next-page') {
                this.nextPage();
            }
        });
        
        // Close dropdowns when clicking outside
        document.addEventListener('click', (e) => {
            if (!e.target.closest('.dropdown-container')) {
                this.closeAllDropdowns();
            }
        });
    }
    
    /**
     * Apply filter to emails
     */
    applyFilter() {
        if (!this.filterValue.trim()) {
            this.filteredEmails = [...this.emails];
        } else {
            const filter = this.filterValue.toLowerCase();
            this.filteredEmails = this.emails.filter(email => 
                email.subject.toLowerCase().includes(filter) ||
                email.sender.toLowerCase().includes(filter)
            );
        }
        
        this.applySorting();
        this.currentPage = 0; // Reset to first page
        this.updateTable();
    }
    
    /**
     * Handle sorting
     */
    handleSort(key) {
        if (this.sortConfig.key === key) {
            this.sortConfig.direction = this.sortConfig.direction === 'asc' ? 'desc' : 'asc';
        } else {
            this.sortConfig.key = key;
            this.sortConfig.direction = 'asc';
        }
        
        this.applySorting();
        this.updateTable();
    }
    
    /**
     * Apply current sorting
     */
    applySorting() {
        if (!this.sortConfig.key) return;
        
        this.filteredEmails.sort((a, b) => {
            const aVal = a[this.sortConfig.key].toLowerCase();
            const bVal = b[this.sortConfig.key].toLowerCase();
            
            if (aVal < bVal) return this.sortConfig.direction === 'asc' ? -1 : 1;
            if (aVal > bVal) return this.sortConfig.direction === 'asc' ? 1 : -1;
            return 0;
        });
    }
    
    /**
     * Handle row selection
     */
    handleRowSelection(emailId, checked) {
        if (checked) {
            this.selectedRows.add(emailId);
        } else {
            this.selectedRows.delete(emailId);
        }
        this.updateTable();
    }
    
    /**
     * Handle select all
     */
    handleSelectAll(checked) {
        this.selectedRows.clear();
        if (checked) {
            this.filteredEmails.forEach(email => this.selectedRows.add(email.id));
        }
        this.updateTable();
    }
    
    /**
     * Toggle dropdown menu
     */
    toggleDropdown(emailId) {
        this.closeAllDropdowns();
        const dropdown = this.container.querySelector(`[data-dropdown-for="${emailId}"]`);
        if (dropdown) {
            dropdown.classList.add('show');
        }
    }
    
    /**
     * Close all dropdowns
     */
    closeAllDropdowns() {
        this.container.querySelectorAll('.dropdown-menu.show').forEach(dropdown => {
            dropdown.classList.remove('show');
        });
    }
    
    /**
     * Handle dropdown actions
     */
    handleAction(action, emailId) {
        this.closeAllDropdowns();
        
        const email = this.emails.find(e => e.id === emailId);
        if (this.options.onRowAction) {
            this.options.onRowAction(action, email);
        }
    }
    
    /**
     * Pagination methods
     */
    previousPage() {
        if (this.currentPage > 0) {
            this.currentPage--;
            this.updateTable();
        }
    }
    
    nextPage() {
        const totalPages = Math.ceil(this.filteredEmails.length / this.pageSize);
        if (this.currentPage < totalPages - 1) {
            this.currentPage++;
            this.updateTable();
        }
    }
    
    /**
     * Update table content
     */
    updateTable() {
        const tableBody = this.container.querySelector('#table-body');
        if (tableBody) {
            tableBody.innerHTML = this.renderTableRows();
        }
        
        // Update pagination if enabled
        if (this.options.showPagination) {
            const paginationContainer = this.container.querySelector('.table-pagination');
            if (paginationContainer) {
                paginationContainer.outerHTML = this.renderPagination();
            }
        }
        
        // Update select all checkbox
        const selectAllCheckbox = this.container.querySelector('#select-all');
        if (selectAllCheckbox) {
            const allSelected = this.filteredEmails.length > 0 && 
                               this.filteredEmails.every(email => this.selectedRows.has(email.id));
            const someSelected = this.selectedRows.size > 0 && !allSelected;
            
            selectAllCheckbox.checked = allSelected;
            selectAllCheckbox.indeterminate = someSelected;
        }
    }
    
    /**
     * Utility methods
     */
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
    
    formatDate(dateString) {
        const date = new Date(dateString);
        return date.toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric'
        });
    }
}

// Export for use in other modules
if (typeof module !== 'undefined' && module.exports) {
    module.exports = EmailDataTable;
}