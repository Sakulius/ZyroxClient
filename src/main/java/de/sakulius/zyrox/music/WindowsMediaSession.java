package de.sakulius.zyrox.music;

public final class WindowsMediaSession implements AutoCloseable {
	public static final boolean SUPPORTED = false;

	public record Snapshot(String appId, String title, String artist, String album, long durationMs, long positionMs, boolean playing) {
	}

	public enum Control {
		TOGGLE,
		NEXT,
		PREVIOUS
	}

	public WindowsMediaSession() {
		throw new UnsupportedOperationException("Music needs Minecraft 26.3 or newer");
	}

	public Snapshot read(boolean onlySpotify) {
		return null;
	}

	public void send(Control control) {
	}

	@Override
	public void close() {
	}
}
