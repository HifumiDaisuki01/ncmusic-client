# NC Music Client（网易云音乐客户端 Mod）

Minecraft **Java 版 26.2 · Fabric** 客户端独立 Mod，无需服务器、无需外部 API 服务，
直接在游戏内登录网易云音乐并播放。

## 安装

1. 安装 Fabric Loader 0.19.5+（MC 26.2，需要 Java 25+）。
2. 从 GitHub Release 下载 `ncmusic-client-1.0.0.jar`，与 **Fabric API 0.159.0+26.2**
   一起放进 `mods/` 目录。

## 功能

- **🌐 浏览器一键登录（首选）**：自动唤起本机 Chrome/Edge 打开网易云登录页，正常登录后
  Mod 自动抓取登录 Cookie（HttpOnly 的 MUSIC_U 也能拿到）并保存，**全程零复制粘贴**。
  绕开扫码接口风控（8821），是目前最可靠的登录方式
- 备用登录：扫码登录 / 手机号+密码登录 / Cookie 文件登录
- **搜索歌曲**：左键立即播放，右键加入队列
- **我的歌单**：加载**完整**歌单曲目（走 `v6/playlist/detail` + `v3/song/detail`，
  千首级歌单也能完整加载，自动跳过已下架歌曲），支持一键**顺序播放** / **随机播放**
- **HUD 悬浮窗**（默认右上角，可用面板切换到左上/左下/右下）：
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
| `/ncm search` / `/ncm playlist` | 搜索界面 / 歌单界面 |
| `/ncm play` `pause` `next` `prev` `stop` | 播放控制 |
| `/ncm mode sequence\|shuffle` | 顺序 / 随机 |
| `/ncm volume 0-100` | 音量（下一首生效） |
| `/ncm hud` / `/ncm hud corner` | HUD 显隐 / 切换角落 |

## 常见问题

**扫码提示 8821（风控）怎么办？**
2026 年起网易云对第三方扫码登录普遍加了"易盾行为验证"风控（返回 8821）。
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
- `gradle build` 即可重新构建（需 JDK 25、Gradle 9.5.1、Loom 1.17）。
