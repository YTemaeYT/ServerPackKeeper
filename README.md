# ServerPackKeeper

Client-side Fabric mod for Minecraft 1.21.4. It stores downloaded server resource packs in a per-server cache under `.minecraft/serverpackkeeper/`.

## Build

Requires Java 21. Run `gradle build`. The JAR is created in `build/libs/`.

GitHub Actions builds the mod on every push to `main`. Download the JAR from the successful workflow run's Artifacts section.

## Cache behavior and limitation

- Packs with a server-provided SHA-1 are cached by server and SHA-1.
- If a server omits the hash, the URL is used as the cache identity. If the server replaces pack contents at the same URL without supplying a new hash, the old cached pack may be reused. Delete that server's folder inside `.minecraft/serverpackkeeper/` to force a fresh download.
- The mod does not bypass the server's resource-pack prompt or acceptance setting.
- This is an initial implementation; test it in a separate Minecraft instance before using it in your main profile.
