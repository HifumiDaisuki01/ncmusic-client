package net.ncmusic.netease;

/** 一个歌单 */
public class NcPlaylist {
	public long id;
	public String name = "";
	public int trackCount = 0;
	public String coverUrl = "";

	public String displayName() {
		return name + " (" + trackCount + "首)";
	}
}
