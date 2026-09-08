package net.ncmusic.player;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.ncmusic.config.NcmConfig;
import net.ncmusic.netease.NcLyric;
import net.ncmusic.netease.NcSong;
import net.ncmusic.netease.NetEaseApi;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 客户端音乐播放器：MP3 流式解码播放、队列管理、顺序/随机模式、进度追踪。
 */
public class MusicPlayer {
	public enum State { IDLE, LOADING, PLAYING, PAUSED }

	public interface Listener {
		/** 歌曲切换（在主线程外触发，UI 侧注意线程安全） */
		default void onTrackChanged(NcSong song) {}
		default void onStateChanged(State state) {}
		default void onError(String message) {}
	}

	public static final MusicPlayer INSTANCE = new MusicPlayer();

	private final List<NcSong> queue = new CopyOnWriteArrayList<>();
	private volatile int index = -1;
	private volatile PlayMode mode = PlayMode.SEQUENCE;
	private volatile State state = State.IDLE;

	private final List<Listener> listeners = new CopyOnWriteArrayList<>();
	private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "ncmusic-worker");
		t.setDaemon(true);
		return t;
	});

	// 当前播放线程控制
	private volatile Thread playThread;
	private volatile boolean stopRequested;
	private volatile boolean pauseRequested;

	// 进度
	private volatile long positionMs;
	private volatile long durationMs;

	// 歌词
	private volatile NcLyric lyric;

	// 随机播放顺序
	private List<Integer> shuffleOrder = new ArrayList<>();
	private int shufflePos = -1;
	private final Random random = new Random();

	private MusicPlayer() {}

	public void addListener(Listener l) { listeners.add(l); }

	// ---------------------------------------------------------------- 队列控制

	public synchronized void playQueue(List<NcSong> songs, int startIndex, PlayMode mode) {
		if (songs == null || songs.isEmpty()) return;
		queue.clear();
		queue.addAll(songs);
		this.mode = mode;
		rebuildShuffle(startIndex);
		playIndex(clampIndex(startIndex));
	}

	public synchronized void playNow(NcSong song) {
		queue.clear();
		queue.add(song);
		index = -1;
		rebuildShuffle(0);
		playIndex(0);
	}

	public synchronized void enqueue(NcSong song) {
		queue.add(song);
		if (state == State.IDLE && index < 0) playIndex(0);
		rebuildShuffle(Math.max(index, 0));
	}

	private int clampIndex(int i) {
		if (queue.isEmpty()) return -1;
		return Math.max(0, Math.min(i, queue.size() - 1));
	}

	private void rebuildShuffle(int currentIdx) {
		shuffleOrder = new ArrayList<>();
		for (int i = 0; i < queue.size(); i++) shuffleOrder.add(i);
		shuffleOrder.remove((Integer) currentIdx);
		Collections.shuffle(shuffleOrder, random);
		shuffleOrder.add(0, currentIdx);
		shufflePos = 0;
	}

	public synchronized void setMode(PlayMode mode) {
		if (this.mode == mode) return;
		this.mode = mode;
		if (mode == PlayMode.SHUFFLE) rebuildShuffle(Math.max(index, 0));
	}

	public PlayMode getMode() { return mode; }

	public synchronized void next() {
		int ni = nextIndex();
		if (ni >= 0) playIndex(ni);
	}

	public synchronized void prev() {
		// 播放超过 3 秒则回到开头（此处简化为重播本曲），否则上一首
		if (positionMs > 3000 && index >= 0) {
			playIndex(index);
			return;
		}
		int pi = prevIndex();
		if (pi >= 0) playIndex(pi);
	}

	private int nextIndex() {
		if (queue.isEmpty()) return -1;
		if (mode == PlayMode.SHUFFLE) {
			if (shuffleOrder.isEmpty()) rebuildShuffle(Math.max(index, 0));
			shufflePos = (shufflePos + 1) % shuffleOrder.size();
			return shuffleOrder.get(shufflePos);
		}
		return (index + 1) % queue.size();
	}

	private int prevIndex() {
		if (queue.isEmpty()) return -1;
		if (mode == PlayMode.SHUFFLE) {
			if (shuffleOrder.isEmpty()) rebuildShuffle(Math.max(index, 0));
			shufflePos = (shufflePos - 1 + shuffleOrder.size()) % shuffleOrder.size();
			return shuffleOrder.get(shufflePos);
		}
		return (index - 1 + queue.size()) % queue.size();
	}

	public synchronized void togglePause() {
		if (state == State.PLAYING) {
			pauseRequested = true;
		} else if (state == State.PAUSED) {
			pauseRequested = false;
			setState(State.PLAYING);
		}
	}

	public synchronized void stop() {
		stopInternal();
		index = -1;
		positionMs = 0;
		durationMs = 0;
		lyric = null;
		setState(State.IDLE);
	}

	public synchronized void shutdown() {
		stopInternal();
		executor.shutdownNow();
	}

	// ---------------------------------------------------------------- 播放核心

	private void playIndex(int idx) {
		if (idx < 0 || idx >= queue.size()) return;
		stopInternal();
		index = idx;
		positionMs = 0;
		NcSong song = queue.get(idx);
		durationMs = song.durationMs;
		lyric = null;
		setState(State.LOADING);
		fireTrackChanged(song);

		Thread t = new Thread(() -> playSong(song), "ncmusic-player");
		t.setDaemon(true);
		playThread = t;
		t.start();

		// 异步加载歌词
		executor.submit(() -> {
			String[] lr = NetEaseApi.INSTANCE.lyric(song.id);
			if (queue.size() > idx && queue.get(idx).id == song.id) {
				lyric = NcLyric.parse(lr[0], lr[1]);
			}
		});
	}

	private void stopInternal() {
		stopRequested = true;
		Thread t = playThread;
		if (t != null) {
			try { t.join(1500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
		}
		playThread = null;
		stopRequested = false;
		pauseRequested = false;
	}

	private void playSong(NcSong song) {
		try {
			String url = NetEaseApi.INSTANCE.songUrl(song.id);
			if (url == null) {
				fireError("无法获取播放地址（VIP/无版权）：" + song.displayName());
				scheduleSkip();
				return;
			}
			HttpRequest req = HttpRequest.newBuilder(URI.create(url))
					.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
					.header("Referer", "http://music.163.com/")
					.header("Accept", "*/*")
					.GET().build();
			HttpResponse<InputStream> resp = NetEaseApi.INSTANCE.rawClient()
					.send(req, HttpResponse.BodyHandlers.ofInputStream());
			if (resp.statusCode() / 100 != 2) {
				fireError("音频流请求失败 HTTP " + resp.statusCode());
				scheduleSkip();
				return;
			}
			if (!stopRequested) setState(State.PLAYING);
			decodeLoop(resp.body(), song);

			// 正常播完 → 自动下一首
			if (!stopRequested) {
				int ni = nextIndex();
				if (ni >= 0) playIndex(ni);
				else setState(State.IDLE);
			}
		} catch (Exception e) {
			if (!stopRequested) {
				fireError("播放出错：" + e.getClass().getSimpleName());
				scheduleSkip();
			}
		}
	}

	private void decodeLoop(InputStream in, NcSong song) throws Exception {
		Bitstream bitstream = new Bitstream(in);
		Decoder decoder = new Decoder();
		SourceDataLine line = null;
		long framesPlayed = 0;
		int sampleRate = 0;
		byte[] pcm = new byte[8192];
		try {
			while (!stopRequested) {
				if (pauseRequested) {
					if (line != null) line.stop();
					while (pauseRequested && !stopRequested) {
						try { Thread.sleep(50); } catch (InterruptedException ignored) {}
					}
					if (line != null) line.start();
					continue;
				}
				Header h = bitstream.readFrame();
				if (h == null) break; // 流结束
				if (line == null) {
					sampleRate = h.frequency();
					int channels = h.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
					AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
					line = AudioSystem.getSourceDataLine(format);
					line.open(format);
					applyVolume(line);
					line.start();
				}
				SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
				short[] samples = output.getBuffer();
				int len = output.getBufferLength();
				if (pcm.length < len * 2) pcm = new byte[len * 2];
				for (int i = 0; i < len; i++) {
					short s = samples[i];
					pcm[i * 2] = (byte) (s & 0xff);
					pcm[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
				}
				line.write(pcm, 0, len * 2);
				int channels = h.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
				framesPlayed += len / channels;
				if (sampleRate > 0) positionMs = framesPlayed * 1000L / sampleRate;
				bitstream.closeFrame();
			}
		} finally {
			if (line != null) { line.drain(); line.stop(); line.close(); }
			try { bitstream.close(); } catch (Exception ignored) {}
			try { in.close(); } catch (Exception ignored) {}
		}
	}

	private void applyVolume(SourceDataLine line) {
		try {
			FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
			float v = Math.max(0.0001f, NcmConfig.get().volume);
			float db = (float) (20.0 * Math.log10(v));
			gain.setValue(Math.max(gain.getMinimum(), Math.min(db, gain.getMaximum())));
		} catch (Exception ignored) {
		}
	}

	/** 音量变化后调用（仅对下一首生效的兜底也接受） */
	public void refreshVolume() {
		// SourceDataLine 在解码线程内创建；简化处理：新歌生效。
	}

	private void scheduleSkip() {
		executor.submit(() -> {
			try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
			synchronized (MusicPlayer.this) {
				if (state == State.LOADING || state == State.IDLE) return;
			}
			if (!stopRequested) {
				int ni = nextIndex();
				if (ni >= 0 && !queue.isEmpty()) playIndex(ni);
			}
		});
	}

	// ---------------------------------------------------------------- 状态访问

	public State getState() { return state; }
	public long getPositionMs() { return positionMs; }
	public long getDurationMs() { return durationMs; }
	public NcLyric getLyric() { return lyric; }
	public List<NcSong> getQueue() { return List.copyOf(queue); }
	public int getIndex() { return index; }

	public NcSong currentSong() {
		return (index >= 0 && index < queue.size()) ? queue.get(index) : null;
	}

	private void setState(State s) {
		state = s;
		for (Listener l : listeners) l.onStateChanged(s);
	}

	private void fireTrackChanged(NcSong song) {
		for (Listener l : listeners) l.onTrackChanged(song);
	}

	private void fireError(String msg) {
		for (Listener l : listeners) l.onError(msg);
	}
}
