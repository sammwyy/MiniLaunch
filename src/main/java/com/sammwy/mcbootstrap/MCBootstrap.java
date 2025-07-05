package com.sammwy.mcbootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sammwy.mcbootstrap.VersionManifest.AssetIndex;
import com.sammwy.mcbootstrap.VersionManifest.VersionJson;
import com.sammwy.mcbootstrap.utils.LaunchUtils;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Main bootstrap class for launching Minecraft
 */
public class MCBootstrap {
    private static final Logger logger = LoggerFactory.getLogger(MCBootstrap.class);
    private static final String MOJANG_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String RESOURCES_URL = "https://resources.download.minecraft.net/";
    private static final String LIBRARIES_URL = "https://libraries.minecraft.net/";

    private final LaunchConfig config;
    private final LaunchState state;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final ExecutorService executorService;
    private final ReentrantLock stateLock = new ReentrantLock();

    private VersionJson versionJson;
    private AssetIndex assetIndex;

    public MCBootstrap(LaunchConfig config) {
        this.config = config;
        this.state = new LaunchState();
        this.httpClient = new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
        this.gson = new GsonBuilder().create();
        this.executorService = Executors.newFixedThreadPool(8);

        validateConfig();
    }

    private void validateConfig() {
        if (config.getUsername() == null || config.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (config.getMcDir() == null) {
            throw new IllegalArgumentException("Minecraft directory is required");
        }
    }

    public LaunchState state() {
        return state;
    }

    public CompletableFuture<Void> init() {
        return CompletableFuture.runAsync(() -> {
            try {
                updateState(s -> s.setStatusMessage("Initializing..."));
                resetState();

                // Create directories
                LaunchUtils.createDirectories(config);

                // Load version JSON
                loadVersionJson();

                // Load asset index
                loadAssetIndex();

                // Check missing files
                checkMissingFiles();

                updateState(s -> {
                    s.setInitialized(true);
                    s.setCanLaunch(s.getTotalMissingFiles() == 0);

                    if (s.canLaunch()) {
                        s.setStatusMessage("Ready to launch");
                    } else {
                        s.setStatusMessage(String.format("Missing %d files, download required",
                                s.getTotalMissingFiles()));
                    }
                });

            } catch (Exception e) {
                logger.error("Initialization failed", e);
                updateState(s -> {
                    s.setLastError(e);
                    s.setStatusMessage("Initialization failed: " + e.getMessage());
                });
            }
        }, executorService);
    }

    private void updateState(StateUpdater updater) {
        stateLock.lock();
        try {
            updater.update(state);
        } finally {
            stateLock.unlock();
        }
    }

    private void resetState() {
        updateState(LaunchState::reset);
    }

    private void loadVersionJson() throws IOException {
        Path versionJsonPath = config.getVersionJsonPath();
        if (!Files.exists(versionJsonPath)) {
            updateState(s -> s.addMissingFile("version.json"));
            return;
        }

        try (Reader reader = Files.newBufferedReader(versionJsonPath)) {
            logger.debug("Loading version.json: {}", versionJsonPath);
            versionJson = gson.fromJson(reader, VersionJson.class);
        }
    }

    private void loadAssetIndex() throws IOException {
        if (versionJson == null) {
            return;
        }

        Path assetIndexPath = config.getAssetsPath()
                .resolve("indexes")
                .resolve(versionJson.assetIndex.id + ".json");

        if (!Files.exists(assetIndexPath)) {
            updateState(s -> s.addMissingFile("asset_index"));
            return;
        }

        try (Reader reader = Files.newBufferedReader(assetIndexPath)) {
            assetIndex = gson.fromJson(reader, AssetIndex.class);
        }
    }

    private void checkMissingFiles() {
        // Check main jar
        if (!Files.exists(config.getVersionJarPath())) {
            updateState(s -> s.addMissingFile("client.jar"));
        }

        if (versionJson == null) {
            return;
        }

        // Check libraries
        for (VersionJson.Library library : versionJson.libraries) {
            if (!LaunchUtils.isLibraryAllowed(library)) {
                continue;
            }

            checkLibraryArtifact(library);
            checkLibraryNatives(library);
        }

        // Check assets
        checkAssets();
    }

    private void checkLibraryArtifact(VersionJson.Library library) {
        VersionJson.Library.Downloads.Artifact artifact = library.downloads.artifact;
        if (artifact != null) {
            Path libraryPath = config.getLibrariesPath().resolve(artifact.path);
            if (!Files.exists(libraryPath)) {
                updateState(s -> s.addMissingLibrary(artifact.path));
            }
        }
    }

    private void checkLibraryNatives(VersionJson.Library library) {
        if (library.downloads.classifiers != null) {
            String nativeClassifier = LaunchUtils.getNativeClassifier(library);
            if (nativeClassifier != null) {
                VersionJson.Library.Downloads.Artifact nativeArtifact = library.downloads.classifiers
                        .get(nativeClassifier);
                if (nativeArtifact != null) {
                    Path nativePath = config.getLibrariesPath().resolve(nativeArtifact.path);
                    if (!Files.exists(nativePath)) {
                        updateState(s -> s.addMissingLibrary(nativeArtifact.path));
                    }
                }
            }
        }
    }

    private void checkAssets() {
        if (assetIndex == null) {
            return;
        }

        for (Map.Entry<String, AssetIndex.Asset> entry : assetIndex.objects.entrySet()) {
            String hash = entry.getValue().hash;
            String subPath = hash.substring(0, 2) + "/" + hash;
            Path assetPath = config.getAssetsPath().resolve("objects").resolve(subPath);

            if (!Files.exists(assetPath)) {
                updateState(s -> s.addMissingAsset(entry.getKey()));
            }
        }
    }

    public CompletableFuture<DownloadState> download() {
        if (!state.isInitialized()) {
            throw new IllegalStateException("Must call init() before download()");
        }

        DownloadState download = new DownloadState();

        CompletableFuture<Void> downloadFuture = CompletableFuture.runAsync(() -> {
            try {
                download.setStatus(DownloadState.DownloadStatus.DOWNLOADING);

                // Calculate total files to download
                calculateTotalDownloadFiles(download);

                // Download missing files
                downloadMissingFiles(download);

                download.setStatus(DownloadState.DownloadStatus.COMPLETED);

                // Re-initialize to update state
                init().join();

            } catch (Exception e) {
                logger.error("Download failed", e);
                download.setStatus(DownloadState.DownloadStatus.FAILED);
                download.notifyError(e);
            }
        }, executorService);

        download.setDownloadFuture(downloadFuture);
        return CompletableFuture.completedFuture(download);
    }

    private void calculateTotalDownloadFiles(DownloadState download) {
        int totalFiles = state.getTotalMissingFiles();
        if (versionJson == null && state.getMissingFiles().contains("version.json")) {
            totalFiles++; // Add version manifest download
        }
        if (assetIndex == null && state.getMissingFiles().contains("asset_index")) {
            totalFiles++; // Add asset index download
        }
        download.setTotalFiles(totalFiles);
    }

    private void downloadMissingFiles(DownloadState download) throws IOException {
        // Download version manifest if needed
        if (versionJson == null && state.getMissingFiles().contains("version.json")) {
            downloadVersionManifest(download);
        }

        // Download asset index if needed
        if (assetIndex == null && state.getMissingFiles().contains("asset_index")) {
            downloadAssetIndex(download);
        }

        // Download client jar
        if (state.getMissingFiles().contains("client.jar")) {
            downloadClientJar(download);
        }

        // Download libraries
        downloadLibraries(download);

        // Download assets
        downloadAssets(download);
    }

    private void downloadVersionManifest(DownloadState download) throws IOException {
        download.setCurrentFile("version_manifest.json");

        // First download the manifest to get the version URL
        Request manifestRequest = new Request.Builder().url(MOJANG_MANIFEST_URL).build();
        try (Response response = httpClient.newCall(manifestRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download version manifest: " + response.code());
            }

            VersionManifest manifest = gson.fromJson(response.body().string(), VersionManifest.class);

            // Find the version URL
            String versionUrl = null;
            for (VersionManifest.Version version : manifest.versions) {
                if (version.id.equals(config.getVersionId())) {
                    versionUrl = version.url;
                    break;
                }
            }

            if (versionUrl == null) {
                throw new IOException("Version " + config.getVersionId() + " not found in manifest");
            }

            // Download the specific version JSON
            downloadVersionJson(versionUrl, download);
        }
    }

    private void downloadVersionJson(String versionUrl, DownloadState download) throws IOException {
        Request versionRequest = new Request.Builder().url(versionUrl).build();
        try (Response versionResponse = httpClient.newCall(versionRequest).execute()) {
            if (!versionResponse.isSuccessful()) {
                throw new IOException("Failed to download version JSON: " + versionResponse.code());
            }

            String versionJsonContent = versionResponse.body().string();
            LaunchUtils.ensureParentDirectories(config.getVersionJsonPath());
            Files.write(config.getVersionJsonPath(), versionJsonContent.getBytes(), StandardOpenOption.CREATE);

            // Parse the version JSON
            logger.debug("Downloaded version.json: {}", versionJsonContent);
            versionJson = gson.fromJson(versionJsonContent, VersionJson.class);

            updateState(s -> s.removeMissingFile("version.json"));
            download.incrementCompletedFiles();
        }
    }

    private void downloadAssetIndex(DownloadState download) throws IOException {
        if (versionJson == null) {
            return;
        }

        download.setCurrentFile("asset_index.json");

        Request request = new Request.Builder()
                .url(versionJson.assetIndex.url)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download asset index: " + response.code());
            }

            String content = response.body().string();
            Path assetIndexPath = config.getAssetsPath()
                    .resolve("indexes")
                    .resolve(versionJson.assetIndex.id + ".json");

            LaunchUtils.ensureParentDirectories(assetIndexPath);
            Files.write(assetIndexPath, content.getBytes(), StandardOpenOption.CREATE);

            // Parse asset index
            assetIndex = gson.fromJson(content, AssetIndex.class);

            updateState(s -> s.removeMissingFile("asset_index"));
            download.incrementCompletedFiles();
        }
    }

    private void downloadClientJar(DownloadState download) throws IOException {
        if (versionJson == null) {
            return;
        }

        download.setCurrentFile("client.jar");

        Request request = new Request.Builder()
                .url(versionJson.downloads.client.url)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download client jar: " + response.code());
            }

            try (InputStream inputStream = response.body().byteStream()) {
                Files.copy(inputStream, config.getVersionJarPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            updateState(s -> s.removeMissingFile("client.jar"));
            download.incrementCompletedFiles();
        }
    }

    private void downloadLibraries(DownloadState download) throws IOException {
        if (versionJson == null) {
            return;
        }

        List<CompletableFuture<Void>> libraryDownloads = new ArrayList<>();

        for (VersionJson.Library library : versionJson.libraries) {
            if (!LaunchUtils.isLibraryAllowed(library)) {
                continue;
            }

            // Download main artifact
            if (library.downloads.artifact != null) {
                String artifactPath = library.downloads.artifact.path;
                if (state.getMissingLibraries().contains(artifactPath)) {
                    libraryDownloads.add(downloadLibraryArtifact(library.downloads.artifact, download));
                }
            }

            // Download natives
            if (library.downloads.classifiers != null) {
                String nativeClassifier = LaunchUtils.getNativeClassifier(library);
                if (nativeClassifier != null) {
                    VersionJson.Library.Downloads.Artifact nativeArtifact = library.downloads.classifiers
                            .get(nativeClassifier);
                    if (nativeArtifact != null && state.getMissingLibraries().contains(nativeArtifact.path)) {
                        libraryDownloads.add(downloadLibraryArtifact(nativeArtifact, download));
                    }
                }
            }
        }

        // Wait for all library downloads to complete
        CompletableFuture.allOf(libraryDownloads.toArray(new CompletableFuture[0])).join();
    }

    private CompletableFuture<Void> downloadLibraryArtifact(VersionJson.Library.Downloads.Artifact artifact,
            DownloadState download) {
        return CompletableFuture.runAsync(() -> {
            try {
                download.setCurrentFile(artifact.path);

                Path libraryPath = config.getLibrariesPath().resolve(artifact.path);

                String downloadUrl;
                if (artifact.url != null
                        && (artifact.url.startsWith("http://") || artifact.url.startsWith("https://"))) {
                    downloadUrl = artifact.url;
                } else {
                    downloadUrl = LIBRARIES_URL + artifact.path;
                }

                LaunchUtils.downloadFile(httpClient, downloadUrl, libraryPath);

                updateState(s -> s.removeMissingLibrary(artifact.path));
                download.incrementCompletedFiles();
            } catch (Exception e) {
                logger.error("Failed to download library: " + artifact.path, e);
                download.incrementFailedFiles();
            }
        }, executorService);
    }

    private void downloadAssets(DownloadState download) throws IOException {
        if (assetIndex == null) {
            return;
        }

        List<CompletableFuture<Void>> assetDownloads = new ArrayList<>();

        for (Map.Entry<String, AssetIndex.Asset> entry : assetIndex.objects.entrySet()) {
            String assetName = entry.getKey();
            if (state.getMissingAssets().contains(assetName)) {
                assetDownloads.add(downloadAsset(assetName, entry.getValue(), download));
            }
        }

        // Wait for all asset downloads to complete
        CompletableFuture.allOf(assetDownloads.toArray(new CompletableFuture[0])).join();
    }

    private CompletableFuture<Void> downloadAsset(String assetName, AssetIndex.Asset asset, DownloadState download) {
        return CompletableFuture.runAsync(() -> {
            try {
                download.setCurrentFile(assetName);

                String hash = asset.hash;
                String subPath = hash.substring(0, 2) + "/" + hash;
                Path assetPath = config.getAssetsPath().resolve("objects").resolve(subPath);

                String url = RESOURCES_URL + subPath;
                LaunchUtils.downloadFile(httpClient, url, assetPath);

                updateState(s -> s.removeMissingAsset(assetName));
                download.incrementCompletedFiles();
            } catch (Exception e) {
                logger.error("Failed to download asset: " + assetName, e);
                download.incrementFailedFiles();
            }
        }, executorService);
    }

    public CompletableFuture<Process> run() {
        return CompletableFuture.supplyAsync(() -> {
            if (!state.canLaunch()) {
                throw new CompletionException(
                        new IllegalStateException("Cannot launch: missing files or not initialized"));
            }

            try {
                List<String> command = buildLaunchCommand();

                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.directory(config.getMcDir().toFile());
                processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

                logger.info("Launching Minecraft with command: {}", String.join(" ", command));

                return processBuilder.start();

            } catch (Exception e) {
                logger.error("Failed to launch Minecraft", e);
                throw new CompletionException(e);
            }
        }, executorService);
    }

    private List<String> buildLaunchCommand() throws IOException {
        List<String> command = new ArrayList<>();

        // Java executable
        command.add("java");

        // JVM arguments
        command.add("-Xmx" + config.getMaxMemory() + "m");
        command.add("-Xms" + config.getMinMemory() + "m");

        // Custom JVM arguments
        config.getCustomJvmArgs().forEach((key, value) -> {
            command.add(key);
            if (value != null && !value.isEmpty()) {
                command.add(value);
            }
        });

        // Classpath
        command.add("-cp");
        command.add(LaunchUtils.buildClasspath(config, versionJson));

        // Main class
        command.add(versionJson.mainClass);

        // Game arguments
        command.addAll(buildGameArguments());

        return command;
    }

    private List<String> buildGameArguments() {
        List<String> args = new ArrayList<>();

        // Basic arguments
        args.add("--username");
        args.add(config.getUsername());

        args.add("--version");
        args.add(versionJson.id);

        args.add("--gameDir");
        args.add(config.getMcDir().toString());

        args.add("--assetsDir");
        args.add(config.getAssetsPath().toString());

        args.add("--assetIndex");
        args.add(versionJson.assetIndex.id);

        args.add("--uuid");
        args.add(UUID.randomUUID().toString());

        args.add("--accessToken");
        args.add("0");

        args.add("--userType");
        args.add("mojang");

        args.add("--versionType");
        args.add(versionJson.type);

        // Custom game arguments
        config.getCustomGameArgs().forEach((key, value) -> {
            args.add(key);
            if (value != null && !value.isEmpty()) {
                args.add(value);
            }
        });

        return args;
    }

    public void close() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }

        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @FunctionalInterface
    private interface StateUpdater {
        void update(LaunchState state);
    }
}