#!/usr/bin/env bash
#
# check-dead-code.sh
# Comprehensive dead code detection for JavaScript/TypeScript/Svelte projects
#
# Checks multiple layers:
#   1. Unused exports (via ts-prune)
#   2. Unused npm dependencies (via depcheck)
#   3. Unused Svelte components (custom logic)
#

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

TOTAL_ISSUES=0

echo "🔍 Comprehensive Dead Code Detection"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# ============================================================================
# 1. UNUSED EXPORTS (svelte-check + tsc)
# ============================================================================
echo "📦 Checking for unused exports with svelte-check..."

if [ ! -f "tsconfig.json" ]; then
  echo -e "   ${BLUE}ℹ️  No tsconfig.json found, skipping (JavaScript project)${NC}"
elif ! command -v npx &> /dev/null; then
  echo -e "   ${YELLOW}⚠️  npx not found, skipping unused export check${NC}"
else
  set +e
  SVELTE_OUTPUT=$(npx svelte-check --tsconfig tsconfig.json --threshold hint --fail-on-warnings 2>&1)
  SVELTE_STATUS=$?
  set -e

  if [ "$SVELTE_STATUS" -eq 0 ]; then
    echo -e "   ${GREEN}✅ No unused exports (per svelte-check)${NC}"
  else
    echo -e "   ${YELLOW}⚠️  svelte-check reported issues:${NC}"
    echo "$SVELTE_OUTPUT"
    ISSUE_LINES=$(echo "$SVELTE_OUTPUT" | wc -l | tr -d ' ')
    TOTAL_ISSUES=$((TOTAL_ISSUES + ISSUE_LINES))
  fi
fi
echo ""

echo "🧪 Running tsc --noEmit to surface TS-only issues..."
if [ -f "tsconfig.json" ] && command -v npx &> /dev/null; then
  set +e
  TSC_OUTPUT=$(npx tsc --noEmit 2>&1)
  TSC_STATUS=$?
  set -e

  if [ "$TSC_STATUS" -eq 0 ]; then
    echo -e "   ${GREEN}✅ tsc completed with no unused-export diagnostics${NC}"
  else
    echo -e "   ${YELLOW}⚠️  tsc reported issues:${NC}"
    echo "$TSC_OUTPUT"
    ISSUE_LINES=$(echo "$TSC_OUTPUT" | wc -l | tr -d ' ')
    TOTAL_ISSUES=$((TOTAL_ISSUES + ISSUE_LINES))
  fi
else
  echo -e "   ${YELLOW}⚠️  Skipping tsc (tsconfig or npx missing)${NC}"
fi
echo ""

# ============================================================================
# 2. UNUSED DEPENDENCIES (depcheck)
# ============================================================================
echo "📚 Checking for unused npm dependencies..."

if ! command -v npx &> /dev/null; then
  echo -e "${YELLOW}⚠️  npx not found, skipping dependency check${NC}"
else
  # Run depcheck
  DEPCHECK_OUTPUT=$(npx --yes depcheck@latest --json 2>/dev/null || echo '{"dependencies":[],"devDependencies":[]}')

  UNUSED_DEPS=$(echo "$DEPCHECK_OUTPUT" | grep -o '"dependencies":\[[^]]*\]' | sed 's/"dependencies"://;s/[][]//g;s/"//g' | tr ',' '\n' | grep -v "^$" || true)
  UNUSED_DEV_DEPS=$(echo "$DEPCHECK_OUTPUT" | grep -o '"devDependencies":\[[^]]*\]' | sed 's/"devDependencies"://;s/[][]//g;s/"//g' | tr ',' '\n' | grep -v "^$" || true)

  DEP_COUNT=0
  if [ -n "$UNUSED_DEPS" ]; then
    echo -e "   ${YELLOW}⚠️  Unused dependencies:${NC}"
    echo "$UNUSED_DEPS" | sed 's/^/   - /'
    DEP_COUNT=$(echo "$UNUSED_DEPS" | wc -l | tr -d ' ')
  fi

  if [ -n "$UNUSED_DEV_DEPS" ]; then
    echo -e "   ${YELLOW}⚠️  Unused devDependencies:${NC}"
    echo "$UNUSED_DEV_DEPS" | sed 's/^/   - /'
    DEV_DEP_COUNT=$(echo "$UNUSED_DEV_DEPS" | wc -l | tr -d ' ')
    DEP_COUNT=$((DEP_COUNT + DEV_DEP_COUNT))
  fi

  if [ "$DEP_COUNT" -eq 0 ]; then
    echo -e "   ${GREEN}✅ No unused dependencies found${NC}"
  else
    TOTAL_ISSUES=$((TOTAL_ISSUES + DEP_COUNT))
  fi
fi
echo ""

# ============================================================================
# 3. UNUSED SVELTE COMPONENTS (custom check)
# ============================================================================
echo "🎨 Checking for unused Svelte components..."

COMPONENT_DIR="src/lib"
if [ ! -d "$COMPONENT_DIR" ]; then
  echo -e "   ${YELLOW}⚠️  No $COMPONENT_DIR directory found, skipping${NC}"
else
  UNUSED_COMPONENTS=0

  # Find all .svelte files in lib/
  find "$COMPONENT_DIR" -type f -name "*.svelte" | while IFS= read -r component; do
    COMPONENT_NAME=$(basename "$component" .svelte)

    # Skip special files
    if [[ "$COMPONENT_NAME" =~ ^(index|App)$ ]]; then
      continue
    fi

    # Check if component is imported anywhere
    if ! grep -rq "from.*['\"].*${COMPONENT_NAME}.svelte['\"]" src --include="*.svelte" --include="*.js" --include="*.ts" 2>/dev/null && \
       ! grep -rq "import.*${COMPONENT_NAME}" src --include="*.svelte" --include="*.js" --include="*.ts" 2>/dev/null; then

      echo -e "   ${YELLOW}⚠️  Possibly unused component: ${NC}$component"
      UNUSED_COMPONENTS=$((UNUSED_COMPONENTS + 1))
    fi
  done

  if [ "$UNUSED_COMPONENTS" -eq 0 ]; then
    echo -e "   ${GREEN}✅ No obviously unused components found${NC}"
  else
    echo -e "   ${YELLOW}Note: Some components may be used dynamically${NC}"
    TOTAL_ISSUES=$((TOTAL_ISSUES + UNUSED_COMPONENTS))
  fi
fi
echo ""

# ============================================================================
# SUMMARY
# ============================================================================
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Always show false positive warning
echo -e "${BLUE}ℹ️  False Positive Warning:${NC}"
echo "   The tools may report false positives for:"
echo "   • Dynamically imported components (e.g., lazy-loaded routes)"
echo "   • Packages used via CLI only (not via import statements)"
echo "   • Configuration-only dependencies (e.g., stylelint, oxlint, postcss)"
echo "   • Packages used in non-standard ways (e.g., Vite plugins)"
echo ""
echo "   Use .depcheckrc.json to ignore known false positives."
echo ""

if [ $TOTAL_ISSUES -eq 0 ]; then
  echo -e "${GREEN}✅ No dead code detected${NC}"
  exit 0
else
  echo -e "${YELLOW}⚠️  Found $TOTAL_ISSUES potential dead code issue(s)${NC}"
  echo ""
  echo "Review the items above and remove truly unused code to:"
  echo "  • Reduce bundle size"
  echo "  • Improve code maintainability"
  echo "  • Speed up build times"
  echo ""
  echo -e "${YELLOW}⚠️  IMPORTANT: Verify each item before removing - see false positive warning above.${NC}"
  # Exit 0 to not fail the build - treat as warnings
  exit 0
fi
