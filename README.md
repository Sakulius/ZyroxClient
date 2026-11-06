# Zyrox Client

In-game client for the [Zyrox launcher](https://github.com/Sakulius/ZyroxLauncher). A Fabric mod for Minecraft 26.3 with a HUD, cosmetics, music and lyrics. This is not a cheat client.

## Build

Needs Java 25.

```
./gradlew build
```

The jar ends up in `build/libs`.

## Test

```
node backend/dev-server.mjs
./gradlew runClientGameTest
```

Backend tests:

```
cd backend
node --test worker.test.mjs
```

## Cosmetics server

`backend` is a Cloudflare Worker with a D1 database.

1. Copy `wrangler.example.toml` to `wrangler.toml` and enter your database ID.
2. Create the table with `schema.sql`.
3. Set a long random `TOKEN_SECRET` with `npx wrangler secret put TOKEN_SECRET`.
4. Run `npx wrangler deploy` and point `serverUrl` in `CloudCosmetics.java` to your server.

## New cosmetics

Blockbench models in the Java Block format can be converted with:

```
python tools/convert_bbmodel.py <model.bbmodel> <cosmetic id>
```

Then add the hat to `HATS` in `Cosmetics.java`.

## Credits

- Windows media session code adapted from Minecraft Spotify Lyrics by Alon Goldberger (MIT)
- Inter font by The Inter Project Authors (SIL Open Font License)

The license texts are in `src/main/resources/assets/zyrox/licenses`.

## License

The code is under the [MIT License](LICENSE). It does not cover the Zyrox name and logo, so forks need their own.
