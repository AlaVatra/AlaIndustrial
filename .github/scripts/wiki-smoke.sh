#!/usr/bin/env bash
# Read-only checks of a deployed copy of the guide site. Goes through the public address like a visitor
# (Cloudflare included), changes nothing, sends no form.
#
#   bash smoke.sh                                          # https://wiki.alaindustrial.org
#   bash smoke.sh https://wiki-next.alaindustrial.org      # the staging name first
#   ORIGIN_IP=203.0.113.7 bash smoke.sh <base-url>         # also proves the origin refuses direct access
#
# Environment (all optional):
#   ORIGIN_IP         address of the origin server; enables the "direct access is refused" checks
#   CANONICAL_HOST    host written into canonical/hreflang/sitemap URLs (default wiki.alaindustrial.org)
#   MIN_SITEMAP_URLS  fewer URLs in sitemap.xml than this is a failure (default 1000)
#   SMOKE_WEBP        path of one animated preview that must exist (default /assets/anim/advanced_circuit.webp)
#   SMOKE_API         1 = also check /api/ (stage 2 only)
#   STRICT            1 = warnings count as failures
#
# Exit status: 0 all good (warnings allowed), 1 at least one failure, 2 cannot run (no curl).
# No pipe feeds "grep -q" anywhere: grep stops at the first match, curl then dies of SIGPIPE and pipefail
# turns a found string into a failure. Every text check reads a captured body through a here-string.
set -uo pipefail

BASE="${1:-https://wiki.alaindustrial.org}"
BASE="${BASE%/}"
HOST="${BASE#*://}"
HOST="${HOST%%/*}"
CANON_HOST="${CANONICAL_HOST:-wiki.alaindustrial.org}"
CANON="https://$CANON_HOST"
ORIGIN_IP="${ORIGIN_IP:-}"
MIN_URLS="${MIN_SITEMAP_URLS:-1000}"
WEBP="${SMOKE_WEBP:-/assets/anim/advanced_circuit.webp}"
STRICT="${STRICT:-0}"

command -v curl >/dev/null 2>&1 || { echo "curl is required" >&2; exit 2; }

fails=0
warns=0
pass() { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; fails=$((fails + 1)); }
warn() { printf '  warn  %s\n' "$1"; warns=$((warns + 1)); }

CURL=(curl -sS --max-time 30)

# status <path> <expected-code> [expected-location] [method]
status() {
  local out code loc
  out="$("${CURL[@]}" -o /dev/null -X "${4:-GET}" -w '%{http_code} %{redirect_url}' "$BASE$1" 2>/dev/null)" || { fail "$1 -> request failed"; return; }
  code="${out%% *}"
  loc="${out#* }"
  if [ "$code" != "$2" ]; then fail "$1 -> $code (want $2)"; return; fi
  if [ -n "${3:-}" ] && [ "$loc" != "$3" ]; then fail "$1 -> redirect to '$loc' (want '$3')"; return; fi
  pass "$1 -> $code${3:+ -> $3}"
}

# header_value <header-block> <name>: first value of a response header, case-insensitive
header_value() {
  local line
  line="$(grep -i -m1 "^$2:" <<<"$1" || true)"
  line="${line#*:}"
  line="${line%$'\r'}"
  printf '%s' "${line# }"
}

echo "Smoke test of $BASE (canonical host $CANON_HOST)"

# ---- the pages ---------------------------------------------------------------------------------------
echo "Pages"
home="$("${CURL[@]}" -w '\n%{http_code}' "$BASE/" 2>/dev/null)" || home=$'\n000'
code="${home##*$'\n'}"
body="${home%$'\n'*}"
if [ "$code" != 200 ]; then fail "/ -> $code (want 200)"; else
  if grep -qF 'rel="canonical"' <<<"$body"; then pass "/ -> 200 with a canonical link"; else fail "/ has no canonical link"; fi
  grep -qF 'en/index.html' <<<"$body" || fail "/ does not lead to en/index.html"
fi

page="$("${CURL[@]}" -w '\n%{http_code}' "$BASE/en/index.html" 2>/dev/null)" || page=$'\n000'
code="${page##*$'\n'}"
body="${page%$'\n'*}"
if [ "$code" != 200 ]; then fail "/en/index.html -> $code (want 200)"; else
  problems=""
  grep -qF "rel=\"canonical\" href=\"$CANON/en/index.html\"" <<<"$body" || problems="$problems canonical"
  grep -qF 'hreflang="x-default"' <<<"$body" || problems="$problems x-default"
  grep -qF '<html lang="en"' <<<"$body" || problems="$problems lang"
  if [ -z "$problems" ]; then pass "/en/index.html -> 200, canonical/hreflang/lang ok"; else fail "/en/index.html lacks:$problems"; fi
fi

sitemap="$("${CURL[@]}" -w '\n%{http_code}' "$BASE/sitemap.xml" 2>/dev/null)" || sitemap=$'\n000'
code="${sitemap##*$'\n'}"
body="${sitemap%$'\n'*}"
block_path=""
if [ "$code" != 200 ]; then fail "/sitemap.xml -> $code (want 200)"; else
  # The generator writes a sitemap index with one sitemap per language: follow it, count the pages.
  if grep -q '<sitemapindex' <<<"$body"; then
    grep -qF "<loc>$CANON/" <<<"$body" || fail "/sitemap.xml (index) does not use $CANON"
    parts=""
    for child in $(grep -o '<loc>[^<]*</loc>' <<<"$body" | sed -e 's#<loc>##' -e 's#</loc>##'); do
      child_path="${child#"$CANON"}"
      part="$("${CURL[@]}" "$BASE$child_path" 2>/dev/null)" || { fail "$child_path cannot be fetched"; continue; }
      parts+="$part"$'\n'
    done
    body="$parts"
  fi
  n="$(grep -o '<loc>' <<<"$body" | wc -l | tr -d ' ')"
  if [ "$n" -ge "$MIN_URLS" ]; then pass "/sitemap.xml -> 200, $n URLs"; else fail "/sitemap.xml has $n URLs (want at least $MIN_URLS)"; fi
  grep -qF "<loc>$CANON/" <<<"$body" || fail "/sitemap.xml does not use $CANON"
  # One page that is not a section index, for the "block page" check below.
  block_url="$(grep -o "<loc>$CANON/en/[^<]*/[a-z0-9_-]*\.html</loc>" <<<"$body" | grep -v '/index\.html' | grep -v 'all-content\|recipes\|getting-started' | sed -n '3p' || true)"
  block_url="${block_url#<loc>}"
  block_path="${block_url%</loc>}"
  block_path="${block_path#"$CANON"}"
fi

if [ -n "$block_path" ]; then
  page="$("${CURL[@]}" -w '\n%{http_code}' "$BASE$block_path" 2>/dev/null)" || page=$'\n000'
  code="${page##*$'\n'}"
  body="${page%$'\n'*}"
  if [ "$code" != 200 ]; then fail "$block_path -> $code (want 200)"; else
    if grep -qF "rel=\"canonical\" href=\"$CANON$block_path\"" <<<"$body"; then pass "$block_path -> 200, canonical matches its own address"; else fail "$block_path: canonical does not match its address"; fi
  fi
else
  fail "no block page found in the sitemap to test"
fi

robots="$("${CURL[@]}" -w '\n%{http_code}' "$BASE/robots.txt" 2>/dev/null)" || robots=$'\n000'
code="${robots##*$'\n'}"
body="${robots%$'\n'*}"
if [ "$code" != 200 ]; then fail "/robots.txt -> $code (want 200)"; else
  if grep -qF "Sitemap: $CANON/sitemap.xml" <<<"$body"; then pass "/robots.txt -> 200, points to the sitemap"; else fail "/robots.txt has no Sitemap line for $CANON"; fi
fi

# ---- files and types ---------------------------------------------------------------------------------
echo "Files"
hdr="$("${CURL[@]}" -I "$BASE$WEBP" 2>/dev/null || true)"
code="$(head -1 <<<"$hdr" | awk '{print $2}')"
ctype="$(header_value "$hdr" content-type)"
if [ "$code" != 200 ]; then fail "$WEBP -> ${code:-no answer} (want 200)"; elif [[ "$ctype" == image/webp* ]]; then pass "$WEBP -> 200 image/webp"; else fail "$WEBP content-type is '$ctype'"; fi

check_type() { # <path> <regex for content-type>
  local h c t
  h="$("${CURL[@]}" -I "$BASE$1" 2>/dev/null || true)"
  c="$(head -1 <<<"$h" | awk '{print $2}')"
  t="$(header_value "$h" content-type)"
  if [ "$c" != 200 ]; then fail "$1 -> ${c:-no answer}"; elif [[ "$t" =~ $2 ]]; then pass "$1 -> $t"; else fail "$1 content-type '$t' (want $2)"; fi
}
check_type /assets/guide-site.css '^text/css'
check_type /assets/guide-site.js '(text|application)/javascript'
check_type /assets/fonts/vt323-latin-400-normal.woff2 '^(font/woff2|application/font-woff2)'
check_type /en/search-index.json '^application/json'
check_type /sitemap.xml '(text|application)/xml'

stats="$("${CURL[@]}" -o /dev/null -w '%{http_code}' "$BASE/data/stats.json" 2>/dev/null)" || stats=000
case "$stats" in
  200) pass "/data/stats.json -> 200" ;;
  404) warn "/data/stats.json -> 404 (the home page hides the stats block without it)" ;;
  *) fail "/data/stats.json -> $stats" ;;
esac

status /CNAME 404
status /.nojekyll 404
status "/no-such-page-$RANDOM" 404
body="$("${CURL[@]}" "$BASE/no-such-page-$RANDOM" 2>/dev/null || true)"
if grep -q '404' <<<"$body"; then pass "the 404 page is the site's own"; else fail "the 404 response is not the site's own page"; fi
status /en/index.html 405 "" POST

# ---- one address per page ----------------------------------------------------------------------------
echo "Canonical forms (the .../index.html spelling is the only one answered with 200)"
status /en/ 301 "$BASE/en/index.html"
status /en 301 "$BASE/en/index.html"
status /en/getting-started 301 "$BASE/en/getting-started.html"
status /en/getting-started.html 200

# ---- transport and headers ---------------------------------------------------------------------------
echo "Transport and headers"
out="$(curl -sS --max-time 20 -o /dev/null -w '%{http_code} %{redirect_url}' "http://$HOST/" 2>/dev/null)" || out="000 "
code="${out%% *}"
loc="${out#* }"
if [[ "$code" =~ ^(301|308)$ ]] && [[ "$loc" == https://* ]]; then pass "http://$HOST/ -> $code -> https"; else fail "http://$HOST/ -> $code '$loc' (want 301/308 to https)"; fi

hdr="$("${CURL[@]}" -I -H 'Accept-Encoding: gzip, br' "$BASE/en/index.html" 2>/dev/null || true)"
want_header() { # <name> <substring>
  local v
  v="$(header_value "$hdr" "$1")"
  if [ -z "$v" ]; then fail "header $1 is missing"; elif [ -n "${2:-}" ] && [[ "$v" != *"$2"* ]]; then fail "header $1 is '$v' (want it to contain '$2')"; else pass "header $1 present"; fi
}
want_header strict-transport-security "max-age="
want_header x-content-type-options nosniff
want_header referrer-policy strict-origin-when-cross-origin
want_header permissions-policy ""
want_header content-security-policy "frame-ancestors 'none'"
want_header cache-control "max-age="
want_header content-type "text/html"
enc="$(header_value "$hdr" content-encoding)"
if [[ "$enc" == gzip* || "$enc" == br* || "$enc" == zstd* ]]; then pass "responses are compressed ($enc)"; else fail "HTML is not compressed (content-encoding '$enc')"; fi

# ---- the CSP lets the pages' own inline scripts run ----------------------------------------------------
echo "Content-Security-Policy against the real pages"
csp="$(header_value "$hdr" content-security-policy)"
if command -v python3 >/dev/null 2>&1 && [ -n "$csp" ]; then
  pages=("/" "/en/index.html" "/en/all-content.html" "/en/recipes.html")
  [ -n "$block_path" ] && pages+=("$block_path")
  for p in "${pages[@]}"; do
    html="$("${CURL[@]}" "$BASE$p" 2>/dev/null || true)"
    missing="$(CSP="$csp" python3 -c '
import base64, hashlib, os, re, sys
csp = os.environ["CSP"]
html = sys.stdin.read()
bad = []
for m in re.finditer(r"<script(?P<a>[^>]*)>(?P<b>.*?)</script>", html, re.S | re.I):
    if re.search(r"\bsrc\s*=", m.group("a"), re.I):
        continue
    t = re.search(r"\btype\s*=\s*[\"\x27]?([^\"\x27\s>]+)", m.group("a"), re.I)
    if t and t.group(1).lower() not in ("text/javascript", "application/javascript", "module"):
        continue
    h = base64.b64encode(hashlib.sha256(m.group("b").encode()).digest()).decode()
    if "sha256-" + h not in csp:
        bad.append(h)
print(" ".join(bad))
' <<<"$html")"
    if [ -z "$missing" ]; then pass "$p: every inline script is covered by the CSP"; else fail "$p: inline script(s) not in the CSP ($missing): update wiki_site_csp_script_hashes"; fi
  done
else
  warn "python3 or the CSP header is missing: inline script hashes were not compared"
fi

# ---- the origin refuses everything that is not Cloudflare ----------------------------------------------
echo "Direct access to the origin"
if [ -n "$ORIGIN_IP" ]; then
  for scheme_port in "https 443" "http 80"; do
    read -r scheme port <<<"$scheme_port"
    out="$(curl -sk --max-time 15 --resolve "$CANON_HOST:$port:$ORIGIN_IP" -o /dev/null -w '%{http_code}' "$scheme://$CANON_HOST/" 2>/dev/null)"
    rc=$?
    # 444 closes the connection without a reply: curl reports exit 52 (empty reply) or 56, code 000.
    if [ "$out" = 000 ] || [ "$rc" = 52 ] || [ "$rc" = 56 ] || [ "$out" = 403 ]; then pass "$scheme straight to the origin with the wiki Host header is refused"; else fail "$scheme straight to the origin answered $out (want it closed)"; fi
  done
  body="$(curl -sk --max-time 15 "https://$ORIGIN_IP/" 2>/dev/null || true)"
  if grep -q 'Ala Industrial Wiki' <<<"$body"; then fail "the wiki answers on the bare IP (no Host header)"; else pass "the bare IP does not serve the wiki"; fi
else
  warn "ORIGIN_IP not set: direct access to the origin was not checked"
fi

# ---- stage 2 ---------------------------------------------------------------------------------------------
if [ "${SMOKE_API:-0}" = 1 ]; then
  echo "API"
  status /api/health 200
  status /_/ 404
  status /api/settings 404
  status /api/realtime 404
else
  echo "API: skipped (SMOKE_API=1 to check /api/)"
fi

echo
echo "$fails failure(s), $warns warning(s)"
if [ "$fails" -gt 0 ]; then exit 1; fi
if [ "$STRICT" = 1 ] && [ "$warns" -gt 0 ]; then exit 1; fi
exit 0
