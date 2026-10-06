#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Marek Dudka
#
# Builds dist/Pulsorb-<version>-x86_64.AppImage from the Compose desktop app image.
# Needs: mksquashfs (squashfs-tools). Downloads the official AppImage type2 runtime once.
set -eu
cd "$(dirname "$0")/.."

VERSION=$(sed -n 's/.*packageVersion = "\(.*\)".*/\1/p' composeApp/build.gradle.kts)
WORK=build/appimage
APPDIR=$WORK/Pulsorb.AppDir
RUNTIME=$WORK/runtime-x86_64
OUT=dist/Pulsorb-$VERSION-x86_64.AppImage

./gradlew :composeApp:packageAppImage --console=plain -q

if [ ! -f "$RUNTIME" ]; then
    mkdir -p "$WORK"
    curl -fL -o "$RUNTIME" https://github.com/AppImage/type2-runtime/releases/download/continuous/runtime-x86_64
fi

rm -rf "$APPDIR"
mkdir -p "$APPDIR" dist
cp -a composeApp/build/compose/binaries/main/app/Pulsorb/. "$APPDIR/"
cp docs/icon/pulsorb-512.png "$APPDIR/pulsorb.png"
ln -s pulsorb.png "$APPDIR/.DirIcon"

cat > "$APPDIR/AppRun" <<'RUN'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/bin/Pulsorb" "$@"
RUN
chmod +x "$APPDIR/AppRun"

cat > "$APPDIR/pulsorb.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Pulsorb
Comment=Drum machine of glowing circles
Exec=Pulsorb
Icon=pulsorb
Categories=AudioVideo;Audio;Music;
Terminal=false
X-AppImage-Version=$VERSION
DESKTOP

rm -f "$WORK/Pulsorb.squashfs"
mksquashfs "$APPDIR" "$WORK/Pulsorb.squashfs" -root-owned -noappend -comp zstd -quiet
cat "$RUNTIME" "$WORK/Pulsorb.squashfs" > "$OUT"
chmod +x "$OUT"
echo "Built $OUT ($(du -h "$OUT" | cut -f1))"
