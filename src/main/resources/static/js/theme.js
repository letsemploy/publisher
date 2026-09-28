/*
 * Resolves the System theme before the page paints (spec 7.3).
 *
 * The server renders the user's choice into <html data-bs-theme>. Light and dark
 * need nothing more; "auto" becomes one of them here, from the operating system's
 * preference, and follows it while the page is open. Without JavaScript "auto"
 * stays light, Tabler's default.
 *
 * A classic script, loaded in <head> without defer, so there is no flash of the
 * light theme; a file rather than inline, because the CSP forbids inline script.
 * hx-boost swaps only the body, so <html> and the listener outlive navigation.
 */
(() => {
    'use strict';

    const root = document.documentElement;
    if (root.getAttribute('data-bs-theme') !== 'auto') {
        return;
    }
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)');
    const apply = () => root.setAttribute('data-bs-theme', prefersDark.matches ? 'dark' : 'light');
    apply();
    prefersDark.addEventListener('change', apply);
})();
