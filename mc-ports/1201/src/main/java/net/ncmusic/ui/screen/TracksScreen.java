package net.ncmusic.ui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.ncmusic.netease.NcPlaylist;
import net.ncmusic.netease.NcSong;
import net.ncmusic.netease.NetEaseApi;
import net.ncmusic.player.MusicPlayer;
import net.ncmusic.player.PlayMode;
import net.ncmusic.ui.MusicHud;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 歌单曲目页：加载完整歌单（不受网页只显示前 20 首的限制），
 * 支持一键顺序/随机播放整个歌单，或点击单曲从该曲开始播放。
 */
public class TracksScreen extends Screen {
	private final Screen parent;
	private final NcPlaylist playlist;
	private volatile List<NcSong> tracks = new ArrayList<>();
	private volatile String status = "加载中…";
	private int scroll = 0;
	private static final int ROW_H = 20;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "ncmusic-tracks");
		t.setDaemon(true);
		return t;
	});

	public TracksScreen(Screen parent, NcPlaylist playlist) {
		super(Component.literal(playlist.name));
		this.parent = parent;
		this.playlist = playlist;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		addRenderableWidget(Button.builder(Component.literal("▶ 顺序播放"), b -> playAll(PlayMode.SEQUENCE))
				.bounds(cx - 206, this.height - 30, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("🔀 随机播放"), b -> playAll(PlayMode.SHUFFLE))
				.bounds(cx - 100, this.height - 30, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
				.bounds(cx + 106, this.height - 30, 100, 20).build());
		load();
	}

	private void load() {
		status = "加载中…";
		executor.submit(() -> {
			try {
				List<NcSong> list = NetEaseApi.INSTANCE.playlistAllTracks(playlist.id,
						(cur, total) -> status = "加载曲目 " + cur + " / " + total + "…");
				tracks = list;
				status = list.isEmpty() ? "歌单为空" : "";
			} catch (Exception e) {
				status = "加载失败: [" + e.getClass().getSimpleName() + "] " + e.getMessage();
			}
		});
	}

	private void playAll(PlayMode mode) {
		List<NcSong> list = tracks;
		if (list.isEmpty()) return;
		MusicPlayer.INSTANCE.playQueue(list, 0, mode);
		MusicHud.showNotice("播放歌单: " + playlist.name + "（" + mode.label + "）");
		onClose();
	}

	private int listTop() { return 40; }
	private int listBottom() { return this.height - 40; }
	private int visibleRows() { return (listBottom() - listTop()) / ROW_H; }

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) return true;
		if (mouseY >= listTop() && mouseY < listBottom()) {
			int idx = (int) ((mouseY - listTop()) / ROW_H) + scroll;
			if (idx < tracks.size()) {
				MusicPlayer.INSTANCE.playQueue(tracks, idx, MusicPlayer.INSTANCE.getMode());
				MusicHud.showNotice("正在播放: " + tracks.get(idx).displayName());
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		int max = Math.max(0, tracks.size() - visibleRows());
		scroll = Math.max(0, Math.min(max, scroll - (int) delta));
		return true;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		this.renderBackground(g);
		super.render(g, mouseX, mouseY, delta);
		int cx = this.width / 2;
		String title = playlist.name + "（" + tracks.size() + " 首）";
		g.drawString(this.font, title, cx - this.font.width(title) / 2, 18, 0xFFEC4141, false);
		if (!status.isEmpty()) {
			g.drawString(this.font, status, cx - this.font.width(status) / 2, listTop() + 10, 0xFFFFFFFF, false);
		}
		List<NcSong> list = tracks;
		int rows = visibleRows();
		for (int i = 0; i < rows; i++) {
			int idx = i + scroll;
			if (idx >= list.size()) break;
			int y = listTop() + i * ROW_H;
			NcSong s = list.get(idx);
			boolean hovered = mouseY >= y && mouseY < y + ROW_H && mouseX > cx - 200 && mouseX < cx + 200;
			if (hovered) g.fill(cx - 200, y, cx + 200, y + ROW_H, 0x33FFFFFF);
			String row = (idx + 1) + ". " + s.name;
			g.drawString(this.font, trim(row, 240), cx - 194, y + 4, 0xFFFFFFFF, false);
			g.drawString(this.font, trim(s.artists, 140), cx + 60, y + 4, 0xFF9A9A9A, false);
		}
	}

	private String trim(String s, int maxW) {
		if (this.font.width(s) <= maxW) return s;
		StringBuilder sb = new StringBuilder(s);
		while (sb.length() > 1 && this.font.width(sb + "…") > maxW) sb.deleteCharAt(sb.length() - 1);
		return sb.append('…').toString();
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	@Override
	public void removed() {
		executor.shutdownNow();
	}
}
