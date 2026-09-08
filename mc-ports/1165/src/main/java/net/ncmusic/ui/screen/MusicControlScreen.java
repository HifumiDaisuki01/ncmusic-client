package net.ncmusic.ui.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Util;
import net.ncmusic.config.NcmConfig;
import net.ncmusic.netease.NcSong;
import net.ncmusic.netease.NetEaseApi;
import net.ncmusic.player.MusicPlayer;
import net.ncmusic.player.PlayMode;
import net.ncmusic.ui.MusicHud;

/**
 * 主控制面板（默认按 M 打开）。
 * 1.16.5 无客户端命令 API，因此登录三方式（浏览器/扫码/手机号/Cookie 文件）、搜索、
 * 歌单、播放控制、HUD 显隐/角位/组件开关全部集中在本面板。
 */
public class MusicControlScreen extends Screen {
	private static final String FOOTER_TEXT = "KeranTechnology © 2026   http://tech.keran.cc";
	private static final String FOOTER_URL = "http://tech.keran.cc";
	private final Screen parent;

	public MusicControlScreen(Screen parent) {
		super(new LiteralText("网易云音乐"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		NcmConfig cfg = NcmConfig.get();
		MusicPlayer player = MusicPlayer.INSTANCE;
		int cx = this.width / 2;
		int top = this.height / 2 - 118;
		int bw = 100, bh = 20, gap = 4;
		int col1 = cx - bw - 8, col2 = cx + 8;

		// 播放控制行
		ButtonWidget prevBtn = new ButtonWidget(col1, top, bw, bh, new LiteralText("上一首"), b -> player.prev());
		this.addButton(prevBtn);
		ButtonWidget toggleBtn = new ButtonWidget(col2, top, bw, bh,
				new LiteralText(player.getState() == MusicPlayer.State.PLAYING ? "⏸ 暂停" : "▶ 播放"), b -> {
					player.togglePause();
					b.setMessage(new LiteralText(
							MusicPlayer.INSTANCE.getState() == MusicPlayer.State.PLAYING ? "⏸ 暂停" : "▶ 播放"));
				});
		this.addButton(toggleBtn);
		this.addButton(new ButtonWidget(col1, top + (bh + gap), bw, bh, new LiteralText("下一首"), b -> player.next()));
		ButtonWidget modeBtn = new ButtonWidget(col2, top + (bh + gap), bw, bh,
				new LiteralText("模式: " + player.getMode().label), b -> {
					player.setMode(player.getMode().next());
					cfg.playMode = player.getMode().name().equals("SHUFFLE") ? "shuffle" : "sequence";
					cfg.save();
					b.setMessage(new LiteralText("模式: " + player.getMode().label));
				});
		this.addButton(modeBtn);

		// 功能行
		this.addButton(new ButtonWidget(col1, top + 2 * (bh + gap), bw, bh, new LiteralText("搜索歌曲"),
				b -> this.client.openScreen(new SearchScreen(this))));
		this.addButton(new ButtonWidget(col2, top + 2 * (bh + gap), bw, bh, new LiteralText("我的歌单"),
				b -> this.client.openScreen(new PlaylistsScreen(this))));

		// 登录行：未登录时提供 浏览器 / 扫码 / 手机号 / Cookie 文件 四个入口
		if (NetEaseApi.INSTANCE.isLoggedIn()) {
			ButtonWidget logoutBtn = new ButtonWidget(col1, top + 3 * (bh + gap), bw * 2 + 16, bh,
					new LiteralText("已登录: " + NetEaseApi.INSTANCE.getNickname()), b -> {
						NetEaseApi.INSTANCE.logout();
						NcmConfig.get().cookies.clear();
						NcmConfig.get().save();
						this.client.openScreen(new MusicControlScreen(parent));
					});
			this.addButton(logoutBtn);
		} else {
			this.addButton(new ButtonWidget(col1, top + 3 * (bh + gap), bw, bh, new LiteralText("浏览器一键登录"),
					b -> this.client.openScreen(new BrowserLoginScreen(this))));
			this.addButton(new ButtonWidget(col2, top + 3 * (bh + gap), bw, bh, new LiteralText("扫码登录"),
					b -> this.client.openScreen(new QrLoginScreen(this))));
			this.addButton(new ButtonWidget(col1, top + 4 * (bh + gap), bw, bh, new LiteralText("手机号登录"),
					b -> this.client.openScreen(new PhoneLoginScreen(this))));
			this.addButton(new ButtonWidget(col2, top + 4 * (bh + gap), bw, bh, new LiteralText("Cookie 文件"),
					b -> this.client.openScreen(new CookieLoginScreen(this))));
		}

		// 音量行
		this.addButton(new ButtonWidget(col1, top + 5 * (bh + gap), bw, bh, new LiteralText("音量 -"), b -> {
					cfg.volume = Math.max(0f, cfg.volume - 0.1f);
					cfg.save();
					this.client.openScreen(new MusicControlScreen(parent));
				}));
		this.addButton(new ButtonWidget(col2, top + 5 * (bh + gap), bw, bh,
				new LiteralText("音量 +  (" + (int) (cfg.volume * 100) + "%)"), b -> {
					cfg.volume = Math.min(1f, cfg.volume + 0.1f);
					cfg.save();
					this.client.openScreen(new MusicControlScreen(parent));
				}));

		// HUD 行
		ButtonWidget hudBtn = new ButtonWidget(col1, top + 6 * (bh + gap), bw, bh,
				new LiteralText("HUD: " + (cfg.showHud ? "显示" : "隐藏")), b -> {
					cfg.showHud = !cfg.showHud;
					cfg.save();
					b.setMessage(new LiteralText("HUD: " + (cfg.showHud ? "显示" : "隐藏")));
				});
		this.addButton(hudBtn);
		ButtonWidget cornerBtn = new ButtonWidget(col2, top + 6 * (bh + gap), bw, bh,
				new LiteralText("位置: " + cfg.corner.label), b -> {
					cfg.corner = cfg.corner.next();
					cfg.save();
					b.setMessage(new LiteralText("位置: " + cfg.corner.label));
				});
		this.addButton(cornerBtn);

		// HUD 组件显隐行
		ButtonWidget coverBtn = new ButtonWidget(col1, top + 7 * (bh + gap), bw / 2 - 2, bh,
				new LiteralText(toggleLabel("封面", cfg.showCover)), b -> {
					cfg.showCover = !cfg.showCover; cfg.save();
					b.setMessage(new LiteralText(toggleLabel("封面", cfg.showCover)));
				});
		this.addButton(coverBtn);
		ButtonWidget progressBtn = new ButtonWidget(col1 + bw / 2 + 2, top + 7 * (bh + gap), bw / 2 - 2, bh,
				new LiteralText(toggleLabel("进度", cfg.showProgress)), b -> {
					cfg.showProgress = !cfg.showProgress; cfg.save();
					b.setMessage(new LiteralText(toggleLabel("进度", cfg.showProgress)));
				});
		this.addButton(progressBtn);
		ButtonWidget lyricBtn = new ButtonWidget(col2, top + 7 * (bh + gap), bw / 2 - 2, bh,
				new LiteralText(toggleLabel("歌词", cfg.showLyric)), b -> {
					cfg.showLyric = !cfg.showLyric; cfg.save();
					b.setMessage(new LiteralText(toggleLabel("歌词", cfg.showLyric)));
				});
		this.addButton(lyricBtn);
		ButtonWidget listBtn = new ButtonWidget(col2 + bw / 2 + 2, top + 7 * (bh + gap), bw / 2 - 2, bh,
				new LiteralText(toggleLabel("列表", cfg.showList)), b -> {
					cfg.showList = !cfg.showList; cfg.save();
					b.setMessage(new LiteralText(toggleLabel("列表", cfg.showList)));
				});
		this.addButton(listBtn);

		this.addButton(new ButtonWidget(cx - 50, top + 8 * (bh + gap) + 6, 100, bh,
				new LiteralText("关闭"), b -> this.onClose()));
	}

	private static String toggleLabel(String name, boolean on) {
		return name + (on ? " ✓" : " ✗");
	}

	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		this.renderBackground(matrices);
		super.render(matrices, mouseX, mouseY, delta);
		String title = "网易云音乐 客户端";
		drawCenteredText(matrices, this.textRenderer, title, this.width / 2, this.height / 2 - 138, 0xFFEC4141);
		MusicPlayer player = MusicPlayer.INSTANCE;
		NcSong cur = player.currentSong();
		String now = cur == null ? "未在播放"
				: "正在播放: " + cur.displayName() + "  " + MusicHud.fmtTime(player.getPositionMs())
				+ " / " + MusicHud.fmtTime(player.getDurationMs());
		drawCenteredText(matrices, this.textRenderer, now, this.width / 2, this.height / 2 - 126, 0xFFFFFFFF);
		int fy = this.height - 16;
		String foot = FOOTER_TEXT;
		int fx = this.width / 2 - this.textRenderer.getWidth(foot) / 2;
		boolean hover = mouseX >= fx && mouseX <= fx + this.textRenderer.getWidth(foot) && mouseY >= fy - 1 && mouseY <= fy + 9;
		this.textRenderer.draw(matrices, foot, fx, fy, hover ? 0xFF4DB8FF : 0xFF8A8A8A);
	}


	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && mouseY >= this.height - 17 && mouseY <= this.height - 7) {
			int fx = this.width / 2 - this.textRenderer.getWidth(FOOTER_TEXT) / 2;
			if (mouseX >= fx && mouseX <= fx + this.textRenderer.getWidth(FOOTER_TEXT)) {
				Util.getOperatingSystem().open(FOOTER_URL);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}
	@Override
	public void onClose() {
		this.client.openScreen(parent);
	}
}
