# PixelColor · 安卓涂色游戏（Paint by Number）

一个基于 **Kotlin + Jetpack Compose** 的 Android 数字填色游戏：把一张图片降采样成网格、用颜色量化生成调色板，玩家按格子填色直到整幅画完成。

> 本仓库源码提取自 GitHub Release `涂色`（v3.15 / versionCode 82）。原始仓库 `Czz45/pixelcolor` 当时仅含 README，完整代码在此。

---

## 技术栈

- **语言**：Kotlin 2.0.0
- **UI**：Jetpack Compose（BOM 2024.12.01）+ Material3
- **构建**：Android Gradle Plugin 8.7.2，Gradle Wrapper 已内置（可直接 `./gradlew`）
- **平台**：minSdk 26（Android 8.0）、compileSdk / targetSdk 35
- **关键依赖**：Navigation Compose、Coil（在线图）、DataStore（设置）、Gson（存档）、core-splashscreen
- **权限**：`INTERNET`（在线图源）、`CAMERA`（拍照取图）

---

## 如何构建 / 运行

```bash
# 1) 准备 Android SDK（local.properties 不入库，需本机自己写 sdk.dir）
echo "sdk.dir=/你的/Android/Sdk路径" > local.properties

# 2) 用内置 Gradle Wrapper 构建（无需本机预装 Gradle）
./gradlew assembleDebug        # 生成 debug APK
./gradlew installDebug         # 连设备/模拟器直接安装

# 运行测试
./gradlew test
```

> 注意：`app/build.gradle.kts` 里的 `signingConfigs.release` 写死了一个 Windows 路径的 debug keystore，**仅本地签名用，不含密钥密码公开风险之外的实质密钥**；正式发布请改用你自己的 keystore 并写进 `.gitignore`。根目录 `endgame.jks` 已按 `.gitignore` 排除，不会进入版本库。

---

## 目录结构与职责

```
app/src/main/java/com/example/pixelcolor/
├── PixelColorApp.kt        # Application：全局崩溃捕获、协程作用域、运行日志
├── MainActivity.kt         # 入口 Activity
├── engine/                 # ★ 核心玩法（纯 Kotlin，无 Android 依赖，可单测）
│   ├── GameEngine.kt       #   填色逻辑：精确填 / 自由填 / 滑动填 / 区域(洪水)填充
│   ├── PixelCanvas.kt      #   网格数据模型 cells / filledCells / fillOrder
│   ├── GameState.kt        #   运行时状态（画布+调色板+进度+用时）
│   ├── ColorPalette.kt     #   调色板（每色剩余计数）
│   ├── GameConfig.kt       #   网格 16~1024、颜色 5~256
│   ├── DailyChallenge.kt   #   按日期种子生成确定性关卡
│   ├── Achievement.kt      #   18 个成就定义与判定
│   └── SpriteTemplates.kt  #   12 个内置 16×16 像素画模板
├── image/                  # ★ 图像 → 像素画流水线
│   ├── ImageProcessor.kt   #   解码 / 降采样 / 建画布 / 导出 PNG
│   └── ColorQuantizer.kt   #   K-Means 颜色量化 + 像素→调色板映射
├── data/                   # 持久化
│   ├── GameRepository.kt   #   存档 读/写/列表/删除（JSON）
│   ├── SettingsStore.kt    #   DataStore 统计与设置
│   └── model/SaveData.kt   #   存档数据模型
├── ui/
│   ├── theme/              # Color / Theme / Type / FrostedGlass（毛玻璃主题）
│   ├── component/          # PixelCanvasView(核心画布渲染) / ColorPaletteBar
│   └── screen/             # 9 个界面（见下）
└── navigation/             # NavGraph、Screen 路由
```

---

## 玩法与功能

1. **选图**：10 张内置预设图（`assets/preset_images/0~9.png`）、拍照、相册，或在线图源（"精选"页，见下）。
2. **生成网格**：`ImageProcessor` 把图降采样到目标网格，对不透明像素做 **K-Means 颜色量化**得到 N 种代表色；透明像素预填、不计入进度。
3. **填色交互**（见 `GameEngine`）：
   - 精确填色（数字填色）：仅当选中色 == 该格目标色
   - 自由模式：点任意格自动选色并填
   - 滑动填色：沿拖动轨迹批量填
   - 区域填充（魔棒）：BFS 洪水填充连通同色区域
4. **进度/完成**：`progress = 已填 / 非透明格`，全部填满即完成。
5. **功能模块**：画廊/我的作品、每日挑战、成就系统、内置像素模板、多主题（毛玻璃）、存档（JSON + 缩略图缓存）、全局崩溃日志（可查看/分享）。

> **关于"精选/PixivTab"**：界面名为 Pixiv，实际数据源是 **Picsum（默认）/ Bing / Pexels**，需自备 Bing/Pexels API Key；并非真正的 Pixiv API。

---

## 测试

- `engine/GameEngineTest.kt`、`image/ColorQuantizerTest.kt` 为 JUnit 单测，覆盖引擎与量化核心。运行：`./gradlew test`。

---

## 提交说明（本仓库初始化）

- 已用 `.gitignore` 排除：`endgame.jks`、`local.properties`、`build/`、`.gradle/`、`.kotlin/`、`.mimocode/node_modules/`、`.workbuddy/`。
- 初始提交为完整可阅读的源码（约 60 个文件，全部为文本源文件 + 标准 Gradle 配置 + 资源 PNG）。
- 提交作者为占位身份，推送前请改为你自己的 `user.name` / `user.email`。
