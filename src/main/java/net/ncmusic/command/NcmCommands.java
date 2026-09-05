package net.ncmusic.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.ncmusic.config.NcmConfig;
import net.ncmusic.netease.NetEaseApi;
import net.ncmusic.player.MusicPlayer;
import net.ncmusic.player.PlayMode;
import net.ncmusic.ui.MusicHud;
import net.ncmusic.ui.screen.MusicControlScreen;
import net.ncmusic.ui.screen.PlaylistsScreen;
import net.ncmusic.ui.screen.BrowserLoginScreen;
import net.ncmusic.ui.screen.QrLoginScreen;
import net.ncmusic.ui.screen.SearchScreen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** /ncm 客户端命令 */
public final class NcmCommands {
	private static final org.slf4j.Logger LOGGER =
			org.slf4j.LoggerFactory.getLogger("ncmusic");
	private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "ncmusic-cmd");
		t.setDaemon(true);
		return t;
	});

	private NcmCommands() {}

	/** 在专用后台线程执行网络/文件 IO；任何 UI 操作请用 feedbackSafe */
	private static void async(Runnable r) {
		EXEC.submit(r);
	}

	/** 后台线程中安全地输出结果（切回主线程后调用） */
	private static void feedbackSafe(String msg) {
		Minecraft.getInstance().execute(() -> feedback(msg));
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommands.literal("ncm")
						.executes(ctx -> {
							open(new MusicControlScreen(null));
							return 1;
						})
						.then(ClientCommands.literal("login").executes(ctx -> {
							open(new QrLoginScreen(null));
							return 1;
						}))
						.then(ClientCommands.literal("browser").executes(ctx -> {
							open(new BrowserLoginScreen(null));
							return 1;
						}))
						.then(ClientCommands.literal("loginphone")
								.then(ClientCommands.argument("phone", StringArgumentType.word())
										.then(ClientCommands.argument("password", StringArgumentType.greedyString())
												.executes(ctx -> {
											String phone = StringArgumentType.getString(ctx, "phone");
											String pwd = StringArgumentType.getString(ctx, "password");
											async(() -> {
												try {
													var r = NetEaseApi.INSTANCE.loginCellphone(phone, pwd, "86");
													int code = r.get("code").getAsInt();
													if (code == 200) {
														NcmConfig cfg = NcmConfig.get();
														cfg.cookies = NetEaseApi.INSTANCE.exportCookies();
														cfg.save();
														NetEaseApi.INSTANCE.refreshAccount();
														cfg.cookies = NetEaseApi.INSTANCE.exportCookies();
														cfg.save();
														feedbackSafe("登录成功: " + NetEaseApi.INSTANCE.getNickname());
													} else if (code == 461) {
														feedbackSafe("需要滑块/验证码验证，请改用扫码登录 (/ncm login)");
													} else {
														String msg = r.has("message") ? r.get("message").getAsString() : ("code " + code);
														feedbackSafe("登录失败: " + msg);
													}
												} catch (Exception e) {
													LOGGER.error("手机登录失败", e);
													feedbackSafe("登录失败: [" + e.getClass().getSimpleName() + "] " + e.getMessage());
												}
											});
													return 1;
												}))))
						.then(ClientCommands.literal("login-cookie")
								.then(ClientCommands.argument("cookie", StringArgumentType.greedyString())
										.executes(ctx -> {
											String cookie = StringArgumentType.getString(ctx, "cookie");
											async(() -> {
												try {
													boolean ok = NetEaseApi.INSTANCE.loginByCookie(cookie);
													if (ok) {
														NcmConfig cfg = NcmConfig.get();
														cfg.cookies = NetEaseApi.INSTANCE.exportCookies();
														cfg.save();
														feedbackSafe("Cookie 登录成功: " + NetEaseApi.INSTANCE.getNickname());
													} else {
														feedbackSafe("Cookie 登录失败：请确认粘贴的是网易云登录后的 Cookie（需含 MUSIC_U）");
													}
												} catch (Exception e) {
													LOGGER.error("Cookie 登录失败", e);
													feedbackSafe("Cookie 登录失败: [" + e.getClass().getSimpleName() + "] " + e.getMessage());
												}
											});
											return 1;
										})))
						.then(ClientCommands.literal("cookie-file").executes(ctx -> {
							// 从 config/ncmusic-cookie.txt 读取整行 Cookie（避免聊天框长度限制）
							Path p = FabricLoader.getInstance().getConfigDir().resolve("ncmusic-cookie.txt");
							async(() -> {
								try {
									if (!Files.exists(p)) {
										feedbackSafe("未找到文件 " + p + "：请先在该位置新建 ncmusic-cookie.txt，内容为 MUSIC_U=你的值");
										return;
									}
									String content = Files.readString(p).trim();
									boolean ok = NetEaseApi.INSTANCE.loginByCookie(content);
									if (ok) {
										NcmConfig cfg = NcmConfig.get();
										cfg.cookies = NetEaseApi.INSTANCE.exportCookies();
										cfg.save();
										feedbackSafe("Cookie 登录成功: " + NetEaseApi.INSTANCE.getNickname());
									} else {
										feedbackSafe("Cookie 无效或已过期，请重新在浏览器登录后复制");
									}
								} catch (Exception e) {
									LOGGER.error("读取 Cookie 文件失败", e);
									feedbackSafe("读取失败: [" + e.getClass().getSimpleName() + "] " + e.getMessage());
								}
							});
							return 1;
						}))
						.then(ClientCommands.literal("logout").executes(ctx -> {
							NetEaseApi.INSTANCE.logout();
							NcmConfig.get().cookies.clear();
							NcmConfig.get().save();
							feedback("已退出网易云登录");
							return 1;
						}))
						.then(ClientCommands.literal("search").executes(ctx -> {
							open(new SearchScreen(null));
							return 1;
						}))
						.then(ClientCommands.literal("playlist").executes(ctx -> {
							open(new PlaylistsScreen(null));
							return 1;
						}))
						.then(ClientCommands.literal("play").executes(ctx -> {
							MusicPlayer.INSTANCE.togglePause();
							return 1;
						}))
						.then(ClientCommands.literal("pause").executes(ctx -> {
							MusicPlayer.INSTANCE.togglePause();
							return 1;
						}))
						.then(ClientCommands.literal("next").executes(ctx -> {
							MusicPlayer.INSTANCE.next();
							return 1;
						}))
						.then(ClientCommands.literal("prev").executes(ctx -> {
							MusicPlayer.INSTANCE.prev();
							return 1;
						}))
						.then(ClientCommands.literal("stop").executes(ctx -> {
							MusicPlayer.INSTANCE.stop();
							return 1;
						}))
						.then(ClientCommands.literal("mode")
								.then(ClientCommands.argument("mode", StringArgumentType.word())
										.executes(ctx -> {
											String m = StringArgumentType.getString(ctx, "mode");
											PlayMode mode = m.startsWith("sh") ? PlayMode.SHUFFLE : PlayMode.SEQUENCE;
											MusicPlayer.INSTANCE.setMode(mode);
											NcmConfig.get().playMode = mode == PlayMode.SHUFFLE ? "shuffle" : "sequence";
											NcmConfig.get().save();
											feedback("播放模式: " + mode.label);
											return 1;
										})))
						.then(ClientCommands.literal("volume")
								.then(ClientCommands.argument("percent", IntegerArgumentType.integer(0, 100))
										.executes(ctx -> {
											int v = IntegerArgumentType.getInteger(ctx, "percent");
											NcmConfig.get().volume = v / 100f;
											NcmConfig.get().save();
											feedback("音量: " + v + "%（下一首生效）");
											return 1;
										})))
						.then(ClientCommands.literal("hud")
								.executes(ctx -> {
									NcmConfig cfg = NcmConfig.get();
									cfg.showHud = !cfg.showHud;
									cfg.save();
									feedback("HUD: " + (cfg.showHud ? "显示" : "隐藏"));
									return 1;
								})
								.then(ClientCommands.literal("corner").executes(ctx -> {
									NcmConfig cfg = NcmConfig.get();
									cfg.corner = cfg.corner.next();
									cfg.save();
									feedback("HUD 位置: " + cfg.corner.label);
									return 1;
								})))
		));
	}

	private static void open(net.minecraft.client.gui.screens.Screen screen) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.gui.setScreen(screen));
	}

	private static void feedback(String msg) {
		MusicHud.showNotice(msg);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[NC音乐] " + msg));
	}
}
