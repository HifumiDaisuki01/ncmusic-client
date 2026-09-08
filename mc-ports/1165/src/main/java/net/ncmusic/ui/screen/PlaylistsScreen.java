package net.ncmusic.ui.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
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
		super(new LiteralText("我的歌单"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		this.addButton(new ButtonWidget(cx - 104, this.height - 30, 100, 20,
				new LiteralText("刷新"), b -> load()));
		this.addButton(new ButtonWidget(cx + 4, this.height - 30, 100, 20,
				new LiteralText("返回"), b -> onClose()));
		if (playlists.isEmpty()) load();
	}

	private void load() {
		if (!NetEaseApi.INSTANCE.isLoggedIn()) {
			status = "未登录 — 请先在主面板登录";
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
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) return true;
		if (mouseY >= listTop() && mouseY < listBottom()) {
			int idx = (int) ((mouseY - listTop()) / ROW_H) + scroll;
			if (idx < playlists.size()) {
				this.client.openScreen(new TracksScreen(this, playlists.get(idx)));
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		int max = Math.max(0, playlists.size() - visibleRows());
		scroll = Math.max(0, Math.min(max, scroll - (int) amount));
		return true;
	}

	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		this.renderBackground(matrices);
		super.render(matrices, mouseX, mouseY, delta);
		int cx = this.width / 2;
		drawCenteredText(matrices, this.textRenderer, "我的歌单", cx, 18, 0xFFEC4141);
		if (!status.isEmpty()) {
			drawCenteredText(matrices, this.textRenderer, status, cx, listTop() + 10, 0xFFFFFFFF);
		}
		List<NcPlaylist> list = playlists;
		int rows = visibleRows();
		for (int i = 0; i < rows; i++) {
			int idx = i + scroll;
			if (idx >= list.size()) break;
			int y = listTop() + i * ROW_H;
			NcPlaylist p = list.get(idx);
			boolean hovered = mouseY >= y && mouseY < y + ROW_H && mouseX > cx - 170 && mouseX < cx + 170;
			if (hovered) fill(matrices, cx - 170, y, cx + 170, y + ROW_H, 0x33FFFFFF);
			this.textRenderer.drawWithShadow(matrices, trim(p.name, 320), cx - 164, y + 3, 0xFFFFFFFF);
			this.textRenderer.drawWithShadow(matrices, p.trackCount + " 首", cx - 164, y + 13, 0xFF9A9A9A);
		}
	}

	private String trim(String s, int maxW) {
		if (this.textRenderer.getWidth(s) <= maxW) return s;
		StringBuilder sb = new StringBuilder(s);
		while (sb.length() > 1 && this.textRenderer.getWidth(sb + "…") > maxW) sb.deleteCharAt(sb.length() - 1);
		return sb.append('…').toString();
	}

	@Override
	public void onClose() {
		this.client.openScreen(parent);
	}

	@Override
	public void removed() {
		executor.shutdownNow();
	}
}
