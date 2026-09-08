package net.ncmusic.netease;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** LRC 歌词解析与按时间定位 */
public class NcLyric {
	private static final Pattern TAG = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");

	/** 一行歌词（record 语法在 Java 11 下不可用，改用普通 final 类） */
	public static final class Line {
		private final long timeMs;
		private final String text;
		private final String translation;

		public Line(long timeMs, String text, String translation) {
			this.timeMs = timeMs;
			this.text = text;
			this.translation = translation;
		}

		public long timeMs() { return timeMs; }
		public String text() { return text; }
		public String translation() { return translation; }
	}

	private final List<Line> lines;

	private NcLyric(List<Line> lines) {
		this.lines = lines;
	}

	public static NcLyric parse(String lrc, String tlyric) {
		List<long[]> times = new ArrayList<>();
		List<String> texts = new ArrayList<>();
		parseInto(lrc, times, texts);

		// 翻译按时间戳对齐
		List<long[]> ttimes = new ArrayList<>();
		List<String> ttexts = new ArrayList<>();
		if (tlyric != null && !tlyric.isEmpty()) parseInto(tlyric, ttimes, ttexts);

		List<Line> out = new ArrayList<>();
		for (int i = 0; i < times.size(); i++) {
			long t = times.get(i)[0];
			String trans = "";
			for (int j = 0; j < ttimes.size(); j++) {
				if (Math.abs(ttimes.get(j)[0] - t) < 300) {
					trans = ttexts.get(j);
					break;
				}
			}
			out.add(new Line(t, texts.get(i), trans));
		}
		out.sort((a, b) -> Long.compare(a.timeMs(), b.timeMs()));
		return new NcLyric(out);
	}

	private static void parseInto(String lrc, List<long[]> times, List<String> texts) {
		if (lrc == null) return;
		for (String rawLine : lrc.split("\\R")) {
			Matcher m = TAG.matcher(rawLine);
			List<Long> lineTimes = new ArrayList<>();
			int lastEnd = 0;
			while (m.find()) {
				long min = Long.parseLong(m.group(1));
				long sec = Long.parseLong(m.group(2));
				long frac = 0;
				if (m.group(3) != null) {
					String f = m.group(3);
					frac = Long.parseLong(f) * (f.length() == 1 ? 100 : f.length() == 2 ? 10 : 1);
				}
				lineTimes.add(min * 60000 + sec * 1000 + frac);
				lastEnd = m.end();
			}
			if (lineTimes.isEmpty()) continue;
			String text = rawLine.substring(lastEnd).trim();
			// 去掉其它元信息标签
			if (text.startsWith("[") || text.isEmpty()) continue;
			for (long t : lineTimes) {
				times.add(new long[]{t});
				texts.add(text);
			}
		}
	}

	public boolean isEmpty() {
		return lines.isEmpty();
	}

	/** 当前播放时间对应的歌词行下标，无则 -1 */
	public int indexAt(long positionMs) {
		int idx = -1;
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).timeMs() <= positionMs) idx = i;
			else break;
		}
		return idx;
	}

	public Line lineAt(int index) {
		if (index < 0 || index >= lines.size()) return null;
		return lines.get(index);
	}

	public int size() {
		return lines.size();
	}
}
