package net.ncmusic;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.ncmusic.command.NcmCommands;
import net.ncmusic.config.NcmConfig;
import net.ncmusic.netease.NcSong;
import net.ncmusic.netease.NetEaseApi;
import net.ncmusic.player.MusicPlayer;
import net.ncmusic.netease.NcSong;
import net.ncmusic.ui.CoverManager;
import java.util.List;
import net.ncmusic.player.PlayMode;
import net.ncmusic.ui.MusicHud;
import net.ncmusic.ui.screen.MusicControlScreen;
import net.ncmusic.ui.screen.QrLoginScreen;
import org.lwjgl.glfw.GLFW;

public class NcMusicClient implements ClientModInitializer, MusicPlayer.Listener {
	public static final String MOD_ID = "ncmusic";

	private static KeyMapping panelKey;

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
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "player_hud"), new MusicHud());

		// 按键：M 打开控制面板
		panelKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.ncmusic.panel", GLFW.GLFW_KEY_M,
				KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "music"))));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			// 扫码登录轮询（不依赖界面 tick，双保险）
			if (mc.screen instanceof QrLoginScreen qr) qr.pollIfDue();
			while (panelKey.consumeClick()) {
				if (mc.screen instanceof MusicControlScreen) {
					mc.setScreen(null);
				} else if (mc.screen == null) {
					mc.setScreen(new MusicControlScreen(null));
				}
			}
		});

		// 命令
		NcmCommands.register();

		// 退出时清理
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> MusicPlayer.INSTANCE.shutdown());
	}

	// ------------------------------------------------ MusicPlayer.Listener

	@Override
	public void onTrackChanged(NcSong song) {
		MusicHud.showNotice("♪ " + song.displayName());
		// 预取当前歌封面 + 接下来几首，保证切歌时封面已就绪
		List<NcSong> q = MusicPlayer.INSTANCE.getQueue();
		int i = MusicPlayer.INSTANCE.getIndex();
		if (song != null) CoverManager.INSTANCE.get(song);
		for (int j = i + 1; j <= i + 3 && j < q.size(); j++) {
			CoverManager.INSTANCE.get(q.get(j));
		}
	}

	@Override
	public void onError(String message) {
		MusicHud.showNotice(message);
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.player != null) {
				mc.player.displayClientMessage(Component.literal("[NC音乐] " + message), false);
			}
		});
	}
}
