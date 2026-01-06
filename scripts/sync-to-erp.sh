#!/bin/bash

# Script pour synchroniser les fichiers compilés vers le projet ERP
# Usage: ./scripts/sync-to-erp.sh

set -e

PACKAGE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ERP_DIR="/Users/sandwidimohamed/Documents/projects-software/mes-projets/manage-app/songyamm/apps/erp"
ERP_NODE_MODULES="$ERP_DIR/node_modules/react-native-thermal-receipt-printer-image-qr"

echo "🔄 Building package..."
cd "$PACKAGE_DIR"
yarn codegen && yarn build

if [ ! -d "$ERP_NODE_MODULES" ]; then
    echo "❌ Error: Package not found in ERP node_modules"
    echo "   Expected: $ERP_NODE_MODULES"
    echo "   Please run 'yarn install' in the ERP project first"
    exit 1
fi

echo ""
echo "📦 Syncing files to ERP node_modules..."
echo "   From: $PACKAGE_DIR"
echo "   To:   $ERP_NODE_MODULES"
echo ""

# Sync lib files (compiled JavaScript/TypeScript)
echo "  → Syncing lib/ (compiled JS/TS)..."
rsync -av --delete \
    "$PACKAGE_DIR/lib/" \
    "$ERP_NODE_MODULES/lib/"

# Sync src files (for react-native source resolution)
echo "  → Syncing src/ (source files)..."
rsync -av --delete \
    "$PACKAGE_DIR/src/" \
    "$ERP_NODE_MODULES/src/"

# Sync native files
echo "  → Syncing ios/ (native iOS)..."
rsync -av --delete \
    --exclude="*.xcodeproj" \
    --exclude="*.xcworkspace" \
    --exclude="build/" \
    "$PACKAGE_DIR/ios/" \
    "$ERP_NODE_MODULES/ios/"

echo "  → Syncing android/ (native Android)..."
rsync -av --delete \
    --exclude="build/" \
    --exclude=".gradle/" \
    "$PACKAGE_DIR/android/" \
    "$ERP_NODE_MODULES/android/"

# Sync nitrogen generated files
echo "  → Syncing nitrogen/ (generated native code)..."
rsync -av --delete \
    "$PACKAGE_DIR/nitrogen/" \
    "$ERP_NODE_MODULES/nitrogen/"

# Sync other important files
echo "  → Syncing config files..."
rsync -av \
    "$PACKAGE_DIR/nitro.json" \
    "$PACKAGE_DIR/react-native-thermal-receipt-printer-image-qr.podspec" \
    "$ERP_NODE_MODULES/"

echo ""
echo "✅ Sync completed!"
echo ""
echo "📝 Next steps:"
echo "   1. In the ERP project, restart the bundler (press 'r' in Metro/Expo)"
echo "   2. For native changes (iOS/Android), rebuild the app:"
echo "      - iOS: cd $ERP_DIR/ios && pod install && cd .. && yarn ios"
echo "      - Android: cd $ERP_DIR && yarn android"
echo ""
