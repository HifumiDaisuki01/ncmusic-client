# NC Music Client（网易云音乐客户端 Mod）· Minecraft 1.16.5 Fabric

把 **MC 26.2（Mojang 映射）** 版 `ncmusic-client` 原样移植到 **MC 1.16.5（Yarn 映射）** Fabric。
纯客户端 Mod：无需服务器、无需外部 API 服务，直接在游戏内登录网易云并播放 MP3。

- 对应 26.2 源项目：`/root/work/ncmusic-client`
- 本移植工程：`/root/work/ports/ncmusic-1165`
- 产物 jar：`ncmusic-client-1165-1.0.0.jar`（本目录）

## 功能清单（与 26.2 版一致）

- **登录（四方式，全部在 M 面板里）**
  - 🌐 浏览器一键登录（首选）：自动唤起本机 Chrome/Edge 打开网易云登录页，正常登录后
    Mod 自动抓取登录 Cookie（HttpOnly 的 MUSIC_U 也能拿到）并保存，全程零复制粘贴，绕开扫码风控
  - 📱 扫码登录（`/weapi/login/qrcode/*`，遇 8821 风控会自动提示改用浏览器登录）
  - 手机号 + 密码登录（`/weapi/login/cellphone`）
  - Cookie 文件 / 手动粘贴登录（读取 `config/ncmusic-cookie.txt` 或直接粘贴 Cookie 串）
- **搜索歌曲**：左键立即播放，右键加入队列
- **我的歌单**：完整加载曲目（`v6/playlist/detail` + `v3/song/detail` 分批，千首级也能完整加载、
  自动跳过下架曲），支持一键**顺序播放 / 随机播放**
- **HUD 悬浮窗**（默认右上角，可切换左上/左下/右下）：封面、歌名/歌手、播放进度条、
  同步歌词（含翻译）、播放列表——每一项都可单独显隐
- **播放控制**：播放/暂停/上一首/下一首/音量/播放模式（顺序/随机）
- 登录态自动保存在 `config/ncmusic.json`，重启游戏无需重新登录
- 封面走 CDN 缩略图 + 原图双地址，Minecraft 原生解码 + Java ImageIO 双保险
- MP3 流式解码（内置 JLayer），不占用 MC 声音通道
- 二维码/浏览器/CDP 用 zxing + java.net.http 纯 Java 实现

## ⚠️ 1.16.5 版与 26.2 版的差异

**1.16.5 没有 `/ncm` 系列命令**（26.2 的命令基于新版 client command API，1.16.5 的 fabric-api 没有）。
因此全部功能**全部改由按 M 打开的面板（MusicControlScreen）提供**，具体映射：

| 26.2 命令 | 1.16.5 M 面板中的位置 |
| --- | --- |
| `/ncm browser` | 登录区 → “浏览器一键登录” |
| `/ncm login` | 登录区 → “扫码登录” |
| `/ncm loginphone <手机> <密码>` | 登录区 → “手机号登录” |
| `/ncm cookie-file` | 登录区 → “Cookie 文件” |
| `/ncm search` / `/ncm playlist` | “搜索歌曲” / “我的歌单” |
| `/ncm play pause next prev` | 上一首 / 播放·暂停 / 下一首 |
| `/ncm mode sequence\|shuffle` | “模式”按钮（每按一次在 顺序/随机 间切换） |
| `/ncm volume 0-100` | “音量 - / 音量 +”（每按一次 ±10%，下一首生效） |
| `/ncm hud` / `/ncm hud corner` | “HUD: 显示/隐藏” + “位置: 四角循环” |
| （HUD 逐项显隐） | “封面/进度/歌词/列表” 四个开关按钮 |

其余逻辑（网易云 weapi、二维码、CDP 浏览器抓 Cookie、JLayer 解码、封面解码、歌单加载）与 26.2
版逐行保持一致，仅做编译层适配（见下）。

## 安装与运行

1. **Java 11+**。注意：MC 1.16.5 官方只要求 Java 8，但本 Mod 使用 `java.net.http`（Java 11 才引入），
   因此**必须用 Java 11 或更高版本启动 1.16.5**。
2. Fabric Loader 0.14+（工程按 0.19.5 构建），Fabric API 0.42.0+1.16（或更高 1.16.5 兼容版）。
3. 把 `ncmusic-client-1165-1.0.0.jar` 放进 `mods/`。

### 快速开始（浏览器一键登录）

1. 进游戏按 **M** 打开音乐控制面板
2. 点 **浏览器一键登录**，浏览器自动打开网易云登录页
3. 正常登录（扫码/账号/短信都行），登录成功的瞬间 Mod 自动抓取 Cookie 并保存

> 需要本机装有 Chrome 或 Edge；浏览器使用独立临时配置，不影响日常浏览器账号。

### 无浏览器时：Cookie 文件登录

1. 浏览器登录 https://music.163.com
2. F12 → Application → Cookies → `music.163.com`，复制 `MUSIC_U` 的值
3. 在 `.minecraft/config/` 新建 `ncmusic-cookie.txt`，内容写一行 `MUSIC_U=复制的值`
4. 游戏内 M 面板 → **Cookie 文件** → 点“从文件读取”（也可直接把完整 Cookie 串粘进输入框点“粘贴并登录”）

## 构建

```bash
cd /root/work/ports/ncmusic-1165
JAVA_HOME=/opt/jdk25 /opt/gradle-9.5.1/bin/gradle \
  -I /root/work/ports/ncmusic-1165/no-mavencentral.init.gradle build
```

产物：`build/libs/ncmusic-client-1165-1.0.0.jar`

- Minecraft 1.16.5 / Yarn `1.16.5+build.10` / Loom `1.17.20` / Gradle `9.5.1`（配 JDK 25）
- `options.release=11`：record/sealed/instanceof 模式不可用，代码里已改写；`var` 与 java.net.http 可用
- 内置 jar-in-jar：`javazoom:jlayer:1.0.1`、`com.google.zxing:core:3.5.3`（Loom 自动写入 fabric.mod.json 的 `jars`）
- `no-mavencentral.init.gradle`：本构建环境无法直连 repo.maven.apache.org，Loom 对 1.16.5 会注入
  独占仓库 `MavenCentralLWJGL`，该脚本把指向 maven.apache.org 的仓库重定向到阿里云镜像，仅需在命令行 `-I` 引入

## 编译期代码适配点（相对 26.2 源）

- `NcLyric.Line`、`BrowserLogin.Result`：Java record → 普通 final 类（字段 + 构造 + `timeMs()/text()/translation()/process()/port()` 访问器）
- `JsonParser.parseString(...)` → `new JsonParser().parse(...)`（1.16.5 内置 gson 无 `parseString`）；
  `JsonArray.isEmpty()` → `size()==0`（gson 2.8.0 无 `isEmpty`）
- 日志：`org.slf4j` → Log4j2（1.16.5 没有 slf4j 依赖）
- Yarn 映射差异：`MinecraftClient`/`Identifier`/`ButtonWidget`（构造器，无 builder）、
  `DrawableHelper.fill/drawCenteredText` 静态方法、`textRenderer.drawWithShadow`、
  `NativeImage`（`net.minecraft.client.texture` 包、像素 ABGR、方法名 `setPixelColor`）、
  DynamicTexture = `NativeImageBackedTexture`、`TextureManager.registerTexture/destroyTexture`、
  `RenderSystem.color4f`、`KeyBinding(String,int,String)`、聊天输出走 `inGameHud.getChatHud().addMessage`
- 所有箭头 switch（Java 14 语法）改写为传统 switch（release 11 约束），含 HUD 角落/状态分支
- HUD 绘制改用 `HudRenderCallback.EVENT` 注册的静态渲染（非 HudElement 抽象）
- 封面等 UI 上移除 MC 1.16.5 默认字体没有的 emoji/特殊符号，改为纯中文/ASCII 标签

## 已知差异 / 限制（功能缺口说明）

- **无 `/ncm` 命令**：1.16.5 fabric-api 没有客户端命令 v2，命令入口整体不移植；全部功能已并入 M 面板，无功能损失
- 面板每次“音量 ±”/“模式”等操作后通过重建界面刷新标签（行为与 26.2 命令版一致，音量对正在播放的歌曲下一首生效）
- MC 1.16.5 默认字库不含少数符号（如 ▶ ♪ 等），个别提示可能显示为占位方块，不影响功能；如需完整显示可挂 CJK 字体资源包
- 手机号/扫码登录在网易风控收紧后可能触发 8821/400 等，属服务端行为，界面会给出明确提示并建议改用浏览器登录
- 编译目标 Java 11（`java.net.http` 需要 11），请用 Java 11+ 启动 1.16.5

## 变更文件清单

工程内全部源文件（相对 26.2 版）：

| 文件 | 处理 |
| --- | --- |
| `netease/CryptoUtil.java` | 原样拷贝 |
| `netease/NcSong.java` / `NcPlaylist.java` | 原样拷贝 |
| `player/PlayMode.java` / `config/NcmConfig.java` | 原样拷贝 |
| `netease/NetEaseApi.java` | 适配 gson 老 API（parseString→new JsonParser().parse、isEmpty→size()==0） |
| `netease/NcLyric.java` | record Line → final 类 + 访问器 |
| `netease/BrowserLogin.java` | record Result → final 类 + 访问器；gson 老 API 适配 |
| `player/MusicPlayer.java` | 业务逻辑原样（拷贝） |
| `NcMusicClient.java` | 重写：Yarn API、HUD 事件注册、M 键、扫码全局轮询；去掉命令注册 |
| `ui/CoverManager.java` | 重写：NativeImage/NativeImageBackedTexture/ABGR、Log4j2 |
| `ui/MusicHud.java` | 重写：HudRenderCallback、DrawableHelper 静态绘制 |
| `ui/screen/MusicControlScreen.java` | 重写：新增 手机号/Cookie 登录入口补齐原命令功能 |
| `ui/screen/QrLoginScreen.java` / `SearchScreen.java` / `PlaylistsScreen.java` / `TracksScreen.java` / `BrowserLoginScreen.java` | 重写 |
| `ui/screen/PhoneLoginScreen.java` / `CookieLoginScreen.java` | 新增（承接原 `/ncm loginphone` `/ncm cookie-file`） |
| `command/NcmCommands.java` | 不移植（1.16.5 无客户端命令 v2） |

## License

MIT，见源码 LICENSE。
