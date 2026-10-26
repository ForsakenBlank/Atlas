#!/bin/sh
# puts atlas in your app launcher (rofi, wofi, fuzzel, walker...) and adds an "atlas" command
# run it from inside the unpacked folder, run it again after unpacking a newer version
set -e

here="$(cd "$(dirname "$0")" && pwd)"
target="$HOME/.local/opt/atlas"
apps="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
icons="${XDG_DATA_HOME:-$HOME/.local/share}/icons/hicolor/512x512/apps"

if [ "$here" != "$target" ]; then
    rm -rf "$target"
    mkdir -p "$(dirname "$target")"
    cp -r "$here" "$target"
fi

mkdir -p "$apps" "$icons" "$HOME/.local/bin"
cp "$target/lib/Atlas.png" "$icons/atlas.png" 2>/dev/null || true
ln -sf "$target/bin/Atlas" "$HOME/.local/bin/atlas"

# StartupWMClass matches the window class atlas sets, so launchers and docks group it properly
cat > "$apps/atlas.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Atlas
Comment=Notes, trackers, money and plans that sync with your phone
Exec=$target/bin/Atlas
Icon=atlas
Terminal=false
Categories=Office;Utility;
StartupWMClass=atlas
DESKTOP

command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$apps" || true
echo "Atlas is installed. Find it in your app launcher or run: atlas"
echo "Your data lives in ${XDG_DATA_HOME:-$HOME/.local/share}/atlas, uninstalling never touches it."
