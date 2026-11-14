#!/usr/bin/env python3
"""Generate fictional scenario voices locally with MLX; never uses system TTS.

Run in the optional voice environment (mlx-audio 0.6.5, soundfile 0.14.0).
Existing designed sample clips are the references, not recordings of real people.
"""
import argparse
import gc
import hashlib
import json
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--model', default='mlx-community/Qwen3-TTS-12Hz-1.7B-Base-bf16')
parser.add_argument('--persona', choices=['friend', 'grandma', 'mom', 'sibling'])
parser.add_argument('--limit', type=int)
args = parser.parse_args()

import mlx.core as mx
import numpy as np
import soundfile as sf
from mlx_audio.tts.utils import load_model

lines = json.loads((ASSETS / 'scenario_voice_lines.json').read_text())['personas']
reference_texts = json.loads((ASSETS / 'legacy_voice_texts.json').read_text())
work = ROOT / '.preview/voice-generation'
out = ASSETS / 'voices/scenarios'
work.mkdir(parents=True, exist_ok=True)
out.mkdir(parents=True, exist_ok=True)
jobs = []
for persona, scenarios in lines.items():
    if args.persona and persona != args.persona:
        continue
    reference = work / f'{persona}-reference.wav'
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', str(ASSETS / f'voices/legacy/{persona}-sample.mp3'), '-ar', '24000', '-ac', '1', str(reference)], check=True)
    for sid, text in scenarios.items():
        target = out / persona / f'{sid}.mp3'
        key = hashlib.sha256((persona + sid + text + 'neural-scenarios-v1').encode()).hexdigest()
        meta = target.with_suffix('.json')
        if target.exists() and meta.exists() and json.loads(meta.read_text()).get('fingerprint') == key:
            continue
        jobs.append((persona, sid, text, reference, target, key))
if args.limit:
    jobs = jobs[:args.limit]
if jobs:
    print(f'Loading local neural voice model for {len(jobs)} clips', flush=True)
    model = load_model(args.model)
    for index, (persona, sid, text, reference, target, key) in enumerate(jobs):
        started = time.monotonic()
        mx.random.seed(int(key[:8], 16))
        chunks = []
        for result in model.generate(text=text, lang_code='Korean', ref_audio=str(reference), ref_text=reference_texts[persona]['sample'], temperature=.65, top_p=.9, top_k=50, repetition_penalty=1.1, max_tokens=250, verbose=False):
            chunks.append(np.asarray(result.audio, dtype=np.float32).reshape(-1))
            rate = result.sample_rate
        if not chunks:
            raise RuntimeError(f'No speech for {persona}/{sid}')
        audio = np.concatenate(chunks)
        duration = len(audio) / rate
        if not .5 < duration < 25 or not np.isfinite(audio).all() or np.max(np.abs(audio)) < .01:
            raise RuntimeError(f'Invalid speech for {persona}/{sid}: {duration}')
        wav = work / 'working.wav'
        sf.write(str(wav), audio, rate)
        target.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', str(wav), '-af', 'silenceremove=start_periods=1:start_duration=0.03:start_threshold=-48dB,loudnorm=I=-18:TP=-2:LRA=9,afade=t=in:d=0.015', '-codec:a', 'libmp3lame', '-b:a', '128k', '-ar', '44100', str(target)], check=True)
        meta = dict(fingerprint=key, text=text, seconds=duration, model='Qwen3-TTS-12Hz-1.7B-Base', fictional=True, sha256=hashlib.sha256(target.read_bytes()).hexdigest())
        target.with_suffix('.json').write_text(json.dumps(meta, ensure_ascii=False, indent=2)+'\n')
        print(f'[{index+1}/{len(jobs)}] {persona}/{sid} {duration:.1f}s voice in {time.monotonic()-started:.1f}s', flush=True)
        mx.clear_cache()
    del model
    gc.collect()

manifest = {}
for persona, scenarios in lines.items():
    manifest[persona] = {}
    for sid in scenarios:
        target = out / persona / f'{sid}.mp3'
        if target.exists():
            manifest[persona][sid] = f'{persona}/{sid}.mp3'
(out / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n')
print('Clips ready:', sum(map(len, manifest.values())), '/ 200', flush=True)
