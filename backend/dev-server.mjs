import { createServer } from "node:http";
import { localDatabase } from "./local.mjs";
import { handle } from "./worker.js";

const port = Number(process.argv[2] ?? 8799);
const env = { DB: localDatabase(), TOKEN_SECRET: "dev-secret" };
const deps = {
	now: () => Math.floor(Date.now() / 1000),
	verifyCertificate: async () => true
};

createServer(async (req, res) => {
	const chunks = [];
	for await (const chunk of req)
		chunks.push(chunk);
	const body = Buffer.concat(chunks);
	const url = `http://127.0.0.1:${port}${req.url}`;

	if (req.url === "/__dev/seed") {
		const seed = JSON.parse(body.toString());
		await env.DB.prepare("INSERT OR REPLACE INTO loadouts (uuid, slot, item, updated_at) VALUES (?, ?, ?, 0)").bind(seed.uuid, seed.slot, seed.item).run();
		res.end("{}");
		return;
	}
	const answer = await handle(new Request(url, {
		method: req.method,
		headers: req.headers,
		body: body.length > 0 ? body : undefined
	}), env, deps);
	res.writeHead(answer.status, { "content-type": "application/json" });
	res.end(await answer.text());
}).listen(port, "127.0.0.1", () => console.log(`ready on ${port}`));
