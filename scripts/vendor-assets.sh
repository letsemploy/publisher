#!/usr/bin/env bash
#
# Copies the pinned front-end dependencies into src/main/resources/static/vendor
# and rebuilds the icon sprite (spec 7.2, 9.1).
#
# Node is a maintenance tool, never a build dependency: the output of this script
# is committed, so ./mvnw package works on a machine with no Node installed.
#
#   npm ci && npm run vendor
#
set -euo pipefail
cd "$(dirname "$0")/.."

VENDOR=src/main/resources/static/vendor
MODULES=node_modules

[ -d "$MODULES" ] || { echo "node_modules missing - run 'npm ci' first" >&2; exit 1; }
mkdir -p "$VENDOR"

copy() { # source, name
  [ -f "$1" ] || { echo "missing from node_modules: $1" >&2; exit 1; }
  cp -f "$1" "$VENDOR/$2"
  printf '  %-20s %8s bytes\n' "$2" "$(wc -c < "$VENDOR/$2")"
}

echo "Copying dist files:"
copy "$MODULES/@tabler/core/dist/css/tabler.min.css" tabler.min.css
copy "$MODULES/@tabler/core/dist/js/tabler.min.js"   tabler.min.js
copy "$MODULES/htmx.org/dist/htmx.min.js"            htmx.min.js

# The sprite carries only the icons actually referenced, from BOTH sources:
# templates use fragments/icon, and the sidebar's icon names live in Java.
echo "Building the icon sprite from referenced icons only:"
python3 - "$MODULES/@tabler/icons/icons/outline" "$VENDOR/icons.svg" <<'PY'
import pathlib, re, subprocess, sys

icon_dir, out_path = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])

def scan(pattern, path, group=1):
    found = set()
    for line in subprocess.run(['grep', '-rhoE', pattern, path],
                               capture_output=True, text=True).stdout.splitlines():
        m = re.search(pattern.replace('\\(', '(').replace('\\)', ')'), line)
        if m:
            found.add(m.group(group))
    return found

names = set()
for line in subprocess.run(
        ['grep', '-rhoE', r"icon :: i\('[a-z0-9-]+'", 'src/main/resources/templates'],
        capture_output=True, text=True).stdout.splitlines():
    names.add(line.split("'")[1])
for line in subprocess.run(
        ['grep', '-rhoE', r'new NavItem\("[a-z0-9-]+"', 'src/main/java'],
        capture_output=True, text=True).stdout.splitlines():
    names.add(line.split('"')[1])

if not names:
    sys.exit('found no icon references - refusing to write an empty sprite')

missing = [n for n in sorted(names) if not (icon_dir / f'{n}.svg').exists()]
if missing:
    sys.exit(f'referenced icons not in @tabler/icons: {missing}')

parts = ['<svg xmlns="http://www.w3.org/2000/svg" style="display:none">']
for name in sorted(names):
    inner = re.search(r'<svg[^>]*>(.*)</svg>', (icon_dir / f'{name}.svg').read_text(), re.S).group(1).strip()
    parts.append(f'<symbol id="ti-{name}" viewBox="0 0 24 24" fill="none" stroke="currentColor" '
                 f'stroke-width="2" stroke-linecap="round" stroke-linejoin="round">{inner}</symbol>')
parts.append('</svg>')
out_path.write_text('\n'.join(parts))
print(f'  {len(names)} icons -> {out_path} ({out_path.stat().st_size} bytes)')
PY

echo "Done. The vendored files are committed; ./mvnw package needs no Node."
