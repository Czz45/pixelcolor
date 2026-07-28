# 发布流程（APK 上传规则）

> **仓库约定（长期有效）**：以后每次有**编译好的 APK**，就直接上传到本仓库的 GitHub Release，
> 并且在上传前**必须把版本号往上加一**。

---

## 版本号现状

- `versionName = "3.15"`（当前）
- `versionCode = 82`（当前）

> 每次发布：
> - `versionCode` **+1**（82 → 83 → 84 …，Google Play / 安卓靠它判断新旧）
> - `versionName` 末尾 **+1**（3.15 → 3.16 → 3.17 …，给人看的版本名）

---

## 一键发布（推荐）

脚本 `scripts/release.sh` 会自动完成「改版本号 → 提交 → 创建 Release → 上传 APK」。

```bash
# 先登录 gh（只需一次）：gh auth login

# 用法：./scripts/release.sh <apk路径> [新版本名] [tag]
./scripts/release.sh app/build/outputs/apk/release/app-release.apk
# 等效于：自动 3.15→3.16，tag=v3.16，创建 pre-release 并上传 APK

# 想沿用原来的 tag 名「涂色」：
./scripts/release.sh app-release.apk 3.16 涂色
```

脚本会：
1. 把 `app/build.gradle.kts` 里的 `versionCode` +1、`versionName` 末位 +1；
2. `git commit` + `git push origin main`；
3. 用 `gh release create` 创建 Release 并上传 APK。

---

## 手动发布（核对用）

1. 编辑 `app/build.gradle.kts`：`versionCode = 83`、`versionName = "3.16"`；
2. `git commit -am "bump version to 3.16 (code 83)" && git push origin main`；
3. `gh release create v3.16 <apk路径> --title "3.16" --notes "PixelColor 3.16" --prerelease`。

---

## 注意事项

- **签名**：当前 `app/build.gradle.kts` 的 `signingConfigs.release` 指向一个本机 debug keystore，
  真正的 `endgame.jks` 已按 `.gitignore` 排除，不入库。要出正式签名包，请用自己的 keystore。
- **tag 命名**：原仓库用的是中文 tag `涂色`；新流程默认用 `vX.Y`（如 `v3.16`）更规范，
  也可用原 `涂色` tag（脚本第三个参数传入即可，存在则追加资产）。
- **Release 默认设为 pre-release**（预发布），确认无误后可到 GitHub 页面手动改为正式发布。
