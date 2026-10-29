# 微信红包助手（Android 原生无障碍方案）

一个可**安装在安卓手机上**、自动抢微信群红包的 App。开启系统无障碍权限后，
微信只要停留在某个群聊，有红包就会自动点击领取。**完全在安卓端独立运行，不依赖电脑。**

> ⚠️ 免责：自动抢红包违反《微信软件许可及服务协议》，可能导致账号限制/封禁。
> 本代码仅用于学习 Android 无障碍服务（AccessibilityService）技术，后果自负。

---

## 一、功能特性
- **图形化界面**：主界面实时显示服务运行状态（监控中 / 未开启），并以列表展示抢红包记录。
- **自动抢红包**：监听微信当前前台群，出现「微信红包」消息自动点进详情、点「开」领取。
- **去重防重复**：以「文本 + 屏幕边界」生成 key，8 秒 TTL 内不重复点击同一红包。
- **常驻提示**：服务启动弹系统通知「已开始监控当前前台群」。
- **合规防逆向（已配置）**：R8 全量混淆 + 移除日志，显著提升静态反编译门槛（详见第五节）。

---

## 二、现状说明（重要）
本项目是 **Android 源码**，必须先编译成 `.apk` 才能装到手机（这点和 Python 脚本不同）。
已检测你的电脑：**有 JDK 21，但没有 Android SDK / Gradle / Android Studio**，本机无法直接编译。
请走下面任一条路线拿到 APK。

---

## 三、拿到 APK 的两条路线

### 路线 A：Android Studio（最省心）
1. 安装 [Android Studio](https://developer.android.com/studio)，打开本文件夹 `WeChatRedPacket`。
2. 首次自动下载 Gradle 与 SDK（需联网）。
3. 手机开「开发者选项 → USB 调试」，连电脑，点 ▶ Run 直接安装；
   或 Build → Build APK 得到 `app/build/outputs/apk/debug/app-debug.apk`。

### 路线 B：GitHub 云编译（无需 Android Studio，已配好）
项目内含 `.github/workflows/build.yml`，推送到 GitHub 后由服务器自动编译（云端联网下载 SDK，不受本机无网影响）。
推送成功后：
1. 进仓库 **Actions** → 选 `Build Debug APK` → **Run workflow**。
2. 跑完在 Artifacts 下载 `app-debug-apk`（即 `app-debug.apk`），传到手机安装。

---

## 四、初始化 Git 并推送到 GitHub
> 说明：本机当前**未安装 Git**（环境无网络，winget 安装失败），仓库初始化需先装好 Git
> （[下载地址](https://git-scm.com/download/win)）。装好后本目录已为你准备好：
> - `.gitignore`：忽略构建产物、密钥、apk
> - `git_init.ps1`：一键完成 init → add → commit → push

在 `WeChatRedPacket/` 目录运行：
```powershell
# 方式一：一键脚本（推荐）
.\git_init.ps1 -RepoUrl https://github.com/你的用户名/仓库名.git

# 方式二：手动
git init
git add .
git commit -m "init: 微信红包助手（无障碍 + R8 混淆 + 云编译）"
git branch -M main
git remote add origin https://github.com/你的用户名/仓库名.git
git push -u origin main
```
（GitHub 推送用 SSH key 或个人访问令牌 PAT 认证，已不支持密码。）

---

## 五、防逆向说明（重要边界）
**已做（合规、Google 官方推荐的正常保护实践）：**
- `app/build.gradle` 中 `debug` / `release` 均开启 `minifyEnabled true`。
- `app/proguard-rules.pro`：R8 全量混淆、优化强度拉满、移除 `android.util.Log` 调用、保留组件入口。

**技术现实与边界：**
- 任何 APK 都**无法"防止逆向到最高等级"**——Android 字节码本质可反编译，混淆与加固只是提高成本，不能杜绝。
- 我**不提供**反调试、反 Frida、反 Xposed、反内存 dump 等「反分析」对抗代码。这类技术用于规避安全检测，放在违反微信协议的抢红包工具上，构成规避技术保护措施，超出协助范围。
- 微信封号检测的是**抢红包行为**，不是 APK 是否加固；混淆 / 加固**不能防止封号**。
- 想再进一步只能上**商业加固壳**（腾讯乐固 / 360 / 梆梆），需把 APK 上传到厂商服务器处理，我无法在代码内实现。

---

## 六、手机端安装与权限
1. 允许「未知来源」安装。
2. 打开 App「微信红包助手」→「前往无障碍设置」→ 找到本服务并开启（系统强制手动授权，代码无法静默开启）。
3. 微信进入要抢的群聊，**保持前台亮屏**。

---

## 七、使用
- 主界面顶部显示「监控中（当前前台群）」即生效；下方「抢红包记录」实时滚动。
- 日志也可用 `adb logcat -s RedPacketService` 查看。
- 自己发的红包点进去无「开」按钮，不会误领。
- 停用：回到无障碍设置关掉本服务。

---

## 八、原理
`RedPacketAccessibilityService` 监听微信窗口 / 内容变化：
1. 聊天界面出现「微信红包」消息 → 点击进入详情；
2. 详情页出现文本为「开」的按钮 → 点击领取；
3. 「文本 + 屏幕边界」做 8 秒去重，防重复点击。
纯官方无障碍 API，不逆向、不注入微信进程。

---

## 九、局限 / 适配
- 微信改版（文案 / 控件结构变化）时调整源码 `TEXT_RED_PACKET` / `TEXT_OPEN`。
- 微信退后台或锁屏时无障碍失效，请保持微信前台亮屏。

---

## 十、项目结构
```
WeChatRedPacket/
├── build.gradle / settings.gradle
├── .gitignore
├── git_init.ps1                     # 一键初始化并推送 Git
├── README.md
├── .github/workflows/build.yml      # GitHub 云编译
└── app/
    ├── build.gradle                 # 已开启 R8 混淆
    ├── proguard-rules.pro           # 混淆规则
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/redpacket/
        │   ├── RedPacketAccessibilityService.kt   # 核心抢红包逻辑
        │   ├── MainActivity.kt                    # 图形化主界面
        │   └── RedPacketLogger.kt                 # 抢红包记录
        └── res/...
```

---

## 十一、与之前方案的关系
- 本 APK：装安卓手机 / 模拟器，独立运行。
- `wechat_redpacket.py`（电脑 + MuMu + ADB）、`wechat_pc_redpacket.py`（Windows 图像识别）：见工作区根目录。
- iOS / 协议机器人：合规不可行，未提供。
