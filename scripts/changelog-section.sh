#!/usr/bin/env bash
# Prints one version's section of CHANGELOG.md, without its "## vX.Y.Z" heading.
# Usage: scripts/changelog-section.sh v0.1.0 [CHANGELOG.md]
# Exits 1 if the version has no section, or the section is empty.
set -euo pipefail
version="${1:?usage: $0 vX.Y.Z [changelog]}"
file="${2:-CHANGELOG.md}"
section="$(awk -v heading="## ${version}" '
  $0 == heading { found = 1; next }
  found && /^## / { exit }
  found { print }
' "$file" | sed -e '/./,$!d' | sed -e ':a' -e '/^\n*$/{$d;N;ba' -e '}')"
if [ -z "${section//[[:space:]]/}" ]; then
  echo "CHANGELOG.md has no entries under \"## ${version}\"." >&2
  exit 1
fi
printf '%s\n' "$section"
