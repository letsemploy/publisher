/*
 * The application's own behaviour (spec 7.2).
 *
 * Plain ES, served as written: no bundler, no transpiler, no framework. Every
 * listener is delegated from the document, so it keeps working after an htmx
 * swap replaces part of the page.
 *
 * Nothing here is load-bearing. Each control is marked `hidden` in the markup and
 * revealed by this module, so a browser without JavaScript shows no dead
 * controls - progressive enhancement is mandatory (spec 7.1).
 *
 * No eval, no building markup from strings: the CSP forbids both (spec 9.4).
 */
(() => {
    'use strict';

    /** Reveal controls that only make sense once this module is running. */
    const revealEnhancedControls = (root = document) => {
        root.querySelectorAll('[data-enhanced]').forEach((el) => el.removeAttribute('hidden'));
    };

    /** Copy the value of the input in the same group, with brief feedback. */
    const copyFromGroup = async (button) => {
        const input = button.closest('.input-group')?.querySelector('input');
        if (!input) {
            return;
        }
        try {
            await navigator.clipboard.writeText(input.value);
        } catch {
            // Clipboard access can be refused; selecting the text still lets the
            // user copy it by hand, which is better than failing silently.
            input.select();
            return;
        }
        const done = button.dataset.copiedLabel;
        if (done) {
            const previous = button.getAttribute('aria-label');
            button.setAttribute('aria-label', done);
            button.classList.add('text-green');
            window.setTimeout(() => {
                button.setAttribute('aria-label', previous ?? '');
                button.classList.remove('text-green');
            }, 1500);
        }
    };

    document.addEventListener('click', (event) => {
        const action = event.target.closest('[data-action]');
        if (!action) {
            return;
        }
        switch (action.dataset.action) {
            case 'dismiss-modal':
                event.preventDefault();
                action.closest('.modal')?.remove();
                break;
            case 'remove-chip':
                event.preventDefault();
                // Removing the chip removes its hidden input, so the selection
                // posts without it (spec 7.8).
                action.closest('[data-chip]')?.remove();
                break;
            case 'copy':
                event.preventDefault();
                copyFromGroup(action);
                break;
            default:
                break;
        }
    });

    // Escape closes a confirmation modal, as the dialog conventions require (spec 7.9).
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') {
            document.querySelector('#modal-container .modal')?.remove();
        }
    });

    // htmx replaces regions wholesale; anything swapped in needs revealing too.
    document.body.addEventListener('htmx:afterSwap', (event) => revealEnhancedControls(event.target));

    revealEnhancedControls();
})();
