#!/usr/bin/env python3
"""Check full spoken content with local Korean ASR; does not certify acting quality."""
import argparse
import json
import re
from pathlib import Path
from mlx_audio.stt.utils import load_model

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--model', default='mlx-community/Qwen3-ASR-0.6B-8bit')
parser.add_argument('--persona')
parser.add_argument('--slot')
parser.add_argument('--review-only', action='store_true')
args = parser.parse_args()
model = load_model(args.model)
lines = json.loads((ASSETS/'scenario_voice_lines.json').read_text())['personas']
def normalize(t):
    return re.sub('[^가-힣a-z0-9]', '', t.lower()).replace('니','네').replace('내','네')
def rate(expected, actual):
    a,b=normalize(expected),normalize(actual)
    row=list(range(len(b)+1))
    for i,ca in enumerate(a,1):
        nxt=[i]
        for j,cb in enumerate(b,1):nxt.append(min(nxt[-1]+1,row[j]+1,row[j-1]+(ca!=cb)))
        row=nxt
    return row[-1]/max(len(a),1)
report=[]
previous_path=ROOT/'.preview/voice-generation/content-check-all-all.json'
previous=json.loads(previous_path.read_text()) if args.review_only else []
review_keys={(r['persona'],r['id']) for r in previous if r['review']}
for persona,scenarios in lines.items():
    if args.persona and persona != args.persona:continue
    for sid,expected in scenarios.items():
        if args.review_only and (persona,sid) not in review_keys:continue
        if args.slot and sid != args.slot:continue
        path=ASSETS/'voices/scenarios'/persona/f'{sid}.mp3'
        actual=model.generate(str(path),language='Korean',max_tokens=150).text
        error=rate(expected,actual)
        report.append(dict(persona=persona,id=sid,expected=expected,actual=actual,error=round(error,3),review=error>.23))
        print(f'{len(report)} {persona}/{sid} {error:.0%} | {actual}',flush=True)
target=ROOT/'.preview/voice-generation'/f'content-check-{args.persona or "all"}-{args.slot or "all"}.json'
if args.review_only:
    updated={(r['persona'],r['id']):r for r in report}
    report=[updated.get((r['persona'],r['id']),r) for r in previous]
target.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(f'{len(report)} clips checked; {sum(r["review"] for r in report)} need review.',flush=True)
