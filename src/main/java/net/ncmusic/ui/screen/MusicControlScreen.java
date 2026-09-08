package net.ncmusic.ui.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.net.URI;
import net.ncmusic.config.NcmConfig;
import net.ncmusic.netease.NcSong;
import net.ncmusic.netease.NetEaseApi;
import net.ncmusic.player.MusicPlayer;
import net.ncmusic.player.PlayMode;
import net.ncmusic.ui.MusicHud;

/** 主控制面板（默认按 M 打开） */
public class MusicControlScreen extends Screen {
	private static final String FOOTER_TEXT = "KeranTechnology © 2026   http://tech.keran.cc";
	private static final String FOOTER_URL = "http://tech.keran.cc";
	private final Screen parent;

	public MusicControlScreen(Screen parent) {
		super(Component.literal("网易云音乐"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		NcmConfig cfg = NcmConfig.get();
		MusicPlayer player = MusicPlayer.INSTANCE;
		int cx = this.width / 2;
		int top = this.height / 2 - 90;
		int bw = 100, bh = 20, gap = 4;
		int col1 = cx - bw - 8, col2 = cx + 8;

		// 播放控制行
		addRenderableWidget(Button.builder(Component.literal("⏮ 上一首"), b -> player.prev())
				.bounds(col1, top, bw, bh).build());
		addRenderableWidget(Button.builder(Component.literal(
						player.getState() == MusicPlayer.State.PLAYING ? "⏸ 暂停" : "▶ 播放"), b -> {
					player.togglePause();
					b.setMessage(Component.literal(
							MusicPlayer.INSTANCE.getState() == MusicPlayer.State.PLAYING ? "⏸ 暂停" : "▶ 播放"));
				}).bounds(col2, top, bw, bh).build());
		addRenderableWidget(Button.builder(Component.literal("⏭ 下一首"), b -> player.next())
				.bounds(col1, top + (bh + gap), bw, bh).build());
		addRenderableWidget(Button.builder(Component.literal("模式: " + player.getMode().label), b -> {
					player.setMode(player.getMode().next());
					cfg.playMode = player.getMode().name().equals("SHUFFLE") ? "shuffle" : "sequence";
					cfg.save();
					b.setMessage(Component.literal("模式: " + player.getMode().label));
				}).bounds(col2, top + (bh + gap), bw, bh).build());

		// 功能行
		addRenderableWidget(Button.builder(Component.literal("🔍 搜索歌曲"), b ->
						this.minecraft.gui.setScreen(new SearchScreen(this)))
				.bounds(col1, top + 2 * (bh + gap), bw, bh).build());
		addRenderableWidget(Button.builder(Component.literal("📃 我的歌单"), b ->
						this.minecraft.gui.setScreen(new PlaylistsScreen(this)))
				.bounds(col2, top + 2 * (bh + gap), bw, bh).build());

		// 登录行：未登录时提供 扫码 / 浏览器一键登录 两个入口
		if (NetEaseApi.INSTANCE.isLoggedIn()) {
			addRenderableWidget(Button.builder(Component.literal(
							"已登录: " + NetEaseApi.INSTANCE.getNickname()), b -> {
						NetEaseApi.INSTANCE.logout();
						NcmConfig.get().cookies.clear();
						NcmConfig.get().save();
						this.minecraft.gui.setScreen(new MusicControlScreen(parent));
					}).bounds(col1, top + 3 * (bh + gap), bw * 2 + 16, bh).build());
		} else {
			addRenderableWidget(Button.builder(Component.literal("🌐 浏览器一键登录"), b ->
							this.minecraft.gui.setScreen(new BrowserLoginScreen(this)))
					.bounds(col1, top + 3 * (bh + gap), bw, bh).build());
			addRenderableWidget(Button.builder(Component.literal("📱 扫码登录"), b ->
							this.minecraft.gui.setScreen(new QrLoginScreen(this)))
					.bounds(col2, top + 3 * (bh + gap), bw, bh).build());
		}

		// 音量行
		addRenderableWidget(Button.builder(Component.literal("音量 -"), b -> {
					cfg.volume = Math.max(0f, cfg.volume - 0.1f);
					cfg.save();
					this.minecraft.gui.setScreen(new MusicControlScreen(parent));
				}).bounds(col1, top + 4 * (bh + gap), bw, bh).build());
		addRenderableWidget(Button.builder(Component.literal("音量 +  (" + (int) (cfg.volume * 100) + "%)"), b -> {
					cfg.volume = Math.min(1f, cfg.volume + 0.1f);
					cfg.save();
					this.minecraft.gui.setScreen(new MusicControlScreen(parent));
				}).bounds(col2, top + 4 * (bh + gap), bw, bh).build());

		// HUD 行
		addRenderableWidget(Button.builder(Component.literal("HUD: " + (cfg.showHud ? "显示" : "隐藏")), b -> {
					cfg.showHud = !cfg.showHud;
					cfg.save();
					b.setMessage(Component.literal("HUD: " + (cfg.showHud ? "显示" : "隐藏")));
				}).bounds(col1, top + 5 * (bh + gap), bw, bh).build());
		addRenderableWidget(Button.builder(Component.literal("位置: " + cfg.corner.label), b -> {
					cfg.corner = cfg.corner.next();
					cfg.save();
					b.setMessage(Component.literal("位置: " + cfg.corner.label));
				}).bounds(col2, top + 5 * (bh + gap), bw, bh).build());

		// HUD 组件显隐行
		addRenderableWidget(Button.builder(Component.literal(toggleLabel("封面", cfg.showCover)), b -> {
					cfg.showCover = !cfg.showCover; cfg.save();
					b.setMessage(Component.literal(toggleLabel("封面", cfg.showCover)));
				}).bounds(col1, top + 6 * (bh + gap), bw / 2 - 2, bh).build());
		addRenderableWidget(Button.builder(Component.literal(toggleLabel("进度", cfg.showProgress)), b -> {
					cfg.showProgress = !cfg.showProgress; cfg.save();
					b.setMessage(Component.literal(toggleLabel("进度", cfg.showProgress)));
				}).bounds(col1 + bw / 2 + 2, top + 6 * (bh + gap), bw / 2 - 2, bh).build());
		addRenderableWidget(Button.builder(Component.literal(toggleLabel("歌词", cfg.showLyric)), b -> {
					cfg.showLyric = !cfg.showLyric; cfg.save();
					b.setMessage(Component.literal(toggleLabel("歌词", cfg.showLyric)));
				}).bounds(col2, top + 6 * (bh + gap), bw / 2 - 2, bh).build());
		addRenderableWidget(Button.builder(Component.literal(toggleLabel("列表", cfg.showList)), b -> {
					cfg.showList = !cfg.showList; cfg.save();
					b.setMessage(Component.literal(toggleLabel("列表", cfg.showList)));
				}).bounds(col2 + bw / 2 + 2, top + 6 * (bh + gap), bw / 2 - 2, bh).build());

		addRenderableWidget(Button.builder(Component.literal("关闭"), b -> this.onClose())
				.bounds(cx - 50, top + 7 * (bh + gap) + 6, 100, bh).build());
	}

	private static String toggleLabel(String name, boolean on) {
		return name + (on ? " ✓" : " ✗");
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
		super.extractRenderState(gfx, mouseX, mouseY, delta);
		gfx.text(this.font, "网易云音乐 客户端", this.width / 2 - this.font.width("网易云音乐 客户端") / 2,
				this.height / 2 - 108, 0xFFEC4141, false);
		MusicPlayer player = MusicPlayer.INSTANCE;
		NcSong cur = player.currentSong();
		String now = cur == null ? "未在播放"
				: "正在播放: " + cur.displayName() + "  " + MusicHud.fmtTime(player.getPositionMs())
				+ " / " + MusicHud.fmtTime(player.getDurationMs());
		gfx.text(this.font, now, this.width / 2 - this.font.width(now) / 2, this.height / 2 - 96, 0xFFFFFFFF, false);
		int fy = this.height - 16;
		String foot = FOOTER_TEXT;
		int fx = this.width / 2 - this.font.width(foot) / 2;
		boolean hover = mouseX >= fx && mouseX <= fx + this.font.width(foot) && mouseY >= fy - 1 && mouseY <= fy + 9;
		gfx.text(this.font, foot, fx, fy, hover ? 0xFF4DB8FF : 0xFF8A8A8A, false);
	}


	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && event.y() >= this.height - 17 && event.y() <= this.height - 7) {
			int fx = this.width / 2 - this.font.width(FOOTER_TEXT) / 2;
			if (event.x() >= fx && event.x() <= fx + this.font.width(FOOTER_TEXT)) {
				return Screen.clickUrlAction(this.minecraft, this, URI.create(FOOTER_URL));
			}
		}
		return super.mouseClicked(event, doubleClick);
	}
	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}
