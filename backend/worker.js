import { PLAYER_CERTIFICATE_KEYS } from "./mojang-keys.js";

const CHALLENGE_SECONDS = 60;
const TOKEN_SECONDS = 30 * 24 * 60 * 60;
const MIN_WRITE_SECONDS = 2;
const MAX_UUIDS = 50;
const MAX_SLOTS = 16;
const MAX_BODY_BYTES = 4096;
const UUID = /^[0-9a-f]{32}$/;
const NAME = /^[a-z][a-z0-9_-]{0,31}$/;

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

async function readLoadouts(url, env) {
	const uuids = [...new Set((url.searchParams.get("uuids") ?? "").split(",").filter(Boolean))];
	if (uuids.length === 0 || uuids.length > MAX_UUIDS || !uuids.every(uuid => UUID.test(uuid)))
		return json({ error: "bad_request" }, 400);
	const marks = uuids.map(() => "?").join(",");
	const { results } = await env.DB.prepare(`SELECT uuid, slot, item FROM loadouts WHERE uuid IN (${marks})`).bind(...uuids).all();
	const loadouts = {};
	for (const row of results)
		(loadouts[row.uuid] ??= {})[row.slot] = row.item;
	return json({ loadouts }, 200, { "cache-control": "public, max-age=30" });
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
	return json({ saved: true });
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
			return readLoadouts(url, env);
		case "PUT /v1/loadout":
			return writeLoadout(request, env, now);
		default:
			return json({ error: "not_found" }, 404);
	}
}

const verifyCertificate = certificateVerifier(PLAYER_CERTIFICATE_KEYS);

export default {
	fetch(request, env) {
		return handle(request, env, { verifyCertificate, now: () => Math.floor(Date.now() / 1000) });
	}
};
