package net.ncmusic.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Mod 配置（含登录态持久化），存放在 config/ncmusic.json */
public class NcmConfig {
	public enum Corner {
		TOP_RIGHT("右上角"), TOP_LEFT("左上角"), BOTTOM_LEFT("左下角"), BOTTOM_RIGHT("右下角");
		public final String label;
		Corner(String label) { this.label = label; }
		public Corner next() { return values()[(ordinal() + 1) % values().length]; }
	}

	public Corner corner = Corner.TOP_RIGHT;
	public boolean showHud = true;
	public boolean showCover = true;
	public boolean showSongInfo = true;
	public boolean showProgress = true;
	public boolean showLyric = true;
	public boolean showList = true;
	public int listSize = 5;          // 播放列表显示行数
	public float volume = 0.8f;       // 0.0 - 1.0
	public String playMode = "sequence"; // sequence / shuffle

	/** 登录态（网易云 cookie） */
	public Map<String, String> cookies = new LinkedHashMap<>();

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static NcmConfig instance;

	public static NcmConfig get() {
		if (instance == null) instance = load();
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("ncmusic.json");
	}

	public static NcmConfig load() {
		Path p = path();
		if (Files.exists(p)) {
			try (Reader r = Files.newBufferedReader(p)) {
				NcmConfig c = GSON.fromJson(r, NcmConfig.class);
				if (c != null) return c;
			} catch (Exception ignored) {
			}
		}
		return new NcmConfig();
	}

	public void save() {
		try {
			Path p = path();
			Files.createDirectories(p.getParent());
			try (Writer w = Files.newBufferedWriter(p)) {
				GSON.toJson(this, w);
			}
		} catch (IOException ignored) {
		}
	}
}
