#!/usr/bin/env python3
"""Validate and package supplied human recordings; never synthesize a voice."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
PERSONAS = {"friend", "grandma", "mom", "sibling"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True, help="Directory containing friend/G01.wav, mom/B01.mp3, etc.")
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()
    if not args.source.is_dir():
        parser.error("Source directory does not exist.")
    if not shutil.which("ffprobe"):
        parser.error("ffprobe is required to validate the actual audio duration and format.")
    ids = {s["id"] for s in json.loads((ASSETS / "scenarios.json").read_text())["scenarios"]}
    accepted = []
    seen = set()
    for path in sorted(args.source.rglob("*")):
        if not path.is_file() or path.name.startswith("."):
            continue
        relative = path.relative_to(args.source)
        if path.suffix.lower() not in {".wav", ".mp3", ".m4a", ".ogg"}:
            continue
        if len(relative.parts) != 2 or relative.parts[0] not in PERSONAS or path.stem not in ids:
            parser.error(f"Use persona/scenario.ext naming: {relative}")
        key = (relative.parts[0], path.stem)
        if key in seen:
            parser.error(f"Multiple recordings for {key}")
        seen.add(key)
        if not 0 < path.stat().st_size <= 10 * 1024 * 1024:
            parser.error(f"File must be 1 byte to 10 MB: {relative}")
        result = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration:stream=codec_type", "-of", "json", str(path.resolve())], capture_output=True, text=True)
        if result.returncode:
            parser.error(f"Cannot decode {relative}")
        metadata = json.loads(result.stdout)
        duration = float(metadata.get("format", {}).get("duration", 0))
        if not .5 <= duration <= 30 or not any(s.get("codec_type") == "audio" for s in metadata.get("streams", [])):
            parser.error(f"Expected audio lasting 0.5–30 seconds: {relative}")
        accepted.append((path, key))
    if not accepted:
        parser.error("No supported recordings were found.")
    if args.check_only:
        print(f"Validated {len(accepted)} recordings; no files changed.")
        return
    directory = ASSETS / "voices/recorded"
    manifest_path = directory / "manifest.json"
    manifest = json.loads(manifest_path.read_text())
    # All inputs pass validation before the first asset is changed.
    for source, (persona, scenario) in accepted:
        relative = Path(persona) / f"{scenario}{source.suffix.lower()}"
        target = directory / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        manifest.setdefault(persona, {})[scenario] = relative.as_posix()
    temporary = manifest_path.with_suffix(".json.tmp")
    temporary.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    temporary.replace(manifest_path)
    print(f"Packaged {len(accepted)} recordings. Review the Git diff, then rebuild the APK.")


if __name__ == "__main__":
    main()
