package de.sakulius.zyrox.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import de.sakulius.zyrox.ZyroxClient;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Lyrics {
	public record Line(long timeMs, String text) {
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(ZyroxClient.MOD_ID);
	private static final String API = "https://lrclib.net/api/";
	private static final String USER_AGENT = "ZyroxClient (https://github.com/Sakulius/Zyrox)";
	private static final Duration TIMEOUT = Duration.ofSeconds(8);
	private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]");
	private static final Pattern TITLE_EXTRAS = Pattern.compile("\\s*(?:[-\u2013]\\s*(?:\\d{4}\\s+)?(?:remaster|live|radio edit|mono|stereo|deluxe|bonus|acoustic|version|from\\b).*|[(\\[](?:feat|ft|with|remaster|live|\\d{4}\\s+remaster).*?[)\\]])\\s*$", Pattern.CASE_INSENSITIVE);
	private static final Pattern FIRST_ARTIST = Pattern.compile("\\s*[,;&].*$");
	private static final long MAX_DURATION_DIFFERENCE_S = 8;
	private static final int HTTP_OK = 200;

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();

	private Lyrics() {
	}

	public static List<Line> parse(String lrc) {
		var lines = new ArrayList<Line>();
		if (lrc == null)
			return lines;
		for (var raw : lrc.split("\\R")) {
			var matcher = TIMESTAMP.matcher(raw);
			var stamps = new ArrayList<Long>();
			int end = 0;
			while (matcher.find() && matcher.start() == end) {
				stamps.add(toMillis(matcher.group(1), matcher.group(2), matcher.group(3)));
				end = matcher.end();
			}
			var text = raw.substring(end).trim();
			for (long stamp : stamps)
				lines.add(new Line(stamp, text));
		}
		lines.sort(Comparator.comparingLong(Line::timeMs));
		return lines;
	}

	private static long toMillis(String minutes, String seconds, String fraction) {
		long millis = 0;
		if (fraction != null)
			millis = Long.parseLong((fraction + "00").substring(0, 3));
		return (Long.parseLong(minutes) * 60 + Long.parseLong(seconds)) * 1000 + millis;
	}

	public static int indexAt(List<Line> lines, long positionMs) {
		int low = 0;
		int high = lines.size() - 1;
		int found = -1;
		while (low <= high) {
			int middle = (low + high) >>> 1;
			if (lines.get(middle).timeMs() <= positionMs) {
				found = middle;
				low = middle + 1;
			} else {
				high = middle - 1;
			}
		}
		return found;
	}

	public static List<Line> find(String artist, String title, String album, long durationMs) {
		var cache = cacheFile(artist, title);
		try {
			if (Files.exists(cache))
				return parse(Files.readString(cache));
		} catch (IOException e) {
			LOGGER.warn("Could not read cached lyrics", e);
		}
		try {
			var lrc = search(artist, title, album, durationMs / 1000);
			if (lrc == null)
				return List.of();
			Files.createDirectories(cache.getParent());
			Files.writeString(cache, lrc);
			return parse(lrc);
		} catch (IOException e) {
			LOGGER.warn("Could not load lyrics: {}", e.toString());
			return List.of();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return List.of();
		}
	}

	private static String search(String artist, String title, String album, long durationS) throws IOException, InterruptedException {
		var exact = get("get?artist_name=" + encode(artist) + "&track_name=" + encode(title) + "&album_name=" + encode(album) + "&duration=" + durationS);
		if (exact != null && exact.isJsonObject()) {
			var lrc = synced(exact.getAsJsonObject());
			if (lrc != null)
				return lrc;
		}
		var titles = new HashSet<>(List.of(title, TITLE_EXTRAS.matcher(title).replaceAll("")));
		var cleanArtist = FIRST_ARTIST.matcher(artist).replaceAll("");
		for (var name : titles) {
			var results = get("search?track_name=" + encode(name) + "&artist_name=" + encode(cleanArtist));
			if (results != null && results.isJsonArray()) {
				var lrc = closest(results.getAsJsonArray(), durationS);
				if (lrc != null)
					return lrc;
			}
		}
		return null;
	}

	private static String closest(JsonArray results, long durationS) {
		String best = null;
		double bestDifference = Double.MAX_VALUE;
		for (var element : results) {
			if (!element.isJsonObject())
				continue;
			var entry = element.getAsJsonObject();
			var lrc = synced(entry);
			if (lrc == null || !entry.has("duration") || entry.get("duration").isJsonNull())
				continue;
			double difference = Math.abs(entry.get("duration").getAsDouble() - durationS);
			if (difference < bestDifference && (durationS == 0 || difference <= MAX_DURATION_DIFFERENCE_S)) {
				best = lrc;
				bestDifference = difference;
			}
		}
		return best;
	}

	private static String synced(JsonObject entry) {
		if (!entry.has("syncedLyrics") || entry.get("syncedLyrics").isJsonNull())
			return null;
		var lrc = entry.get("syncedLyrics").getAsString();
		return lrc.isBlank() ? null : lrc;
	}

	private static JsonElement get(String path) throws IOException, InterruptedException {
		var request = HttpRequest.newBuilder(URI.create(API + path)).timeout(TIMEOUT).header("User-Agent", USER_AGENT).GET().build();
		var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != HTTP_OK)
			return null;
		try {
			return JsonParser.parseString(response.body());
		} catch (JsonParseException e) {
			return null;
		}
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static Path cacheFile(String artist, String title) {
		try {
			var hash = MessageDigest.getInstance("SHA-256").digest((artist + "\n" + title).getBytes(StandardCharsets.UTF_8));
			return FabricLoader.getInstance().getConfigDir().resolve("zyrox/lyrics").resolve(HexFormat.of().formatHex(hash) + ".lrc");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
