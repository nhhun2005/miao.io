#!/usr/bin/env bash
#
# Verify that animal and food IDs are consistent between frontend and backend.
# This script extracts IDs from both source files and compares them.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

FRONTEND_ANIMALS="$ROOT_DIR/frontend/src/game/data/animals.ts"
FRONTEND_FOODS="$ROOT_DIR/frontend/src/game/data/foods.ts"
BACKEND_ANIMALS="$ROOT_DIR/backend/src/main/java/com/mimope/server/game/data/AnimalDefinition.java"
BACKEND_FOODS="$ROOT_DIR/backend/src/main/java/com/mimope/server/game/data/FoodDefinition.java"

echo "=== Checking Animal ID Consistency ==="

# Extract frontend animal IDs (keys of the ANIMALS object)
FRONTEND_ANIMAL_IDS=$(grep -oP "^\s+(\w+):\s*\{" "$FRONTEND_ANIMALS" | grep -oP '\w+(?=:)' | sort)

# Extract backend animal IDs (map.put("xxx", ...) calls)
BACKEND_ANIMAL_IDS=$(grep -oP 'map\.put\("(\w+)"' "$BACKEND_ANIMALS" | grep -oP '"\w+"' | tr -d '"' | sort)

echo "Frontend animals: $FRONTEND_ANIMAL_IDS"
echo "Backend animals:  $BACKEND_ANIMAL_IDS"

if [ "$FRONTEND_ANIMAL_IDS" = "$BACKEND_ANIMAL_IDS" ]; then
    echo "✅ Animal IDs match!"
else
    echo "❌ Animal ID MISMATCH!"
    echo "Frontend only: $(comm -23 <(echo "$FRONTEND_ANIMAL_IDS") <(echo "$BACKEND_ANIMAL_IDS"))"
    echo "Backend only:  $(comm -13 <(echo "$FRONTEND_ANIMAL_IDS") <(echo "$BACKEND_ANIMAL_IDS"))"
    exit 1
fi

echo ""
echo "=== Checking Food ID Consistency ==="

# Extract frontend food IDs
FRONTEND_FOOD_IDS=$(grep -oP "^\s+(\w+):\s*\{" "$FRONTEND_FOODS" | grep -oP '\w+(?=:)' | sort)

# Extract backend food IDs
BACKEND_FOOD_IDS=$(grep -oP 'map\.put\("(\w+)"' "$BACKEND_FOODS" | grep -oP '"\w+"' | tr -d '"' | sort)

echo "Frontend foods: $FRONTEND_FOOD_IDS"
echo "Backend foods:  $BACKEND_FOOD_IDS"

if [ "$FRONTEND_FOOD_IDS" = "$BACKEND_FOOD_IDS" ]; then
    echo "✅ Food IDs match!"
else
    echo "❌ Food ID MISMATCH!"
    echo "Frontend only: $(comm -23 <(echo "$FRONTEND_FOOD_IDS") <(echo "$BACKEND_FOOD_IDS"))"
    echo "Backend only:  $(comm -13 <(echo "$FRONTEND_FOOD_IDS") <(echo "$BACKEND_FOOD_IDS"))"
    exit 1
fi

echo ""
echo "=== All IDs consistent ✅ ==="
