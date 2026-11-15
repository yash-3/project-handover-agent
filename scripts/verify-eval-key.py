#!/usr/bin/env python3
"""Checks an eval answer key against the cloned repo: each mustContain term must appear somewhere in the
files the agent can see (same excludes as the project config), each mustCite substring must match a file path,
and unanswerable questions are probed for obvious giveaway words.
Usage: scripts/verify-eval-key.py evals/shenyu.yaml benchmarks/repos/shenyu"""
import fnmatch, os, re, subprocess, sys

yaml_path, repo = sys.argv[1], sys.argv[2]
EXCLUDE = ["/src/test/", "shenyu-examples/", "shenyu-e2e/", "shenyu-integrated-test/", "docs/"]
SKIP_EXT = (".js", ".sql", ".md")
files = [f for f in subprocess.check_output(["git", "-C", repo, "ls-files"], text=True).split("\n") if f]
files = [f for f in files if not any(x in "/" + f for x in EXCLUDE) and not f.endswith(SKIP_EXT)]
text = {}
for f in files:
    try:
        text[f] = open(os.path.join(repo, f), errors="ignore").read().lower()
    except OSError:
        pass
blob = "\n".join(text.values())

# minimal parser for this file's flat structure
qs, cur = [], None
for line in open(yaml_path):
    m = re.match(r"\s*- id: (\S+)", line)
    if m:
        cur = {"id": m.group(1), "mustContain": [], "mustCite": [], "nf": False}
        qs.append(cur)
    elif cur is not None:
        m = re.match(r"\s*(mustContain|mustCite): \[(.*)\]", line)
        if m:
            cur[m.group(1)] = [t.strip().strip('"') for t in m.group(2).split(",") if t.strip()]
        elif "expectNotFound: true" in line:
            cur["nf"] = True

bad = 0
for q in qs:
    for t in q["mustContain"]:
        if t.lower() not in blob:
            print(f"MISSING TERM  {q['id']}: {t}"); bad += 1
    for c in q["mustCite"]:
        if not any(c.lower() in f.lower() for f in text):
            print(f"MISSING PATH  {q['id']}: {c}"); bad += 1
    if q["nf"]:
        for w in ("stripe", "paypal", "braintree", "pagerduty", "oncall", "on-call rota"):
            if w in blob:
                print(f"NOTE {q['id']}: sources mention '{w}', check the question is really unanswerable")
print(f"{len(qs)} questions, {sum(1 for q in qs if q['nf'])} unanswerable, {bad} problems, {len(files)} files visible")
sys.exit(1 if bad else 0)
