# NC Music Client（网易云音乐客户端 Mod）

Minecraft **Java 版 26.2 · Fabric** 客户端独立 Mod，无需服务器、无需外部 API 服务，
直接在游戏内登录网易云音乐并播放。

## 安装

1. 安装 Fabric Loader 0.19.5+（MC 26.2，需要 Java 25+）。
2. 把 `ncmusic-client-1.0.0.jar` 和 **Fabric API 0.159.0+26.2** 一起放进 `mods/` 目录。

## 功能

- **扫码登录**网易云账号（也支持 `/ncm` 面板内操作），登录态保存在 `config/ncmusic.json`
- **搜索歌曲**：左键立即播放，右键加入队列
- **我的歌单**：加载**完整**歌单曲目（走 `v6/playlist/detail` + `v3/song/detail`，
  不受网页只显示前 20 首的限制），支持一键**顺序播放** / **随机播放**
- **HUD 悬浮窗**（默认右上角，可用面板切换到左上/左下/右下）：
  封面、歌名/歌手、播放进度条、同步歌词（含翻译）、播放列表——**每一项都可单独显隐**
- 播放控制：播放/暂停/上一首/下一首/音量/播放模式

## 使用

| 操作 | 说明 |
| --- | --- |
| 按 **M** | 打开音乐控制面板（登录/搜索/歌单/设置都在这里） |
| `/ncm` | 打开面板 |
| `/ncm login` | 扫码登录 |
| `/ncm search` / `/ncm playlist` | 搜索界面 / 歌单界面 |
| `/ncm play` `pause` `next` `prev` `stop` | 播放控制 |
| `/ncm mode sequence|shuffle` | 顺序 / 随机 |
| `/ncm volume 0-100` | 音量（下一首生效） |
| `/ncm hud` / `/ncm hud corner` | HUD 显隐 / 切换角落 |

## 说明

- 登录后才能拿到大部分歌曲的播放地址；VIP/无版权歌曲会提示并自动跳过。
- 音频为 MP3 流式解码（内置 JLayer），不占用 MC 声音通道，暂停游戏音乐不受影响。
- 源码见 `ncmusic-client-src.zip`，`gradle build` 即可重新构建（需 JDK 25、Gradle 9.5.1、Loom 1.17）。

## 扫码提示 8821（风控）怎么办

2026 年起网易云对第三方扫码登录普遍加了"易盾行为验证"风控（返回 8821），连开源社区主流工具都被拦。
最可靠的登录方式是 **Cookie 登录**：
1. 浏览器打开并登录 https://music.163.com
2. F12 → 应用(Application) → Cookie → https://music.163.com
3. 找到 `MUSIC_U`，复制它的值
4. 游戏内执行：`/ncm login-cookie MUSIC_U=粘贴的值`
   或完整格式 `/ncm login-cookie MUSIC_U=xxx; __csrf=yyy`
登录态会自动保存，无需重复登录。
