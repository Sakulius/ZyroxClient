package de.sakulius.zyrox.music;

import de.sakulius.zyrox.ZyroxClient;
import de.sakulius.zyrox.module.Modules;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MusicManager {
	public enum LyricsState {
		NONE,
		LOADING,
		READY,
		NOT_FOUND
	}

	private record Clock(long baseMs, long nanos, boolean playing, long durationMs) {
		long now() {
			long position = baseMs;
			if (playing)
				position += (System.nanoTime() - nanos) / 1_000_000L;
			return durationMs > 0 ? Math.min(position, durationMs) : position;
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(ZyroxClient.MOD_ID);
	private static final long POLL_MS = 400;
	private static final long IDLE_MS = 1000;
	private static final long RETRY_MS = 5000;
	private static final long RESYNC_MS = 500;

	public static boolean onlySpotify = true;
	public static boolean karaoke = true;
	public static int offsetMs;
	public static boolean skyLyrics;
	public static int skySize = 1;

	private static final ConcurrentLinkedQueue<WindowsMediaSession.Control> CONTROLS = new ConcurrentLinkedQueue<>();
	private static volatile WindowsMediaSession.Snapshot track;
	private static volatile Clock clock = new Clock(0, 0, false, 0);
	private static volatile List<Lyrics.Line> lines = List.of();
	private static volatile LyricsState state = LyricsState.NONE;
	private static volatile String trackKey = "";
	private static volatile long trackChangedNanos = System.nanoTime();
	private static volatile boolean frozen;

	private MusicManager() {
	}

	public static void start() {
		if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows"))
			return;
		var worker = new Thread(MusicManager::run, "Zyrox-Music");
		worker.setDaemon(true);
		worker.start();
	}

	private static boolean needed() {
		return !frozen && (Modules.LYRICS.enabled || Modules.NOW_PLAYING.enabled);
	}

	private static void run() {
		try {
			while (true) {
				if (!needed()) {
					Thread.sleep(IDLE_MS);
					continue;
				}
				try (var session = new WindowsMediaSession()) {
					while (needed()) {
						update(session.read(onlySpotify));
						for (WindowsMediaSession.Control control; (control = CONTROLS.poll()) != null; )
							session.send(control);
						Thread.sleep(POLL_MS);
					}
				} catch (InterruptedException e) {
					throw e;
				} catch (Throwable t) {
					LOGGER.warn("Could not read the media session: {}", t.toString());
					Thread.sleep(RETRY_MS);
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static void update(WindowsMediaSession.Snapshot next) {
		if (frozen)
			return;
		track = next == null || next.title().isBlank() ? null : next;
		if (track == null) {
			clock = new Clock(0, System.nanoTime(), false, 0);
			changeTrack("", null);
			return;
		}
		var current = clock;
		if (current.playing() != next.playing() || current.durationMs() != next.durationMs() || Math.abs(current.now() - next.positionMs()) > RESYNC_MS)
			clock = new Clock(next.positionMs(), System.nanoTime(), next.playing(), next.durationMs());
		changeTrack(next.artist() + "\n" + next.title(), next);
	}

	private static void changeTrack(String key, WindowsMediaSession.Snapshot next) {
		if (key.equals(trackKey))
			return;
		trackKey = key;
		trackChangedNanos = System.nanoTime();
		lines = List.of();
		state = next == null ? LyricsState.NONE : LyricsState.LOADING;
		if (next == null)
			return;
		var loader = new Thread(() -> {
			var found = Lyrics.find(next.artist(), next.title(), next.album(), next.durationMs());
			if (key.equals(trackKey)) {
				lines = found;
				state = found.isEmpty() ? LyricsState.NOT_FOUND : LyricsState.READY;
			}
		}, "Zyrox-Lyrics");
		loader.setDaemon(true);
		loader.start();
	}

	public static void send(WindowsMediaSession.Control control) {
		CONTROLS.add(control);
	}

	public static WindowsMediaSession.Snapshot track() {
		return track;
	}

	public static long positionMs() {
		return clock.now();
	}

	public static List<Lyrics.Line> lines() {
		return lines;
	}

	public static LyricsState state() {
		return state;
	}

	public static float secondsSinceTrackChange() {
		return (System.nanoTime() - trackChangedNanos) / 1_000_000_000f;
	}

	public static void showForTest(WindowsMediaSession.Snapshot snapshot, List<Lyrics.Line> testLines) {
		frozen = true;
		track = snapshot;
		lines = testLines;
		state = testLines.isEmpty() ? LyricsState.NOT_FOUND : LyricsState.READY;
		clock = new Clock(snapshot.positionMs(), System.nanoTime(), snapshot.playing(), snapshot.durationMs());
	}
}
