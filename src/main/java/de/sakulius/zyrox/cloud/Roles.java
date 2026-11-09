package de.sakulius.zyrox.cloud;

import com.google.gson.JsonObject;
import de.sakulius.zyrox.ZyroxClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Roles {
	public enum Role {
		OWNER("owner", "Owner", "", 0xFFE8343E),
		STAFF("staff", "Staff", "", 0xFFF07824),
		TESTER("tester", "Tester", "", 0xFF24BA84);

		public final String id;
		public final String label;
		public final String glyph;
		public final int color;

		Role(String id, String label, String glyph, int color) {
			this.id = id;
			this.label = label;
			this.glyph = glyph;
			this.color = color;
		}

		public static Role of(String id) {
			for (var role : values())
				if (role.id.equals(id))
					return role;
			return null;
		}
	}

	public record Member(UUID id, String name, Role role) {
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(ZyroxClient.MOD_ID);
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final int HTTP_OK = 200;
	private static final int HTTP_FORBIDDEN = 403;

	private static final Map<UUID, Role> KNOWN = new ConcurrentHashMap<>();
	private static volatile Role own;
	private static volatile List<Member> team = List.of();
	private static volatile String message = "";

	private Roles() {
	}

	public static Role own() {
		return own;
	}

	public static boolean isOwner() {
		return own == Role.OWNER;
	}

	public static boolean isStaff() {
		return own == Role.OWNER || own == Role.STAFF;
	}

	public static List<Member> team() {
		return team;
	}

	public static String message() {
		return message;
	}

	public static Role of(UUID player) {
		if (player.equals(Minecraft.getInstance().getUser().getProfileId()))
			return own;
		return KNOWN.get(player);
	}

	static void put(UUID player, Role role) {
		if (role == null)
			KNOWN.remove(player);
		else
			KNOWN.put(player, role);
	}

	public static void watch() {
		Friends.watch();
	}

	public static void refreshSoon() {
		Friends.refreshSoon();
	}

	public static void accept(JsonObject body) {
		if (!body.has("role"))
			return;
		own = Role.of(Friends.text(body, "role"));
		var list = new ArrayList<Member>();
		if (body.has("team") && body.get("team").isJsonArray()) {
			for (var entry : body.getAsJsonArray("team")) {
				var json = entry.getAsJsonObject();
				var role = Role.of(Friends.text(json, "role"));
				if (role != null)
					list.add(new Member(Friends.uuid(Friends.text(json, "uuid")), Friends.text(json, "name"), role));
			}
		}
		team = List.copyOf(list);
	}

	public static void set(Minecraft client, String name, Role role, Consumer<String> done) {
		var wanted = name.strip();
		Consumer<String> report = text -> {
			message = text;
			if (done != null)
				client.execute(() -> done.accept(text));
		};
		if (!NAME.matcher(wanted).matches()) {
			report.accept("That is not a Minecraft name.");
			return;
		}
		if (role == Role.OWNER) {
			report.accept("Owners can only be added on the server.");
			return;
		}
		message = "Looking for " + wanted + "...";
		CloudCosmetics.submit(() -> {
			try {
				var id = Friends.resolver.uuidOf(wanted);
				if (id == null) {
					report.accept("There is no player called " + wanted + ".");
					return;
				}
				var body = new JsonObject();
				body.addProperty("uuid", Friends.compact(id));
				body.addProperty("name", wanted);
				body.addProperty("role", role == null ? "" : role.id);
				var answer = CloudCosmetics.call(client, "POST", "/v1/roles", body);
				if (answer.status() == HTTP_OK) {
					put(id, role);
					report.accept(role == null ? wanted + " has no role now." : wanted + " is now " + role.label + ".");
				} else if (answer.status() == HTTP_FORBIDDEN) {
					report.accept(isOwner() ? "You cannot change that player." : "Only the owner can give roles.");
				} else {
					report.accept("That did not work, try again.");
				}
				Friends.refreshSoon();
			} catch (Exception e) {
				handle(e);
				report.accept("The Zyrox server is not reachable.");
			}
		});
	}

	private static void handle(Exception e) {
		if (e instanceof InterruptedException)
			Thread.currentThread().interrupt();
		LOGGER.debug("Role request failed", e);
	}
}
