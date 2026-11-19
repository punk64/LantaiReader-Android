# 兰台阅读 · 安卓端

Android 原生客户端（`com.lantai.reader`），Kotlin + Jetpack Compose。与鸿蒙端功能对齐的移植版本：书库、系列、详情、EPUB / 漫画 / 本机书阅读、离线下载、朗读、标注、统计共 17 屏，全部接兰台 Lantai 后端真实数据。

- 代码 73 个 `.kt` / 约 22,000 行；release 包约 10.5 MB
- 出包脚本 `release.py` / `release_android.py` 在主仓根目录，不在本仓库
- 版本真源是 `app/build.gradle.kts` 的 `versionName` / `versionCode`

## 技术栈

| 项 | 值 |
|---|---|
| AGP | 9.1.1 |
| Kotlin Compose 插件 | 2.4.20 |
| Compose BOM | 2026.09.00 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 26 |
| Java | 17 |
| 关键依赖 | androidx.webkit、work-runtime-ktx、junrar、commons-compress |

## 构建

仓库不含 `gradlew`、签名密钥与 `local.properties`，构建走主仓脚本：

```
python release.py android               # patch +1，clean，出签名包
python release.py android --no-bump     # 同版本重出
python release.py android --debug       # 调试包
python release.py android --version 0.0.9
```

脚本从 `MyEnv/gradle`（`GRADLE_USER_HOME`）找 `gradle.bat`，从 `MyEnv/jdk-21` 找 JDK；`GRADLE_USER_HOME`、`LT_JAVA_HOME` 可覆盖。

签名依赖 `keystore.properties` + `keystore/lantai-release.jks`，两者均只存本机、被 `.gitignore` 排除。缺任一项时 `signingConfig` 自动跳过，产出无证书包。

产物归档到主仓 `产物/android/`，附 `.sha256`。

## 目录

```
app/src/main/
├── assets/ebook/        ebook.html / ebook.js / ebook.css / shell.js —— 阅读器 Web 层
├── java/com/lantai/reader/
│   ├── common/          LtTheme（配色/尺寸/圆角）、I18n、StrEn、Format、Json、Glyph、ImageOps、ProgressMarks、Crash、Tip
│   ├── components/      CoverCache（封面 LRU）
│   ├── data/            OfflineDb（SQLite）
│   ├── local/           LocalLibrary、LocalStore、LocalMetas、LocalFiles、LocalBookSource、ZipBook、ComicArchive、CacheQuota
│   ├── model/           Models、Store（全局状态）、Prefs、ServerProfile
│   ├── net/             Api、Session、AutoSwitch、LanScan、NativeBridge、ResourceProxy、OfflineCache、
│   │                    OfflineDownloader、ProgressOutbox、ReadingSession、TtsService、UpdateChecker、
│   │                    CachePurge、OrphanSweep、BackgroundTask、SeriesCache
│   ├── reader/          ReadingWebHost
│   ├── ui/              17 个 Screen + MainShell、Nav、OfflineStub、TtsBar
│   │   └── comp/        Shell（顶栏/底栏）、BookViews、CoverImage、CoverViewer、StateViews、Skeletons、Fields、FilterOption、ReaderGuide
│   ├── LtApp.kt
│   └── MainActivity.kt
└── res/                 应用图标、主题、网络安全配置
```

## 界面

登录、首页、书库、详情、系列详情、阅读（EPUB / 漫画 / 本机书共用一屏）、导入、下载、合集、书单、标注、书源、服务器、偏好、统计、我的。

底部四页签：首页 / 书库 / 统计 / 我的。

## 功能要点

- **服务端优先**：Cookie 会话、多服务器档案切换、局域网扫描与自动切换、连接状态徽章
- **阅读**：EPUB 走 WebView 复用兰台 `ebook.js`；TXT 分章、PDF 逐页渲染、CBZ / CBR 原生解包
- **离线**：下载队列落 SQLite，进度写盘走 outbox 失败重试；隐身模式三处同时断开
- **朗读**：系统 TTS，分句与高亮由 Web 层回推
- **本机导入**：EPUB / TXT / PDF / CBZ / CBR，复制副本 + 权限持久化
- **交互**：新手引导、长按选区自绘工具条、标注与笔记

## 与鸿蒙端的关系

两端代码不共用，同名内核文件（I18n、Format、ProgressMarks、StrEn）各存一份手动同步。UI 以同一份效果预览稿为基准，尺寸、间距、圆角逐项对齐。

鸿蒙端见 `LantaiReader-hmos` 仓库。
