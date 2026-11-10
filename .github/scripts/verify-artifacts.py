#!/usr/bin/env python3
"""Validate release jars and write a SHA256 manifest."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def read_zip_text(jar: Path, member: str) -> str:
    with zipfile.ZipFile(jar) as zf:
        try:
            return zf.read(member).decode("utf-8")
        except KeyError:
            raise SystemExit(f"artifact: {jar} lacks {member}")


def validate_fabric(jar: Path, version: str, minecraft: str) -> dict:
    text = read_zip_text(jar, "fabric.mod.json")
    data = json.loads(text)
    actual = str(data.get("version", ""))
    if actual != version:
        raise SystemExit(f"artifact: Fabric metadata version {actual!r} != {version!r}")
    if data.get("id") != "alaindustrial":
        raise SystemExit(f"artifact: Fabric mod id is {data.get('id')!r}, expected 'alaindustrial'")
    if data.get("depends", {}).get("minecraft") != f"~{minecraft}":
        raise SystemExit("artifact: Fabric Minecraft dependency differs from release target")
    return {"loader": "fabric", "metadata_version": actual}


def validate_neoforge(jar: Path, version: str, minecraft: str) -> dict:
    text = read_zip_text(jar, "META-INF/neoforge.mods.toml")
    mods = re.findall(r"\[\[mods\]\](.*?)(?=\[|\Z)", text, re.S)
    expected = [m for m in mods if re.search(r'^modId\s*=\s*"alaindustrial"', m, re.M)]
    if len(expected) != 1 or not re.search(r'^version\s*=\s*"' + re.escape(version) + r'"', expected[0], re.M):
        raise SystemExit("artifact: NeoForge mod id/version differs from release target")
    next_minor = str(int(minecraft.split(".")[1]) + 1)
    wanted_range = f"[{minecraft}, 26.{next_minor})"
    dependencies = re.findall(r"\[\[dependencies\.alaindustrial\]\](.*?)(?=\[\[|\Z)", text, re.S)
    mc = [d for d in dependencies if re.search(r'^modId\s*=\s*"minecraft"', d, re.M)]
    if len(mc) != 1 or not re.search(r'^versionRange\s*=\s*"' + re.escape(wanted_range) + r'"', mc[0], re.M):
        raise SystemExit("artifact: NeoForge Minecraft dependency differs from release target")
    return {"loader": "neoforge", "metadata_version": version}


# The lines that ship a Forge jar (ADR-052: Forge on Minecraft 26.3 only). A Forge jar on any other line, or a
# line in this set without one, is a release that does not match its own publication matrix.
FORGE_LINES = ("26.3",)
# Forge's bundled Mixin (0.8.7) knows compatibility levels up to JAVA_21; a config asking for more fails to load.
FORGE_MAX_MIXIN_LEVEL = 21


def manifest_attribute(jar: Path, name: str) -> str | None:
    """One main-section attribute of META-INF/MANIFEST.MF, continuation lines joined."""
    text = read_zip_text(jar, "META-INF/MANIFEST.MF").replace("\r\n", "\n")
    joined = text.replace("\n ", "")
    for line in joined.split("\n\n", 1)[0].split("\n"):
        key, sep, value = line.partition(":")
        if sep and key.strip() == name:
            return value.strip()
    return None


def validate_forge(jar: Path, version: str, minecraft: str) -> dict:
    if minecraft not in FORGE_LINES:
        raise SystemExit(f"artifact: a Forge jar on Minecraft {minecraft}; Forge ships on {', '.join(FORGE_LINES)} only")
    text = read_zip_text(jar, "META-INF/mods.toml")
    mods = re.findall(r"\[\[mods\]\](.*?)(?=\[|\Z)", text, re.S)
    expected = [m for m in mods if re.search(r'^modId\s*=\s*"alaindustrial"', m, re.M)]
    if len(expected) != 1 or not re.search(r'^version\s*=\s*"' + re.escape(version) + r'"', expected[0], re.M):
        raise SystemExit("artifact: Forge mod id/version differs from release target")
    next_minor = str(int(minecraft.split(".")[1]) + 1)
    wanted_range = f"[{minecraft}, 26.{next_minor})"
    dependencies = re.findall(r"\[\[dependencies\.alaindustrial\]\](.*?)(?=\[\[|\Z)", text, re.S)
    mc = [d for d in dependencies if re.search(r'^modId\s*=\s*"minecraft"', d, re.M)]
    if len(mc) != 1 or not re.search(r'^versionRange\s*=\s*"' + re.escape(wanted_range) + r'"', mc[0], re.M):
        raise SystemExit("artifact: Forge Minecraft dependency differs from release target")
    if not any(re.search(r'^modId\s*=\s*"forge"', d, re.M) for d in dependencies):
        raise SystemExit("artifact: Forge metadata does not depend on forge")

    with zipfile.ZipFile(jar) as zf:
        names = zf.namelist()
        configs = sorted(n for n in names if "/" not in n and n.endswith(".mixins.json"))
        levels = {n: str(json.loads(zf.read(n).decode("utf-8")).get("compatibilityLevel", "")) for n in configs}
    for foreign in ("fabric.mod.json", "META-INF/neoforge.mods.toml"):
        if foreign in names:
            raise SystemExit(f"artifact: Forge jar carries another loader's metadata ({foreign})")
    leaked = [n for n in names if "gametest" in n.lower()]
    if leaked:
        raise SystemExit(f"artifact: Forge jar ships game-test files: {', '.join(leaked[:3])}")
    for root in ("assets/", "data/"):
        if not any(n.startswith(root) for n in names):
            raise SystemExit(f"artifact: Forge jar has no {root} tree")
    # Forge reads mixin configs from the manifest only: a config in the jar the manifest does not name never applies.
    declared = sorted(c.strip() for c in (manifest_attribute(jar, "MixinConfigs") or "").split(",") if c.strip())
    if "alaindustrial.mixins.json" not in declared:
        raise SystemExit("artifact: Forge jar manifest names no MixinConfigs (alaindustrial.mixins.json missing)")
    if declared != configs:
        raise SystemExit(f"artifact: Forge MixinConfigs {declared} differ from the configs in the jar {configs}")
    for name, level in levels.items():
        m = re.fullmatch(r"JAVA_(\d+)", level)
        if not m or int(m.group(1)) > FORGE_MAX_MIXIN_LEVEL:
            raise SystemExit(f"artifact: Forge mixin config {name} asks for {level or 'no level'}, "
                             f"Forge's Mixin stops at JAVA_{FORGE_MAX_MIXIN_LEVEL}")
    return {"loader": "forge", "metadata_version": version, "mixin_configs": declared}


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--version", required=True)
    p.add_argument("--minecraft", required=True, choices=["26.2", "26.3"])
    p.add_argument("--fabric", required=True, type=Path)
    p.add_argument("--neoforge", required=True, type=Path)
    p.add_argument("--forge", type=Path, help=f"required on Minecraft {', '.join(FORGE_LINES)}, refused elsewhere")
    p.add_argument("--output", required=True, type=Path)
    args = p.parse_args()

    loaders = [
        ("fabric", args.fabric, validate_fabric),
        ("neoforge", args.neoforge, validate_neoforge),
    ]
    if args.minecraft in FORGE_LINES and args.forge is None:
        raise SystemExit(f"artifact: Minecraft {args.minecraft} ships a Forge jar too; pass --forge")
    if args.forge is not None:
        loaders.append(("forge", args.forge, validate_forge))

    entries = []
    for loader, jar, validator in loaders:
        if not jar.is_file() or jar.stat().st_size <= 0:
            raise SystemExit(f"artifact: missing or empty {loader} jar: {jar}")
        if jar.name != f"alaindustrial-{loader}-{args.minecraft}-{args.version}.jar":
            raise SystemExit(f"artifact: {loader} jar filename does not end with -{args.version}.jar: {jar.name}")
        meta = validator(jar, args.version, args.minecraft)
        artifact_path = f"{loader}/build/libs/{jar.name}"
        entries.append({
            "loader": loader,
            "file": artifact_path,
            "name": jar.name,
            "size": jar.stat().st_size,
            "sha256": sha256(jar),
            **meta,
        })

    manifest = {
        "project": "Ala Industrial",
        "modid": "alaindustrial",
        "version": args.version,
        "minecraft": args.minecraft,
        "artifacts": entries,
    }
    # The documented local invocation writes into $PUB/build/, which a fresh clone does not
    # have: the root project builds nothing of its own. CI writes into the workspace root and
    # so never saw this, and the failure landed only on the release runbook (MOD-585).
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"OK artifacts validated; manifest written to {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
