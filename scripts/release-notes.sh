#!/usr/bin/env bash
#
# Prints the GitHub release notes for a version: its section of CHANGELOG.md,
# the container image, and the links to the full changelog and the compare view.
# Run by the release job in .github/workflows/container.yml for every v* tag.
#
#   scripts/release-notes.sh 1.5.0 [CHANGELOG.md]
#
set -euo pipefail
cd "$(dirname "$0")/.."

version=${1:?usage: $0 <version> [changelog]}
file=${2:-CHANGELOG.md}
image=ghcr.io/letsemploy/publisher

fail() {
    echo "release-notes: $*" >&2
    exit 1
}

[[ -f $file ]] || fail "no $file"
grep -q "^## \[${version//./\\.}\]" "$file" || fail "$version is not in $file"

# The section's body: everything between its heading and the next one, without
# the blank lines at either end.
body=$(awk -v v="$version" '
    index($0, "## [" v "]") == 1 { inside = 1; next }
    /^## \[/ { inside = 0 }
    /^\[[^]]+\]: / { inside = 0 }
    inside { print }' "$file" | sed -e '/./,$!d' | tac | sed -e '/./,$!d' | tac)
[[ -n $body ]] || fail "$version lists no changes"

repo=https://github.com/letsemploy/publisher
compare=$(grep -E "^\[${version//./\\.}\]: " "$file" | sed -E 's/^\[[^]]+\]: //' || true)

printf '%s\n\n' "$body"
printf '### Container image\n\n```\n%s:%s\n```\n\n' "$image" "$version"
printf 'Full changelog: [CHANGELOG.md](%s/blob/v%s/CHANGELOG.md)' "$repo" "$version"
if [[ -n $compare ]]; then
    printf ' · [%s](%s)' "${compare##*/}" "$compare"
fi
printf '\n'
