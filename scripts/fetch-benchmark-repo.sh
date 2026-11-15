#!/usr/bin/env bash
# Shallow-clones a PUBLIC repository for benchmarking into benchmarks/repos/<name> (git-ignored).
# Usage: scripts/fetch-benchmark-repo.sh https://github.com/<owner>/<repo> <name>
# Prints the measured size so you can check it before using it as a benchmark.
set -euo pipefail
url="${1:?usage: $0 <public git url> <name>}"
name="${2:?usage: $0 <public git url> <name>}"
[[ "$name" =~ ^[a-z0-9][a-z0-9-]*$ ]] || { echo "name must be lowercase letters, digits and '-'" >&2; exit 2; }
dest="benchmarks/repos/$name"
[ -e "$dest" ] && { echo "$dest already exists; delete it first to re-fetch" >&2; exit 1; }
git clone --depth 1 --quiet "$url" "$dest"
echo "Cloned $url into $dest"
echo "Files:       $(git -C "$dest" ls-files | wc -l | tr -d ' ')"
echo "Lines (all tracked text files, rough): $(git -C "$dest" ls-files -z | xargs -0 cat 2>/dev/null | wc -l | tr -d ' ')"
echo "Next: write projects/$name.yaml (type local, url $dest) and run: benchmark $name"
