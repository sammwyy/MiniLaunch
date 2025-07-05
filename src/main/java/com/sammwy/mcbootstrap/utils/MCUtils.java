package com.sammwy.mcbootstrap.utils;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sammwy.mcbootstrap.VersionManifest;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Utility class for Minecraft operations
 */
public class MCUtils {
    private static final Logger logger = LoggerFactory.getLogger(MCUtils.class);
    private static final String MOJANG_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private static final Map<String, VersionManifest> manifestCache = new ConcurrentHashMap<>();
    private static final AtomicLong lastCacheUpdate = new AtomicLong(0);
    private static final long CACHE_EXPIRATION = 5 * 60 * 1000; // 5 minutes

    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private static final Gson gson = new GsonBuilder().create();
    private static final ExecutorService executorService = Executors.newFixedThreadPool(4);

    /**
     * Represents a Minecraft version with its source
     */
    public static class MinecraftVersion {
        public final String id;
        public final String type;
        public final String releaseTime;
        public final String url;
        public final boolean isLocal;
        public final Path localPath;

        public MinecraftVersion(String id, String type, String releaseTime, String url, boolean isLocal,
                Path localPath) {
            this.id = id;
            this.type = type;
            this.releaseTime = releaseTime;
            this.url = url;
            this.isLocal = isLocal;
            this.localPath = localPath;
        }

        @Override
        public String toString() {
            return String.format("MinecraftVersion{id='%s', type='%s', local=%s}", id, type, isLocal);
        }

        public static MinecraftVersion dummy() {
            return new MinecraftVersion("Loading versions...", "loading", "", "", false, null);
        }
    }

    /**
     * Get all available Minecraft versions (both local and remote)
     * 
     * @param mcDir Minecraft directory to check for local versions
     * @return CompletableFuture with list of available versions
     */
    public static CompletableFuture<List<MinecraftVersion>> getAvailableVersions(Path mcDir) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Set<MinecraftVersion> versions = new LinkedHashSet<>();

                // Get local versions first
                List<MinecraftVersion> localVersions = getLocalVersions(mcDir);
                versions.addAll(localVersions);

                // Try to get remote versions
                try {
                    List<MinecraftVersion> remoteVersions = getRemoteVersions();
                    // Add remote versions that aren't already local
                    for (MinecraftVersion remoteVersion : remoteVersions) {
                        boolean isLocalVersion = localVersions.stream()
                                .anyMatch(local -> local.id.equals(remoteVersion.id));
                        if (!isLocalVersion) {
                            versions.add(remoteVersion);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to fetch remote versions, using local only", e);
                }

                return new ArrayList<>(versions);
            } catch (Exception e) {
                logger.error("Failed to get available versions", e);
                throw new RuntimeException("Failed to get available versions", e);
            }
        }, executorService);
    }

    /**
     * Get local Minecraft versions
     * 
     * @param mcDir Minecraft directory
     * @return List of local versions
     */
    public static List<MinecraftVersion> getLocalVersions(Path mcDir) {
        List<MinecraftVersion> versions = new ArrayList<>();

        try {
            Path versionsDir = mcDir.resolve("versions");
            if (!Files.exists(versionsDir)) {
                return versions;
            }

            Files.list(versionsDir)
                    .filter(Files::isDirectory)
                    .forEach(versionDir -> {
                        String versionId = versionDir.getFileName().toString();
                        Path jsonFile = versionDir.resolve(versionId + ".json");

                        if (Files.exists(jsonFile)) {
                            try (Reader reader = Files.newBufferedReader(jsonFile)) {
                                VersionManifest.VersionJson versionJson = gson.fromJson(reader,
                                        VersionManifest.VersionJson.class);
                                versions.add(new MinecraftVersion(
                                        versionId,
                                        versionJson.type != null ? versionJson.type : "unknown",
                                        versionJson.releaseTime != null ? versionJson.releaseTime : "",
                                        null,
                                        true,
                                        jsonFile));
                            } catch (Exception e) {
                                logger.warn("Failed to parse local version: " + versionId, e);
                            }
                        }
                    });
        } catch (IOException e) {
            logger.error("Failed to scan local versions", e);
        }

        return versions;
    }

    /**
     * Get remote Minecraft versions from Mojang
     * 
     * @return List of remote versions
     */
    public static List<MinecraftVersion> getRemoteVersions() throws IOException {
        VersionManifest manifest = getVersionManifest();
        List<MinecraftVersion> versions = new ArrayList<>();

        for (VersionManifest.Version version : manifest.versions) {
            versions.add(new MinecraftVersion(
                    version.id,
                    version.type,
                    version.releaseTime,
                    version.url,
                    false,
                    null));
        }

        return versions;
    }

    /**
     * Get version manifest from Mojang with caching
     * 
     * @return VersionManifest
     */
    public static VersionManifest getVersionManifest() throws IOException {
        long currentTime = System.currentTimeMillis();

        // Check if cache is valid
        if (manifestCache.containsKey("main") &&
                (currentTime - lastCacheUpdate.get()) < CACHE_EXPIRATION) {
            return manifestCache.get("main");
        }

        // Fetch new manifest
        Request request = new Request.Builder()
                .url(MOJANG_MANIFEST_URL)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch version manifest: " + response.code());
            }

            String content = response.body().string();
            VersionManifest manifest = gson.fromJson(content, VersionManifest.class);

            // Update cache
            manifestCache.put("main", manifest);
            lastCacheUpdate.set(currentTime);

            return manifest;
        }
    }

    /**
     * Check if a version exists locally
     * 
     * @param mcDir     Minecraft directory
     * @param versionId Version ID to check
     * @return true if version exists locally
     */
    public static boolean isVersionLocal(Path mcDir, String versionId) {
        Path versionDir = mcDir.resolve("versions").resolve(versionId);
        Path jsonFile = versionDir.resolve(versionId + ".json");
        Path jarFile = versionDir.resolve(versionId + ".jar");

        return Files.exists(jsonFile) && Files.exists(jarFile);
    }

    /**
     * Get version info from local installation
     * 
     * @param mcDir     Minecraft directory
     * @param versionId Version ID
     * @return MinecraftVersion or null if not found
     */
    public static MinecraftVersion getLocalVersion(Path mcDir, String versionId) {
        Path versionDir = mcDir.resolve("versions").resolve(versionId);
        Path jsonFile = versionDir.resolve(versionId + ".json");

        if (!Files.exists(jsonFile)) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(jsonFile)) {
            VersionManifest.VersionJson versionJson = gson.fromJson(reader, VersionManifest.VersionJson.class);
            return new MinecraftVersion(
                    versionId,
                    versionJson.type != null ? versionJson.type : "unknown",
                    versionJson.releaseTime != null ? versionJson.releaseTime : "",
                    null,
                    true,
                    jsonFile);
        } catch (Exception e) {
            logger.error("Failed to parse local version: " + versionId, e);
            return null;
        }
    }

    /**
     * Clear version manifest cache
     */
    public static void clearCache() {
        manifestCache.clear();
        lastCacheUpdate.set(0);
    }

    /**
     * Shutdown utility resources
     */
    public static void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
        }

        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }
}