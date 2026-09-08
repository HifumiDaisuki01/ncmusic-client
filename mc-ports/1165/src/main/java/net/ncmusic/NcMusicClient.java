package net.ncmusic;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.LiteralText;
import net.ncmusic.config.NcmConfig;
import net.ncmusic.netease.NetEaseApi;
import net.ncmusic.player.MusicPlayer;
import net.ncmusic.player.PlayMode;
import net.ncmusic.ui.MusicHud;
import net.ncmusic.ui.screen.MusicControlScreen;
import net.ncmusic.ui.screen.QrLoginScreen;
import org.lwjgl.glfw.GLFW;

/**
 * 入口：初始化登录态、HUD、M 键面板、扫码轮询、退出清理。
 * 1.16.5 无客户端命令 API（fabric 命令 v2 需要 1.19+），故不再注册 /ncm 命令，
 * 所有功能均由按 M 打开的面板提供。
 */
public class NcMusicClient implements ClientModInitializer, MusicPlayer.Listener {
	public static final String MOD_ID = "ncmusic";

	private static KeyBinding panelKey;

	@Override
	public void onInitializeClient() {
		// 配置与登录态
		NcmConfig cfg = NcmConfig.get();
		NetEaseApi api = NetEaseApi.INSTANCE;
		api.importCookies(cfg.cookies);
		MusicPlayer.INSTANCE.setMode(PlayMode.fromName(cfg.playMode));
		MusicPlayer.INSTANCE.addListener(this);

		// 后台初始化网络层
		Thread netInit = new Thread(() -> {
			api.initBaseCookies();
			if (api.isLoggedIn()) {
				api.refreshAccount();
				cfg.cookies = api.exportCookies();
				cfg.save();
			}
		}, "ncmusic-init");
		netInit.setDaemon(true);
		netInit.start();

		// HUD
		MusicHud.register();

		// 按键：M 打开控制面板（1.16.5 无 /ncm 命令，全部功能走面板）
		panelKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.ncmusic.panel", GLFW.GLFW_KEY_M,
				"key.categories.ncmusic"));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			// 扫码登录轮询（不依赖界面 tick，双保险）
			if (mc.currentScreen instanceof QrLoginScreen) {
				((QrLoginScreen) mc.currentScreen).pollIfDue();
			}
			while (panelKey.wasPressed()) {
				if (mc.currentScreen instanceof MusicControlScreen) {
					mc.openScreen(null);
				} else if (mc.currentScreen == null) {
					mc.openScreen(new MusicControlScreen(null));
				}
			}
		});

		// 退出时清理
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> MusicPlayer.INSTANCE.shutdown());
	}

	// ------------------------------------------------ MusicPlayer.Listener

	@Override
	public void onTrackChanged(net.ncmusic.netease.NcSong song) {
		MusicHud.showNotice("♪ " + song.displayName());
		// 预取当前歌封面 + 接下来几首，保证切歌时封面已就绪
		java.util.List<net.ncmusic.netease.NcSong> q = MusicPlayer.INSTANCE.getQueue();
		int i = MusicPlayer.INSTANCE.getIndex();
		if (song != null) net.ncmusic.ui.CoverManager.INSTANCE.get(song);
		for (int j = i + 1; j <= i + 3 && j < q.size(); j++) {
			net.ncmusic.ui.CoverManager.INSTANCE.get(q.get(j));
		}
	}

	@Override
	public void onError(String message) {
		MusicHud.showNotice(message);
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.execute(() -> {
			if (mc.player != null && mc.inGameHud != null && mc.inGameHud.getChatHud() != null) {
				mc.inGameHud.getChatHud().addMessage(new LiteralText("[NC音乐] " + message));
			}
		});
	}
}
