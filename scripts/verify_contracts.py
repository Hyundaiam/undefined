#!/usr/bin/env python3
"""Verify recording slots and the published Gleo/Android integration contracts."""
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
assets = ROOT / "app/src/main/assets"
scenarios = json.loads((assets / "scenarios.json").read_text())["scenarios"]
ids = {s["id"] for s in scenarios}
assert len(ids) == len(scenarios) == 38
assert {s["id"] for s in scenarios if s["tone"] == "GOOD"} == {f"G{i:02}" for i in range(1, 16)}
assert {s["id"] for s in scenarios if s["tone"] == "CAUTION"} == {f"B{i:02}" for i in range(1, 16)}
assert len([s for s in scenarios if s["tone"] == "COMMON"]) == 8
personas = {"friend", "grandma", "mom", "sibling"}
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
for source in (ROOT / "app/src/main/java").rglob("*.kt"):
    assert "TextToSpeech" not in source.read_text(), source
print("Contracts verified: 15 good + 15 caution + 8 common, four voices, three Gleo actions.")
