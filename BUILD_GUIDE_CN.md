# 编译环境配置

## 推荐环境

- Android Studio Ladybug 或更高版本
- JDK 17（Android Studio 自带的 JBR 17 也可以）
- Android SDK Platform 34
- Android SDK Build-Tools 34.0.0 或更高版本
- Gradle 8.7（项目包含 Gradle Wrapper，无需单独安装）
- Android Gradle Plugin 8.5.2

最低运行版本为 Android 6.0（API 23），目标版本为 Android 14（API 34）。

当前应用版本为 2.6.0。向右使用 25 张完整角色整帧动画，身体、双脚、双臂和尾巴均直接绘制在同一帧内，不使用分层拼接；向左动画由每张完整向右帧整体水平镜像生成。拖到左右屏幕边缘后会进入只露半个头的停靠状态，点击头部可恢复。项目还包含透明投喂素材、长按投喂交互、8 帧趴下/睡眠素材和原生二维随机移动状态机。

## Android Studio 编译

1. 解压源码，在 Android Studio 中选择 **Open**，打开项目根目录。
2. 等待 Gradle Sync 完成；若提示缺少 Android SDK 34，按提示安装。
3. 选择 **Build > Build Bundle(s) / APK(s) > Build APK(s)**。
4. 调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 命令行编译

Linux/macOS：

```bash
./gradlew assembleDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat assembleDebug
```

查看 Java 和 Android SDK 是否配置成功：

```bash
java -version
```

如果未使用 Android Studio 自带 SDK，需要设置环境变量：

Windows PowerShell 示例：

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:Path="$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
```

## 发布签名

调试版 APK 会使用 Android 默认调试密钥。正式发布前，请在 Android Studio 中选择 **Build > Generate Signed Bundle / APK** 创建并使用自己的发布密钥。发布密钥必须长期备份，丢失后无法为同一应用发布兼容更新。
