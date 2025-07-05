package com.sammwy.minilaunch;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import com.sammwy.mcbootstrap.DownloadState;
import com.sammwy.mcbootstrap.LaunchConfig;
import com.sammwy.mcbootstrap.LaunchState;
import com.sammwy.mcbootstrap.MCBootstrap;
import com.sammwy.mcbootstrap.utils.MCUtils;

public class MiniLaunch extends JFrame {
    private static final Color BACKGROUND_COLOR = new Color(32, 34, 37);
    private static final Color CARD_COLOR = new Color(54, 57, 63);
    private static final Color PRIMARY_COLOR = new Color(88, 101, 242);
    private static final Color SUCCESS_COLOR = new Color(67, 181, 129);
    private static final Color ERROR_COLOR = new Color(237, 66, 69);
    private static final Color TEXT_COLOR = new Color(220, 221, 222);
    private static final Color SECONDARY_TEXT_COLOR = new Color(142, 146, 151);

    private static final String CONFIG_FILE = "launcher_config.properties";

    private JTextField usernameField;
    private JComboBox<VersionItem> versionComboBox;
    private JSlider memorySlider;
    private JLabel memoryLabel;
    private JTextField minecraftDirField;
    private JButton downloadButton;
    private JButton launchButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel detailsLabel;
    private JTextArea logArea;

    private MCBootstrap bootstrap;
    private LaunchConfig config;
    private boolean isInitialized = false;
    private boolean isDownloading = false;
    private boolean isLaunching = false;

    private Properties launcherConfig;

    // Wrapper class for version items in combo box
    private static class VersionItem {
        private final MCUtils.MinecraftVersion version;
        private final String displayText;

        public VersionItem(MCUtils.MinecraftVersion version) {
            this.version = version;
            this.displayText = version.id + " (" + version.type + ")" + (version.isLocal ? " [Local]" : "");
        }

        public MCUtils.MinecraftVersion getVersion() {
            return version;
        }

        @Override
        public String toString() {
            return displayText;
        }
    }

    public MiniLaunch() {
        loadConfig();
        initializeGUI();
        setupEventHandlers();
        autoInitialize();
    }

    private void loadConfig() {
        launcherConfig = new Properties();
        File configFile = new File(CONFIG_FILE);

        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                launcherConfig.load(fis);
                logMessage("Configuration loaded from " + CONFIG_FILE);
            } catch (IOException e) {
                logMessage("Error loading configuration: " + e.getMessage());
            }
        } else {
            // Set default values
            launcherConfig.setProperty("username", "Player");
            launcherConfig.setProperty("memory", "2048");
            launcherConfig.setProperty("minecraftDir", getDefaultMinecraftDir());
            logMessage("Using default configuration");
        }
    }

    private void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            launcherConfig.setProperty("username", usernameField.getText().trim());
            launcherConfig.setProperty("memory", String.valueOf(memorySlider.getValue()));
            launcherConfig.setProperty("minecraftDir", minecraftDirField.getText());

            // Save selected version if any
            VersionItem selectedVersion = (VersionItem) versionComboBox.getSelectedItem();
            if (selectedVersion != null) {
                launcherConfig.setProperty("lastVersion", selectedVersion.getVersion().id);
            }

            launcherConfig.store(fos, "MiniLaunch Configuration");
            logMessage("Configuration saved to " + CONFIG_FILE);
        } catch (IOException e) {
            logMessage("Error saving configuration: " + e.getMessage());
        }
    }

    private void initializeGUI() {
        setTitle("MiniLaunch");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 700);
        setLocationRelativeTo(null);
        setResizable(false);

        // Set dark theme
        getContentPane().setBackground(BACKGROUND_COLOR);

        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(BACKGROUND_COLOR);
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Header
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Content
        JPanel contentPanel = createContentPanel();
        mainPanel.add(contentPanel, BorderLayout.CENTER);

        // Footer
        JPanel footerPanel = createFooterPanel();
        mainPanel.add(footerPanel, BorderLayout.SOUTH);

        add(mainPanel);

        // Window closing handler
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveConfig();
                cleanup();
            }
        });
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BACKGROUND_COLOR);
        panel.setBorder(new EmptyBorder(0, 0, 20, 0));

        JLabel titleLabel = new JLabel("MiniLaunch");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        titleLabel.setForeground(TEXT_COLOR);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        panel.add(titleLabel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createContentPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BACKGROUND_COLOR);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Configuration card
        JPanel configCard = createConfigCard();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        panel.add(configCard, gbc);

        // Status card
        JPanel statusCard = createStatusCard();
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(statusCard, gbc);

        // Action buttons
        JPanel actionPanel = createActionPanel();
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(actionPanel, gbc);

        // Log area
        JPanel logPanel = createLogPanel();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(logPanel, gbc);

        return panel;
    }

    private JPanel createConfigCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(CARD_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(47, 49, 54), 1),
                new EmptyBorder(15, 15, 15, 15)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Username
        gbc.gridx = 0;
        gbc.gridy = 0;
        card.add(createLabel("Username:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        String savedUsername = launcherConfig.getProperty("username", "Player");
        usernameField = createTextField(savedUsername);
        card.add(usernameField, gbc);

        // Version
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        card.add(createLabel("Version:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        versionComboBox = createVersionComboBox();
        card.add(versionComboBox, gbc);

        // Memory
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        card.add(createLabel("Memory:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel memoryPanel = createMemoryPanel();
        card.add(memoryPanel, gbc);

        // Minecraft Directory
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        card.add(createLabel("MC Directory:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        String savedMcDir = launcherConfig.getProperty("minecraftDir", getDefaultMinecraftDir());
        minecraftDirField = createTextField(savedMcDir);
        card.add(minecraftDirField, gbc);

        return card;
    }

    private JPanel createStatusCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(CARD_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(47, 49, 54), 1),
                new EmptyBorder(15, 15, 15, 15)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Status
        gbc.gridx = 0;
        gbc.gridy = 0;
        card.add(createLabel("Status:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        statusLabel = createLabel("Initializing...");
        statusLabel.setForeground(PRIMARY_COLOR);
        card.add(statusLabel, gbc);

        // Details
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        card.add(createLabel("Details:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        detailsLabel = createLabel("Loading versions...");
        detailsLabel.setForeground(SECONDARY_TEXT_COLOR);
        card.add(detailsLabel, gbc);

        // Progress bar
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Initializing...");
        progressBar.setBackground(CARD_COLOR);
        progressBar.setForeground(PRIMARY_COLOR);
        progressBar.setIndeterminate(true);
        card.add(progressBar, gbc);

        return card;
    }

    private JPanel createActionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        panel.setBackground(BACKGROUND_COLOR);

        downloadButton = createButton("Download", PRIMARY_COLOR);
        launchButton = createButton("Launch", SUCCESS_COLOR);

        downloadButton.setEnabled(false);
        launchButton.setEnabled(false);

        panel.add(downloadButton);
        panel.add(launchButton);

        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BACKGROUND_COLOR);
        panel.setBorder(new EmptyBorder(10, 0, 0, 0));

        JLabel logLabel = createLabel("Log:");
        panel.add(logLabel, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(CARD_COLOR);
        logArea.setForeground(TEXT_COLOR);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBorder(new EmptyBorder(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(0, 150));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(47, 49, 54), 1));

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createFooterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        panel.setBackground(BACKGROUND_COLOR);
        panel.setBorder(new EmptyBorder(20, 0, 0, 0));

        JLabel footerLabel = new JLabel("MiniLaunch v1.0 (by Sammwy)");
        footerLabel.setForeground(SECONDARY_TEXT_COLOR);
        footerLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

        panel.add(footerLabel);

        return panel;
    }

    private JPanel createMemoryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(CARD_COLOR);

        int savedMemory = Integer.parseInt(launcherConfig.getProperty("memory", "2048"));
        memorySlider = new JSlider(512, 8192, savedMemory);
        memorySlider.setBackground(CARD_COLOR);
        memorySlider.setForeground(TEXT_COLOR);
        memorySlider.setMajorTickSpacing(1024);
        memorySlider.setMinorTickSpacing(512);
        memorySlider.setPaintTicks(true);

        memoryLabel = createLabel(savedMemory + " MB");
        memoryLabel.setHorizontalAlignment(SwingConstants.CENTER);
        memoryLabel.setPreferredSize(new Dimension(80, 20));

        memorySlider.addChangeListener(e -> {
            int value = memorySlider.getValue();
            memoryLabel.setText(value + " MB");
        });

        panel.add(memorySlider, BorderLayout.CENTER);
        panel.add(memoryLabel, BorderLayout.EAST);

        return panel;
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT_COLOR);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        return label;
    }

    private JTextField createTextField(String defaultValue) {
        JTextField field = new JTextField(defaultValue);
        field.setBackground(BACKGROUND_COLOR);
        field.setForeground(TEXT_COLOR);
        field.setCaretColor(TEXT_COLOR);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(47, 49, 54), 1),
                new EmptyBorder(5, 10, 5, 10)));
        return field;
    }

    private JComboBox<VersionItem> createVersionComboBox() {
        JComboBox<VersionItem> combo = new JComboBox<>();
        combo.setBackground(BACKGROUND_COLOR);
        combo.setForeground(TEXT_COLOR);
        combo.setBorder(BorderFactory.createLineBorder(new Color(47, 49, 54), 1));
        combo.addItem(
                new VersionItem(MCUtils.MinecraftVersion.dummy()));
        combo.setEnabled(false);
        return combo;
    }

    private JButton createButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Hover effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(color.brighter());
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(color);
            }
        });

        return button;
    }

    private void setupEventHandlers() {
        downloadButton.addActionListener(e -> downloadFiles());
        launchButton.addActionListener(e -> launchMinecraft());

        // Save config when username changes
        usernameField.addActionListener(e -> saveConfig());

        // Save config when memory changes
        memorySlider.addChangeListener(e -> {
            if (!memorySlider.getValueIsAdjusting()) {
                saveConfig();
            }
        });

        // Save config when minecraft directory changes
        minecraftDirField.addActionListener(e -> saveConfig());
    }

    private void autoInitialize() {
        logMessage("Starting auto-initialization...");
        loadVersions();
    }

    private void loadVersions() {
        SwingUtilities.invokeLater(() -> {
            versionComboBox.removeAllItems();
            versionComboBox.addItem(
                    new VersionItem(MCUtils.MinecraftVersion.dummy()));
            versionComboBox.setEnabled(false);
        });

        String mcDir = minecraftDirField.getText();
        if (mcDir.isEmpty()) {
            mcDir = getDefaultMinecraftDir();
        }

        MCUtils.getAvailableVersions(Paths.get(mcDir))
                .thenAccept(versions -> {
                    SwingUtilities.invokeLater(() -> {
                        // Sort versions by releaseTime (newest first)
                        versions.sort((v1, v2) -> v2.releaseTime.compareTo(v1.releaseTime));

                        versionComboBox.removeAllItems();
                        DefaultComboBoxModel<VersionItem> model = new DefaultComboBoxModel<>();

                        for (MCUtils.MinecraftVersion version : versions) {
                            model.addElement(new VersionItem(version));
                        }

                        versionComboBox.setModel(model);
                        versionComboBox.setEnabled(true);

                        // Select the last used version or the first one
                        String lastVersion = launcherConfig.getProperty("lastVersion");
                        if (lastVersion != null) {
                            for (int i = 0; i < versionComboBox.getItemCount(); i++) {
                                VersionItem item = versionComboBox.getItemAt(i);
                                if (item.getVersion().id.equals(lastVersion)) {
                                    versionComboBox.setSelectedIndex(i);
                                    break;
                                }
                            }
                        } else if (versionComboBox.getItemCount() > 0) {
                            versionComboBox.setSelectedIndex(0);
                        }

                        // Auto-initialize launcher
                        initializeLauncher();
                    });
                })
                .exceptionally(throwable -> {
                    SwingUtilities.invokeLater(() -> {
                        versionComboBox.removeAllItems();
                        versionComboBox.addItem(new VersionItem(MCUtils.MinecraftVersion.dummy()));
                        versionComboBox.setEnabled(false);
                        logMessage("Error loading versions: " + throwable.getMessage());

                        statusLabel.setText("Error");
                        statusLabel.setForeground(ERROR_COLOR);
                        detailsLabel.setText("Failed to load versions");
                        progressBar.setIndeterminate(false);
                        progressBar.setString("Error");
                    });
                    return null;
                });
    }

    private void initializeLauncher() {
        if (isInitialized) {
            return;
        }

        progressBar.setIndeterminate(true);
        progressBar.setString("Initializing...");
        statusLabel.setText("Initializing");
        statusLabel.setForeground(PRIMARY_COLOR);

        logMessage("Initializing launcher...");

        // Create config
        VersionItem selectedVersionItem = (VersionItem) versionComboBox.getSelectedItem();
        if (selectedVersionItem == null || selectedVersionItem.getVersion().id.contains("Loading")
                || selectedVersionItem.getVersion().id.contains("Error")) {
            logMessage("Error: No valid version selected");
            statusLabel.setText("Error");
            statusLabel.setForeground(ERROR_COLOR);
            detailsLabel.setText("No valid version selected");
            progressBar.setIndeterminate(false);
            progressBar.setString("Error");
            return;
        }

        MCUtils.MinecraftVersion selectedVersion = selectedVersionItem.getVersion();

        config = new LaunchConfig()
                .username(usernameField.getText().trim())
                .mcDir(minecraftDirField.getText())
                .libraries(Paths.get(minecraftDirField.getText(), "libraries"))
                .assets(Paths.get(minecraftDirField.getText(), "assets"))
                .discoverVersion(selectedVersion.id)
                .maxMemory(memorySlider.getValue())
                .minMemory(512);

        bootstrap = new MCBootstrap(config);

        CompletableFuture.runAsync(() -> {
            try {
                bootstrap.init().join();
                LaunchState state = bootstrap.state();

                SwingUtilities.invokeLater(() -> {
                    isInitialized = true;
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    progressBar.setString("Initialized");
                    statusLabel.setText("Initialized");
                    statusLabel.setForeground(SUCCESS_COLOR);

                    if (state.needsDownload()) {
                        detailsLabel.setText("Missing " + state.getTotalMissingFiles() + " files");
                        downloadButton.setEnabled(true);
                        logMessage("Initialization complete. Missing files need to be downloaded.");
                    } else {
                        detailsLabel.setText("Ready to launch");
                        launchButton.setEnabled(true);
                        logMessage("Initialization complete. Ready to launch!");
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    logMessage("Error during initialization: " + e.getMessage());
                    statusLabel.setText("Error");
                    statusLabel.setForeground(ERROR_COLOR);
                    detailsLabel.setText(e.getMessage());
                    progressBar.setIndeterminate(false);
                    progressBar.setString("Error");
                });
            }
        });
    }

    private void downloadFiles() {
        if (!isInitialized || isDownloading) {
            return;
        }

        isDownloading = true;
        downloadButton.setEnabled(false);
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        progressBar.setString("Downloading...");
        statusLabel.setText("Downloading");
        statusLabel.setForeground(PRIMARY_COLOR);

        logMessage("Starting download...");

        CompletableFuture.runAsync(() -> {
            try {
                DownloadState downloadState = bootstrap.download().join();

                downloadState.onProgress(progress -> {
                    SwingUtilities.invokeLater(() -> {
                        if (progress.getTotalFiles() > 0) {
                            int percentage = (int) progress.getProgressPercentage();
                            progressBar.setValue(percentage);
                            progressBar.setString(String.format("Downloading... %d%%", percentage));
                            detailsLabel.setText(String.format("%d/%d files",
                                    progress.getCompletedFiles(), progress.getTotalFiles()));
                        }
                    });
                });

                downloadState.waitForCompletion().join();

                SwingUtilities.invokeLater(() -> {
                    isDownloading = false;
                    if (downloadState.isCompleted()) {
                        progressBar.setValue(100);
                        progressBar.setString("Download complete");
                        statusLabel.setText("Ready");
                        statusLabel.setForeground(SUCCESS_COLOR);
                        detailsLabel.setText("Ready to launch");
                        launchButton.setEnabled(true);
                        logMessage("Download completed successfully!");
                    } else {
                        progressBar.setString("Download failed");
                        statusLabel.setText("Error");
                        statusLabel.setForeground(ERROR_COLOR);
                        detailsLabel.setText("Download failed");
                        downloadButton.setEnabled(true);
                        logMessage("Download failed or was cancelled.");
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    isDownloading = false;
                    logMessage("Error during download: " + e.getMessage());
                    statusLabel.setText("Error");
                    statusLabel.setForeground(ERROR_COLOR);
                    detailsLabel.setText(e.getMessage());
                    downloadButton.setEnabled(true);
                });
            }
        });
    }

    private void launchMinecraft() {
        if (!isInitialized || isLaunching) {
            return;
        }

        // Save config before launching
        saveConfig();

        isLaunching = true;
        launchButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setString("Launching...");
        statusLabel.setText("Launching");
        statusLabel.setForeground(PRIMARY_COLOR);

        logMessage("Launching Minecraft...");

        CompletableFuture.runAsync(() -> {
            try {
                Process process = bootstrap.run().join();

                SwingUtilities.invokeLater(() -> {
                    isLaunching = false;
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    progressBar.setString("Launched");
                    statusLabel.setText("Running");
                    statusLabel.setForeground(SUCCESS_COLOR);
                    detailsLabel.setText("PID: " + process.pid());
                    launchButton.setEnabled(true);
                    logMessage("Minecraft launched successfully with PID: " + process.pid());
                });

                // Monitor process in background
                new Thread(() -> {
                    try {
                        int exitCode = process.waitFor();
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("Stopped");
                            statusLabel.setForeground(SECONDARY_TEXT_COLOR);
                            detailsLabel.setText("Exit code: " + exitCode);
                            progressBar.setString("Stopped");
                            logMessage("Minecraft exited with code: " + exitCode);
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    isLaunching = false;
                    logMessage("Error during launch: " + e.getMessage());
                    statusLabel.setText("Error");
                    statusLabel.setForeground(ERROR_COLOR);
                    detailsLabel.setText(e.getMessage());
                    launchButton.setEnabled(true);
                });
            }
        });
    }

    private void logMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = java.time.LocalTime.now().toString().substring(0, 8);
            String logEntry = "[" + timestamp + "] " + message + "\n";
            if (logArea != null) {
                logArea.append(logEntry);
                logArea.setCaretPosition(logArea.getDocument().getLength());
            } else {
                System.out.println(logEntry);
            }
        });
    }

    private String getDefaultMinecraftDir() {
        String userHome = System.getProperty("user.home");
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("win")) {
            return userHome + "\\AppData\\Roaming\\.minecraft";
        } else if (osName.contains("mac")) {
            return userHome + "/Library/Application Support/minecraft";
        } else {
            return userHome + "/.minecraft";
        }
    }

    private void cleanup() {
        if (bootstrap != null) {
            bootstrap.close();
        }
        MCUtils.shutdown();
    }

    public static void main(String[] args) {
        // Set system look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Ignore
        }

        SwingUtilities.invokeLater(() -> {
            new MiniLaunch().setVisible(true);
        });
    }
}