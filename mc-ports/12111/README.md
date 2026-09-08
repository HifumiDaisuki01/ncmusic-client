# NC Music Client（网易云音乐客户端 Mod）

Minecraft **Java 版 1.21.11 · Fabric** 客户端独立 Mod（从 MC 26.2 版移植），无需服务器、无需外部 API 服务，
直接在游戏内登录网易云音乐并播放。

## 安装要求

- Minecraft **1.21.11（Java 版）**，**Fabric Loader ≥ 0.18.4**（建议最新）
- **Fabric API ≥ 0.140.2**（本工程按 `0.141.6+1.21.11` 构建）
- **Java 21+**（本工程以 `--release 21` 编译，运行时需 Java 21 及以上）

安装步骤：

1. 安装 Fabric Loader（1.21.11 对应版本）与 Java 21+。
2. 下载 `ncmusic-client-12111-1.0.0.jar`，与 **Fabric API（1.21.11 版）** 一起放进 `mods/` 目录。
3. 启动游戏，按 **M** 或在聊天框输入 `/ncm` 打开音乐控制面板。

> jlayer（MP3 解码）与 zxing（二维码）已打入 jar 内（jar-in-jar），无需额外安装。

## 功能

- **🌐 浏览器一键登录（首选）**：自动唤起本机 Chrome/Edge 打开网易云登录页，正常登录后
  Mod 自动抓取登录 Cookie（HttpOnly 的 MUSIC_U 也能拿到）并保存，**全程零复制粘贴**，
  绕开扫码接口风控（8821）
- 备用登录：扫码登录 / 手机号+密码登录 / Cookie 文件登录
- **搜索歌曲**：左键立即播放，右键加入队列
- **我的歌单**：加载**完整**歌单曲目（`v6/playlist/detail` + `v3/song/detail`，
  千首级歌单也能完整加载，自动跳过已下架歌曲），支持一键**顺序播放** / **随机播放**
- **HUD 悬浮窗**（默认右上角，可用面板切换左上/左下/右下）：
  封面、歌名/歌手、播放进度条、同步歌词（含翻译）、播放列表——**每一项都可单独显隐**
- 播放控制：播放/暂停/上一首/下一首/音量/播放模式
- 登录态自动保存在 `config/ncmusic.json`，重启游戏无需重新登录

## 快速开始（浏览器一键登录）

1. 进游戏后按 **M** 打开音乐控制面板
2. 点 **"🌐 浏览器一键登录"**（或聊天框输入 `/ncm browser`）
3. 浏览器窗口自动打开网易云登录页，正常登录即可（扫码 / 账号 / 短信都行）
4. 登录成功的瞬间 Mod 自动获取并保存 Cookie，回到面板即可开始搜索或播放

> 浏览器登录使用独立临时配置，不影响你日常浏览器的账号。需要本机装有 Chrome 或 Edge。

## 使用

| 操作 | 说明 |
| --- | --- |
| 按 **M** | 打开音乐控制面板（登录/搜索/歌单/设置都在这里） |
| `/ncm` | 打开面板 |
| `/ncm browser` | 浏览器一键登录（首选） |
| `/ncm login` | 扫码登录 |
| `/ncm loginphone <手机号> <密码>` | 手机号+密码登录（可能触发风控） |
| `/ncm login-cookie <cookie>` | 直接粘贴 Cookie 登录 |
| `/ncm cookie-file` | 从 `config/ncmusic-cookie.txt` 读取 Cookie 登录 |
| `/ncm search` / `/ncm playlist` | 搜索界面 / 歌单界面 |
| `/ncm play` `pause` `next` `prev` `stop` | 播放控制 |
| `/ncm mode sequence\|shuffle` | 顺序 / 随机 |
| `/ncm volume 0-100` | 音量（下一首生效） |
| `/ncm hud` / `/ncm hud corner` | HUD 显隐 / 切换角落 |

## 常见问题

**扫码提示 8821（风控）怎么办？**
网易云对第三方扫码登录普遍加了"易盾行为验证"风控（返回 8821）。
**请直接改用"浏览器一键登录"**（`/ncm browser`），它是普通网页登录，不会遇到此问题。

**浏览器一键登录不可用（没装 Chrome/Edge）？**
可退回 Cookie 文件登录：
1. 浏览器打开并登录 https://music.163.com
2. F12 → 应用(Application) → Cookie → https://music.163.com，找到 `MUSIC_U` 复制其值
3. 在 `.minecraft/config/` 新建 `ncmusic-cookie.txt`，内容为一行 `MUSIC_U=复制的值`
4. 游戏内执行 `/ncm cookie-file`

## 说明

- 登录后才能拿到大部分歌曲的播放地址；VIP/无版权歌曲会提示并自动跳过。
- 音频为 MP3 流式解码（内置 JLayer），不占用 MC 声音通道，暂停游戏音乐不受影响。
- 封面走 CDN 缩略图 + 原图双地址、Minecraft 原生解码 + Java ImageIO 双保险。
- `gradle build` 即可重新构建（需 JDK 25、Gradle 9.5.1、Fabric Loom 1.14.10）。

## 移植说明（26.2 → 1.21.11）

本工程由 MC 26.2 版移植而来，业务层（netease/player/config）保持不变，仅界面/入口层按 1.21.11 API 调整：

| 26.2 写法 | 1.21.11 写法 |
| --- | --- |
| `GuiGraphicsExtractor` + `extractRenderState(...)` | `GuiGraphics` + `render(GuiGraphics,int,int,float)`（`renderBackground` 带坐标与 delta） |
| `gfx.text(Font, ...)` | `GuiGraphics.drawString(Font, ..., shadow)` |
| `mc.gui.setScreen(...)` / `mc.gui.screen()` | `mc.setScreen(...)` / `mc.screen` |
| `HudElement.extractRenderState(GuiGraphicsExtractor, DeltaTracker)` | `HudElement.render(GuiGraphics, DeltaTracker)`（绘制逻辑原样搬入） |
| `mc.gui.hud.isHidden()` | `mc.options.hideGui` |
| `keymapping.v1.KeyMappingHelper` | `keybinding.v1.KeyBindingHelper`（注册 `KeyMapping` + `KeyMapping.Category.register`） |
| `client.command.v2.ClientCommands` | `client.command.v2.ClientCommandManager` |
| `player.sendSystemMessage(msg)` | `player.displayClientMessage(msg, false)` |
| `net.minecraft.resources.Identifier` | 同为 `net.minecraft.resources.Identifier`（1.21.11 尚未改名） |
| 带 `RenderPipelines.GUI_TEXTURED` 的 `blit` | 签名一致，可直接沿用 |
