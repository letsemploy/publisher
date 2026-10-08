// Copies the pinned front-end dependencies into the served static/vendor directory
// and builds the icon sprite (spec 7.2, 9.1).
//
// Run by the Maven build (frontend-maven-plugin, generate-resources) after `npm ci`,
// with target/classes/static/vendor as the output. Nothing it writes is committed.
//
//   node scripts/vendor-assets.mjs <output-dir>
//
import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const modules = join(root, 'node_modules');
const out = process.argv[2];

const fail = (message) => { console.error(message); process.exit(1); };

if (!out) fail('usage: node scripts/vendor-assets.mjs <output-dir>');
if (!existsSync(modules)) fail("node_modules missing - run 'npm ci' first");
mkdirSync(out, { recursive: true });

const copy = (source, name) => {
  const from = join(modules, source);
  if (!existsSync(from)) fail(`missing from node_modules: ${source}`);
  copyFileSync(from, join(out, name));
  console.log(`  ${name.padEnd(22)} ${String(statSync(join(out, name)).size).padStart(8)} bytes`);
};

console.log('Copying dist files:');
copy('@tabler/core/dist/css/tabler.min.css', 'tabler.min.css');
copy('@tabler/core/dist/css/tabler-themes.min.css', 'tabler-themes.min.css');
copy('@tabler/core/dist/js/tabler.min.js', 'tabler.min.js');
copy('htmx.org/dist/htmx.min.js', 'htmx.min.js');
// mCaptcha's widget glue (spec 2.12, 7.2): used only where mCaptcha is the configured captcha.
copy('@mcaptcha/vanilla-glue/dist/index.js', 'mcaptcha-glue.js');

// The sprite carries only the icons actually referenced, from BOTH sources:
// templates use fragments/icon, and the sidebar's icon names live in Java.
const files = (dir) => readdirSync(dir, { withFileTypes: true }).flatMap((entry) =>
  entry.isDirectory() ? files(join(dir, entry.name)) : [join(dir, entry.name)]);

const scan = (dir, pattern) => files(join(root, dir))
  .flatMap((file) => [...readFileSync(file, 'utf8').matchAll(pattern)].map((m) => m[1]));

const names = [...new Set([
  ...scan('src/main/resources/templates', /icon :: i\('([a-z0-9-]+)'/g),
  ...scan('src/main/java', /new NavItem\("([a-z0-9-]+)"/g),
])].sort();

if (names.length === 0) fail('found no icon references - refusing to write an empty sprite');

const iconDir = join(modules, '@tabler/icons/icons/outline');
const missing = names.filter((name) => !existsSync(join(iconDir, `${name}.svg`)));
if (missing.length) fail(`referenced icons not in @tabler/icons: ${missing.join(', ')}`);

const symbols = names.map((name) => {
  const inner = readFileSync(join(iconDir, `${name}.svg`), 'utf8').match(/<svg[^>]*>([\s\S]*)<\/svg>/)[1].trim();
  return `<symbol id="ti-${name}" viewBox="0 0 24 24" fill="none" stroke="currentColor" `
    + `stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${inner}</symbol>`;
});
const sprite = join(out, 'icons.svg');
writeFileSync(sprite, ['<svg xmlns="http://www.w3.org/2000/svg" style="display:none">', ...symbols, '</svg>'].join('\n'));
console.log(`  ${names.length} icons -> icons.svg (${statSync(sprite).size} bytes)`);
