import assert from "node:assert/strict";
import { test } from "node:test";
import { localDatabase } from "./local.mjs";
import { certificateVerifier, handle } from "./worker.js";

const PLAYER = "0123456789abcdef0123456789abcdef";
const OTHER = "fedcba9876543210fedcba9876543210";
const RSA = { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]) };
const base64 = bytes => Buffer.from(bytes).toString("base64");

const fakeMojang = await crypto.subtle.generateKey({ ...RSA, hash: "SHA-1" }, true, ["sign", "verify"]);
const notMojang = await crypto.subtle.generateKey({ ...RSA, hash: "SHA-1" }, true, ["sign", "verify"]);
const mojangKey = base64(await crypto.subtle.exportKey("spki", fakeMojang.publicKey));

async function certificate(uuid, expiresAt, signer = fakeMojang) {
	const player = await crypto.subtle.generateKey({ ...RSA, hash: "SHA-256" }, true, ["sign", "verify"]);
	const publicKey = new Uint8Array(await crypto.subtle.exportKey("spki", player.publicKey));
	const payload = new Uint8Array(24 + publicKey.length);
	payload.set(Buffer.from(uuid, "hex"));
	new DataView(payload.buffer).setBigUint64(16, BigInt(expiresAt));
	payload.set(publicKey, 24);
	const keySignature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", signer.privateKey, payload);
	return { player, publicKey: base64(publicKey), keySignature: base64(keySignature), expiresAt };
}

function setup() {
	const state = { now: 1_000_000, env: { DB: localDatabase(), TOKEN_SECRET: "test-secret" } };
	state.deps = { now: () => state.now, verifyCertificate: certificateVerifier([mojangKey]) };
	state.call = (method, path, body, token) => handle(new Request(`https://test${path}`, {
		method,
		body: body === undefined ? undefined : JSON.stringify(body),
		headers: token ? { authorization: `Bearer ${token}` } : {}
	}), state.env, state.deps);
	state.challenge = async () => (await (await state.call("POST", "/v1/auth/start")).json()).challenge;
	state.finish = async (cert, uuid, challenge, signedText) => {
		const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", cert.player.privateKey, new TextEncoder().encode(signedText ?? `zyrox-auth:${challenge}`));
		return state.call("POST", "/v1/auth/finish", {
			challenge, uuid, publicKey: cert.publicKey, expiresAt: cert.expiresAt, keySignature: cert.keySignature, signature: base64(signature)
		});
	};
	state.validCertificate = (uuid = PLAYER) => certificate(uuid, (state.now + 3600) * 1000);
	state.signIn = async (uuid = PLAYER) => {
		const answer = await state.finish(await state.validCertificate(uuid), uuid, await state.challenge());
		assert.equal(answer.status, 200);
		return (await answer.json()).token;
	};
	return state;
}

test("a real certificate signs in", async () => {
	const s = setup();
	const answer = await s.finish(await s.validCertificate(), PLAYER, await s.challenge());
	assert.equal(answer.status, 200);
	assert.equal((await answer.json()).uuid, PLAYER);
});

test("a certificate not signed by Mojang is refused", async () => {
	const s = setup();
	const forged = await certificate(PLAYER, (s.now + 3600) * 1000, notMojang);
	assert.equal((await s.finish(forged, PLAYER, await s.challenge())).status, 401);
});

test("the certificate of one player cannot sign in as another", async () => {
	const s = setup();
	assert.equal((await s.finish(await s.validCertificate(PLAYER), OTHER, await s.challenge())).status, 401);
});

test("an expired certificate is refused", async () => {
	const s = setup();
	const old = await certificate(PLAYER, (s.now - 10) * 1000);
	assert.equal((await s.finish(old, PLAYER, await s.challenge())).status, 401);
});

test("changing the expiry breaks the certificate", async () => {
	const s = setup();
	const cert = await certificate(PLAYER, (s.now + 10) * 1000);
	cert.expiresAt = (s.now + 999999) * 1000;
	assert.equal((await s.finish(cert, PLAYER, await s.challenge())).status, 401);
});

test("the signature must prove the private key for this challenge", async () => {
	const s = setup();
	const cert = await s.validCertificate();
	const challenge = await s.challenge();
	assert.equal((await s.finish(cert, PLAYER, challenge, "something else")).status, 401);
	const stranger = await s.validCertificate();
	const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", stranger.player.privateKey, new TextEncoder().encode(`zyrox-auth:${challenge}`));
	const answer = await s.call("POST", "/v1/auth/finish", {
		challenge, uuid: PLAYER, publicKey: cert.publicKey, expiresAt: cert.expiresAt, keySignature: cert.keySignature, signature: base64(signature)
	});
	assert.equal(answer.status, 401);
});

test("challenges not issued here are refused", async () => {
	const s = setup();
	assert.equal((await s.finish(await s.validCertificate(), PLAYER, "a".repeat(40))).status, 401);
});

test("challenges expire", async () => {
	const s = setup();
	const challenge = await s.challenge();
	const cert = await s.validCertificate();
	s.now += 61;
	assert.equal((await s.finish(cert, PLAYER, challenge)).status, 401);
});

test("broken certificate data is refused", async () => {
	const s = setup();
	const challenge = await s.challenge();
	for (const body of [
		{ challenge, uuid: PLAYER, publicKey: "%%%", expiresAt: (s.now + 60) * 1000, keySignature: "%%%", signature: "%%%" },
		{ challenge, uuid: PLAYER, publicKey: "AAAA", expiresAt: (s.now + 60) * 1000, keySignature: "AAAA", signature: "AAAA" },
		{ challenge, uuid: PLAYER, expiresAt: (s.now + 60) * 1000 }
	])
		assert.equal((await s.call("POST", "/v1/auth/finish", body)).status, 401, JSON.stringify(body));
	assert.equal((await s.call("POST", "/v1/auth/finish", { challenge, uuid: "nope", expiresAt: 5 })).status, 400);
	assert.equal((await s.call("POST", "/v1/auth/finish", { challenge, uuid: PLAYER, expiresAt: "soon" })).status, 400);
});

test("a player can save a cape and everyone can read it", async () => {
	const s = setup();
	const token = await s.signIn();
	assert.equal((await s.call("PUT", "/v1/loadout", { items: { cape: "aurora" } }, token)).status, 200);
	const { loadouts } = await (await s.call("GET", `/v1/loadouts?uuids=${PLAYER},${OTHER}`)).json();
	assert.deepEqual(loadouts, { [PLAYER]: { cape: "aurora" } });
});

test("saving replaces the old loadout", async () => {
	const s = setup();
	const token = await s.signIn();
	await s.call("PUT", "/v1/loadout", { items: { cape: "aurora", hat: "crown" } }, token);
	s.now += 5;
	await s.call("PUT", "/v1/loadout", { items: { cape: "void" } }, token);
	const { loadouts } = await (await s.call("GET", `/v1/loadouts?uuids=${PLAYER}`)).json();
	assert.deepEqual(loadouts[PLAYER], { cape: "void" });
});

test("nobody can write without a valid token", async () => {
	const s = setup();
	const token = await s.signIn();
	const forged = token.replace(PLAYER, OTHER);
	assert.equal((await s.call("PUT", "/v1/loadout", { items: { cape: "void" } })).status, 401);
	assert.equal((await s.call("PUT", "/v1/loadout", { items: { cape: "void" } }, forged)).status, 401);
	assert.equal((await s.call("PUT", "/v1/loadout", { items: { cape: "void" } }, "garbage")).status, 401);
	const { loadouts } = await (await s.call("GET", `/v1/loadouts?uuids=${OTHER}`)).json();
	assert.deepEqual(loadouts, {});
});

test("tokens expire", async () => {
	const s = setup();
	const token = await s.signIn();
	s.now += 31 * 24 * 60 * 60;
	assert.equal((await s.call("PUT", "/v1/loadout", { items: { cape: "void" } }, token)).status, 401);
});

test("writing too fast is refused", async () => {
	const s = setup();
	const token = await s.signIn();
	await s.call("PUT", "/v1/loadout", { items: { cape: "void" } }, token);
	assert.equal((await s.call("PUT", "/v1/loadout", { items: { cape: "ember" } }, token)).status, 429);
});

test("bad input is refused", async () => {
	const s = setup();
	const token = await s.signIn();
	for (const items of [null, [], { cape: 5 }, { cape: "../x" }, { "BAD SLOT": "x" }, { cape: "x".repeat(40) }])
		assert.equal((await s.call("PUT", "/v1/loadout", { items }, token)).status, 400, JSON.stringify(items));
	assert.equal((await s.call("GET", "/v1/loadouts?uuids=' OR 1=1 --")).status, 400);
	assert.equal((await s.call("GET", "/v1/loadouts")).status, 400);
	assert.equal((await s.call("GET", `/v1/loadouts?uuids=${Array.from({ length: 51 }, (_, i) => i.toString(16).padStart(32, "0")).join(",")}`)).status, 400);
	assert.equal((await s.call("GET", "/nope")).status, 404);
});
