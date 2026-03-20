#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-crawl}"

"$ROOT_DIR/scripts/compile.sh"

if [[ "$MODE" == "compare" ]]; then
  exec java --enable-preview -cp "$ROOT_DIR/out" io.github.tli.vtcrawler.CrawlerApplication demo-local --compare
fi

if [[ "$MODE" == "site" ]]; then
  exec java --enable-preview -cp "$ROOT_DIR/out" io.github.tli.vtcrawler.CrawlerApplication demo-local --site
fi

exec java --enable-preview -cp "$ROOT_DIR/out" io.github.tli.vtcrawler.CrawlerApplication demo-local
