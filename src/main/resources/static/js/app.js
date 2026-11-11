/*
 * The application's own behaviour (spec 7.2).
 *
 * Plain ES, served as written: no bundler, no transpiler, no framework. Every
 * listener is delegated from the document, so it keeps working after an htmx
 * swap replaces part of the page.
 *
 * Controls that need this module are marked `hidden` in the markup and revealed
 * by it, so no screen offers a dead button. JavaScript may be assumed where it
 * serves (spec 7.1): forms open as modals (spec 7.4), and their URLs still answer
 * the whole page.
 *
 * No eval, no building markup from strings: the CSP forbids both (spec 9.4).
 */
(() => {
    'use strict';

    /** Reveal controls that only make sense once this module is running. */
    const revealEnhancedControls = (root = document) => {
        root.querySelectorAll('[data-enhanced]').forEach((el) => el.removeAttribute('hidden'));
        // And the reverse: a fallback that this module makes unnecessary.
        root.querySelectorAll('[data-without-js]').forEach((el) => el.setAttribute('hidden', ''));
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
        // A tag still to be created posts its name under a field of its own (spec 7.8).
        const name = result.dataset.name ?? target;
        const chips = document.querySelector(`[data-chips="${CSS.escape(target)}"]`);
        if (!chips) {
            return;
        }
        const chosen = [...chips.querySelectorAll('input[type="hidden"]')]
            .some((input) => input.name === name && input.value === value);
        if (!chosen) {
            const chip = document.createElement('span');
            chip.className = name === target ? 'badge bg-blue-lt me-1 mb-1' : 'badge bg-green-lt me-1 mb-1';
            chip.dataset.chip = '';
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = name;
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
        // Back to the search before the result goes, so a keyboard pick does not
        // lose the focus and with it the list.
        result.closest('[data-picker]')?.querySelector('input[type="search"]')?.focus();
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

    /**
     * A picker's results close on Escape and when focus leaves the picker
     * (spec 7.8); typing in the search opens them again.
     */
    const closeResults = (picker) => {
        picker?.querySelector('[data-picker-results]')?.replaceChildren();
    };
    document.addEventListener('focusout', (event) => {
        const picker = event.target.closest('[data-picker]');
        if (picker && !picker.contains(event.relatedTarget)) {
            closeResults(picker);
        }
    });
    // Choosing a result keeps the focus in the search, so the list is not closed
    // under the click and the next result can be picked at once.
    document.addEventListener('mousedown', (event) => {
        if (event.target.closest('[data-picker-results] [data-action="pick"]')) {
            event.preventDefault();
        }
    });

    // Escape closes a modal - a confirmation or a form - as the dialog conventions require (spec 7.9).
    // Inside an open picker it closes only the picker's results.
    document.addEventListener('keydown', (event) => {
        if (event.key !== 'Escape') {
            return;
        }
        const picker = event.target.closest?.('[data-picker]');
        const results = picker?.querySelector('[data-picker-results]');
        if (results?.hasChildNodes()) {
            // Not the browser's own Escape either, which would clear the search.
            event.preventDefault();
            closeResults(picker);
            return;
        }
        document.querySelector('#modal-container .modal')?.remove();
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
     * Controls that submit their form as soon as something is chosen: the
     * employer switcher (spec 2.5, 7.3) and the picture on Settings (spec 7.26).
     * Without JavaScript a button beside each does the same. A file over the
     * limit was refused by the listener above, and requestSubmit validates, so
     * it is not sent.
     */
    document.addEventListener('change', (event) => {
        const control = event.target.closest('select[data-auto-submit], input[type="file"][data-auto-submit]');
        control?.form?.requestSubmit();
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

    /**
     * A form opened as a modal (spec 7.4, 7.7) starts at its first field, or at
     * the first one in error when a refusal brought it back.
     */
    const focusModalForm = (root) => {
        const modal = root.closest?.('#modal-container') ? root.querySelector('.modal') : null;
        const field = modal?.querySelector('.is-invalid')
            ?? modal?.querySelector('form.modal-content .modal-body :is(input, select, textarea):not([type="hidden"])');
        field?.focus();
    };

    // htmx replaces regions wholesale; anything swapped in needs revealing too.
    // htmx 4 fires this on the element that made the request, not the region it
    // replaced, so the region comes from the request context.
    document.addEventListener('htmx:after:swap', (event) => {
        const target = event.detail?.ctx?.target;
        const root = target?.isConnected ? target : document;
        revealEnhancedControls(root);
        armConfirmNames(root);
        drawSparklines(root);
        focusModalForm(root);
    });

    revealEnhancedControls();
    armConfirmNames();
})();
