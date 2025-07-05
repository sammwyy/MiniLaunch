package com.sammwy.mcbootstrap.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sammwy.mcbootstrap.LaunchConfig;
import com.sammwy.mcbootstrap.VersionManifest.VersionJson;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Utility methods for MCBootstrap operations
 */
public class LaunchUtils {
    private static final Logger logger = LoggerFactory.getLogger(LaunchUtils.class);

    /**
     * Create all necessary directories for Minecraft
     * 
     * @param config LaunchConfig
     * @throws IOException if directory creation fails
     */
    public static void createDirectories(LaunchConfig config) throws IOException {
        Files.createDirectories(config.getMcDir());
        Files.createDirectories(config.getLibrariesPath());
        Files.createDirectories(config.getAssetsPath());
        Files.createDirectories(config.getMcDir().resolve("versions"));
        Files.createDirectories(config.getAssetsPath().resolve("indexes"));
        Files.createDirectories(config.getAssetsPath().resolve("objects"));
    }

    /**
     * Calculate SHA-1 hash of a file
     * 
     * @param file Path to file
     * @return SHA-1 hash as hex string
     * @throws IOException if calculation fails
     */
    public static String calculateSha1(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream inputStream = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }

            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IOException("Failed to calculate SHA-1", e);
        }
    }

    /**
     * Check if a library is allowed for the current OS
     * 
     * @param library Library to check
     * @return true if library is allowed
     */
    public static boolean isLibraryAllowed(VersionJson.Library library) {
        if (library.rules == null || library.rules.isEmpty()) {
            return true;
        }

        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();

        boolean allowed = false;

        for (VersionJson.Library.Rule rule : library.rules) {
            boolean matches = true;

            if (rule.os != null) {
                if (rule.os.name != null) {
                    String ruleOsName = rule.os.name.toLowerCase();
                    if (ruleOsName.equals("windows") && !osName.contains("win")) {
                        matches = false;
                    } else if (ruleOsName.equals("linux") && !osName.contains("linux")) {
                        matches = false;
                    } else if (ruleOsName.equals("osx") && !osName.contains("mac")) {
                        matches = false;
                    }
                }

                if (rule.os.arch != null && !osArch.contains(rule.os.arch)) {
                    matches = false;
                }
            }

            if (matches) {
                allowed = "allow".equals(rule.action);
            }
        }

        return allowed;
    }

    /**
     * Get native classifier for current OS
     * 
     * @param library Library to check
     * @return native classifier or null
     */
    public static String getNativeClassifier(VersionJson.Library library) {
        if (library.natives == null) {
            return null;
        }

        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("win")) {
            return library.natives.windows;
        } else if (osName.contains("linux")) {
            return library.natives.linux;
        } else if (osName.contains("mac")) {
            return library.natives.osx;
        }

        return null;
    }

    /**
     * Download a file from URL to target path
     * 
     * @param httpClient HTTP client
     * @param url        URL to download from
     * @param targetPath Target file path
     * @throws IOException if download fails
     */
    public static void downloadFile(OkHttpClient httpClient, String url, Path targetPath) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download file: " + response.code());
            }

            Files.createDirectories(targetPath.getParent());

            try (InputStream inputStream = response.body().byteStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Download a file asynchronously
     * 
     * @param httpClient      HTTP client
     * @param url             URL to download from
     * @param targetPath      Target file path
     * @param executorService Executor service for async operation
     * @return CompletableFuture for the download operation
     */
    public static CompletableFuture<Void> downloadFileAsync(OkHttpClient httpClient, String url, Path targetPath,
            ExecutorService executorService) {
        return CompletableFuture.runAsync(() -> {
            try {
                downloadFile(httpClient, url, targetPath);
            } catch (IOException e) {
                logger.error("Failed to download file: " + url, e);
                throw new RuntimeException(e);
            }
        }, executorService);
    }

    /**
     * Build classpath string from libraries
     * 
     * @param config      LaunchConfig
     * @param versionJson Version JSON
     * @return Classpath string
     */
    public static String buildClasspath(LaunchConfig config, VersionJson versionJson) {
        List<String> classpathEntries = new ArrayList<>();

        // Add client jar
        classpathEntries.add(config.getVersionJarPath().toString());

        // Add libraries
        for (VersionJson.Library library : versionJson.libraries) {
            if (!isLibraryAllowed(library)) {
                continue;
            }

            if (library.downloads.artifact != null) {
                Path libraryPath = config.getLibrariesPath().resolve(library.downloads.artifact.path);
                classpathEntries.add(libraryPath.toString());
            }
        }

        return String.join(System.getProperty("path.separator"), classpathEntries);
    }

    /**
     * Validate if a file exists and has correct hash
     * 
     * @param filePath     Path to file
     * @param expectedHash Expected SHA-1 hash
     * @return true if file exists and hash matches
     */
    public static boolean validateFileHash(Path filePath, String expectedHash) {
        if (!Files.exists(filePath)) {
            return false;
        }

        try {
            String actualHash = calculateSha1(filePath);
            return expectedHash.equalsIgnoreCase(actualHash);
        } catch (IOException e) {
            logger.warn("Failed to calculate hash for file: " + filePath, e);
            return false;
        }
    }

    /**
     * Get file size in bytes
     * 
     * @param filePath Path to file
     * @return File size or 0 if file doesn't exist
     */
    public static long getFileSize(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Check if file exists and is not empty
     * 
     * @param filePath Path to file
     * @return true if file exists and is not empty
     */
    public static boolean isValidFile(Path filePath) {
        return Files.exists(filePath) && getFileSize(filePath) > 0;
    }

    /**
     * Ensure parent directories exist for a file
     * 
     * @param filePath Path to file
     * @throws IOException if directory creation fails
     */
    public static void ensureParentDirectories(Path filePath) throws IOException {
        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }
}