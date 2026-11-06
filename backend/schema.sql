CREATE TABLE IF NOT EXISTS loadouts (
	uuid TEXT NOT NULL,
	slot TEXT NOT NULL,
	item TEXT NOT NULL,
	updated_at INTEGER NOT NULL,
	PRIMARY KEY (uuid, slot)
);
