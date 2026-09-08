package net.ncmusic.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.ncmusic.netease.NcSong;
import net.ncmusic.netease.NetEaseApi;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 封面纹理缓存：下载 → NativeImage → DynamicTexture，LRU 上限 12 张。
 * - 同一首歌"下载中"去重（HUD 每帧都会调用 get，必须防抖）
 * - 小图(param=64y64)失败时回退整张原图
 * - 失败有 60 秒冷却，避免无限重试刷爆队列
 */
public class CoverManager {
	public static final CoverManager INSTANCE = new CoverManager();

	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("ncmusic-cover");
	private static final int MAX_CACHE = 12;
	private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

	private final Map<Long, ResourceLocation> cache = new LinkedHashMap<>(16, 0.75f, true);
	private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
	private final Map<Long, Long> lastFail = new ConcurrentHashMap<>();
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "ncmusic-cover");
		t.setDaemon(true);
		return t;
	});

	private CoverManager() {}

	/** 获取封面纹理；未加载好时返回 null（此时内部会触发一次异步下载） */
	public ResourceLocation get(NcSong song) {
		if (song == null || song.coverUrl.isEmpty()) return null;
		synchronized (cache) {
			ResourceLocation id = cache.get(song.id);
			if (id != null) {
				touch(id);
				return id;
			}
		}
		// 已有请求在途 / 最近失败过 → 不再重复触发
		if (inFlight.contains(song.id)) return null;
		long failAt = lastFail.getOrDefault(song.id, 0L);
		if (System.currentTimeMillis() - failAt < 20_000) return null;
		inFlight.add(song.id);
		executor.submit(() -> {
			try {
				download(song);
			} finally {
				inFlight.remove(song.id);
			}
		});
		return null;
	}

	private void download(NcSong song) {
		// 逐个地址尝试，谁能成功解码就用谁（小图变体可能是坏的/防盗链页）
		for (String url : new String[]{song.smallCoverUrl(), song.coverUrl}) {
			if (url == null || url.isEmpty()) continue;
			try {
				HttpRequest req = HttpRequest.newBuilder(URI.create(url))
						.header("User-Agent", UA)
						.header("Referer", "https://music.163.com/")
						.timeout(java.time.Duration.ofSeconds(10))
						.GET().build();
				HttpResponse<byte[]> resp = NetEaseApi.INSTANCE.rawClient()
						.send(req, HttpResponse.BodyHandlers.ofByteArray());
				if (resp.statusCode() / 100 != 2 || resp.body() == null || resp.body().length < 8) {
					LOGGER.warn("cover HTTP {} songId={}", resp.statusCode(), song.id);
					continue;
				}
				try {
					NativeImage image = decodeImage(resp.body());
					LOGGER.info("cover 下载并解码成功 {} bytes songId={} url={}", resp.body().length, song.id,
							url.substring(0, Math.min(90, url.length())));
					Minecraft.getInstance().execute(() -> {
						try {
							register(song.id, image);
						} catch (Exception e) {
							LOGGER.error("cover 纹理注册失败 songId=" + song.id, e);
							try { image.close(); } catch (Exception ignored) {}
							lastFail.put(song.id, System.currentTimeMillis());
						}
					});
					return;
				} catch (Exception dec) {
					LOGGER.warn("cover 该地址解码失败，尝试下一地址 songId={} bytes={} err={}",
							song.id, resp.body().length, dec.getClass().getSimpleName());
				}
			} catch (Exception e) {
				LOGGER.warn("cover 请求失败 songId=" + song.id, e);
			}
		}
		lastFail.put(song.id, System.currentTimeMillis());
	}

	/**
	 * 解码封面。优先用 Minecraft 的 NativeImage(stb)，失败时回退 Java ImageIO——
	 * 部分 JPEG（Adobe/CMYK/渐进式等变体）stb 解不了但 ImageIO 可以。
	 */
	private static NativeImage decodeImage(byte[] bytes) throws Exception {
		try {
			return NativeImage.read(new ByteArrayInputStream(bytes));
		} catch (Exception stbFail) {
			java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));
			if (bi == null) throw new java.io.IOException("无法识别图片格式");
			NativeImage out = new NativeImage(bi.getWidth(), bi.getHeight(), true);
			for (int y = 0; y < bi.getHeight(); y++) {
				for (int x = 0; x < bi.getWidth(); x++) {
					int argb = bi.getRGB(x, y);
					// NativeImage 内存按 R,G,B,A 字节序排列，int 值需以 A 为最高字节、R 为最低字节
					int a = (argb >>> 24) & 0xff, r = (argb >>> 16) & 0xff, g = (argb >>> 8) & 0xff, b = argb & 0xff;
					out.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
				}
			}
			return out;
		}
	}

	private void touch(ResourceLocation keep) {
		while (cache.size() > MAX_CACHE) {
			Long eldest = cache.keySet().iterator().next();
			ResourceLocation evict = cache.remove(eldest);
			if (evict != null && !evict.equals(keep)) release(evict);
		}
	}

	private void register(long songId, NativeImage image) {
		synchronized (cache) {
			if (cache.containsKey(songId)) { image.close(); return; }
			ResourceLocation id = new ResourceLocation("ncmusic", "cover_" + songId);
			DynamicTexture tex = new DynamicTexture(image);
			tex.upload();
			Minecraft.getInstance().getTextureManager().register(id, tex);
			cache.put(songId, id);
			touch(id);
			lastFail.remove(songId);
		}
	}

	private void release(ResourceLocation id) {
		Minecraft.getInstance().getTextureManager().release(id);
	}
}
