CREATE TABLE IF NOT EXISTS loadouts (
	uuid TEXT NOT NULL,
	slot TEXT NOT NULL,
	item TEXT NOT NULL,
	updated_at INTEGER NOT NULL,
	PRIMARY KEY (uuid, slot)
);

CREATE TABLE IF NOT EXISTS friends (
	uuid TEXT NOT NULL,
	friend TEXT NOT NULL,
	PRIMARY KEY (uuid, friend)
);

CREATE TABLE IF NOT EXISTS friend_requests (
	sender TEXT NOT NULL,
	receiver TEXT NOT NULL,
	created_at INTEGER NOT NULL,
	PRIMARY KEY (sender, receiver)
);

CREATE TABLE IF NOT EXISTS presence (
	uuid TEXT PRIMARY KEY,
	name TEXT NOT NULL,
	server TEXT NOT NULL,
	seen_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS roles (
	uuid TEXT PRIMARY KEY,
	role TEXT NOT NULL,
	name TEXT NOT NULL DEFAULT ''
);

INSERT OR REPLACE INTO roles (uuid, role, name) VALUES ('a73db1899b88423998a6ba21c3fcef41', 'owner', 'Sakulius');
