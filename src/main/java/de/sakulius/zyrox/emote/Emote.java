package de.sakulius.zyrox.emote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.joml.Vector3f;

public record Emote(String id, String name, float length, Loop loop, Map<String, Track> bones) {
	public enum Loop {
		ONCE,
		LOOP,
		HOLD
	}

	public record Key(float time, Vector3f value) {
	}

	public record Track(List<Key> rotation, List<Key> position) {
	}

	public static Emote parse(String key, JsonObject json) {
		var id = key.substring(key.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
		var loop = Loop.ONCE;
		if (json.has("loop")) {
			var value = json.get("loop");
			if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean())
				loop = value.getAsBoolean() ? Loop.LOOP : Loop.ONCE;
			else if (value.isJsonPrimitive() && "hold_on_last_frame".equals(value.getAsString()))
				loop = Loop.HOLD;
		}
		var bones = new HashMap<String, Track>();
		float last = 0f;
		if (json.has("bones") && json.get("bones").isJsonObject()) {
			for (var entry : json.getAsJsonObject("bones").entrySet()) {
				if (!entry.getValue().isJsonObject())
					continue;
				var bone = entry.getValue().getAsJsonObject();
				var track = new Track(keys(bone.get("rotation")), keys(bone.get("position")));
				for (var keyframe : track.rotation())
					last = Math.max(last, keyframe.time());
				for (var keyframe : track.position())
					last = Math.max(last, keyframe.time());
				bones.put(boneName(entry.getKey()), track);
			}
		}
		float length = json.has("animation_length") ? number(json.get("animation_length")) : last;
		return new Emote(id, pretty(id), Math.max(length, 0.05f), loop, Map.copyOf(bones));
	}

	public static String boneName(String raw) {
		return raw.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
	}

	private static String pretty(String id) {
		var words = id.replace('_', ' ').strip();
		return words.isEmpty() ? id : Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

	private static float number(JsonElement element) {
		if (element == null || !element.isJsonPrimitive())
			return 0f;
		try {
			return element.getAsFloat();
		} catch (NumberFormatException e) {
			return 0f;
		}
	}

	private static Vector3f vector(JsonElement element) {
		if (element == null)
			return new Vector3f();
		if (element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			return new Vector3f(number(array.size() > 0 ? array.get(0) : null), number(array.size() > 1 ? array.get(1) : null), number(array.size() > 2 ? array.get(2) : null));
		}
		if (element.isJsonObject()) {
			var object = element.getAsJsonObject();
			for (var name : List.of("post", "vector", "pre"))
				if (object.has(name))
					return vector(object.get(name));
			return new Vector3f();
		}
		float value = number(element);
		return new Vector3f(value, value, value);
	}

	private static List<Key> keys(JsonElement element) {
		var list = new ArrayList<Key>();
		if (element == null)
			return list;
		if (element.isJsonObject() && !element.getAsJsonObject().has("post") && !element.getAsJsonObject().has("vector")) {
			for (var entry : element.getAsJsonObject().entrySet()) {
				try {
					list.add(new Key(Float.parseFloat(entry.getKey()), vector(entry.getValue())));
				} catch (NumberFormatException ignored) {
				}
			}
		} else {
			list.add(new Key(0f, vector(element)));
		}
		list.sort(Comparator.comparingDouble(Key::time));
		return List.copyOf(list);
	}

	public static Vector3f sample(List<Key> keys, float time) {
		if (keys.isEmpty())
			return null;
		if (time <= keys.getFirst().time())
			return new Vector3f(keys.getFirst().value());
		for (int i = 1; i < keys.size(); i++) {
			var next = keys.get(i);
			if (time <= next.time()) {
				var previous = keys.get(i - 1);
				float span = Math.max(1e-4f, next.time() - previous.time());
				return new Vector3f(previous.value()).lerp(next.value(), (time - previous.time()) / span);
			}
		}
		return new Vector3f(keys.getLast().value());
	}
}
