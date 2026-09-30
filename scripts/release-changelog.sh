#!/usr/bin/env bash
#
# Turns the [Unreleased] section of CHANGELOG.md into a release (Keep a Changelog
# 1.1.0): a "## [x.y.z] - YYYY-MM-DD" heading under an empty [Unreleased], and
# the compare links moved on - [Unreleased] from the new tag, the release from
# the previous one. Run by `make git-release`, before the release commit.
#
#   scripts/release-changelog.sh 0.6.0 [CHANGELOG.md]
#
set -euo pipefail
cd "$(dirname "$0")/.."

version=${1:?usage: $0 <version> [changelog]}
file=${2:-CHANGELOG.md}
date=$(date -u +%Y-%m-%d)

fail() {
    echo "release-changelog: $*" >&2
    exit 1
}

[[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+([-+][0-9A-Za-z.-]+)?$ ]] || fail "not a version: $version"
[[ -f $file ]] || fail "no $file"
grep -q '^## \[Unreleased\]$' "$file" || fail "no '## [Unreleased]' heading in $file"
grep -q "^## \[${version//./\\.}\]" "$file" && fail "$version is already in $file"

# A release with nothing under it is a mistake to catch before it is tagged.
awk '/^## \[Unreleased\]$/ { inside = 1; next }
     /^## \[/ { inside = 0 }
     inside && /^- / { found = 1 }
     END { exit !found }' "$file" || fail "[Unreleased] lists no changes"

grep -Eq '^\[Unreleased\]: .+/compare/.+\.\.\.HEAD$' "$file" \
    || fail "no '[Unreleased]: <repo>/compare/<previous>...HEAD' link in $file"

tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT
awk -v v="$version" -v d="$date" '
    /^## \[Unreleased\]$/ {
        print; print ""; print "## [" v "] - " d
        next
    }
    /^\[Unreleased\]: .+\/compare\/.+\.\.\.HEAD$/ {
        base = $0; sub(/^\[Unreleased\]: /, "", base); sub(/\/compare\/.*$/, "", base)
        prev = $0; sub(/^.*\/compare\//, "", prev); sub(/\.\.\.HEAD$/, "", prev)
        print "[Unreleased]: " base "/compare/v" v "...HEAD"
        print "[" v "]: " base "/compare/" prev "...v" v
        next
    }
    { print }' "$file" > "$tmp"
cat "$tmp" > "$file"

echo "release-changelog: $file now releases $version ($date)"
