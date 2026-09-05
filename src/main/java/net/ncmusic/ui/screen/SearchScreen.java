package net.ncmusic.ui.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.ncmusic.netease.NcSong;
import net.ncmusic.netease.NetEaseApi;
import net.ncmusic.player.MusicPlayer;
import net.ncmusic.ui.MusicHud;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 搜索界面：输入关键词 → 结果列表；左键立即播放，右键加入队列 */
public class SearchScreen extends Screen {
	private final Screen parent;
	private EditBox searchBox;
	private volatile List<NcSong> results = new ArrayList<>();
	private volatile String status = "";
	private int scroll = 0;
	private static final int ROW_H = 22;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "ncmusic-search");
		t.setDaemon(true);
		return t;
	});

	public SearchScreen(Screen parent) {
		super(Component.literal("搜索歌曲"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		searchBox = new EditBox(this.font, cx - 130, 34, 200, 20, Component.literal("关键词"));
		searchBox.setMaxLength(60);
		searchBox.setHint(Component.literal("输入歌曲 / 歌手名"));
		addRenderableWidget(searchBox);
		addRenderableWidget(Button.builder(Component.literal("搜索"), b -> doSearch())
				.bounds(cx + 76, 34, 54, 20).build());
		addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
				.bounds(cx - 50, this.height - 30, 100, 20).build());
		setInitialFocus(searchBox);
	}

	private void doSearch() {
		String kw = searchBox.getValue().trim();
		if (kw.isEmpty()) return;
		status = "搜索中…";
		scroll = 0;
		executor.submit(() -> {
			try {
				List<NcSong> r = NetEaseApi.INSTANCE.search(kw, 30, 0);
				results = r;
				status = r.isEmpty() ? "没有找到相关歌曲" : "";
			} catch (Exception e) {
				status = "搜索失败: " + e.getMessage();
			}
		});
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (event.key() == 257 && this.getFocused() == searchBox) { // Enter
			doSearch();
			return true;
		}
		return super.keyPressed(event);
	}

	private int listTop() { return 64; }
	private int listBottom() { return this.height - 40; }
	private int visibleRows() { return (listBottom() - listTop()) / ROW_H; }

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		int idx = rowAt(event.y());
		if (idx >= 0 && idx < results.size()) {
			NcSong song = results.get(idx);
			if (event.button() == 1) { // 右键加入队列
				MusicPlayer.INSTANCE.enqueue(song);
				MusicHud.showNotice("已加入队列: " + song.displayName());
			} else { // 左键立即播放
				MusicPlayer.INSTANCE.playQueue(results, idx, MusicPlayer.INSTANCE.getMode());
				MusicHud.showNotice("正在播放: " + song.displayName());
			}
			return true;
		}
		return false;
	}

	private int rowAt(double my) {
		if (my < listTop() || my >= listBottom()) return -1;
		int row = (int) ((my - listTop()) / ROW_H) + scroll;
		return row < results.size() ? row : -1;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
		int max = Math.max(0, results.size() - visibleRows());
		scroll = Math.max(0, Math.min(max, scroll - (int) vAmount));
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
		super.extractRenderState(gfx, mouseX, mouseY, delta);
		int cx = this.width / 2;
		String title = "搜索歌曲（左键播放 / 右键加入队列）";
		gfx.text(this.font, title, cx - this.font.width(title) / 2, 18, 0xFFEC4141, false);

		List<NcSong> res = results;
		if (!status.isEmpty()) {
			gfx.text(this.font, status, cx - this.font.width(status) / 2, listTop() + 10, 0xFFFFFFFF, false);
		}
		int rows = visibleRows();
		for (int i = 0; i < rows; i++) {
			int idx = i + scroll;
			if (idx >= res.size()) break;
			int y = listTop() + i * ROW_H;
			NcSong s = res.get(idx);
			boolean hovered = mouseY >= y && mouseY < y + ROW_H && mouseX > cx - 160 && mouseX < cx + 160;
			if (hovered) gfx.fill(cx - 160, y, cx + 160, y + ROW_H, 0x33FFFFFF);
			gfx.text(this.font, trim(s.name, 300), cx - 154, y + 3, 0xFFFFFFFF, false);
			gfx.text(this.font, trim(s.artists + (s.album.isEmpty() ? "" : " · " + s.album), 300), cx - 154, y + 12, 0xFF9A9A9A, false);
		}
		if (res.size() > rows) {
			gfx.text(this.font, (scroll + 1) + "-" + Math.min(res.size(), scroll + rows) + " / " + res.size(),
					cx + 120, listBottom() - 12, 0xFF9A9A9A, false);
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
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public void removed() {
		executor.shutdownNow();
	}
}
