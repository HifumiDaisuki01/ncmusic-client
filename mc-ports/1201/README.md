# ncmusic-client-1201（网易云音乐 Fabric Mod · Minecraft 1.20.1 移植版）

由 MC 26.2 版 `ncmusic-client` 移植到 **Minecraft 1.20.1（Fabric，Mojang 官方映射）**。
版本无关的网络/播放/配置层原样保留，MC 交互层按 1.20.1 API 逐文件改写，编译通过并产出可安装 jar。

## 产物

- `ncmusic-client-1201-1.0.0.jar` — 编译好的 mod（含 jar-in-jar 的 JLayer / ZXing）
- `ncmusic-client-1201-src.zip` — 完整源码（不含 build/.gradle）

## 安装依赖

| 依赖 | 版本 |
|---|---|
| Minecraft | 1.20.1 |
| Fabric Loader | >= 0.14.0 |
| Fabric API | >= 0.92.0（建议 0.92.12+1.20.1） |
| Java | >= 17 |

安装方式：把 jar 放入 `.minecraft/mods/`，启动 1.20.1 Fabric 客户端即可（客户端侧功能，进单人/多人世界均可按 M 打开面板）。

## 功能（与 26.2 原版一致）

- 网易云 weapi API（纯 Java 直连 `music.163.com`，AES/RSA 加密在本地）
- 登录方式：**二维码登录** / **浏览器一键登录**（CDP 抓 HttpOnly Cookie）/ **手机号密码登录**（`/ncm loginphone`）/ **Cookie 串登录**（`/ncm login-cookie`）/ **Cookie 文件登录**（`/ncm cookie-file`）
- 搜索歌曲：左键播放、右键入队（界面内）
- 用户歌单完整加载（千首级、v6/playlist/detail 全 trackIds + 分批 v3/song/detail，自动跳过下架曲）
- 顺序 / 随机播放（含播放队列）
- HUD：封面 / 歌名歌手 / 进度条 / 当前歌词(含翻译) / 播放列表，可放四角、每项独立显隐
- 封面：NativeImage(stb) 解码，失败回退 Java ImageIO（CMYK/渐进式 JPEG 等），小图失败回退原图，LRU 12 张缓存 + 失败冷却
- MP3 播放：JLayer 流式解码 + `javax.sound` 输出
- 全部 `ui/screen` 面板（音乐控制 / 扫码 / 浏览器登录 / 搜索 / 歌单 / 曲目）与 `/ncm` 客户端命令均保留

## 主要差异说明（26.2 → 1.20.1）

| 层面 | 26.2 | 1.20.1 移植 |
|---|---|---|
| 资源标识 | `Identifier` / `fromNamespaceAndPath` | `ResourceLocation` + `new ResourceLocation("ncmusic", …)` |
| 屏幕渲染 | `extractRenderState(GuiGraphicsExtractor,…)` | `render(GuiGraphics, int mouseX, int mouseY, float delta)`；显式 `renderBackground(g)`（1.20.1 `Screen.render` 默认不画背景） |
| 文本 | `gfx.text(font, s, x, y, color, false)` | `g.drawString(font, s, x, y, color, false)`（5 参版本默认带阴影，故显式传 `false` 保持原视觉） |
| 矩形 | `gfx.fill(...)` | `g.fill(...)` |
| 贴图 | `gfx.blit(RenderPipelines.GUI_TEXTURED, rl, …)` | `g.blit(ResourceLocation, x, y, u, v, w, h, texW, texH)` |
| HUD | `HudElementRegistry.addLast(...)` | `HudRenderCallback.EVENT.register(...)`；F1 隐藏用 `mc.options.hideGui` 判断 |
| 屏幕对象 | `mc.gui.screen()` / `mc.gui.setScreen()` | `mc.screen` / `mc.setScreen()` |
| 按键 | `KeyMappingHelper.registerKeyMapping`（新 Category record） | `net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding`；category 用字符串 `"ncmusic:music"`（控制界面中无翻译文件，会原样显示） |
| 点击/滚动/键盘 | `MouseButtonEvent` / `keyPressed(KeyEvent)` / 双滚轮 | `mouseClicked(double,int,int)` / `mouseScrolled(double,double,double)` / `keyPressed(int,int,int)` |
| 动态纹理 | `new DynamicTexture(()->"name", image)` | `new DynamicTexture(image)` + `upload()` |
| 像素写入 | `setPixelABGR` | `setPixelRGBA`（已 javap 核对字节码：1.20.1 的 `setPixelRGBA` 与原 `setPixelABGR` 语义一致，int 值 A 为高位、R 为低位；纯黑白二维码不受影响） |
| 命令 API | `ClientCommands` | `net.fabricmc.fabric.api.client.command.v2.ClientCommandManager`（literal/argument 同名静态方法） |
| 聊天反馈 | `sendSystemMessage(Component)` | `player.displayClientMessage(Component, false)` |
| 解码 | `NativeImage.read(byte[])` 等 | `NativeImage.read(InputStream)`（同时保留 `read(byte[])`/`ByteBuffer` 重载，代码用 InputStream 版） |

### 运行时边界说明（无功能被砍）

- 1.20.1 的 `HudRenderCallback` 只在未打开任何 Screen 时触发；26.2 行为类似。面板打开时 HUD 不绘制，属原版行为差异而非功能缺失。
- 手机号登录（`/ncm loginphone`）、扫码的风控（8821/461）提示文案与原版一致，触发时引导使用其它登录方式。
- 浏览器一键登录依赖本机已安装 Chrome/Edge（跨平台查找逻辑原样保留）。
- 原项目无语言文件（`key.ncmusic.panel` 等翻译键未提供），1.20.1 中按键设置项显示原始键名，与原版行为一致。

## 自行构建

工具链：Gradle 8.10.2 + JDK 17~22（本环境用 Zulu 20 运行 Gradle），`options.release = 17`。
Fabric Loom 采用 1.7.4（旧版 marker 未收录于 maven-metadata，故在 `build.gradle` 用 `buildscript classpath 'net.fabricmc:fabric-loom:1.7.4'` + `apply plugin: 'fabric-loom'` 方式加载）。

```bash
JAVA_HOME=<jdk17-22> gradle build
# 产物 build/libs/ncmusic-client-1201-1.0.0.jar
```

> 构建环境网络说明：本机 `repo.maven.apache.org` 不可达。除 `/root/.gradle/init.gradle` 的阿里云/腾讯镜像外，
> `settings.gradle` 中额外注册了名为 `MavenCentralLWJGL` 的镜像仓库（Loom 会用该名字 + exclusiveContent 把
> `org.lwjgl` 锁到中央仓库，同名先注册即可让它改用镜像）。
