import { PLAYER_CERTIFICATE_KEYS } from "./mojang-keys.js";

const CHALLENGE_SECONDS = 60;
const TOKEN_SECONDS = 30 * 24 * 60 * 60;
const MIN_WRITE_SECONDS = 2;
const MAX_UUIDS = 50;
const MAX_SLOTS = 16;
const MAX_BODY_BYTES = 4096;
const UUID = /^[0-9a-f]{32}$/;
const NAME = /^[a-z][a-z0-9_-]{0,31}$/;
const PLAYER_NAME = /^[A-Za-z0-9_]{1,16}$/;
const SERVER = /^[\x20-\x7e]{0,100}$/;
const ONLINE_SECONDS = 6 * 60;
const MIN_PRESENCE_SECONDS = 20;
const MAX_FRIENDS = 100;
const MAX_OUTGOING = 50;
const MAX_TEAM = 200;
const GIVEN_ROLES = ["staff", "tester"];
const CACHE_SECONDS = 4;
const MAX_CACHED = 5000;

const caches = new WeakMap();

function cacheFor(env) {
	let cache = caches.get(env.DB);
	if (!cache) {
		cache = new Map();
		caches.set(env.DB, cache);
	}
	return cache;
}

function forget(env, uuid) {
	cacheFor(env).delete(uuid);
}

function reader(env) {
	return typeof env.DB.withSession === "function" ? env.DB.withSession("first-unconstrained") : env.DB;
}

const encoder = new TextEncoder();

function json(body, status = 200, headers = {}) {
	return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", ...headers } });
}

function hex(bytes) {
	return [...new Uint8Array(bytes)].map(byte => byte.toString(16).padStart(2, "0")).join("");
}

async function sign(secret, text) {
	const key = await crypto.subtle.importKey("raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
	return hex(await crypto.subtle.sign("HMAC", key, encoder.encode(text)));
}

function sameText(a, b) {
	if (a.length !== b.length)
		return false;
	let difference = 0;
	for (let i = 0; i < a.length; i++)
		difference |= a.charCodeAt(i) ^ b.charCodeAt(i);
	return difference === 0;
}

async function newChallenge(env, now) {
	const nonce = hex(crypto.getRandomValues(new Uint8Array(8)));
	const expires = (now + CHALLENGE_SECONDS).toString(16);
	const signature = (await sign(env.TOKEN_SECRET, `challenge.${nonce}.${expires}`)).slice(0, 16);
	return `${nonce}${expires}${signature}`;
}

async function validChallenge(env, challenge, now) {
	if (typeof challenge !== "string" || !/^[0-9a-f]{33,41}$/.test(challenge))
		return false;
	const nonce = challenge.slice(0, 16);
	const signature = challenge.slice(-16);
	const expires = challenge.slice(16, -16);
	const expected = (await sign(env.TOKEN_SECRET, `challenge.${nonce}.${expires}`)).slice(0, 16);
	return sameText(signature, expected) && parseInt(expires, 16) >= now;
}

async function newToken(env, uuid, now) {
	const payload = `${uuid}.${now + TOKEN_SECONDS}`;
	return `${payload}.${await sign(env.TOKEN_SECRET, `token.${payload}`)}`;
}

async function tokenOwner(env, request, now) {
	const header = request.headers.get("authorization") ?? "";
	const parts = header.replace(/^Bearer /, "").split(".");
	if (parts.length !== 3 || !UUID.test(parts[0]) || !/^\d+$/.test(parts[1]))
		return null;
	const expected = await sign(env.TOKEN_SECRET, `token.${parts[0]}.${parts[1]}`);
	return sameText(parts[2], expected) && Number(parts[1]) >= now ? parts[0] : null;
}

async function readJson(request) {
	const text = await request.text();
	if (text.length > MAX_BODY_BYTES)
		return null;
	try {
		return JSON.parse(text);
	} catch {
		return null;
	}
}

function decode(base64) {
	return Uint8Array.from(atob(base64), character => character.charCodeAt(0));
}

function certificatePayload(uuid, expiresAt, publicKey) {
	const payload = new Uint8Array(24 + publicKey.length);
	for (let i = 0; i < 16; i++)
		payload[i] = parseInt(uuid.slice(i * 2, i * 2 + 2), 16);
	new DataView(payload.buffer).setBigUint64(16, BigInt(expiresAt));
	payload.set(publicKey, 24);
	return payload;
}

export function certificateVerifier(mojangKeys) {
	return async (uuid, expiresAt, publicKey, keySignature) => {
		const payload = certificatePayload(uuid, expiresAt, publicKey);
		for (const mojangKey of mojangKeys) {
			const key = await crypto.subtle.importKey("spki", decode(mojangKey), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-1" }, false, ["verify"]);
			if (await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, keySignature, payload))
				return true;
		}
		return false;
	};
}

async function finishAuth(request, env, deps, now) {
	const body = await readJson(request);
	if (!body || typeof body.uuid !== "string" || !UUID.test(body.uuid) || !Number.isSafeInteger(body.expiresAt) || body.expiresAt < 0)
		return json({ error: "bad_request" }, 400);
	if (!(await validChallenge(env, body.challenge, now)))
		return json({ error: "bad_challenge" }, 401);
	if (body.expiresAt <= now * 1000)
		return json({ error: "certificate_expired" }, 401);
	try {
		const publicKey = decode(body.publicKey);
		if (!(await deps.verifyCertificate(body.uuid, body.expiresAt, publicKey, decode(body.keySignature))))
			return json({ error: "bad_certificate" }, 401);
		const key = await crypto.subtle.importKey("spki", publicKey, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
		if (!(await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, decode(body.signature), encoder.encode(`zyrox-auth:${body.challenge}`))))
			return json({ error: "bad_signature" }, 401);
	} catch {
		return json({ error: "bad_certificate" }, 401);
	}
	return json({ uuid: body.uuid, token: await newToken(env, body.uuid, now), expires: now + TOKEN_SECONDS });
}

async function readLoadouts(url, env, now) {
	const uuids = [...new Set((url.searchParams.get("uuids") ?? "").split(",").filter(Boolean))];
	if (uuids.length === 0 || uuids.length > MAX_UUIDS || !uuids.every(uuid => UUID.test(uuid)))
		return json({ error: "bad_request" }, 400);
	const cache = cacheFor(env);
	const missing = uuids.filter(uuid => !(cache.get(uuid)?.at > now - CACHE_SECONDS));
	if (missing.length > 0) {
		const db = reader(env);
		const marks = missing.map(() => "?").join(",");
		const [items, team] = await Promise.all([
			db.prepare(`SELECT uuid, slot, item FROM loadouts WHERE uuid IN (${marks})`).bind(...missing).all(),
			db.prepare(`SELECT uuid, role FROM roles WHERE uuid IN (${marks})`).bind(...missing).all()
		]);
		if (cache.size + missing.length > MAX_CACHED)
			cache.clear();
		for (const uuid of missing)
			cache.set(uuid, { at: now, items: null, role: null });
		for (const row of items.results)
			(cache.get(row.uuid).items ??= {})[row.slot] = row.item;
		for (const row of team.results)
			cache.get(row.uuid).role = row.role;
	}
	const loadouts = {};
	const roles = {};
	for (const uuid of uuids) {
		const entry = cache.get(uuid);
		if (entry?.items)
			loadouts[uuid] = entry.items;
		if (entry?.role)
			roles[uuid] = entry.role;
	}
	return json({ loadouts, roles }, 200, { "cache-control": "public, max-age=4" });
}

async function writeLoadout(request, env, now) {
	const uuid = await tokenOwner(env, request, now);
	if (!uuid)
		return json({ error: "unauthorized" }, 401);
	const body = await readJson(request);
	const items = body?.items;
	if (!items || typeof items !== "object" || Array.isArray(items))
		return json({ error: "bad_request" }, 400);
	const entries = Object.entries(items);
	if (entries.length > MAX_SLOTS || !entries.every(([slot, item]) => NAME.test(slot) && typeof item === "string" && NAME.test(item)))
		return json({ error: "bad_request" }, 400);

	const last = await env.DB.prepare("SELECT MAX(updated_at) AS at FROM loadouts WHERE uuid = ?").bind(uuid).first();
	if (last?.at != null && now - last.at < MIN_WRITE_SECONDS)
		return json({ error: "too_fast" }, 429);

	const insert = env.DB.prepare("INSERT INTO loadouts (uuid, slot, item, updated_at) VALUES (?, ?, ?, ?)");
	await env.DB.batch([
		env.DB.prepare("DELETE FROM loadouts WHERE uuid = ?").bind(uuid),
		...entries.map(([slot, item]) => insert.bind(uuid, slot, item, now))
	]);
	forget(env, uuid);
	return json({ saved: true });
}

async function playerBody(request) {
	const body = await readJson(request);
	return body && typeof body.uuid === "string" && UUID.test(body.uuid) ? body.uuid : null;
}

async function count(env, sql, value) {
	return (await env.DB.prepare(sql).bind(value).first())?.total ?? 0;
}

async function areFriends(env, a, b) {
	return (await env.DB.prepare("SELECT 1 AS yes FROM friends WHERE uuid = ? AND friend = ?").bind(a, b).first()) != null;
}

async function befriend(env, a, b) {
	await env.DB.batch([
		env.DB.prepare("DELETE FROM friend_requests WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)").bind(a, b, b, a),
		env.DB.prepare("INSERT OR IGNORE INTO friends (uuid, friend) VALUES (?, ?)").bind(a, b),
		env.DB.prepare("INSERT OR IGNORE INTO friends (uuid, friend) VALUES (?, ?)").bind(b, a)
	]);
}

async function sendRequest(request, env, now) {
	const uuid = await tokenOwner(env, request, now);
	if (!uuid)
		return json({ error: "unauthorized" }, 401);
	const target = await playerBody(request);
	if (!target || target === uuid)
		return json({ error: "bad_request" }, 400);
	if (await areFriends(env, uuid, target))
		return json({ status: "friends" });
	const reverse = await env.DB.prepare("SELECT 1 AS yes FROM friend_requests WHERE sender = ? AND receiver = ?").bind(target, uuid).first();
	if (reverse) {
		if (await count(env, "SELECT COUNT(*) AS total FROM friends WHERE uuid = ?", uuid) >= MAX_FRIENDS)
			return json({ error: "too_many_friends" }, 409);
		await befriend(env, uuid, target);
		return json({ status: "friends" });
	}
	if (await count(env, "SELECT COUNT(*) AS total FROM friend_requests WHERE sender = ?", uuid) >= MAX_OUTGOING)
		return json({ error: "too_many_requests" }, 409);
	await env.DB.prepare("INSERT OR IGNORE INTO friend_requests (sender, receiver, created_at) VALUES (?, ?, ?)").bind(uuid, target, now).run();
	return json({ status: "sent" });
}

async function acceptRequest(request, env, now) {
	const uuid = await tokenOwner(env, request, now);
	if (!uuid)
		return json({ error: "unauthorized" }, 401);
	const sender = await playerBody(request);
	if (!sender)
		return json({ error: "bad_request" }, 400);
	const pending = await env.DB.prepare("SELECT 1 AS yes FROM friend_requests WHERE sender = ? AND receiver = ?").bind(sender, uuid).first();
	if (!pending)
		return json({ error: "no_request" }, 404);
	if (await count(env, "SELECT COUNT(*) AS total FROM friends WHERE uuid = ?", uuid) >= MAX_FRIENDS)
		return json({ error: "too_many_friends" }, 409);
	await befriend(env, uuid, sender);
	return json({ status: "friends" });
}

async function removeFriend(request, env, now) {
	const uuid = await tokenOwner(env, request, now);
	if (!uuid)
		return json({ error: "unauthorized" }, 401);
	const other = await playerBody(request);
	if (!other)
		return json({ error: "bad_request" }, 400);
	await env.DB.batch([
		env.DB.prepare("DELETE FROM friends WHERE (uuid = ? AND friend = ?) OR (uuid = ? AND friend = ?)").bind(uuid, other, other, uuid),
		env.DB.prepare("DELETE FROM friend_requests WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)").bind(uuid, other, other, uuid)
	]);
	return json({ status: "removed" });
}

async function listFriends(request, env, now) {
	const uuid = await tokenOwner(env, request, now);
	if (!uuid)
		return json({ error: "unauthorized" }, 401);
	const friends = await env.DB.prepare(
		"SELECT f.friend AS uuid, p.name AS name, p.server AS server, p.seen_at AS seen FROM friends f LEFT JOIN presence p ON p.uuid = f.friend WHERE f.uuid = ?"
	).bind(uuid).all();
	const incoming = await env.DB.prepare(
		"SELECT r.sender AS uuid, p.name AS name FROM friend_requests r LEFT JOIN presence p ON p.uuid = r.sender WHERE r.receiver = ? ORDER BY r.created_at"
	).bind(uuid).all();
	const outgoing = await env.DB.prepare(
		"SELECT r.receiver AS uuid, p.name AS name FROM friend_requests r LEFT JOIN presence p ON p.uuid = r.receiver WHERE r.sender = ? ORDER BY r.created_at"
	).bind(uuid).all();
	const { role, team } = await teamOf(env, uuid);
	return json({
		role,
		team,
		friends: friends.results.map(row => {
			const online = row.seen != null && now - row.seen <= ONLINE_SECONDS;
			return { uuid: row.uuid, name: row.name ?? "", online, server: online ? row.server ?? "" : "" };
		}),
		incoming: incoming.results.map(row => ({ uuid: row.uuid, name: row.name ?? "" })),
		outgoing: outgoing.results.map(row => ({ uuid: row.uuid, name: row.name ?? "" }))
	});
}

async function writePresence(request, env, now) {
	const uuid = await tokenOwner(env, request, now);
	if (!uuid)
		return json({ error: "unauthorized" }, 401);
	const body = await readJson(request);
	if (!body || typeof body.name !== "string" || !PLAYER_NAME.test(body.name) || typeof body.server !== "string" || !SERVER.test(body.server))
		return json({ error: "bad_request" }, 400);
	const last = await env.DB.prepare("SELECT seen_at AS at, server FROM presence WHERE uuid = ?").bind(uuid).first();
	if (last && now - last.at < MIN_PRESENCE_SECONDS && last.server === body.server)
		return json({ error: "too_fast" }, 429);
	await env.DB.prepare("INSERT OR REPLACE INTO presence (uuid, name, server, seen_at) VALUES (?, ?, ?, ?)").bind(uuid, body.name, body.server, now).run();
	return json({ saved: true });
}

async function goOffline(request, env, now) {
	const uuid = await tokenOwner(env, request, now);
	if (!uuid)
		return json({ error: "unauthorized" }, 401);
	await env.DB.prepare("UPDATE presence SET seen_at = 0, server = '' WHERE uuid = ?").bind(uuid).run();
	return json({ saved: true });
}

async function roleOf(env, uuid) {
	return (await env.DB.prepare("SELECT role FROM roles WHERE uuid = ?").bind(uuid).first())?.role ?? "";
}

async function readTeam(request, env, now) {
	const uuid = await tokenOwner(env, request, now);
	if (!uuid)
		return json({ error: "unauthorized" }, 401);
	return json(await teamOf(env, uuid));
}

async function teamOf(env, uuid) {
	const role = await roleOf(env, uuid);
	if (role !== "owner" && role !== "staff")
		return { role, team: [] };
	const { results } = await env.DB.prepare(`SELECT uuid, name, role FROM roles ORDER BY CASE role WHEN 'owner' THEN 0 WHEN 'staff' THEN 1 ELSE 2 END, name LIMIT ${MAX_TEAM}`).all();
	return { role, team: results.map(row => ({ uuid: row.uuid, name: row.name ?? "", role: row.role })) };
}

async function writeRole(request, env, now) {
	const uuid = await tokenOwner(env, request, now);
	if (!uuid)
		return json({ error: "unauthorized" }, 401);
	if (await roleOf(env, uuid) !== "owner")
		return json({ error: "forbidden" }, 403);
	const body = await readJson(request);
	if (!body || typeof body.uuid !== "string" || !UUID.test(body.uuid) || typeof body.role !== "string" || (body.role !== "" && !GIVEN_ROLES.includes(body.role)))
		return json({ error: "bad_request" }, 400);
	const name = typeof body.name === "string" && PLAYER_NAME.test(body.name) ? body.name : "";
	if (body.uuid === uuid || await roleOf(env, body.uuid) === "owner")
		return json({ error: "forbidden" }, 403);
	forget(env, body.uuid);
	if (body.role === "") {
		await env.DB.prepare("DELETE FROM roles WHERE uuid = ?").bind(body.uuid).run();
		return json({ status: "removed" });
	}
	if (await count(env, "SELECT COUNT(*) AS total FROM roles WHERE role != ?", "owner") >= MAX_TEAM)
		return json({ error: "team_full" }, 409);
	await env.DB.prepare("INSERT OR REPLACE INTO roles (uuid, role, name) VALUES (?, ?, ?)").bind(body.uuid, body.role, name).run();
	return json({ status: "saved", role: body.role });
}

export async function handle(request, env, deps) {
	const url = new URL(request.url);
	const now = deps.now();
	const route = `${request.method} ${url.pathname}`;
	switch (route) {
		case "POST /v1/auth/start":
			return json({ challenge: await newChallenge(env, now) });
		case "POST /v1/auth/finish":
			return finishAuth(request, env, deps, now);
		case "GET /v1/loadouts":
			return readLoadouts(url, env, now);
		case "PUT /v1/loadout":
			return writeLoadout(request, env, now);
		case "GET /v1/friends":
			return listFriends(request, env, now);
		case "POST /v1/friends/request":
			return sendRequest(request, env, now);
		case "POST /v1/friends/accept":
			return acceptRequest(request, env, now);
		case "POST /v1/friends/remove":
			return removeFriend(request, env, now);
		case "PUT /v1/presence":
			return writePresence(request, env, now);
		case "DELETE /v1/presence":
			return goOffline(request, env, now);
		case "GET /v1/team":
			return readTeam(request, env, now);
		case "POST /v1/roles":
			return writeRole(request, env, now);
		default:
			return json({ error: "not_found" }, 404);
	}
}

const verifyCertificate = certificateVerifier(PLAYER_CERTIFICATE_KEYS);

export default {
	fetch(request, env) {
		return handle(request, env, { verifyCertificate, now: () => Math.floor(Date.now() / 1000) });
	},
	async scheduled(event, env) {
		await env.DB.prepare("SELECT 1 AS awake").first();
	}
};
