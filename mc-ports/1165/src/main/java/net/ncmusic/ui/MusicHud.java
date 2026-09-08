package net.ncmusic.ui;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.ncmusic.config.NcmConfig;
import net.ncmusic.netease.NcLyric;
import net.ncmusic.netease.NcSong;
import net.ncmusic.player.MusicPlayer;

import java.util.List;

/**
 * 游戏内 HUD：封面 / 歌曲信息 / 播放进度 / 歌词 / 播放列表。
 * 默认右上角，可配置到四个角落；每个模块可单独显隐。
 * 1.16.5：经 HudRenderCallback.EVENT 注册；绘制用 DrawableHelper.fill 静态方法与
 * TextRenderer.drawWithShadow；矩形贴图用静态 drawTexture(MatrixStack,x,y,z,u,v,w,h,tw,th)。
 */
public class MusicHud {
	private static final int PANEL_W = 190;
	private static final int MARGIN = 8;
	private static final int PAD = 6;

	private static final int C_BG = 0x66000000;
	private static final int C_TITLE = 0xFFFFFFFF;
	private static final int C_SUB = 0xFFB0B0B0;
	private static final int C_ACCENT = 0xFFEC4141;      // 网易云红
	private static final int C_BAR_BG = 0xFF3A3A3A;
	private static final int C_LYRIC = 0xFFFFFFFF;
	private static final int C_LYRIC_NEXT = 0xFF8A8A8A;
	private static final int C_LIST = 0xFF9A9A9A;
	private static final int C_LIST_CUR = 0xFFFFD75E;

	private static volatile String notice = "";
	private static volatile long noticeUntil = 0;

	public static void showNotice(String msg) {
		notice = msg;
		noticeUntil = System.currentTimeMillis() + 4000;
	}

	public static void register() {
		HudRenderCallback.EVENT.register(MusicHud::render);
	}

	private static void render(MatrixStack matrices, float tickDelta) {
		NcmConfig cfg = NcmConfig.get();
		if (!cfg.showHud) return;

		MinecraftClient mc = MinecraftClient.getInstance();
		// 仅在游戏中渲染（主菜单/切歌单等没有 world 时不画）
		if (mc.world == null) return;

		TextRenderer font = mc.textRenderer;
		MusicPlayer player = MusicPlayer.INSTANCE;
		NcSong song = player.currentSong();

		// ---------- 计算面板内容 ----------
		int panelH = PAD;
		boolean hasSong = song != null;

		int infoH = 0;
		if (hasSong && (cfg.showCover || cfg.showSongInfo)) {
			infoH = cfg.showCover ? 40 : 24;
		}
		int progressH = (hasSong && cfg.showProgress) ? 18 : 0;
		int lyricH = 0;
		NcLyric lyric = player.getLyric();
		if (hasSong && cfg.showLyric) {
			lyricH = (lyric != null && !lyric.isEmpty()) ? 22 : 12;
		}
		int listH = 0;
		List<NcSong> queue = player.getQueue();
		if (cfg.showList && !queue.isEmpty()) {
			listH = Math.min(cfg.listSize, queue.size()) * 11 + 2;
		}
		boolean noticeVisible = System.currentTimeMillis() < noticeUntil && !notice.isEmpty();
		int noticeH = noticeVisible ? 12 : 0;

		panelH += infoH + progressH + lyricH + listH + noticeH;
		if (!hasSong && !noticeVisible) {
			// 空闲时只显示一行提示
			panelH = PAD * 2 + 10;
		}
		panelH += PAD - 2;
		if (panelH <= 0) return;

		int screenW = mc.getWindow().getScaledWidth();
		int screenH = mc.getWindow().getScaledHeight();

		int x, y;
		switch (cfg.corner) {
			case TOP_LEFT:
				x = MARGIN; y = MARGIN;
				break;
			case BOTTOM_LEFT:
				x = MARGIN; y = screenH - panelH - MARGIN;
				break;
			case BOTTOM_RIGHT:
				x = screenW - PANEL_W - MARGIN; y = screenH - panelH - MARGIN;
				break;
			default:
				x = screenW - PANEL_W - MARGIN; y = MARGIN;
				break;
		}

		// ---------- 绘制 ----------
		net.minecraft.client.gui.DrawableHelper.fill(matrices, x, y, x + PANEL_W, y + panelH, C_BG);
		// 左侧强调条
		net.minecraft.client.gui.DrawableHelper.fill(matrices, x, y, x + 2, y + panelH, C_ACCENT);

		int cx = x + PAD + 2;
		int cy = y + PAD;

		if (noticeVisible) {
			font.drawWithShadow(matrices, trim(font, notice, PANEL_W - PAD * 2 - 2), cx, cy, 0xFFFFA0A0);
			cy += noticeH;
		}

		if (!hasSong) {
			font.drawWithShadow(matrices, "网易云音乐 · 按 M 打开面板", cx, cy, C_SUB);
			return;
		}

		// 封面 + 歌曲信息
		if (infoH > 0) {
			int textX = cx;
			if (cfg.showCover) {
				CoverManager.Entry cover = CoverManager.INSTANCE.get(song);
				if (cover != null) {
					mc.getTextureManager().bindTexture(cover.id);
					int s = cover.size;
					net.minecraft.client.gui.DrawableHelper.drawTexture(matrices, cx, cy, 0,
							0f, 0f, 36, 36, Math.max(s, 1), Math.max(s, 1));
				} else {
					net.minecraft.client.gui.DrawableHelper.fill(matrices, cx, cy, cx + 36, cy + 36, 0xFF222222);
				}
				textX += 42;
			}
			if (cfg.showSongInfo) {
				int textW = x + PANEL_W - PAD - textX;
				font.drawWithShadow(matrices, trim(font, song.name, textW), textX, cy + 4, C_TITLE);
				String sub = song.artists.isEmpty() ? song.album : song.artists;
				font.drawWithShadow(matrices, trim(font, sub, textW), textX, cy + 16, C_SUB);
				String stateText;
				switch (player.getState()) {
					case PLAYING:
						stateText = "播放中  · " + player.getMode().label;
						break;
					case PAUSED:
						stateText = "已暂停  · " + player.getMode().label;
						break;
					case LOADING:
						stateText = "加载中…";
						break;
					default:
						stateText = "已停止";
						break;
				}
				font.drawWithShadow(matrices, stateText, textX, cy + 28, C_ACCENT);
			}
			cy += infoH;
		}

		// 进度条
		if (progressH > 0) {
			long pos = player.getPositionMs();
			long dur = player.getDurationMs();
			float ratio = dur > 0 ? Math.min(1f, pos / (float) dur) : 0f;
			int barW = PANEL_W - PAD * 2 - 2;
			net.minecraft.client.gui.DrawableHelper.fill(matrices, cx, cy + 2, cx + barW, cy + 5, C_BAR_BG);
			net.minecraft.client.gui.DrawableHelper.fill(matrices, cx, cy + 2, cx + (int) (barW * ratio), cy + 5, C_ACCENT);
			font.drawWithShadow(matrices, fmtTime(pos) + " / " + fmtTime(dur), cx, cy + 8, C_SUB);
			cy += progressH;
		}

		// 歌词
		if (lyricH > 0) {
			if (lyric != null && !lyric.isEmpty()) {
				int li = lyric.indexAt(player.getPositionMs());
				NcLyric.Line cur = lyric.lineAt(li);
				NcLyric.Line next = lyric.lineAt(li + 1);
				int textW = PANEL_W - PAD * 2 - 2;
				String curText = cur == null ? "♪" : cur.text();
				String nextText = next == null ? "" : next.text();
				if (cur != null && !cur.translation().isEmpty()) nextText = cur.translation();
				font.drawWithShadow(matrices, trim(font, curText, textW), cx, cy, C_LYRIC);
				font.drawWithShadow(matrices, trim(font, nextText, textW), cx, cy + 11, C_LYRIC_NEXT);
			} else {
				font.drawWithShadow(matrices, "暂无歌词", cx, cy, C_LYRIC_NEXT);
			}
			cy += lyricH;
		}

		// 播放列表
		if (listH > 0) {
			int cur = player.getIndex();
			int size = Math.min(cfg.listSize, queue.size());
			int start = Math.max(0, Math.min(cur - size / 2, queue.size() - size));
			int textW = PANEL_W - PAD * 2 - 2;
			for (int i = 0; i < size; i++) {
				int qi = start + i;
				NcSong s = queue.get(qi);
				String row = (qi + 1) + ". " + s.displayName();
				int color = qi == cur ? C_LIST_CUR : C_LIST;
				font.drawWithShadow(matrices, (qi == cur ? "> " : "  ") + trim(font, row, textW - 24), cx, cy, color);
				cy += 11;
			}
		}
	}

	private static String trim(TextRenderer font, String s, int maxW) {
		if (font.getWidth(s) <= maxW) return s;
		StringBuilder sb = new StringBuilder(s);
		while (sb.length() > 1 && font.getWidth(sb + "…") > maxW) sb.deleteCharAt(sb.length() - 1);
		return sb.append('…').toString();
	}

	public static String fmtTime(long ms) {
		long totalSec = Math.max(0, ms / 1000);
		return String.format("%d:%02d", totalSec / 60, totalSec % 60);
	}
}
