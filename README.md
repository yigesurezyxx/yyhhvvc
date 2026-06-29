# ParseHub

> 📱 极简多平台内容解析下载工具
>
> 粘贴链接 → 一键解析 → 保存到相册。就是这么简单。

---

## ✨ 功能

- 🎬 **16+ 平台** — 抖音、B站、YouTube、小红书、Twitter/X、微博、快手 等
- 📥 **一键下载** — 图片视频直接保存到系统相册
- 🖼️ **预览缩略图** — 解析后可预览媒体内容
- 🔌 **完全离线** — 解析引擎内置，无需服务器
- ⚡ **极简单页** — 没有多余功能，打开就用

---

## 🏗️ 技术栈

| 层级 | 技术 |
|------|------|
| UI | Jetpack Compose + Material 3 |
| 语言 | Kotlin 1.9 |
| Python 嵌入 | Chaquopy 14 (Python 3.11) |
| 核心解析 | parsehub |
| 图片加载 | Coil |
| 存储 | MediaStore (系统相册) |

---

## 📁 项目结构

```
ParseHubApp/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/parsehub/app/
│       │   ├── MainActivity.kt           # 入口
│       │   ├── data/
│       │   │   ├── Models.kt             # 数据模型
│       │   │   └── ParseRepository.kt    # Kotlin-Python 桥接
│       │   └── ui/
│       │       ├── theme/
│       │       └── screens/
│       │           └── ParseScreen.kt    # 主页面（解析+下载）
│       ├── python/
│       │   └── parse_bridge.py           # Python 解析桥接层
│       └── res/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🚀 构建

### 环境要求
- Android Studio Hedgehog (2023.1.1)+
- JDK 17+
- minSdk 24

### 步骤
```bash
# 用 Android Studio 打开项目
# 或命令行构建：
./gradlew assembleDebug    # Debug APK
./gradlew assembleRelease  # Release APK
```

APK 输出位置：`app/build/outputs/apk/`

---

## 📝 使用说明

1. 打开 App
2. 粘贴分享链接（或点粘贴板按钮一键粘贴）
3. 点击「开始解析」
4. 预览结果，点击「下载到相册」
5. 授权存储权限，完成！

> 💡 图片保存到 `相册/ParseHub` 目录，视频保存到 `电影/ParseHub` 目录

---

## 相关项目

- [parse_hub_bot](https://github.com/z-mio/parse_hub_bot) — Telegram Bot 版本
