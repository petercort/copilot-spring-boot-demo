#!/usr/bin/env bash
# =============================================================================
# Playwright CLI Test Runner — scripts/playwright-cli-test.sh
#
# Uses @playwright/cli to drive a real browser against the demo-ui.
# Each suite navigates a tab, runs DOM assertions via `eval --raw`, and
# captures a screenshot. Exit code is non-zero if any assertion fails.
#
# Usage:
#   bash scripts/playwright-cli-test.sh [--headed] [--base-url <url>]
#
# Requirements:
#   npm install -g @playwright/cli@latest
#   playwright-cli install --skills
# =============================================================================

set -euo pipefail

BASE_URL="http://localhost:8090"
HEADED=false
SESSION="demo-ui-test"

for arg in "$@"; do
  case $arg in
    --headed)   HEADED=true ;;
    --base-url) BASE_URL="${2:-}" ;;
  esac
done

SCREENSHOT_DIR="$(cd "$(dirname "$0")/.." && pwd)/results/screenshots"
mkdir -p "$SCREENSHOT_DIR"

PASS=0
FAIL=0

# ── Helpers ──────────────────────────────────────────────────────────────────

pc() { playwright-cli -s="$SESSION" "$@" 2>/dev/null; }

# eval returning a raw value (strips JSON quotes/whitespace)
pcval() { playwright-cli -s="$SESSION" eval "$1" --raw 2>/dev/null | tr -d '"[:space:]'; }

pass() { echo "  ✓  $1"; PASS=$((PASS + 1)); }
fail() { echo "  ✗  $1  —  $2"; FAIL=$((FAIL + 1)); }

assert_eq() {
  local name="$1" got="$2" expected="$3"
  if [ "$got" = "$expected" ]; then pass "$name"; else fail "$name" "got '$got', want '$expected'"; fi
}

assert_gte() {
  local name="$1" got="$2" min="$3"
  if [ "$got" -ge "$min" ] 2>/dev/null; then pass "$name"; else fail "$name" "got '$got', want >=$min"; fi
}

assert_contains() {
  local name="$1" got="$2" needle="$3"
  if echo "$got" | grep -q "$needle"; then pass "$name"; else fail "$name" "'$needle' not found in '$got'"; fi
}

assert_not_contains() {
  local name="$1" got="$2" needle="$3"
  if ! echo "$got" | grep -q "$needle"; then pass "$name"; else fail "$name" "unexpected '$needle' in '$got'"; fi
}

screenshot() {
  local file="$SCREENSHOT_DIR/$1"
  playwright-cli -s="$SESSION" screenshot --filename="$file" 2>/dev/null
  echo "  📸  $1"
}

# ── Start ─────────────────────────────────────────────────────────────────────

echo ""
echo "🎭  Playwright CLI Test Runner"
echo "   base-url : $BASE_URL"
echo "   mode     : $( $HEADED && echo 'headed' || echo 'headless' )"
echo ""

OPEN_ARGS=""
$HEADED && OPEN_ARGS="--headed"
playwright-cli -s="$SESSION" open $OPEN_ARGS "$BASE_URL" 2>/dev/null

# ── Suite 1: Page Load ────────────────────────────────────────────────────────

echo "Suite: Page Load"

TITLE=$(pcval "() => document.title")
assert_contains "Page title" "$TITLE" "E-Commerce"

NAV=$(playwright-cli -s="$SESSION" eval "() => [...document.querySelectorAll('nav button')].map(b=>b.textContent.trim()).join(',')" --raw 2>/dev/null)
assert_contains "Customers nav button" "$NAV" "Customers"
assert_contains "Products nav button"  "$NAV" "Products"
assert_contains "Orders nav button"    "$NAV" "Orders"
assert_contains "Create Order nav button" "$NAV" "Create Order"

screenshot "cli-01-page-load.png"

# ── Suite 2: Customers Tab ────────────────────────────────────────────────────

echo ""
echo "Suite: Customers Tab"

pc eval "() => showTab('customers')" >/dev/null
sleep 1

CUST_ROWS=$(pcval "() => document.querySelectorAll('#customers-body tr').length")
assert_gte "Customer table has rows" "$CUST_ROWS" 3

FIRST_NAME=$(pcval "() => document.querySelector('#customers-body tr td:nth-child(2)')?.textContent")
assert_not_contains "Customer name is not undefined" "$FIRST_NAME" "undefined"
assert_contains     "Customer name is not empty"     "$FIRST_NAME" "."

FIRST_EMAIL=$(pcval "() => document.querySelector('#customers-body tr td:nth-child(3)')?.textContent")
assert_contains "Customer email present" "$FIRST_EMAIL" "@"

screenshot "cli-02-customers.png"

# ── Suite 3: Products Tab ─────────────────────────────────────────────────────

echo ""
echo "Suite: Products Tab"

pc eval "() => showTab('products')" >/dev/null
sleep 1

PROD_ROWS=$(pcval "() => document.querySelectorAll('#products-body tr').length")
assert_gte "Product table has rows" "$PROD_ROWS" 6

FIRST_PROD=$(pcval "() => document.querySelector('#products-body tr td:nth-child(2)')?.textContent")
assert_contains "Product name present" "$FIRST_PROD" "Laptop"

FIRST_PRICE=$(pcval "() => document.querySelector('#products-body tr td:nth-child(3)')?.textContent")
assert_contains "Product price present" "$FIRST_PRICE" "\$"

STOCK=$(pcval "() => parseInt(document.querySelector('#products-body tr td:nth-child(4)')?.textContent ?? '0')")
assert_gte "Product stock > 0" "$STOCK" 1

screenshot "cli-03-products.png"

# ── Suite 4: Orders Tab ───────────────────────────────────────────────────────

echo ""
echo "Suite: Orders Tab"

pc eval "() => showTab('orders')" >/dev/null
sleep 1

ERR_DISPLAY=$(pcval "() => getComputedStyle(document.getElementById('orders-error')).display")
assert_eq "Orders error div hidden" "$ERR_DISPLAY" "none"

ORDERS_HEADING=$(pcval "() => document.querySelector('#tab-orders h2')?.textContent")
assert_contains "Orders heading visible" "$ORDERS_HEADING" "Orders"

screenshot "cli-04-orders.png"

# ── Suite 5: Create Order Form ────────────────────────────────────────────────

echo ""
echo "Suite: Create Order Form"

pc eval "() => showTab('create-order')" >/dev/null
sleep 1

CUST_OPTS=$(pcval "() => document.querySelector('#create-order-form select')?.options.length ?? 0")
assert_gte "Customer dropdown populated" "$CUST_OPTS" 2

SUBMIT=$(pcval "() => !!document.querySelector('[data-testid=\"submit-order\"]')")
assert_eq "Place Order button exists" "$SUBMIT" "true"

FORM_ERR=$(pcval "() => getComputedStyle(document.getElementById('order-error')).display")
assert_eq "Order error div hidden" "$FORM_ERR" "none"

screenshot "cli-05-create-order.png"

# ── Cleanup ───────────────────────────────────────────────────────────────────

playwright-cli -s="$SESSION" close 2>/dev/null

# ── Summary ───────────────────────────────────────────────────────────────────

TOTAL=$((PASS + FAIL))
echo ""
echo "─────────────────────────────────────────────"
if [ "$FAIL" -eq 0 ]; then
  echo "✅  $PASS/$TOTAL tests passed"
else
  echo "❌  $PASS/$TOTAL passed  |  $FAIL failed"
fi
echo "   Screenshots → results/screenshots/cli-0*.png"
echo "─────────────────────────────────────────────"
echo ""

exit $FAIL
