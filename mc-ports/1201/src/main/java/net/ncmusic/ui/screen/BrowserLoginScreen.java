package net.ncmusic.ui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.ncmusic.config.NcmConfig;
import net.ncmusic.netease.BrowserLogin;
import net.ncmusic.netease.NetEaseApi;
import net.ncmusic.ui.MusicHud;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 浏览器一键登录：唤起本机 Chrome/Edge → 打开网易云登录页 → 用户登录后
 * 自动读取 Cookie（无需复制粘贴，HttpOnly 的 MUSIC_U 也能抓到）。
 */
public class BrowserLoginScreen extends Screen {
	private final Screen parent;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "ncmusic-browser-login");
		t.setDaemon(true);
		return t;
	});

	private volatile String status = "准备就绪";
	private volatile boolean running = false;
	private volatile boolean done = false;
	private volatile BrowserLogin.Result browser;

	public BrowserLoginScreen(Screen parent) {
		super(Component.literal("浏览器登录网易云"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = this.height / 2 + 20;
		Button start = Button.builder(Component.literal("打开浏览器并登录"), b -> startLogin())
				.bounds(cx - 150, y, 300, 20).build();
		addRenderableWidget(start);
		addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
				.bounds(cx - 50, y + 30, 100, 20).build());
	}

	private void startLogin() {
		if (running) return;
		running = true;
		status = "正在寻找浏览器…";
		executor.submit(() -> {
			try {
				String exe = BrowserLogin.findBrowser();
				if (exe == null) {
					status = "未找到 Chrome/Edge。请改用命令 /ncm cookie-file 方式登录（见 README）";
					running = false;
					return;
				}
				int port = BrowserLogin.pickPort();
				status = "正在启动浏览器…";
				browser = BrowserLogin.startBrowser(exe, port, "https://music.163.com/login");
				status = "浏览器已打开：请在弹窗中登录网易云音乐（支持扫码 / 账号登录）";
				Minecraft mc = Minecraft.getInstance();
				String cookie = BrowserLogin.captureMusicUCookie(port, 10 * 60 * 1000L);
				if (cookie != null && NetEaseApi.INSTANCE.loginByCookie(cookie)) {
					NcmConfig cfg = NcmConfig.get();
					cfg.cookies = NetEaseApi.INSTANCE.exportCookies();
					cfg.save();
					status = "登录成功: " + NetEaseApi.INSTANCE.getNickname();
					MusicHud.showNotice("网易云登录成功: " + NetEaseApi.INSTANCE.getNickname());
					done = true;
					executor.submit(() -> {
						try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
						mc.execute(() -> mc.setScreen(new MusicControlScreen(
								parent instanceof MusicControlScreen ? null : parent)));
					});
				} else {
					status = "未检测到登录（10 分钟内）。可关闭本窗口重新打开再试";
					running = false;
				}
			} catch (Exception e) {
				status = "启动失败: " + e.getClass().getSimpleName() + " " + e.getMessage();
				running = false;
			} finally {
				// 成功后清理浏览器
				if (done) BrowserLogin.stopBrowser(browser);
			}
		});
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		this.renderBackground(g);
		super.render(g, mouseX, mouseY, delta);
		int cx = this.width / 2;
		String title = "网易云音乐 · 浏览器登录";
		g.drawString(this.font, title, cx - this.font.width(title) / 2, this.height / 2 - 60, 0xFFEC4141, false);
		String[] lines = {
				"1. 点击下方按钮，会自动打开本机 Chrome/Edge",
				"2. 在弹出的浏览器窗口里正常登录网易云音乐（扫码 / 账号都行）",
				"3. 登录成功后本窗口会自动检测到 Cookie 并保存，无需手动复制",
		};
		int y = this.height / 2 - 38;
		for (String l : lines) {
			g.drawString(this.font, l, cx - this.font.width(l) / 2, y, 0xFF9A9A9A, false);
			y += 12;
		}
		String s = running ? status : "等待开始…";
		g.drawString(this.font, s, cx - this.font.width(s) / 2, this.height / 2 + 4, 0xFFFFFFFF, false);
	}

	@Override
	public void onClose() {
		if (!done) BrowserLogin.stopBrowser(browser);
		this.minecraft.setScreen(parent);
	}

	@Override
	public void removed() {
		executor.shutdownNow();
	}
}
