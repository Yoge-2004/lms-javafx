#!/bin/sh
# postextract.sh — Icon & desktop integration for .tar.xz distribution
#
# Installs LibraryOS icons, a desktop entry, and a ~/bin symlink entirely
# inside the user's home directory. No root / sudo required.
#
# Called automatically by the launcher wrapper (libraryos) on the very
# first run after the tarball is extracted.
#
# Usage (standalone):
#   ./postextract.sh <install_dir>
#   e.g.  ./postextract.sh /home/user/LibraryOS
#
# The <install_dir> is the root of the extracted tarball — the directory
# that contains bin/ and lib/.

set -e

# ── Resolve install directory ─────────────────────────────────────────────────
INSTALL_DIR="${1:-}"
if [ -z "$INSTALL_DIR" ]; then
    echo "[LibraryOS] ERROR: No install directory supplied." >&2
    echo "  Usage: postextract.sh <install_dir>" >&2
    exit 1
fi

if [ ! -d "$INSTALL_DIR" ]; then
    echo "[LibraryOS] ERROR: '$INSTALL_DIR' is not a directory." >&2
    exit 1
fi

ICON_SRC="$INSTALL_DIR/lib/LibraryOS.png"
BIN_SRC="$INSTALL_DIR/bin/LibraryOS"

if [ ! -f "$ICON_SRC" ]; then
    echo "[LibraryOS] WARNING: Icon not found at '$ICON_SRC'. Skipping icon installation." >&2
fi

if [ ! -f "$BIN_SRC" ]; then
    echo "[LibraryOS] ERROR: Binary not found at '$BIN_SRC'." >&2
    exit 1
fi

# ── XDG base dirs (respect overrides, fall back to spec defaults) ─────────────
XDG_DATA_HOME="${XDG_DATA_HOME:-$HOME/.local/share}"
XDG_BIN_HOME="$HOME/.local/bin"

ICON_BASE="$XDG_DATA_HOME/icons/hicolor"
APPS_DIR="$XDG_DATA_HOME/applications"

# ── 1. Install icons into hicolor theme (user-level) ─────────────────────────
if [ -f "$ICON_SRC" ]; then
    echo "[LibraryOS] Installing icons..."
    for SIZE in 512x512 256x256 128x128 64x64 48x48 32x32 16x16; do
        DEST="$ICON_BASE/$SIZE/apps"
        mkdir -p "$DEST"
        cp "$ICON_SRC" "$DEST/libraryos.png"
    done
    echo "[LibraryOS] Icons installed to $ICON_BASE"
fi

# ── 2. Refresh icon cache ─────────────────────────────────────────────────────
if command -v gtk-update-icon-cache >/dev/null 2>&1; then
    gtk-update-icon-cache -f -t "$ICON_BASE" 2>/dev/null || true
    echo "[LibraryOS] Icon cache refreshed."
fi

# ── 3. Install .desktop entry ─────────────────────────────────────────────────
mkdir -p "$APPS_DIR"
DESKTOP_FILE="$APPS_DIR/libraryos.desktop"

cat > "$DESKTOP_FILE" <<EOF
[Desktop Entry]
Name=Library OS
Comment=Professional Library Management System
Exec=$BIN_SRC %U
Icon=libraryos
Terminal=false
Type=Application
Categories=Office;Database;Education;
StartupWMClass=LibraryOS
StartupNotify=true
EOF

echo "[LibraryOS] Desktop entry written to $DESKTOP_FILE"

# ── 4. Refresh desktop database ───────────────────────────────────────────────
if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database "$APPS_DIR" 2>/dev/null || true
    echo "[LibraryOS] Desktop database updated."
fi

# ── 5. Create ~/.local/bin symlink for terminal access ────────────────────────
mkdir -p "$XDG_BIN_HOME"
LINK="$XDG_BIN_HOME/libraryos"
ln -sf "$BIN_SRC" "$LINK"
echo "[LibraryOS] Symlink created: $LINK -> $BIN_SRC"

# ── 6. Remind user to add ~/.local/bin to PATH if needed ─────────────────────
case ":$PATH:" in
    *":$XDG_BIN_HOME:"*) ;;   # already in PATH, nothing to do
    *)
        echo ""
        echo "[LibraryOS] NOTE: '$XDG_BIN_HOME' is not in your PATH."
        echo "  Add the following line to your ~/.bashrc or ~/.zshrc:"
        echo "    export PATH=\"\$HOME/.local/bin:\$PATH\""
        ;;
esac

echo ""
echo "[LibraryOS] Integration complete."
