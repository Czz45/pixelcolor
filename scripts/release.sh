#!/usr/bin/env bash
#
# release.sh —— PixelColor APK 发布脚本
#
# 作用（严格按照仓库约定）：
#   1. 自增 versionCode（+1）并把 versionName 末尾 +1（如 3.15 → 3.16）
#   2. 把改动提交并推送到 main
#   3. 在 GitHub 上创建 Release 并上传给定的 APK
#
# 用法：
#   ./scripts/release.sh <apk路径> [新版本名] [tag]
#   例：
#     ./scripts/release.sh app/build/outputs/apk/release/app-release.apk
#     ./scripts/release.sh app-release.apk 3.16 v3.16
#     ./scripts/release.sh app-release.apk 3.16 涂色      # 沿用原 tag 名
#
# 依赖：git、gh（已登录）、且仓库已设置 origin 远程。
#
set -euo pipefail

APK="${1:?用法: release.sh <apk路径> [版本名] [tag]}"
[ -f "$APK" ] || { echo "✗ 找不到 APK: $APK"; exit 1; }

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
BUILD_FILE="$REPO_ROOT/app/build.gradle.kts"

cd "$REPO_ROOT"

# —— 读取当前版本 ——
CUR_CODE=$(grep -oE 'versionCode = [0-9]+' "$BUILD_FILE" | grep -oE '[0-9]+')
CUR_NAME=$(grep -oE 'versionName = "[^"]+"' "$BUILD_FILE" | sed -E 's/versionName = "([^"]+)"/\1/')
echo "当前版本: versionName=$CUR_NAME  versionCode=$CUR_CODE"

# —— 计算新版本 ——
NEXT_CODE=$((CUR_CODE + 1))
NEW_VER="${2:-}"
if [ -z "$NEW_VER" ]; then
  # 默认：末位 +1（3.15 → 3.16）
  NEW_VER=$(echo "$CUR_NAME" | awk -F. '{$NF=$NF+1; OFS="."}1')
fi
TAG="${3:-v$NEW_VER}"

echo "新版本: versionName=$NEW_VER  versionCode=$NEXT_CODE  tag=$TAG"

# —— 修改 build.gradle.kts ——
sed -i -E "s/versionCode = [0-9]+/versionCode = $NEXT_CODE/" "$BUILD_FILE"
sed -i -E "s/versionName = \"[^\"]+\"/versionName = \"$NEW_VER\"/" "$BUILD_FILE"

# —— 提交并推送版本号改动 ——
git add "$BUILD_FILE"
git commit -q -m "bump version to $NEW_VER (versionCode $NEXT_CODE)"
git push origin main

# —— 创建 GitHub Release 并上传 APK ——
# 若 tag 已存在则追加资产，否则新建
if gh release view "$TAG" >/dev/null 2>&1; then
  echo "Release $TAG 已存在，追加 APK…"
  gh release upload "$TAG" "$APK" --clobber
else
  gh release create "$TAG" "$APK" \
    --title "$NEW_VER" \
    --notes "PixelColor $NEW_VER (versionCode $NEXT_CODE)" \
    --prerelease
fi

echo ""
echo "✅ 已发布：https://github.com/Czz45/pixelcolor/releases/tag/$TAG"
echo "   APK: $APK"
