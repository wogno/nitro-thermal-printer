#!/bin/bash

# Script pour watch et rebuild automatiquement le package
# Usage: ./scripts/watch-and-build.sh

set -e

PACKAGE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ERP_DIR="/Users/sandwidimohamed/Documents/projects-software/mes-projets/manage-app/songyamm/apps/erp"

echo "🔍 Watching for changes in $PACKAGE_DIR"
echo "📦 Auto-rebuilding package..."
echo ""

# Fonction pour builder
build_package() {
    echo "🔄 Rebuilding package..."
    cd "$PACKAGE_DIR"
    yarn codegen && yarn build
    echo "✅ Build completed at $(date +%H:%M:%S)"
    echo ""
}

# Build initial
build_package

# Watch les fichiers source
if command -v fswatch &> /dev/null; then
    echo "👀 Watching for changes (using fswatch)..."
    fswatch -o "$PACKAGE_DIR/src" "$PACKAGE_DIR/nitro.json" | while read; do
        build_package
    done
elif command -v inotifywait &> /dev/null; then
    echo "👀 Watching for changes (using inotifywait)..."
    while inotifywait -r -e modify,create,delete "$PACKAGE_DIR/src" "$PACKAGE_DIR/nitro.json"; do
        build_package
    done
else
    echo "⚠️  No file watcher found. Install 'fswatch' (macOS) or 'inotify-tools' (Linux) for auto-rebuild."
    echo "   Or manually run: cd $PACKAGE_DIR && yarn codegen && yarn build"
fi
