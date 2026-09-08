package net.ncmusic.ui.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
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
	private TextFieldWidget searchBox;
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
		super(new LiteralText("搜索歌曲"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		searchBox = new TextFieldWidget(this.textRenderer, cx - 130, 34, 200, 20, new LiteralText("关键词"));
		searchBox.setMaxLength(60);
		this.addButton(searchBox);
		this.addButton(new ButtonWidget(cx + 76, 34, 54, 20,
				new LiteralText("搜索"), b -> doSearch()));
		this.addButton(new ButtonWidget(cx - 50, this.height - 30, 100, 20,
				new LiteralText("返回"), b -> onClose()));
		searchBox.setTextFieldFocused(true);
		this.setFocused(searchBox);
	}

	private void doSearch() {
		String kw = searchBox.getText().trim();
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
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if ((keyCode == 257 || keyCode == 335) && this.getFocused() == searchBox) { // Enter
			doSearch();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private int listTop() { return 64; }
	private int listBottom() { return this.height - 40; }
	private int visibleRows() { return (listBottom() - listTop()) / ROW_H; }

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) return true;
		int idx = rowAt(mouseY);
		if (idx >= 0 && idx < results.size()) {
			NcSong song = results.get(idx);
			if (button == 1) { // 右键加入队列
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
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		int max = Math.max(0, results.size() - visibleRows());
		scroll = Math.max(0, Math.min(max, scroll - (int) amount));
		return true;
	}

	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		this.renderBackground(matrices);
		super.render(matrices, mouseX, mouseY, delta);
		int cx = this.width / 2;
		String title = "搜索歌曲（左键播放 / 右键加入队列）";
		drawCenteredText(matrices, this.textRenderer, title, cx, 18, 0xFFEC4141);

		List<NcSong> res = results;
		if (!status.isEmpty()) {
			drawCenteredText(matrices, this.textRenderer, status, cx, listTop() + 10, 0xFFFFFFFF);
		}
		int rows = visibleRows();
		for (int i = 0; i < rows; i++) {
			int idx = i + scroll;
			if (idx >= res.size()) break;
			int y = listTop() + i * ROW_H;
			NcSong s = res.get(idx);
			boolean hovered = mouseY >= y && mouseY < y + ROW_H && mouseX > cx - 160 && mouseX < cx + 160;
			if (hovered) fill(matrices, cx - 160, y, cx + 160, y + ROW_H, 0x33FFFFFF);
			this.textRenderer.drawWithShadow(matrices, trim(s.name, 300), cx - 154, y + 3, 0xFFFFFFFF);
			this.textRenderer.drawWithShadow(matrices, trim(s.artists + (s.album.isEmpty() ? "" : " · " + s.album), 300),
					cx - 154, y + 12, 0xFF9A9A9A);
		}
		if (res.size() > rows) {
			this.textRenderer.drawWithShadow(matrices,
					(scroll + 1) + "-" + Math.min(res.size(), scroll + rows) + " / " + res.size(),
					cx + 120, listBottom() - 12, 0xFF9A9A9A);
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
