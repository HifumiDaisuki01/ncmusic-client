package net.ncmusic.netease;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** 一首歌曲 */
public class NcSong {
	public long id;
	public String name = "";
	public String artists = "";
	public String album = "";
	public String coverUrl = "";
	public long durationMs = 0;

	public static NcSong fromJson(JsonObject o) {
		NcSong s = new NcSong();
		// id 缺失或为 JsonNull 时返回 null，由调用方跳过（下架/失效曲目）
		if (!o.has("id") || o.get("id").isJsonNull()) return null;
		s.id = o.get("id").getAsLong();
		s.name = o.has("name") && !o.get("name").isJsonNull() ? o.get("name").getAsString() : "";
		// 搜索接口艺术家在 "ar"，歌单详情在 "artists"
		JsonArray ar = o.has("ar") && !o.get("ar").isJsonNull() ? o.getAsJsonArray("ar")
				: (o.has("artists") && !o.get("artists").isJsonNull() ? o.getAsJsonArray("artists") : null);
		if (ar != null) {
			StringBuilder sb = new StringBuilder();
			for (JsonElement a : ar) {
				if (a.isJsonNull() || !a.isJsonObject()) continue;
				JsonObject aObj = a.getAsJsonObject();
				if (aObj.has("name") && !aObj.get("name").isJsonNull()) {
					if (sb.length() > 0) sb.append('/');
					sb.append(aObj.get("name").getAsString());
				}
			}
			s.artists = sb.toString();
		}
		JsonObject al = o.has("al") && !o.get("al").isJsonNull() ? o.getAsJsonObject("al")
				: (o.has("album") && !o.get("album").isJsonNull() ? o.getAsJsonObject("album") : null);
		if (al != null) {
			s.album = al.has("name") && !al.get("name").isJsonNull() ? al.get("name").getAsString() : "";
			if (al.has("picUrl") && !al.get("picUrl").isJsonNull()) s.coverUrl = al.get("picUrl").getAsString();
		}
		s.durationMs = o.has("dt") ? o.get("dt").getAsLong() : (o.has("duration") ? o.get("duration").getAsLong() : 0);
		return s;
	}

	public String displayName() {
		return name + (artists.isEmpty() ? "" : " - " + artists);
	}

	/** 小尺寸封面地址 */
	public String smallCoverUrl() {
		if (coverUrl.isEmpty()) return "";
		return coverUrl + (coverUrl.contains("?") ? "&" : "?") + "param=64y64";
	}
}
