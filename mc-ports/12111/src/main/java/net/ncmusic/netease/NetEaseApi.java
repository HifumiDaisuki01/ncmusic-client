package net.ncmusic.netease;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网易云音乐网页端（weapi）API 封装。
 * 全部请求直接发往 music.163.com，无需任何第三方服务。
 */
public class NetEaseApi {
	public static final NetEaseApi INSTANCE = new NetEaseApi();

	private static final String BASE = "https://music.163.com";
	private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
			+ "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

	private final Gson gson = new Gson();
	private final CookieManager cookieManager;
	private final HttpClient http;

	private volatile long userId = 0;
	private volatile String nickname = "";

	private NetEaseApi() {
		cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		http = HttpClient.newBuilder()
				.cookieHandler(cookieManager)
				.connectTimeout(Duration.ofSeconds(15))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	// ---------------------------------------------------------------- 基础请求

	/** 访问一次首页拿基础 cookie（匿名也可以调用大部分接口） */
	public void initBaseCookies() {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/"))
					.header("User-Agent", UA).GET().build();
			http.send(req, HttpResponse.BodyHandlers.discarding());
		} catch (Exception ignored) {
		}
	}

	public JsonObject weapi(String path, Map<String, Object> body) throws IOException, InterruptedException {
		// 附加公共参数（调用方可能传入不可变 Map，先复制一份）
		body = new LinkedHashMap<>(body);
		body.putIfAbsent("csrf_token", getCookie("__csrf"));
		String json = gson.toJson(body);
		String[] enc = CryptoUtil.weapiEncrypt(json);
		String form = "params=" + URLEncoder.encode(enc[0], StandardCharsets.UTF_8)
				+ "&encSecKey=" + URLEncoder.encode(enc[1], StandardCharsets.UTF_8);

		HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path))
				.header("User-Agent", UA)
				.header("Referer", BASE + "/")
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form))
				.timeout(Duration.ofSeconds(20))
				.build();
		HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() / 100 != 2) {
			throw new IOException("HTTP " + resp.statusCode() + " for " + path);
		}
		String bodyText = resp.body() == null ? "" : resp.body().trim();
		if (bodyText.isEmpty()) {
			throw new IOException("空响应 for " + path);
		}
		try {
			return JsonParser.parseString(bodyText).getAsJsonObject();
		} catch (Exception e) {
			throw new IOException("非 JSON 响应 for " + path + ": "
					+ bodyText.substring(0, Math.min(200, bodyText.length())));
		}
	}

	private String getCookie(String name) {
		for (HttpCookie c : cookieManager.getCookieStore().get(URI.create(BASE))) {
			if (c.getName().equals(name)) return c.getValue();
		}
		return "";
	}

	/** 导出登录态 cookie（用于持久化） */
	public Map<String, String> exportCookies() {
		Map<String, String> map = new LinkedHashMap<>();
		for (HttpCookie c : cookieManager.getCookieStore().get(URI.create(BASE))) {
			map.put(c.getName(), c.getValue());
		}
		return map;
	}

	public void importCookies(Map<String, String> cookies) {
		if (cookies == null) return;
		for (Map.Entry<String, String> e : cookies.entrySet()) {
			HttpCookie c = new HttpCookie(e.getKey(), e.getValue());
			c.setPath("/");
			c.setDomain(".163.com");
			cookieManager.getCookieStore().add(URI.create(BASE), c);
		}
	}

	public boolean isLoggedIn() {
		return !getCookie("MUSIC_U").isEmpty();
	}

	/** 解析并导入形如 "a=b; c=d" 的 Cookie 串（某些接口登录成功时把 cookie 放在响应体里） */
	public void importCookieString(String cookieHeader) {
		if (cookieHeader == null || cookieHeader.isEmpty()) return;
		for (String part : cookieHeader.split(";")) {
			int eq = part.indexOf('=');
			if (eq <= 0) continue;
			String name = part.substring(0, eq).trim();
			String value = part.substring(eq + 1).trim();
			if (name.isEmpty() || name.equalsIgnoreCase("path")) continue;
			HttpCookie c = new HttpCookie(name, value);
			c.setPath("/");
			c.setDomain(".163.com");
			cookieManager.getCookieStore().add(URI.create(BASE), c);
		}
	}

	/**
	 * 用浏览器 Cookie 登录（网易风控收紧后最可靠的登录方式）。
	 * 传入形如 "MUSIC_U=xxxx"（可多段用分号拼接）的 Cookie 串即可。
	 * @return 是否成功并获得账号信息
	 */
	public boolean loginByCookie(String cookieHeader) {
		if (cookieHeader == null || cookieHeader.isBlank()) return false;
		cookieHeader = cookieHeader.trim();
		// 容错：如果只贴了 MUSIC_U 的值（没有 "MUSIC_U=" 前缀），自动补上
		if (!cookieHeader.contains("=")) {
			cookieHeader = "MUSIC_U=" + cookieHeader;
		}
		importCookieString(cookieHeader);
		if (!isLoggedIn()) return false;
		return refreshAccount();
	}

	public long getUserId() { return userId; }
	public String getNickname() { return nickname; }

	public void logout() {
		cookieManager.getCookieStore().removeAll();
		userId = 0;
		nickname = "";
	}

	// ---------------------------------------------------------------- 登录

	/** 生成二维码登录 key */
	public String qrCreateKey() throws IOException, InterruptedException {
		JsonObject o = weapi("/weapi/login/qrcode/unikey", Map.of("type", 1));
		return o.get("unikey").getAsString();
	}

	/**
	 * 轮询二维码状态（新版接口 /weapi/login/qrcode/client/login，旧 check 接口已下线）。
	 * @return code: 800 过期 / 801 等待扫码 / 802 已扫码待确认 / 200 或 803 登录成功
	 */
	public JsonObject qrCheck(String key) throws IOException, InterruptedException {
		return weapi("/weapi/login/qrcode/client/login", Map.of("key", key, "type", 1));
	}

	/** 手机号 + 密码登录 */
	public JsonObject loginCellphone(String phone, String password, String countryCode) throws IOException, InterruptedException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("phone", phone);
		body.put("countrycode", countryCode == null || countryCode.isEmpty() ? "86" : countryCode);
		body.put("password", CryptoUtil.md5Hex(password));
		body.put("rememberLogin", true);
		return weapi("/weapi/login/cellphone", body);
	}

	/** 登录成功后刷新账号信息 */
	public boolean refreshAccount() {
		try {
			JsonObject o = weapi("/weapi/nuser/account/get", Map.of());
			if (o.has("account") && !o.get("account").isJsonNull()) {
				userId = o.getAsJsonObject("account").get("id").getAsLong();
			}
			if (o.has("profile") && !o.get("profile").isJsonNull()) {
				nickname = o.getAsJsonObject("profile").get("nickname").getAsString();
			}
			return userId != 0;
		} catch (Exception e) {
			return false;
		}
	}

	// ---------------------------------------------------------------- 歌单

	/** 当前用户的全部歌单（含收藏） */
	public List<NcPlaylist> userPlaylists(long uid) throws IOException, InterruptedException {
		JsonObject o = weapi("/weapi/user/playlist", Map.of(
				"uid", String.valueOf(uid), "limit", 1000, "offset", 0, "includeVideo", true));
		List<NcPlaylist> list = new ArrayList<>();
		if (!o.has("playlist")) return list;
		for (JsonElement el : o.getAsJsonArray("playlist")) {
			JsonObject p = el.getAsJsonObject();
			NcPlaylist pl = new NcPlaylist();
			pl.id = p.get("id").getAsLong();
			pl.name = p.get("name").getAsString();
			pl.trackCount = p.has("trackCount") ? p.get("trackCount").getAsInt() : 0;
			pl.coverUrl = p.has("coverImgUrl") ? p.get("coverImgUrl").getAsString() : "";
			list.add(pl);
		}
		return list;
	}

	/**
	 * 获取歌单完整曲目。
	 * 注意：直接抓取歌单网页或未登录的 /api/playlist/detail 经常只返回前 20 首。
	 * 这里使用 /weapi/v6/playlist/detail 拿到完整 trackIds，再用 /weapi/v3/song/detail
	 * 分批取回每首歌的详细信息，保证拿到整个歌单。
	 */
	public List<NcSong> playlistAllTracks(long playlistId, ProgressListener listener) throws IOException, InterruptedException {
		JsonObject detail = weapi("/weapi/v6/playlist/detail", Map.of("id", playlistId, "n", 100000, "s", 8));
		int code = detail.has("code") ? detail.get("code").getAsInt() : -1;
		if (code != 200) {
			throw new IOException("歌单接口返回 code=" + code);
		}
		if (!detail.has("playlist") || detail.get("playlist").isJsonNull()) {
			throw new IOException("歌单数据为空：该歌单可能已删除、设为私密或需要更高权限");
		}
		JsonObject pl = detail.getAsJsonObject("playlist");
		if (!pl.has("trackIds") || pl.get("trackIds").isJsonNull() || pl.getAsJsonArray("trackIds").isEmpty()) {
			throw new IOException("歌单没有可播放曲目");
		}
		JsonArray trackIds = pl.getAsJsonArray("trackIds");
		List<Long> ids = new ArrayList<>(trackIds.size());
		for (JsonElement el : trackIds) {
			JsonObject t = el.isJsonObject() ? el.getAsJsonObject() : null;
			if (t != null && t.has("id") && !t.get("id").isJsonNull()) {
				ids.add(t.get("id").getAsLong());
			}
		}
		List<NcSong> songs = new ArrayList<>(ids.size());
		int batchSize = 200; // 分批取详情，避免超大响应
		for (int from = 0; from < ids.size(); from += batchSize) {
			int to = Math.min(from + batchSize, ids.size());
			songs.addAll(songDetails(ids.subList(from, to)));
			if (listener != null) listener.onProgress(songs.size(), ids.size());
		}
		return songs;
	}

	/** 批量获取歌曲详情（含封面 al.picUrl） */
	private List<NcSong> songDetails(List<Long> ids) throws IOException, InterruptedException {
		StringBuilder c = new StringBuilder("[");
		for (int i = 0; i < ids.size(); i++) {
			if (i > 0) c.append(',');
			c.append("{\"id\":").append(ids.get(i)).append('}');
		}
		c.append(']');
		JsonObject o = weapi("/weapi/v3/song/detail", Map.of("c", c.toString()));
		int code = o.has("code") ? o.get("code").getAsInt() : -1;
		if (code != 200) {
			throw new IOException("歌曲详情接口返回 code=" + code);
		}
		List<NcSong> out = new ArrayList<>();
		if (o.has("songs") && !o.get("songs").isJsonNull()) {
			for (JsonElement el : o.getAsJsonArray("songs")) {
				if (el.isJsonObject()) {
					NcSong s = NcSong.fromJson(el.getAsJsonObject());
					if (s != null) out.add(s);
				}
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- 搜索 / 播放

	/**
	 * 搜索单曲，返回最多 limit 条。
	 * 匿名访问 cloudsearch 会被拒绝（50000005），因此使用 /weapi/search/get，
	 * 再用 v3/song/detail 补全封面等信息。
	 */
	public List<NcSong> search(String keyword, int limit, int offset) throws IOException, InterruptedException {
		JsonObject o = weapi("/weapi/search/get", Map.of(
				"s", keyword, "type", 1, "limit", limit, "offset", offset));
		if (!o.has("result")) return List.of();
		JsonObject result = o.getAsJsonObject("result");
		if (!result.has("songs")) return List.of();
		List<Long> ids = new ArrayList<>();
		for (JsonElement el : result.getAsJsonArray("songs")) {
			ids.add(el.getAsJsonObject().get("id").getAsLong());
		}
		if (ids.isEmpty()) return List.of();
		// 补全详情；保持搜索结果的原始顺序
		List<NcSong> details = songDetails(ids);
		Map<Long, NcSong> byId = new LinkedHashMap<>();
		for (NcSong s : details) byId.put(s.id, s);
		List<NcSong> ordered = new ArrayList<>();
		for (Long id : ids) {
			NcSong s = byId.get(id);
			if (s != null) ordered.add(s);
		}
		return ordered;
	}

	/** 获取歌曲播放地址（mp3 流）。失败返回 null（VIP/无版权等） */
	public String songUrl(long songId) {
		// 实测：新版 url/v1 接口必须带 encodeType 参数，否则返回 400 参数错误
		for (String level : new String[]{"exhigh", "higher", "standard"}) {
			try {
				JsonObject o = weapi("/weapi/song/enhance/player/url/v1", Map.of(
						"ids", List.of(songId), "level", level, "encodeType", "mp3"));
				if (!o.has("data")) continue;
				JsonArray data = o.getAsJsonArray("data");
				if (data.isEmpty()) continue;
				JsonObject d0 = data.get(0).getAsJsonObject();
				if (d0.has("url") && !d0.get("url").isJsonNull()) {
					return d0.get("url").getAsString();
				}
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	/** 获取歌词，返回 {原文 lrc, 翻译 lrc} */
	public String[] lyric(long songId) {
		try {
			JsonObject o = weapi("/weapi/song/lyric", Map.of("id", songId, "lv", -1, "kv", -1, "tv", -1));
			String lrc = o.has("lrc") && o.getAsJsonObject("lrc").has("lyric")
					? o.getAsJsonObject("lrc").get("lyric").getAsString() : "";
			String tlyric = o.has("tlyric") && o.getAsJsonObject("tlyric").has("lyric")
					? o.getAsJsonObject("tlyric").get("lyric").getAsString() : "";
			return new String[]{lrc, tlyric};
		} catch (Exception e) {
			return new String[]{"", ""};
		}
	}

	/** 直接通过 HttpClient（带 cookie）下载资源（封面 / 音频流） */
	public HttpClient rawClient() {
		return http;
	}

	public interface ProgressListener {
		void onProgress(int current, int total);
	}
}
