package net.ncmusic.netease;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * "浏览器登录"：用本机 Chrome/Edge 打开网易云登录页，用户正常登录，
 * 通过 Chrome DevTools 协议读取 HttpOnly Cookie（MUSIC_U），自动回填。
 */
public class BrowserLogin {
	private static final HttpClient HTTP = HttpClient.newHttpClient();

	private BrowserLogin() {}

	/** 定位本机可用浏览器（优先 Chrome，其次 Edge/Chromium） */
	public static String findBrowser() {
		String os = System.getProperty("os.name", "").toLowerCase();
		List<String> candidates = new ArrayList<>();
		if (os.contains("win")) {
			String pf = System.getenv("ProgramFiles");
			String pf86 = System.getenv("ProgramFiles(x86)");
			String lc = System.getenv("LOCALAPPDATA");
			if (pf != null) {
				candidates.add(pf + "\\Google\\Chrome\\Application\\chrome.exe");
				candidates.add(pf + "\\Microsoft\\Edge\\Application\\msedge.exe");
			}
			if (pf86 != null) {
				candidates.add(pf86 + "\\Google\\Chrome\\Application\\chrome.exe");
				candidates.add(pf86 + "\\Microsoft\\Edge\\Application\\msedge.exe");
			}
			if (lc != null) candidates.add(lc + "\\Google\\Chrome\\Application\\chrome.exe");
		} else if (os.contains("mac")) {
			candidates.add("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
			candidates.add("/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge");
			candidates.add("/Applications/Chromium.app/Contents/MacOS/Chromium");
		} else {
			for (String name : List.of("google-chrome", "google-chrome-stable", "chromium", "chromium-browser", "microsoft-edge")) {
				try {
					Process p = new ProcessBuilder("sh", "-c", "command -v " + name).redirectErrorStream(true).start();
					byte[] out = p.getInputStream().readAllBytes();
					p.waitFor();
					if (out.length > 0) candidates.add(name);
				} catch (Exception ignored) {
				}
			}
		}
		for (String c : candidates) {
			try {
				if (Files.exists(Path.of(c))) return c;
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	public record Result(Process process, int port) {}

	/** 启动浏览器打开登录页（独立临时 profile，不影响日常浏览器） */
	public static Result startBrowser(String exe, int port, String loginUrl) throws IOException {
		Path profile = FabricLoader.getInstance().getConfigDir().resolve("ncmusic-browser-profile");
		Files.createDirectories(profile);
		ProcessBuilder pb = new ProcessBuilder(exe,
				"--remote-debugging-port=" + port,
				"--remote-debugging-address=127.0.0.1",
				"--user-data-dir=" + profile,
				"--no-first-run",
				"--no-default-browser-check",
				"--new-window",
				loginUrl);
		pb.redirectErrorStream(true);
		Process process = pb.start();
		Thread t = new Thread(() -> {
			try { process.getInputStream().readAllBytes(); } catch (Exception ignored) {}
		}, "ncmusic-browser-drain");
		t.setDaemon(true);
		t.start();
		return new Result(process, port);
	}

	public static int pickPort() {
		return 9200 + new Random().nextInt(600);
	}

	public static void stopBrowser(Result r) {
		if (r == null) return;
		try { r.process().destroy(); } catch (Exception ignored) {}
		try {
			if (!r.process().waitFor(3, TimeUnit.SECONDS)) r.process().destroyForcibly();
		} catch (Exception ignored) {}
		try {
			deleteRecursively(FabricLoader.getInstance().getConfigDir().resolve("ncmusic-browser-profile"));
		} catch (Exception ignored) {}
	}

	private static void deleteRecursively(Path p) throws IOException {
		if (p == null || !Files.exists(p)) return;
		try (var walk = Files.walk(p)) {
			walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
					.forEach(x -> {
						try { Files.deleteIfExists(x); } catch (Exception ignored) {}
					});
		}
	}

	// ---------------------------------------------------------------- CDP

	/** 轮询获取网易云 cookie，最长 maxWaitMs 毫秒；成功返回 cookie 串，否则 null */
	public static String captureMusicUCookie(int port, long maxWaitMs) {
		long deadline = System.currentTimeMillis() + maxWaitMs;
		String pageWs = null;
		while (System.currentTimeMillis() < deadline) {
			pageWs = findLoginPageWs(port);
			if (pageWs != null) break;
			sleep(800);
		}
		if (pageWs == null) return null;

		BlockingQueue<JsonObject> inbox = new LinkedBlockingQueue<>();
		final WebSocket ws;
		try {
			ws = HTTP.newWebSocketBuilder()
					.connectTimeout(java.time.Duration.ofSeconds(5))
					.buildAsync(URI.create(pageWs), new WebSocket.Listener() {
						private final StringBuilder buf = new StringBuilder();

						@Override
						public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
							buf.append(data);
							if (last) {
								try {
									JsonObject o = JsonParser.parseString(buf.toString()).getAsJsonObject();
									if (o.has("id") || o.has("method")) inbox.offer(o);
								} catch (Exception ignored) {
								}
								buf.setLength(0);
							}
							webSocket.request(1);
							return null;
						}
					}).join();
		} catch (Exception e) {
			return null;
		}

		try {
			request(ws, inbox, 1, "Network.enable", null);
			int msgId = 1;
			while (System.currentTimeMillis() < deadline) {
				JsonObject resp = request(ws, inbox, ++msgId, "Network.getAllCookies", null);
				if (resp != null) {
					String header = cookiesToHeader(resp);
					if (header.contains("MUSIC_U=")) return header;
				}
				sleep(1200);
			}
		} finally {
			try { ws.sendClose(WebSocket.NORMAL_CLOSURE, "done"); } catch (Exception ignored) {}
		}
		return null;
	}

	private static String cookiesToHeader(JsonObject resp) {
		StringBuilder header = new StringBuilder();
		JsonElement result = resp.get("result");
		if (result == null || !result.isJsonObject()) return "";
		JsonElement cookiesEl = result.getAsJsonObject().get("cookies");
		if (cookiesEl == null || !cookiesEl.isJsonArray()) return "";
		for (JsonElement el : cookiesEl.getAsJsonArray()) {
			JsonObject c = el.getAsJsonObject();
			if (!c.has("name") || !c.has("value")) continue;
			String domain = c.has("domain") ? c.get("domain").getAsString() : "";
			if (domain.endsWith(".163.com") || domain.equals("music.163.com")) {
				if (header.length() > 0) header.append("; ");
				header.append(c.get("name").getAsString()).append('=')
						.append(c.get("value").getAsString());
			}
		}
		return header.toString();
	}

	private static JsonObject request(WebSocket ws, BlockingQueue<JsonObject> inbox,
									  int id, String method, JsonObject params) {
		JsonObject req = new JsonObject();
		req.addProperty("id", id);
		req.addProperty("method", method);
		if (params != null) req.add("params", params);
		try {
			ws.sendText(req.toString(), true).join();
		} catch (Exception e) {
			return null;
		}
		// 等待 id 匹配的响应（忽略 method 事件）
		long deadline = System.currentTimeMillis() + 5000;
		while (System.currentTimeMillis() < deadline) {
			JsonObject o;
			try {
				o = inbox.poll(500, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return null;
			}
			if (o != null && o.has("id") && o.get("id").getAsInt() == id) return o;
		}
		return null;
	}

	private static String findLoginPageWs(int port) {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json/list"))
					.timeout(java.time.Duration.ofSeconds(2)).GET().build();
			HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() != 200) return null;
			JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
			JsonObject best = null;
			for (JsonElement el : arr) {
				JsonObject t = el.getAsJsonObject();
				if (!"page".equals(t.get("type").getAsString())) continue;
				String u = t.has("url") ? t.get("url").getAsString() : "";
				if (u.contains("music.163.com")) {
					return t.get("webSocketDebuggerUrl").getAsString();
				}
				if (best == null) best = t;
			}
			if (best != null) return best.get("webSocketDebuggerUrl").getAsString();
		} catch (Exception ignored) {
		}
		return null;
	}

	private static void sleep(long ms) {
		try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
	}
}
