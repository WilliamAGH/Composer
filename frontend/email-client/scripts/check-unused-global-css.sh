#!/usr/bin/env bash
#
# check-unused-global-css.sh
# Detects unused :global() CSS classes in Svelte projects
#
# This script extracts all :global() class selectors from CSS files and
# checks if they're actually used anywhere in the codebase. It's specifically
# designed to catch dead global CSS that Svelte's compiler won't warn about.
#

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

SRC_DIR="${1:-src}"
UNUSED_COUNT=0

echo "🔍 Checking for unused :global() CSS selectors in $SRC_DIR..."
echo ""

# Extract all unique top-level :global(.class-name) declarations
# Only matches standalone :global() at start of selector, not nested ones
GLOBAL_CLASSES=$(grep -rohE ':global\(\.[a-zA-Z0-9_-]+\)' "$SRC_DIR" --include="*.css" --include="*.svelte" 2>/dev/null | \
  grep -E '^:global' | \
  sed -E 's/:global\(\.([a-zA-Z0-9_-]+)\).*/\1/' | \
  sort -u)

if [ -z "$GLOBAL_CLASSES" ]; then
  echo -e "${GREEN}✅ No top-level :global() CSS selectors found to check${NC}"
  exit 0
fi

# Check each class for usage
while IFS= read -r class; do
  if [ -z "$class" ]; then
    continue
  fi

  # Search for usage in source files (look for the class in class="", className, etc.)
  if ! grep -rq --include="*.svelte" --include="*.js" --include="*.ts" \
       -e "class=\"[^\"]*${class}" \
       -e "class:[^=]*${class}" \
       -e "className=\"[^\"]*${class}" \
       -e "'${class}'" \
       -e "\"${class}\"" \
       "$SRC_DIR" 2>/dev/null; then

    # Find which file defines it
    DEFINING_FILE=$(grep -rl ":global(\.${class})" "$SRC_DIR" --include="*.css" --include="*.svelte" 2>/dev/null | head -1)
    echo -e "${YELLOW}⚠️  Unused: ${NC}.${class} ${RED}(in ${DEFINING_FILE})${NC}"
    UNUSED_COUNT=$((UNUSED_COUNT + 1))
  fi
done <<< "$GLOBAL_CLASSES"

# Final summary
echo ""
if [ $UNUSED_COUNT -eq 0 ]; then
  echo -e "${GREEN}✅ No unused :global() CSS selectors found${NC}"
  exit 0
else
  echo -e "${RED}❌ Found $UNUSED_COUNT unused :global() CSS selector(s)${NC}"
  echo ""
  echo "Note: This only checks top-level :global() classes."
  echo "      Nested global selectors (e.g., .parent :global(.child)) are not checked."
  exit 1
fi
