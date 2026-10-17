# 学习锁屏

给小米 14 / Android 16 准备的最小原生 Android 应用。每天本地时间 19:00–23:00 进入固定学习画面，到点自动解除。学习画面没有可滑动内容，只有拨打电话按钮。系统锁定任务模式限制返回桌面、最近任务和通知栏；系统电话界面仍可操作。

这是需要实机验收的首版。已完成编译、APK 签名验证和日程/到期逻辑测试；尚未在用户的小米 14 上验证系统手势、接听、拨打及紧急呼叫入口。不能据此保证该 HyperOS 版本完全兼容，也不能承诺抵抗刷机、恢复出厂设置或电脑调试等绕过方式。

## GitHub 云端编译

安卓工程位于仓库的 `study-lock-android/` 子目录，工作流位于仓库根目录的 `.github/workflows/build.yml`。源代码提交后自动运行，也可在仓库的 **Actions → Build Android APK → Run workflow** 手动启动。

云端使用标准 Ubuntu 运行器、Java 17、Gradle 8.13 和 Android SDK 36。先验证日程及自动解除逻辑，再生成并验证 `app-debug.apk`。个人电脑无需安装 Android Studio、模拟器或开发工具。

构建成功后，打开本次运行的 **Artifacts → StudyLock-Android16-APK**，下载 ZIP 并解压取得 `app-debug.apk`。私有仓库下载需要登录有权限的 GitHub 账号。产物保留 3 天；到期可手动重新运行工作流。

私有仓库使用账号内含的免费分钟数和产物存储额度，超额可能收费或停止运行。此工作流使用单个标准 Linux 运行器，15 分钟超时，不启用构建缓存。不会修改账号账单或付费预算。详见 [GitHub Actions 额度说明](https://docs.github.com/en/billing/concepts/product-billing/github-actions)。

不保证首次构建在 2 分钟内结束，也不预设 APK 大小。体积以构建摘要为准。Java 与 Kotlin 均可生成原生 APK；本项目复用已有 Java 源码，不引入 WebView 或跨平台运行时。云端编译和“纯原生”本身不会提高 HyperOS 系统权限或保证更省电。

云端调试 APK 使用临时调试签名，不保证不同构建之间能覆盖安装，也不保证能覆盖本地签名版本。遇到签名不匹配，先在非学习时段移除旧版本的设备管理身份，再卸载旧版本；切勿在锁定过程中强行更换安装包。

**云端编译只解决打包和下载；完整禁止系统手势仍需要手机端的设备管理配置。** 下载 APK 后可在手机文件管理器中直接安装；下面的 ADB 安装命令是可选途径，设备管理配置则另行处理。

## 安装

安装包：`dist/学习锁屏-0.1.0.apk`。默认关闭每日计划，安装不会自动锁定手机。

此版本不是微信小程序，也不是透明触摸遮罩。阻止系统手势需要 Android 的设备所有者权限。普通设备管理员或无障碍授权不等同于设备所有者。

需要用户自行开启 USB 调试、通过 USB 连接电脑并在手机上允许本电脑调试。连接后先检查：

```powershell
.\.tools\sdk\platform-tools\adb.exe devices -l
```

确认选中的是本人手机后，可安装 APK。多个设备在线时必须添加 `-s 序列号`：

```powershell
.\.tools\sdk\platform-tools\adb.exe install -r '.\dist\学习锁屏-0.1.0.apk'
```

**以下命令会改变手机的设备管理身份，必须得到用户明确同意后再执行。** 它本身不会清空手机，但已有账户、其他管理员或厂商限制可能使配置失败。遇到失败应停止并查看原始错误；不要直接删除账户或恢复出厂设置。

```powershell
.\.tools\sdk\platform-tools\adb.exe shell dpm set-device-owner cn.studylock/.AdminReceiver
```

应用内允许“闹钟和提醒”，用于到点启动和解除。根据小米系统实际设置，可能还需允许自启动和后台弹出界面；这些项目的名称和是否需要必须实机确认。

## 验收后再启用

1. 打开应用，点击“先试锁 2 分钟”。试锁期间不能修改计划。
2. 检查屏幕滑动、空白处点击、返回、主页手势、最近任务和通知栏是否被限制。
3. 用另一部电话呼入，验证接听与挂断；用“拨打电话”验证普通电话呼出与挂断。
4. 检查电源菜单的紧急呼叫入口仍能打开，无需实际拨打紧急号码。
5. 确认 2 分钟结束后自动恢复，包括电话界面在前台时也能恢复。
6. 上述项目均通过后，勾选电话确认项，再启用每日 19:00–23:00 计划。

有任一项目失败，就保持每日计划关闭。系统版本或默认拨号应用变化后，应用要求重新试锁。还需实机验证关屏后解锁、重启后解锁、息屏跨过 19:00 和 23:00 的行为。

## 解除与卸载

学习期间没有手动退出密码，也不改变原有系统解锁密码。23:00 自动解除；试锁在 2 分钟后自动解除。不依赖聊天保管密码。

非学习时段可在应用中关闭每日锁定，或点击“移除设备管理”后正常卸载。该按钮不清空手机。

如定时权限不可用、拨号界面无法打开或系统拒绝锁定，应用尝试撤销自身策略并关闭计划。到期清理不依赖学习画面持续位于前台；同时使用墙上时间和单调时钟的到期闹钟。重启后会重建计划，试锁不会因重启重新计时。上述恢复路径仍需实机验证。

## 构建

无第三方运行依赖，无联网权限，不读取联系人、通话记录或屏幕内容。源码位于 `app/src/main/java/cn/studylock/`。支持 Android 13 及以上，目标 SDK 36。

云端编译无需在个人电脑安装工具。如需使用备用本地构建脚本，先准备 JDK 17 和 Android SDK 36，再运行：

```powershell
.\build.ps1
```

脚本先运行日程与到期测试，再编译、打包、对齐和签名 APK。签名为本地开发用途，密钥保存在忽略提交的 `.tools/studylock.keystore`。更新安装时保留同一密钥。

其他电脑可指定已安装的工具路径：

```powershell
.\build.ps1 -JdkPath 'C:\path\to\jdk-17' -SdkPath 'C:\path\to\Android\Sdk'
```

SDK 需含 `platforms/android-36` 和 `build-tools/36.0.0`。另外提供标准 Gradle 项目配置，当前已验证的构建路径为上述 PowerShell 脚本。

系统能力依据：[Android 锁定任务模式](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode)、[设备配置要求](https://developer.android.com/work/dpc/dedicated-devices)、[精确定时](https://developer.android.com/develop/background-work/services/alarms)。
