package com.sammwy.mcbootstrap;

import java.util.List;
import java.util.Map;

/**
 * Data model for Minecraft version manifest and version JSON
 */
public class VersionManifest {

    public static class Version {
        public String id;
        public String type;
        public String url;
        public String time;
        public String releaseTime;
        public String sha1;
        public int complianceLevel;
    }

    public static class Latest {
        public String release;
        public String snapshot;
    }

    public Latest latest;
    public List<Version> versions;

    // Version JSON structure
    public static class VersionJson {
        public Arguments arguments;
        public AssetIndex assetIndex;
        public String assets;
        public int complianceLevel;
        public Downloads downloads;
        public String id;
        public List<Library> libraries;
        public Logging logging;
        public String mainClass;
        public String minimumLauncherVersion;
        public String releaseTime;
        public String time;
        public String type;

        public static class Arguments {
            public List<Object> game;
            public List<Object> jvm;
        }

        public static class AssetIndex {
            public String id;
            public String sha1;
            public int size;
            public int totalSize;
            public String url;
        }

        public static class Downloads {
            public Download client;
            public Download client_mappings;
            public Download server;
            public Download server_mappings;

            public static class Download {
                public String sha1;
                public int size;
                public String url;
            }
        }

        public static class Library {
            public Downloads downloads;
            public String name;
            public List<Rule> rules;
            public Natives natives;
            public Extract extract;

            public static class Downloads {
                public Artifact artifact;
                public Map<String, Artifact> classifiers;

                public static class Artifact {
                    public String path;
                    public String sha1;
                    public int size;
                    public String url;
                }
            }

            public static class Rule {
                public String action;
                public Os os;
                public Map<String, Object> features;

                public static class Os {
                    public String name;
                    public String version;
                    public String arch;
                }
            }

            public static class Natives {
                public String linux;
                public String osx;
                public String windows;
            }

            public static class Extract {
                public List<String> exclude;
            }
        }

        public static class Logging {
            public Client client;

            public static class Client {
                public String argument;
                public File file;
                public String type;

                public static class File {
                    public String id;
                    public String sha1;
                    public int size;
                    public String url;
                }
            }
        }
    }

    // Asset index structure
    public static class AssetIndex {
        public Map<String, Asset> objects;

        public static class Asset {
            public String hash;
            public int size;
        }
    }
}