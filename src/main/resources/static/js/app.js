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
        // Tabs (spec 7.23): until now every pane showed, for a page without JavaScript.
        root.querySelectorAll('[data-tabs]').forEach((el) => el.classList.add('tabs-enhanced'));
    };

    /** Copy the value of the input in the same group, with brief feedback. */
    const copyFromGroup = async (button) => {
        const input = button.closest('.input-group')?.querySelector('input, textarea');
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

    /**
     * Turn a search result into a chip with its hidden input (spec 7.8), built as
     * elements, never from a string of markup. Choosing one already chosen adds
     * nothing. The result leaves the list either way.
     */
    const pick = (result) => {
        const { target, value, label } = result.dataset;
        const chips = document.querySelector(`[data-chips="${CSS.escape(target)}"]`);
        if (!chips) {
            return;
        }
        const chosen = [...chips.querySelectorAll('input[type="hidden"]')].some((input) => input.value === value);
        if (!chosen) {
            const chip = document.createElement('span');
            chip.className = 'badge bg-blue-lt me-1 mb-1';
            chip.dataset.chip = '';
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = target;
            input.value = value;
            const text = document.createElement('span');
            text.textContent = label;
            const remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'btn-close btn-close-white ms-1';
            remove.dataset.action = 'remove-chip';
            remove.setAttribute('aria-label', chips.dataset.removeLabel ?? '');
            chip.append(input, text, remove);
            chips.querySelector('[data-chips-empty]')?.remove();
            chips.append(chip);
        }
        result.remove();
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
            case 'pick':
                event.preventDefault();
                pick(action);
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

    /**
     * A typed confirmation (spec 7.13): the form's submit stays disabled until the
     * input matches its data-confirm-name. Only a convenience - the server checks
     * the name, and without this module the button simply works.
     */
    const syncConfirmName = (input) => {
        const submit = input.form?.querySelector('[type="submit"]');
        if (submit) {
            submit.disabled = input.value.trim() !== input.dataset.confirmName;
        }
    };
    const armConfirmNames = (root = document) => {
        root.querySelectorAll('[data-confirm-name]').forEach(syncConfirmName);
    };

    document.addEventListener('input', (event) => {
        const input = event.target.closest('[data-confirm-name]');
        if (input) {
            syncConfirmName(input);
        }
    });

    /**
     * A file over the server's limit is refused here, before it is sent: past
     * the limit the server cannot answer with the form (spec 7.27). The server
     * checks again; this only saves the wait and the error page.
     */
    document.addEventListener('change', (event) => {
        const input = event.target.closest('input[type="file"][data-max-bytes]');
        if (!input) {
            return;
        }
        const tooBig = [...(input.files || [])].some((file) => file.size > Number(input.dataset.maxBytes));
        input.setCustomValidity(tooBig ? input.dataset.tooBig : '');
        input.reportValidity();
    });

    /**
     * Draw Tabler's sparklines (spec 7.10). tabler.min.js draws those present when
     * the page loads; a boosted navigation swaps the body in afterwards, and its
     * charts would stay empty. The figures are in the text beside them either way.
     */
    const drawSparklines = (root) => {
        const sparkline = window.tabler?.Sparkline;
        if (!sparkline) {
            return;
        }
        root.querySelectorAll('[data-bs-toggle="sparkline"]')
            .forEach((el) => sparkline.getOrCreateInstance(el));
    };

    // htmx replaces regions wholesale; anything swapped in needs revealing too.
    document.body.addEventListener('htmx:afterSwap', (event) => {
        revealEnhancedControls(event.target);
        armConfirmNames(event.target);
        drawSparklines(event.target);
    });

    revealEnhancedControls();
    armConfirmNames();
})();
