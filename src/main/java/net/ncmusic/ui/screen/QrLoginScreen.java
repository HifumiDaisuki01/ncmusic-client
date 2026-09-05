package net.ncmusic.ui.screen;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.ncmusic.config.NcmConfig;
import net.ncmusic.netease.NetEaseApi;
import net.ncmusic.ui.MusicHud;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 扫码登录界面。
 * 轮询不依赖 Screen.tick()：由 NcMusicClient 注册的全局 END_CLIENT_TICK 每秒驱动一次，
 * 同时保留界面自身 tick 作为双保险。状态码实时显示在界面下方，便于排查。
 */
public class QrLoginScreen extends Screen {
	private static final Identifier QR_TEX = Identifier.fromNamespaceAndPath("ncmusic", "qr_login");
	private static final int QR_SIZE = 150;

	private final Screen parent;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "ncmusic-qr");
		t.setDaemon(true);
		return t;
	});

	private volatile String status = "正在生成二维码…";
	private volatile boolean qrReady = false;
	private volatile boolean done = false;
	private volatile String qrKey = null;
	private volatile long lastPollAt = 0;

	public QrLoginScreen(Screen parent) {
		super(Component.literal("网易云扫码登录"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
				.bounds(this.width / 2 - 50, this.height / 2 + 108, 100, 20).build());
		if (qrKey == null) generateQr();
	}

	/** 由全局客户端 tick 每秒调用一次（也可由界面自身 tick 调用，内部有去重） */
	public void pollIfDue() {
		if (!qrReady || done || qrKey == null) return;
		long now = System.currentTimeMillis();
		if (now - lastPollAt < 1000) return;
		lastPollAt = now;
		executor.submit(this::pollOnce);
	}

	private void pollOnce() {
		try {
			var resp = NetEaseApi.INSTANCE.qrCheck(qrKey);
			int code = resp.get("code").getAsInt();
			String cookieBody = resp.has("cookie") && !resp.get("cookie").isJsonNull()
					? resp.get("cookie").getAsString() : null;
			Minecraft mc = Minecraft.getInstance();
			mc.execute(() -> handleCode(code, cookieBody));
		} catch (Exception e) {
			status = "网络错误，将自动重试…";
		}
	}

	private void handleCode(int code, String cookieBody) {
		switch (code) {
			case 800 -> {
				status = "二维码已过期，正在刷新…";
				qrReady = false;
				qrKey = null;
				generateQr();
			}
			case 801 -> status = "请使用网易云音乐 App 扫码（801）";
			case 802 -> status = "已扫码，请在手机上确认登录（802）";
			case 200, 803 -> onSuccess(cookieBody);
			case 8821 -> {
				status = "触发风控(8821)：请用 /ncm login-cookie 登录，或稍后再试";
				done = true;
			}
			default -> status = "未知状态码: " + code;
		}
	}

	private void onSuccess(String cookieBody) {
		if (done) return;
		done = true;
		status = "登录成功，正在校验…";
		NetEaseApi api = NetEaseApi.INSTANCE;
		if (cookieBody != null && !cookieBody.isEmpty()) api.importCookieString(cookieBody);
		boolean ok = api.isLoggedIn() && api.refreshAccount();
		if (!ok) {
			status = "校验失败：未获取到登录态，请关闭本窗口后重试";
			done = false;
			return;
		}
		NcmConfig cfg = NcmConfig.get();
		cfg.cookies = api.exportCookies();
		cfg.save();
		MusicHud.showNotice("网易云登录成功: " + api.getNickname());
		this.minecraft.gui.setScreen(new MusicControlScreen(
				parent instanceof MusicControlScreen ? null : parent));
	}

	private void generateQr() {
		status = "正在生成二维码…";
		executor.submit(() -> {
			try {
				String key = NetEaseApi.INSTANCE.qrCreateKey();
				qrKey = key;
				String url = "https://music.163.com/login?codekey=" + key;
				QRCodeWriter writer = new QRCodeWriter();
				BitMatrix matrix = writer.encode(url, BarcodeFormat.QR_CODE, 1, 1);
				int w = matrix.getWidth(), h = matrix.getHeight();
				NativeImage img = new NativeImage(QR_SIZE, QR_SIZE, true);
				img.fillRect(0, 0, QR_SIZE, QR_SIZE, 0xFFFFFFFF);
				for (int px = 0; px < QR_SIZE; px++) {
					for (int py = 0; py < QR_SIZE; py++) {
						img.setPixelABGR(px, py, matrix.get(px * w / QR_SIZE, py * h / QR_SIZE)
								? 0xFF000000 : 0xFFFFFFFF);
					}
				}
				Minecraft.getInstance().execute(() -> {
					DynamicTexture tex = new DynamicTexture(() -> "ncmusic qr", img);
					tex.upload();
					Minecraft.getInstance().getTextureManager().register(QR_TEX, tex);
					qrReady = true;
					status = "请使用网易云音乐 App 扫码（801）";
				});
			} catch (Exception e) {
				status = "二维码生成失败: " + e.getMessage();
			}
		});
	}

	@Override
	public void tick() {
		super.tick();
		pollIfDue();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
		super.extractRenderState(gfx, mouseX, mouseY, delta);
		int cx = this.width / 2;
		String title = "网易云音乐 扫码登录";
		gfx.text(this.font, title, cx - this.font.width(title) / 2, this.height / 2 - 118, 0xFFEC4141, false);
		if (qrReady) {
			gfx.fill(cx - QR_SIZE / 2 - 6, this.height / 2 - 92, cx + QR_SIZE / 2 + 6,
					this.height / 2 - 92 + QR_SIZE + 12, 0xFFFFFFFF);
			gfx.blit(RenderPipelines.GUI_TEXTURED, QR_TEX, cx - QR_SIZE / 2, this.height / 2 - 86,
					0, 0, QR_SIZE, QR_SIZE, QR_SIZE, QR_SIZE);
		} else if (!done) {
			gfx.fill(cx - QR_SIZE / 2 - 6, this.height / 2 - 92, cx + QR_SIZE / 2 + 6,
					this.height / 2 - 92 + QR_SIZE + 12, 0xFF222222);
		}
		String s = status;
		gfx.text(this.font, s, cx - this.font.width(s) / 2, this.height / 2 + 84, 0xFFFFFFFF, false);
		String hint = "打开手机网易云音乐 → 扫一扫 → 确认登录";
		gfx.text(this.font, hint, cx - this.font.width(hint) / 2, this.height / 2 + 96, 0xFF9A9A9A, false);
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
