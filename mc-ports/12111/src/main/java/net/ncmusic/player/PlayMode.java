package net.ncmusic.player;

public enum PlayMode {
	SEQUENCE("顺序播放"),
	SHUFFLE("随机播放");

	public final String label;

	PlayMode(String label) {
		this.label = label;
	}

	public PlayMode next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public static PlayMode fromName(String name) {
		for (PlayMode m : values()) {
			if (m.name().equalsIgnoreCase(name)) return m;
		}
		return SEQUENCE;
	}
}
