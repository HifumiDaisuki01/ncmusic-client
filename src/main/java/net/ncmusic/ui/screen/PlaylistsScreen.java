package net.ncmusic.ui.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.ncmusic.netease.NcPlaylist;
import net.ncmusic.netease.NetEaseApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 用户歌单列表（需登录），点击进入歌单曲目页 */
public class PlaylistsScreen extends Screen {
	private final Screen parent;
	private volatile List<NcPlaylist> playlists = new ArrayList<>();
	private volatile String status = "加载中…";
	private int scroll = 0;
	private static final int ROW_H = 24;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "ncmusic-playlists");
		t.setDaemon(true);
		return t;
	});

	public PlaylistsScreen(Screen parent) {
		super(Component.literal("我的歌单"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		addRenderableWidget(Button.builder(Component.literal("刷新"), b -> load())
				.bounds(cx - 104, this.height - 30, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
				.bounds(cx + 4, this.height - 30, 100, 20).build());
		if (playlists.isEmpty()) load();
	}

	private void load() {
		if (!NetEaseApi.INSTANCE.isLoggedIn()) {
			status = "未登录 — 请先在主面板扫码登录";
			return;
		}
		status = "加载中…";
		executor.submit(() -> {
			try {
				if (NetEaseApi.INSTANCE.getUserId() == 0) NetEaseApi.INSTANCE.refreshAccount();
				List<NcPlaylist> list = NetEaseApi.INSTANCE.userPlaylists(NetEaseApi.INSTANCE.getUserId());
				playlists = list;
				status = list.isEmpty() ? "没有歌单" : "";
			} catch (Exception e) {
				status = "加载失败: " + e.getMessage();
			}
		});
	}

	private int listTop() { return 44; }
	private int listBottom() { return this.height - 40; }
	private int visibleRows() { return (listBottom() - listTop()) / ROW_H; }

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		if (event.y() >= listTop() && event.y() < listBottom()) {
			int idx = (int) ((event.y() - listTop()) / ROW_H) + scroll;
			if (idx < playlists.size()) {
				this.minecraft.gui.setScreen(new TracksScreen(this, playlists.get(idx)));
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
		int max = Math.max(0, playlists.size() - visibleRows());
		scroll = Math.max(0, Math.min(max, scroll - (int) vAmount));
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
		super.extractRenderState(gfx, mouseX, mouseY, delta);
		int cx = this.width / 2;
		String title = "我的歌单";
		gfx.text(this.font, title, cx - this.font.width(title) / 2, 18, 0xFFEC4141, false);
		if (!status.isEmpty()) {
			gfx.text(this.font, status, cx - this.font.width(status) / 2, listTop() + 10, 0xFFFFFFFF, false);
		}
		List<NcPlaylist> list = playlists;
		int rows = visibleRows();
		for (int i = 0; i < rows; i++) {
			int idx = i + scroll;
			if (idx >= list.size()) break;
			int y = listTop() + i * ROW_H;
			NcPlaylist p = list.get(idx);
			boolean hovered = mouseY >= y && mouseY < y + ROW_H && mouseX > cx - 170 && mouseX < cx + 170;
			if (hovered) gfx.fill(cx - 170, y, cx + 170, y + ROW_H, 0x33FFFFFF);
			gfx.text(this.font, trim(p.name, 320), cx - 164, y + 3, 0xFFFFFFFF, false);
			gfx.text(this.font, p.trackCount + " 首", cx - 164, y + 13, 0xFF9A9A9A, false);
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
