#!/usr/bin/env python3
"""
Prepares a release on a release/* branch: run it, check the diff, commit.

  python3 scripts/prepare-release.py 0.1.0

- Renames "## Unreleased" in CHANGELOG.md to "## v0.1.0".
- Sets versionName to 0.1.0 and versionCode to major * 10000 + minor * 100 + patch
  in app/build.gradle.kts.
- Writes the F-Droid changelog, fastlane/metadata/android/en-US/changelogs/<versionCode>.txt,
  as plain text of at most 500 characters.
"""
import re
import sys
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPO_URL = "https://github.com/IxianPixel/Reliquary"
FDROID_LIMIT = 500


def fail(message: str) -> None:
    sys.exit(f"prepare-release: {message}")


def main() -> None:
    if len(sys.argv) != 2 or not re.fullmatch(r"\d+\.\d+\.\d+", sys.argv[1]):
        fail("usage: prepare-release.py MAJOR.MINOR.PATCH, e.g. 0.1.0")
    version = sys.argv[1]
    major, minor, patch = (int(p) for p in version.split("."))
    if minor > 99 or patch > 99:
        fail("minor and patch must be at most 99 for the versionCode scheme")
    code = major * 10000 + minor * 100 + patch
    tag = f"v{version}"

    changelog = ROOT / "CHANGELOG.md"
    text = changelog.read_text()
    if f"\n## {tag}\n" in text:
        fail(f"CHANGELOG.md already has a {tag} section")
    if "\n## Unreleased\n" not in text:
        fail('CHANGELOG.md has no "## Unreleased" section to release')
    text = text.replace("\n## Unreleased\n", f"\n## {tag}\n", 1)
    section = text.split(f"\n## {tag}\n", 1)[1].split("\n## ", 1)[0].strip()
    if not re.search(r"^- ", section, re.M):
        fail(f"the {tag} section has no entries")
    changelog.write_text(text)

    gradle = ROOT / "app/build.gradle.kts"
    build = gradle.read_text()
    build, names = re.subn(r'versionName = "[^"]*"', f'versionName = "{version}"', build)
    build, codes = re.subn(r"versionCode = \d+", f"versionCode = {code}", build)
    if names != 1 or codes != 1:
        fail("couldn't find versionName and versionCode in app/build.gradle.kts")
    gradle.write_text(build)

    notes = ROOT / f"fastlane/metadata/android/en-US/changelogs/{code}.txt"
    notes.parent.mkdir(parents=True, exist_ok=True)
    notes.write_text(fdroid_changelog(section, tag) + "\n")

    print(f"Prepared {tag} (versionCode {code}). Check the diff, then commit:")
    print(f"  git add -A && git commit -m 'Release {tag}'")


def fdroid_changelog(section: str, tag: str) -> str:
    """Bullet points as plain text: just each entry's bold lead, which fits F-Droid's limit."""
    lines = []
    for block in re.split(r"\n(?=- |### )", section):
        block = block.strip()
        if block.startswith("### "):
            lines.append(block[4:].strip() + ":")
        elif block.startswith("- "):
            entry = " ".join(block[2:].split())
            lead = re.match(r"\*\*(.+?)\*\*", entry)
            summary = lead.group(1) if lead else entry
            lines.append("• " + re.sub(r"[`*]", "", summary).rstrip("."))
    more = f"Full notes: {REPO_URL}/releases/tag/{tag}"
    body = "\n".join(lines)
    if len(body) + len(more) + 1 > FDROID_LIMIT:
        body = textwrap.shorten(body, FDROID_LIMIT - len(more) - 2, placeholder="…")
    return f"{body}\n{more}"


if __name__ == "__main__":
    main()
