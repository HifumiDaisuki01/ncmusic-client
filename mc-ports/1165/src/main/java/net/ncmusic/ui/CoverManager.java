package net.ncmusic.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.ncmusic.netease.NcSong;
import net.ncmusic.netease.NetEaseApi;

import java.io.ByteArrayInputStream;
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
 * 封面纹理缓存：下载 → NativeImage → NativeImageBackedTexture(DynamicTexture)，LRU 上限 12 张。
 * - 同一首歌"下载中"去重（HUD 每帧都会调用 get，必须防抖）
 * - 小图(param=64y64)失败时回退整张原图
 * - 失败有 20 秒冷却，避免无限重试刷爆队列
 * 1.16.5 Yarn：NativeImage 在 net.minecraft.client.texture 包内，像素颜色为 ABGR；
 * DynamicTexture 对应 NativeImageBackedTexture；TextureManager.registerTexture/destroyTexture。
 */
public class CoverManager {
	public static final CoverManager INSTANCE = new CoverManager();

	private static final org.apache.logging.log4j.Logger LOGGER =
			org.apache.logging.log4j.LogManager.getLogger("ncmusic-cover");
	private static final int MAX_CACHE = 12;
	private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

	/** 封面条目：纹理 id + 源图宽（方形，用于按实际尺寸贴图） */
	public static final class Entry {
		public final Identifier id;
		public final int size;
		public Entry(Identifier id, int size) {
			this.id = id;
			this.size = size;
		}
	}

	private final Map<Long, Entry> cache = new LinkedHashMap<>(16, 0.75f, true);
	private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
	private final Map<Long, Long> lastFail = new ConcurrentHashMap<>();
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "ncmusic-cover");
		t.setDaemon(true);
		return t;
	});

	private CoverManager() {}

	/** 获取封面纹理；未加载好时返回 null（此时内部会触发一次异步下载） */
	public Entry get(NcSong song) {
		if (song == null || song.coverUrl.isEmpty()) return null;
		synchronized (cache) {
			Entry e = cache.get(song.id);
			if (e != null) {
				touch(e);
				return e;
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
					MinecraftClient.getInstance().execute(() -> {
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
	 * 回退路径把 ImageIO 结果重新编码为 PNG 再交给 stb 解码，避免手写 ABGR 逐像素搬移出错。
	 */
	private static NativeImage decodeImage(byte[] bytes) throws Exception {
		try {
			return NativeImage.read(new ByteArrayInputStream(bytes));
		} catch (Exception stbFail) {
			java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));
			if (bi == null) throw new java.io.IOException("无法识别图片格式");
			java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
			if (!javax.imageio.ImageIO.write(bi, "png", baos)) {
				throw new java.io.IOException("无法把图片重新编码为 PNG");
			}
			return NativeImage.read(new ByteArrayInputStream(baos.toByteArray()));
		}
	}

	private void touch(Entry keep) {
		while (cache.size() > MAX_CACHE) {
			java.util.Map.Entry<Long, Entry> eldest = cache.entrySet().iterator().next();
			Entry evict = eldest.getValue();
			cache.remove(eldest.getKey());
			if (evict != null && evict != keep) release(evict);
		}
	}

	private void register(long songId, NativeImage image) {
		synchronized (cache) {
			if (cache.containsKey(songId)) { image.close(); return; }
			Identifier id = new Identifier("ncmusic", "cover_" + songId);
			NativeImageBackedTexture tex = new NativeImageBackedTexture(image);
			tex.upload();
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
			int size = image.getWidth();
			cache.put(songId, new Entry(id, size));
			touch(new Entry(id, size));
			lastFail.remove(songId);
		}
	}

	private void release(Entry e) {
		MinecraftClient.getInstance().getTextureManager().destroyTexture(e.id);
	}
}
