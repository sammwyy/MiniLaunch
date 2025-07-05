package com.sammwy.mcbootstrap;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration builder for Minecraft launch parameters
 */
public class LaunchConfig {
    private Path versionJsonPath;
    private Path versionJarPath;
    private Path librariesPath;
    private Path assetsPath;
    private Path mcDir;
    private String username;
    private String versionId;
    private Map<String, String> customJvmArgs;
    private Map<String, String> customGameArgs;
    private int maxMemory = 2048; // MB
    private int minMemory = 512; // MB

    public LaunchConfig() {
        this.customJvmArgs = new HashMap<>();
        this.customGameArgs = new HashMap<>();
    }

    public LaunchConfig versionJson(String path) {
        this.versionJsonPath = Paths.get(path);
        return this;
    }

    public LaunchConfig versionJson(Path path) {
        this.versionJsonPath = path;
        return this;
    }

    public LaunchConfig versionJar(String path) {
        this.versionJarPath = Paths.get(path);
        return this;
    }

    public LaunchConfig versionJar(Path path) {
        this.versionJarPath = path;
        return this;
    }

    public LaunchConfig libraries(String path) {
        this.librariesPath = Paths.get(path);
        return this;
    }

    public LaunchConfig libraries(Path path) {
        this.librariesPath = path;
        return this;
    }

    public LaunchConfig assets(String path) {
        this.assetsPath = Paths.get(path);
        return this;
    }

    public LaunchConfig assets(Path path) {
        this.assetsPath = path;
        return this;
    }

    public LaunchConfig mcDir(String path) {
        this.mcDir = Paths.get(path);
        return this;
    }

    public LaunchConfig mcDir(Path path) {
        this.mcDir = path;
        return this;
    }

    public LaunchConfig username(String username) {
        this.username = username;
        return this;
    }

    public LaunchConfig versionId(String versionId) {
        this.versionId = versionId;
        return this;
    }

    public LaunchConfig maxMemory(int megabytes) {
        this.maxMemory = megabytes;
        return this;
    }

    public LaunchConfig minMemory(int megabytes) {
        this.minMemory = megabytes;
        return this;
    }

    public LaunchConfig addJvmArg(String key, String value) {
        this.customJvmArgs.put(key, value);
        return this;
    }

    public LaunchConfig addGameArg(String key, String value) {
        this.customGameArgs.put(key, value);
        return this;
    }

    // Getters
    public Path getVersionJsonPath() {
        return versionJsonPath;
    }

    public Path getVersionJarPath() {
        return versionJarPath;
    }

    public Path getLibrariesPath() {
        return librariesPath;
    }

    public Path getAssetsPath() {
        return assetsPath;
    }

    public Path getMcDir() {
        return mcDir;
    }

    public String getUsername() {
        return username;
    }

    public String getVersionId() {
        return versionId;
    }

    public Map<String, String> getCustomJvmArgs() {
        return customJvmArgs;
    }

    public Map<String, String> getCustomGameArgs() {
        return customGameArgs;
    }

    public int getMaxMemory() {
        return maxMemory;
    }

    public int getMinMemory() {
        return minMemory;
    }

    // Helper methods for auto-configuration
    public LaunchConfig useDotMinecraft() {
        String userHome = System.getProperty("user.home");
        String osName = System.getProperty("os.name").toLowerCase();

        Path dotMinecraft;
        if (osName.contains("win")) {
            dotMinecraft = Paths.get(userHome, "AppData", "Roaming", ".minecraft");
        } else if (osName.contains("mac")) {
            dotMinecraft = Paths.get(userHome, "Library", "Application Support", "minecraft");
        } else {
            dotMinecraft = Paths.get(userHome, ".minecraft");
        }

        return mcDir(dotMinecraft)
                .libraries(dotMinecraft.resolve("libraries"))
                .assets(dotMinecraft.resolve("assets"));
    }

    public LaunchConfig discoverVersion(String version) {
        if (mcDir == null) {
            throw new IllegalStateException("mcDir must be set before discovering version");
        }

        Path versionsDir = mcDir.resolve("versions").resolve(version);
        return this.versionId(version)
                .versionJson(versionsDir.resolve(version + ".json"))
                .versionJar(versionsDir.resolve(version + ".jar"));
    }
}