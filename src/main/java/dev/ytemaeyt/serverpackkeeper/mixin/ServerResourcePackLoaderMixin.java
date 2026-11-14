package dev.ytemaeyt.serverpackkeeper.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.resource.server.ServerResourcePackLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Mixin(ServerResourcePackLoader.class)
public abstract class ServerResourcePackLoaderMixin {
    @Unique
    private static final ConcurrentMap<UUID, Path> SERVERPACKKEEPER_PENDING = new ConcurrentHashMap<>();

    @Unique
    private static Path serverpackkeeper$cachePath(URL url, String hash) {
        MinecraftClient client = MinecraftClient.getInstance();
        ServerInfo server = client.getCurrentServerEntry();
        String address = server == null || server.address == null ? "unknown-server" : server.address;
        String serverKey = serverpackkeeper$sha256(address.toLowerCase());
        // Prefer the server-provided content hash. Without one, URL identity is
        // the only stable identifier available without downloading to compare.
        String packIdentity = (hash != null && !hash.isBlank()) ? "sha1-" + hash.toLowerCase()
                : "url-" + serverpackkeeper$sha256(url.toExternalForm());
        return FabricLoader.getInstance().getGameDir()
                .resolve("serverpackkeeper")
                .resolve(serverKey)
                .resolve(packIdentity + ".zip");
    }

    @Unique
    private static String serverpackkeeper$sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @Inject(
            method = "addResourcePack(Ljava/util/UUID;Ljava/net/URL;Ljava/lang/String;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void serverpackkeeper$useCachedPack(UUID id, URL url, String hash, CallbackInfo ci) {
        try {
            Path cached = serverpackkeeper$cachePath(url, hash);
            if (Files.isRegularFile(cached) && Files.size(cached) > 0) {
                ((ServerResourcePackLoader) (Object) this).addResourcePack(id, cached);
                ci.cancel();
                return;
            }
            Files.createDirectories(cached.getParent());
            SERVERPACKKEEPER_PENDING.put(id, cached);
        } catch (Exception exception) {
            System.err.println("[ServerPackKeeper] Cache lookup failed; using normal download: " + exception);
        }
    }

    @Inject(
            method = "addResourcePack(Ljava/util/UUID;Ljava/nio/file/Path;)V",
            at = @At("HEAD")
    )
    private void serverpackkeeper$saveDownloadedPack(UUID id, Path downloadedPack, CallbackInfo ci) {
        Path destination = SERVERPACKKEEPER_PENDING.remove(id);
        if (destination == null) return;

        try {
            if (Files.isRegularFile(downloadedPack) && !downloadedPack.equals(destination)) {
                Files.createDirectories(destination.getParent());
                Files.copy(downloadedPack, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            System.err.println("[ServerPackKeeper] Could not save downloaded pack: " + exception);
        }
    }
}
