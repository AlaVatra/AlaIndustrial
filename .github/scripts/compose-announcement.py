#!/usr/bin/env python3
"""Compose ONE Discord release post for every Minecraft line of a mod version.

A version ships as one tag per Minecraft line (vX.Y.Z-mc26.3, vX.Y.Z-mc26.2). Their
player changelogs are mostly the same text, so the post lists the shared entries once
and the entries a line has on its own under "Only on Minecraft <line>".

Usage:
  python compose-announcement.py --version 0.1.183 \
      --line v0.1.183-mc26.3=path/to/CHANGELOG.md --line v0.1.183-mc26.2=path/to/CHANGELOG.md \
      --forge v0.1.183-mc26.3 --forge v0.1.183-mc26.2 --out post.json

`--forge TAG` names a line whose tagged tree carries forge/ and so shipped a Forge jar; the
post links its +forge version. A line without it gets Fabric and NeoForge links only.

Writes {"title", "url", "description", "downloads", "media_tag"} as JSON.
"""
from __future__ import annotations

import argparse
import json
import re
import sys

REPO = "AlaVatra/AlaIndustrial"
CF_FILES = "https://www.curseforge.com/minecraft/mc-mods/ala-industrial/files"
MODRINTH_VERSION = "https://modrinth.com/mod/ala-industrial/version/"
EMBED_DESCRIPTION_LIMIT = 4096
SHORTENED_NOTE = "_Shortened for Discord: the full notes are on the release page._"
BOLD_LEAD = re.compile(r"\*\*(.+?)\*\*")
FIRST_SENTENCE = re.compile(r"(.+?[.!?])(?:\s|$)")
COUNT_SENTENCE = re.compile(r"^\d+ updates? in this release\.\s*")
TAG = re.compile(r"^v(\d+\.\d+\.\d+)-mc(\d+(?:\.\d+)*)$")


def parse_changelog(text: str) -> tuple[str, list[tuple[str, list[str]]]]:
    """Return (intro, [(section, [bullet, ...]), ...]) of a one-version public changelog."""
    intro_lines: list[str] = []
    sections: list[tuple[str, list[str]]] = []
    bullet: list[str] | None = None
    for raw in text.splitlines():
        line = raw.rstrip()
        if line.startswith("# ") or line.startswith("## ") or "<img" in line:
            continue
        if line.startswith("### "):
            sections.append((line[4:].strip(), []))
            bullet = None
            continue
        if not sections:
            if line:
                intro_lines.append(line.strip())
            continue
        if line.startswith("- "):
            bullet = [line[2:].strip()]
            sections[-1][1].append(bullet)
        elif line and bullet is not None and raw[:1].isspace():
            bullet.append(line.strip())
        elif not line:
            bullet = None
    intro = COUNT_SENTENCE.sub("", " ".join(intro_lines)).strip()
    return intro, [(name, [" ".join(b) for b in bullets]) for name, bullets in sections]


def lead(bullet: str) -> str:
    """The headline of an entry: its bold opening, or else its first sentence."""
    m = BOLD_LEAD.match(bullet)
    if m:
        return f"**{m.group(1)}**"
    m = FIRST_SENTENCE.match(bullet)
    return m.group(1) if m else bullet


def minecraft_of(tag: str) -> str:
    m = TAG.match(tag)
    if not m:
        raise ValueError(f"not a release tag: {tag}")
    return m.group(2)


def compose(version: str, lines: list[tuple[str, str]], forge_tags: frozenset[str] | set[str] = frozenset()) -> dict:
    if not lines:
        raise ValueError("no release lines to announce")
    for tag, _ in lines:
        if TAG.match(tag).group(1) != version:
            raise ValueError(f"{tag} is not a release of {version}")
    lines = sorted(lines, key=lambda l: [int(p) for p in minecraft_of(l[0]).split(".")], reverse=True)
    parsed = [(tag, *parse_changelog(text)) for tag, text in lines]
    leader_tag, intro, _ = parsed[0]

    def key(b: str) -> str:
        return " ".join(b.split())

    everywhere = set.intersection(*({key(b) for _, bs in secs for b in bs} for _, _, secs in parsed))

    def body(shown) -> str:
        out: list[str] = []
        if intro:
            out += [intro, ""]
        for name, bullets in parsed[0][2]:
            shared = [b for b in bullets if key(b) in everywhere]
            if shared:
                out += [f"**{name}**", *(f"- {shown(b)}" for b in shared), ""]
        if len(parsed) > 1:
            for tag, _, secs in parsed:
                own = [(name, b) for name, bs in secs for b in bs if key(b) not in everywhere]
                if own:
                    out.append(f"**Only on Minecraft {minecraft_of(tag)}**")
                    out += [f"- {shown(b)}" if name in ("New",) else f"- {name}: {shown(b)}" for name, b in own]
                    out.append("")
        return "\n".join(out).strip()

    # A release with many detailed entries does not fit one embed. Rather than refuse to announce
    # it, keep every entry and show its headline only; the title links to the full notes.
    description = body(lambda b: b)
    if len(description) > EMBED_DESCRIPTION_LIMIT:
        description = body(lead) + "\n\n" + SHORTENED_NOTE
    if len(description) > EMBED_DESCRIPTION_LIMIT:
        raise ValueError(f"post is {len(description)} chars even with headlines only, "
                         f"Discord allows {EMBED_DESCRIPTION_LIMIT}")

    downloads = []
    for tag, _, _ in parsed:
        mc = minecraft_of(tag)
        mr = MODRINTH_VERSION + tag[1:]
        forge = f" · [Forge]({mr}+forge)" if tag in forge_tags else ""
        downloads.append(f"**Minecraft {mc}** — Modrinth [Fabric]({mr}+fabric) · "
                         f"[NeoForge]({mr}+neoforge){forge} · [CurseForge]({CF_FILES})")
    minecrafts = " & ".join(minecraft_of(tag) for tag, _, _ in parsed)
    return {
        "title": f"Ala Industrial {version} — Minecraft {minecrafts}",
        "url": f"https://github.com/{REPO}/releases/tag/{leader_tag}",
        "description": description,
        "downloads": "\n".join(downloads),
        "media_tag": leader_tag,
    }


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--version", required=True)
    ap.add_argument("--line", action="append", default=[], metavar="TAG=CHANGELOG")
    ap.add_argument("--forge", action="append", default=[], metavar="TAG",
                    help="a line whose tagged tree carries forge/ (it shipped a Forge jar)")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    lines = []
    for spec in args.line:
        tag, _, path = spec.partition("=")
        with open(path, encoding="utf-8") as fh:
            lines.append((tag, fh.read()))
    try:
        unknown = sorted(set(args.forge) - {tag for tag, _ in lines})
        if unknown:
            raise ValueError(f"--forge names a tag with no --line: {', '.join(unknown)}")
        post = compose(args.version, lines, frozenset(args.forge))
    except ValueError as err:
        print(f"compose-announcement: {err}", file=sys.stderr)
        return 1
    with open(args.out, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(post, fh, ensure_ascii=False)
    print(post["title"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
