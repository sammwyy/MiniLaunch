package com.sammwy.mcbootstrap;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Represents a download session with progress tracking
 */
public class DownloadState {
    private final AtomicInteger totalFiles = new AtomicInteger(0);
    private final AtomicInteger completedFiles = new AtomicInteger(0);
    private final AtomicInteger failedFiles = new AtomicInteger(0);
    private final AtomicLong totalBytes = new AtomicLong(0);
    private final AtomicLong downloadedBytes = new AtomicLong(0);
    private final AtomicReference<String> currentFile = new AtomicReference<>("");
    private final AtomicReference<DownloadStatus> status = new AtomicReference<>(DownloadStatus.INITIALIZING);

    private Consumer<DownloadState> progressCallback;
    private Consumer<String> statusCallback;
    private Consumer<Exception> errorCallback;
    private CompletableFuture<Void> downloadFuture;

    public enum DownloadStatus {
        INITIALIZING,
        DOWNLOADING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public DownloadState() {
    }

    // Progress tracking methods
    public int getTotalFiles() {
        return totalFiles.get();
    }

    public int getCompletedFiles() {
        return completedFiles.get();
    }

    public int getFailedFiles() {
        return failedFiles.get();
    }

    public long getTotalBytes() {
        return totalBytes.get();
    }

    public long getDownloadedBytes() {
        return downloadedBytes.get();
    }

    public String getCurrentFile() {
        return currentFile.get();
    }

    public DownloadStatus getStatus() {
        return status.get();
    }

    public double getProgress() {
        int total = getTotalFiles();
        if (total == 0)
            return 0.0;
        return (double) getCompletedFiles() / total;
    }

    public double getProgressPercentage() {
        return getProgress() * 100.0;
    }

    public double getBytesProgress() {
        long total = getTotalBytes();
        if (total == 0)
            return 0.0;
        return (double) getDownloadedBytes() / total;
    }

    public double getBytesProgressPercentage() {
        return getBytesProgress() * 100.0;
    }

    public boolean isCompleted() {
        return status.get() == DownloadStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status.get() == DownloadStatus.FAILED;
    }

    public boolean isDownloading() {
        return status.get() == DownloadStatus.DOWNLOADING;
    }

    public boolean isCancelled() {
        return status.get() == DownloadStatus.CANCELLED;
    }

    // Internal methods for updating progress
    void setTotalFiles(int total) {
        totalFiles.set(total);
        notifyProgress();
    }

    void incrementCompletedFiles() {
        completedFiles.incrementAndGet();
        notifyProgress();
    }

    void incrementFailedFiles() {
        failedFiles.incrementAndGet();
        notifyProgress();
    }

    void setTotalBytes(long total) {
        totalBytes.set(total);
        notifyProgress();
    }

    void addDownloadedBytes(long bytes) {
        downloadedBytes.addAndGet(bytes);
        notifyProgress();
    }

    void setCurrentFile(String file) {
        currentFile.set(file);
        notifyProgress();
    }

    void setStatus(DownloadStatus newStatus) {
        status.set(newStatus);
        if (statusCallback != null) {
            statusCallback.accept(newStatus.name());
        }
        notifyProgress();
    }

    void setDownloadFuture(CompletableFuture<Void> future) {
        this.downloadFuture = future;
    }

    // Callback methods
    public DownloadState onProgress(Consumer<DownloadState> callback) {
        this.progressCallback = callback;
        return this;
    }

    public DownloadState onStatus(Consumer<String> callback) {
        this.statusCallback = callback;
        return this;
    }

    public DownloadState onError(Consumer<Exception> callback) {
        this.errorCallback = callback;
        return this;
    }

    private void notifyProgress() {
        if (progressCallback != null) {
            progressCallback.accept(this);
        }
    }

    void notifyError(Exception error) {
        if (errorCallback != null) {
            errorCallback.accept(error);
        }
    }

    // Control methods
    public CompletableFuture<Void> waitForCompletion() {
        return downloadFuture != null ? downloadFuture : CompletableFuture.completedFuture(null);
    }

    public void cancel() {
        if (downloadFuture != null && !downloadFuture.isDone()) {
            downloadFuture.cancel(true);
            setStatus(DownloadStatus.CANCELLED);
        }
    }

    // Utility methods
    public String getFormattedProgress() {
        return String.format("%d/%d files (%.1f%%)",
                getCompletedFiles(), getTotalFiles(), getProgressPercentage());
    }

    public String getFormattedBytesProgress() {
        return String.format("%s/%s (%.1f%%)",
                formatBytes(getDownloadedBytes()),
                formatBytes(getTotalBytes()),
                getBytesProgressPercentage());
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    @Override
    public String toString() {
        return String.format("MCDownload{status=%s, progress=%s, current='%s'}",
                getStatus(), getFormattedProgress(), getCurrentFile());
    }
}