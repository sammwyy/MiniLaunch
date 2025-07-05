package com.sammwy.mcbootstrap;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the current state of the Minecraft launcher
 */
public class LaunchState {
    private boolean initialized = false;
    private boolean canLaunch = false;
    private List<String> missingFiles = new ArrayList<>();
    private List<String> missingLibraries = new ArrayList<>();
    private List<String> missingAssets = new ArrayList<>();
    private String statusMessage = "Not initialized";
    private Exception lastError;

    public LaunchState() {
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public boolean canLaunch() {
        return canLaunch;
    }

    public void setCanLaunch(boolean canLaunch) {
        this.canLaunch = canLaunch;
    }

    public List<String> getMissingFiles() {
        return new ArrayList<>(missingFiles);
    }

    public void addMissingFile(String file) {
        if (!missingFiles.contains(file)) {
            missingFiles.add(file);
        }
    }

    public void removeMissingFile(String file) {
        missingFiles.remove(file);
    }

    public void clearMissingFiles() {
        missingFiles.clear();
    }

    public List<String> getMissingLibraries() {
        return new ArrayList<>(missingLibraries);
    }

    public void addMissingLibrary(String library) {
        if (!missingLibraries.contains(library)) {
            missingLibraries.add(library);
        }
    }

    public void removeMissingLibrary(String library) {
        missingLibraries.remove(library);
    }

    public void clearMissingLibraries() {
        missingLibraries.clear();
    }

    public List<String> getMissingAssets() {
        return new ArrayList<>(missingAssets);
    }

    public void addMissingAsset(String asset) {
        if (!missingAssets.contains(asset)) {
            missingAssets.add(asset);
        }
    }

    public void removeMissingAsset(String asset) {
        missingAssets.remove(asset);
    }

    public void clearMissingAssets() {
        missingAssets.clear();
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public Exception getLastError() {
        return lastError;
    }

    public void setLastError(Exception lastError) {
        this.lastError = lastError;
    }

    public boolean hasError() {
        return lastError != null;
    }

    public int getTotalMissingFiles() {
        return missingFiles.size() + missingLibraries.size() + missingAssets.size();
    }

    public boolean needsDownload() {
        return getTotalMissingFiles() > 0;
    }

    public void reset() {
        initialized = false;
        canLaunch = false;
        missingFiles.clear();
        missingLibraries.clear();
        missingAssets.clear();
        statusMessage = "Not initialized";
        lastError = null;
    }

    @Override
    public String toString() {
        return String.format("MCLaunchState{initialized=%s, canLaunch=%s, missingFiles=%d, status='%s'}",
                initialized, canLaunch, getTotalMissingFiles(), statusMessage);
    }
}
