package net.ncmusic.ui.screen;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.ncmusic.config.NcmConfig;
import net.ncmusic.netease.NetEaseApi;
import net.ncmusic.ui.MusicHud;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cookie 文件 / 手动粘贴登录（无浏览器时的兜底方案）。
 * 文件方式：在 .minecraft/config/ 下新建 ncmusic-cookie.txt，内容为一行 MUSIC_U=xxx，
 * 然后点"从文件读取"。也可直接把 Cookie 串粘贴到输入框点"登录"。
 */
public class CookieLoginScreen extends Screen {
	private final Screen parent;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "ncmusic-cookie-login");
		t.setDaemon(true);
		return t;
	});

	private TextFieldWidget cookieField;
	private volatile String status = "等待输入";
	private volatile boolean running = false;

	public CookieLoginScreen(Screen parent) {
		super(new LiteralText("Cookie 登录"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int cy = this.height / 2;
		cookieField = new TextFieldWidget(this.textRenderer, cx - 160, cy - 30, 320, 20,
				new LiteralText("Cookie"));
		cookieField.setMaxLength(4096);
		this.addButton(cookieField);
		this.addButton(new ButtonWidget(cx - 160, cy, 100, 20,
				new LiteralText("从文件读取"), b -> readFromFile()));
		this.addButton(new ButtonWidget(cx - 50, cy, 100, 20,
				new LiteralText("粘贴并登录"), b -> doLogin(cookieField.getText().trim())));
		this.addButton(new ButtonWidget(cx + 60, cy, 100, 20,
				new LiteralText("返回"), b -> onClose()));
	}

	private void readFromFile() {
		executor.submit(() -> {
			try {
				Path p = FabricLoader.getInstance().getConfigDir().resolve("ncmusic-cookie.txt");
				if (!Files.exists(p)) {
					status = "未找到 config/ncmusic-cookie.txt（详见下方说明）";
					return;
				}
				List<String> lines = Files.readAllLines(p);
				StringBuilder sb = new StringBuilder();
				for (String ln : lines) {
					if (ln == null) continue;
					String t = ln.trim();
					if (t.isEmpty() || t.startsWith("#")) continue;
					if (sb.length() > 0) sb.append("; ");
					sb.append(t);
				}
				String cookie = sb.toString();
				if (cookie.isEmpty()) {
					status = "文件为空。请在其中写一行 MUSIC_U=你的值";
					return;
				}
				net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
					cookieField.setText(cookie.length() > 60 ? cookie.substring(0, 60) + "…" : cookie);
					doLogin(cookie);
				});
			} catch (Exception e) {
				status = "读取失败: " + e.getClass().getSimpleName();
			}
		});
	}

	private void doLogin(String cookie) {
		if (running) return;
		if (cookie == null || cookie.isEmpty()) {
			status = "Cookie 不能为空";
			return;
		}
		running = true;
		status = "登录中…";
		executor.submit(() -> {
			try {
				NetEaseApi api = NetEaseApi.INSTANCE;
				boolean ok = api.loginByCookie(cookie);
				if (ok) {
					NcmConfig cfg = NcmConfig.get();
					cfg.cookies = api.exportCookies();
					cfg.save();
					MusicHud.showNotice("网易云登录成功: " + api.getNickname());
					status = "登录成功: " + api.getNickname();
					net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
					mc.execute(() -> mc.openScreen(new MusicControlScreen(
							parent instanceof MusicControlScreen ? null : parent)));
				} else {
					status = "登录失败：Cookie 无效或已过期（需包含 MUSIC_U）";
					running = false;
				}
			} catch (Exception e) {
				status = "网络错误: " + e.getClass().getSimpleName();
				running = false;
			}
		});
	}

	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		this.renderBackground(matrices);
		super.render(matrices, mouseX, mouseY, delta);
		int cx = this.width / 2;
		drawCenteredText(matrices, this.textRenderer, "网易云音乐 Cookie 登录", cx, this.height / 2 - 100, 0xFFEC4141);
		String[] lines = {
				"方法一（文件）：在 .minecraft/config/ 新建 ncmusic-cookie.txt，",
				"内容写一行 MUSIC_U=你的值，然后点“从文件读取”。",
				"获取方法：浏览器登录 music.163.com → F12 → 应用 → Cookie → 复制 MUSIC_U",
				"方法二（粘贴）：把完整 Cookie 串（含 MUSIC_U=…）粘贴到输入框点登录",
		};
		int y = this.height / 2 - 78;
		for (String l : lines) {
			drawCenteredText(matrices, this.textRenderer, l, cx, y, 0xFF9A9A9A);
			y += 12;
		}
		String s = status;
		drawCenteredText(matrices, this.textRenderer, s, cx, this.height / 2 + 42, 0xFFFFFFFF);
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
