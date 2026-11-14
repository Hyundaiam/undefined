#!/usr/bin/env python3
"""Verify recording slots and the published Gleo/Android integration contracts."""
import json
import wave
import hashlib
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
assets = ROOT / "app/src/main/assets"
scenarios = json.loads((assets / "scenarios.json").read_text())["scenarios"]
ids = {s["id"] for s in scenarios}
assert len(ids) == len(scenarios) == 58
assert {s["id"] for s in scenarios if s["tone"] == "GOOD"} == {f"G{i:02}" for i in range(1, 26)}
assert {s["id"] for s in scenarios if s["tone"] == "CAUTION"} == {f"B{i:02}" for i in range(1, 26)}
assert len([s for s in scenarios if s["tone"] == "COMMON"]) == 8
personas = {"friend", "grandma", "mom", "sibling"}
driving_ids = {s["id"] for s in scenarios if s["tone"] != "COMMON"}
voice_lines = json.loads((assets / "scenario_voice_lines.json").read_text())["personas"]
generated = json.loads((assets / "voices/scenarios/manifest.json").read_text())
assert set(generated) == set(voice_lines) == personas
digests = set()
for persona in personas:
    assert set(generated[persona]) == set(voice_lines[persona]) == driving_ids
    for sid, relative in generated[persona].items():
        path = (assets / "voices/scenarios" / relative).resolve()
        assert path.is_relative_to((assets / "voices/scenarios").resolve()) and path.is_file()
        meta = json.loads(path.with_suffix(".json").read_text())
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        assert digest == meta["sha256"] and meta["text"] == voice_lines[persona][sid] and meta["fictional"]
        assert .5 < meta["seconds"] < 25 and digest not in digests
        digests.add(digest)
for scenario in scenarios:
    assert scenario["recordingText"] and scenario["trigger"]
    if scenario.get("legacyClip"):
        for persona in personas:
            assert (assets / f"voices/legacy/{persona}-{scenario['legacyClip']}.mp3").is_file()
packaged = json.loads((assets / "voices/recorded/manifest.json").read_text())
for persona, clips in packaged.items():
    assert persona in personas
    for scenario, path in clips.items():
        assert scenario in ids
        resolved = (assets / "voices/recorded" / path).resolve()
        assert resolved.is_relative_to((assets / "voices/recorded").resolve()) and resolved.is_file()
schema = json.loads((assets / "schema.json").read_text())
functions = [call["function"] for call in schema["actionCalls"]]
expected = {"GET_DRIVING_CONTEXT", "GET_DRIVING_PROGRESS", "GET_RECORDING_STATUS"}
assert {f["name"] for f in functions} == expected and len(functions) <= 3
assert all(f["component"] == "service" for f in functions)
ns = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
actions = {a.attrib[ns + "name"] for a in manifest.findall("application/receiver/intent-filter/action")}
assert actions == expected
assert not any("CONTROL_" in p.attrib[ns + "name"] for p in manifest.findall("uses-permission"))
activities = {a.attrib[ns + "name"]: a for a in manifest.findall("application/activity")}
def driving_optimized(name):
    return next(m.attrib[ns + "value"] for m in activities[name].findall("meta-data") if m.attrib[ns + "name"] == "distractionOptimized")
assert driving_optimized(".DriveActivity") == "true"
assert driving_optimized(".MainActivity") == "false"
with wave.open(str(assets / "voices/preroll.wav")) as preroll:
    assert preroll.getsampwidth() == 2 and preroll.getnchannels() == 1
    assert 0.15 <= preroll.getnframes() / preroll.getframerate() <= 0.2
    assert not any(preroll.readframes(preroll.getnframes()))
for source in (ROOT / "app/src/main/java").rglob("*.kt"):
    assert "TextToSpeech" not in source.read_text(), source
print("Contracts verified: 25 good + 25 caution + 8 common, 200 distinct scenario voices, three Gleo actions.")
