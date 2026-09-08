package net.ncmusic.ui.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.ncmusic.config.NcmConfig;
import net.ncmusic.netease.NetEaseApi;
import net.ncmusic.ui.MusicHud;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 手机号 + 密码登录（网易云 weapi /weapi/login/cellphone）。
 * 注意：2026 年起网易云对账密登录也有风控，失败请改用浏览器一键登录。
 */
public class PhoneLoginScreen extends Screen {
	private final Screen parent;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "ncmusic-phone-login");
		t.setDaemon(true);
		return t;
	});

	private TextFieldWidget phoneField;
	private TextFieldWidget passField;
	private volatile String status = "输入手机号和密码";
	private volatile boolean running = false;

	public PhoneLoginScreen(Screen parent) {
		super(new LiteralText("手机号登录"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int cy = this.height / 2;
		phoneField = new TextFieldWidget(this.textRenderer, cx - 110, cy - 44, 220, 20, new LiteralText("手机号"));
		phoneField.setMaxLength(20);
		this.addButton(phoneField);
		passField = new TextFieldWidget(this.textRenderer, cx - 110, cy - 16, 220, 20, new LiteralText("密码"));
		passField.setMaxLength(64);
		// 遮蔽输入
		passField.setRenderTextProvider((text, cursor) ->
				new LiteralText("*".repeat(text.length())).asOrderedText());
		this.addButton(passField);
		this.addButton(new ButtonWidget(cx - 60, cy + 12, 120, 20,
				new LiteralText("登录"), b -> doLogin()));
		this.addButton(new ButtonWidget(cx - 50, cy + 84, 100, 20,
				new LiteralText("返回"), b -> onClose()));
		phoneField.setTextFieldFocused(true);
		this.setFocused(phoneField);
	}

	private void doLogin() {
		if (running) return;
		String phone = phoneField.getText().trim();
		String pass = passField.getText();
		if (phone.isEmpty() || pass.isEmpty()) {
			status = "手机号或密码不能为空";
			return;
		}
		running = true;
		status = "登录中…";
		executor.submit(() -> {
			try {
				var resp = NetEaseApi.INSTANCE.loginCellphone(phone, pass, "86");
				int code = resp.has("code") ? resp.get("code").getAsInt() : -1;
				String msg = resp.has("message") && !resp.get("message").isJsonNull()
						? resp.get("message").getAsString() : "";
				if (code == 200) {
					String cookieBody = resp.has("cookie") && !resp.get("cookie").isJsonNull()
							? resp.get("cookie").getAsString() : null;
					NetEaseApi api = NetEaseApi.INSTANCE;
					if (cookieBody != null && !cookieBody.isEmpty()) api.importCookieString(cookieBody);
					boolean ok = api.isLoggedIn() && api.refreshAccount();
					if (!ok) {
						status = "校验失败：未获取到登录态，请重试或改用浏览器登录";
						running = false;
						return;
					}
					NcmConfig cfg = NcmConfig.get();
					cfg.cookies = api.exportCookies();
					cfg.save();
					MusicHud.showNotice("网易云登录成功: " + api.getNickname());
					status = "登录成功: " + api.getNickname();
					net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
					mc.execute(() -> mc.openScreen(new MusicControlScreen(
							parent instanceof MusicControlScreen ? null : parent)));
				} else {
					status = "登录失败 code=" + code + (msg.isEmpty() ? "" : " " + msg)
							+ "（可能触发风控，请改用浏览器登录）";
					running = false;
				}
			} catch (Exception e) {
				status = "网络错误: " + e.getClass().getSimpleName();
				running = false;
			}
		});
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 257 || keyCode == 335) { // Enter / 小键盘 Enter
			if (this.getFocused() == phoneField || this.getFocused() == passField) {
				doLogin();
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		this.renderBackground(matrices);
		super.render(matrices, mouseX, mouseY, delta);
		int cx = this.width / 2;
		drawCenteredText(matrices, this.textRenderer, "网易云音乐 手机号登录", cx, this.height / 2 - 76, 0xFFEC4141);
		drawCenteredText(matrices, this.textRenderer, "手机号", cx, this.height / 2 - 56, 0xFF9A9A9A);
		drawCenteredText(matrices, this.textRenderer, "密码", cx, this.height / 2 - 28, 0xFF9A9A9A);
		String s = status;
		drawCenteredText(matrices, this.textRenderer, s, cx, this.height / 2 + 58, 0xFFFFFFFF);
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
