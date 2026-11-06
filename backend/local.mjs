import { readFileSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";

class Statement {
	constructor(database, sql, values = []) {
		this.database = database;
		this.sql = sql;
		this.values = values;
	}

	bind(...values) {
		return new Statement(this.database, this.sql, values);
	}

	async all() {
		return { results: this.database.prepare(this.sql).all(...this.values) };
	}

	async first() {
		return this.database.prepare(this.sql).get(...this.values) ?? null;
	}

	async run() {
		this.database.prepare(this.sql).run(...this.values);
	}
}

export function localDatabase() {
	const database = new DatabaseSync(":memory:");
	database.exec(readFileSync(new URL("./schema.sql", import.meta.url), "utf8"));
	return {
		prepare: sql => new Statement(database, sql),
		async batch(statements) {
			database.exec("BEGIN");
			try {
				for (const statement of statements)
					await statement.run();
				database.exec("COMMIT");
			} catch (error) {
				database.exec("ROLLBACK");
				throw error;
			}
		}
	};
}
