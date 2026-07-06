#!/usr/bin/env bash
# Sets up local development by linking Assets.zip into run/.
# Detects assets from the Hytale launcher install, or accepts a manual path.
#
# Usage:
#   ./setup.sh                        Link from launcher (defaults to pre-release)
#   ./setup.sh pre-release            Link from launcher pre-release install
#   ./setup.sh release                Link from launcher release install
#   ./setup.sh <path-to-Assets.zip>   Link a specific file

set -euo pipefail
cd "$(dirname "$0")"

mkdir -p run

# Resolve launcher install base for current OS
case "$(uname -s)" in
  Darwin)       INSTALL_BASE="$HOME/Library/Application Support/Hytale/install" ;;
  Linux)        INSTALL_BASE="${XDG_DATA_HOME:-$HOME/.local/share}/Hytale/install" ;;
  MINGW*|MSYS*) INSTALL_BASE="${APPDATA:-}/Hytale/install" ;;
  *)            INSTALL_BASE="" ;;
esac

link_assets() {
  ln -sf "$1" run/Assets.zip
  echo "Linked: run/Assets.zip -> $1"
}

link_from_launcher() {
  local PATCHLINE="$1"
  if [ -z "$INSTALL_BASE" ]; then
    echo "Could not determine launcher install path for this OS"
    exit 1
  fi
  local ASSETS="$INSTALL_BASE/$PATCHLINE/package/game/latest/Assets.zip"
  if [ ! -f "$ASSETS" ]; then
    echo "Assets not found for '$PATCHLINE' patchline: $ASSETS"
    exit 1
  fi
  link_assets "$ASSETS"
}

if [ $# -ge 1 ]; then
  case "$1" in
    pre-release|release)
      link_from_launcher "$1"
      ;;
    *)
      # Manual path
      ASSETS_SRC="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
      if [ ! -f "$ASSETS_SRC" ]; then
        echo "File not found: $1"
        exit 1
      fi
      link_assets "$ASSETS_SRC"
      ;;
  esac
else
  # No args: default to pre-release, fall back to release
  if [ -e run/Assets.zip ]; then
    echo "run/Assets.zip already exists"
    exit 0
  fi

  if [ -n "$INSTALL_BASE" ]; then
    for PATCHLINE in pre-release release; do
      ASSETS="$INSTALL_BASE/$PATCHLINE/package/game/latest/Assets.zip"
      if [ -f "$ASSETS" ]; then
        link_assets "$ASSETS"
        exit 0
      fi
    done
  fi

  echo "Assets.zip not found"
  echo ""
  echo "Usage:"
  echo "  $0                        Auto-detect from launcher (pre-release, then release)"
  echo "  $0 pre-release            Link from launcher pre-release install"
  echo "  $0 release                Link from launcher release install"
  echo "  $0 <path-to-Assets.zip>   Link a specific file"
  exit 1
fi
