package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public class Instrumentation {

    public class FileStats {

        private final String name;
        private int physicalPageReads;
        private int logicalPageReads;
        private int pageWrites;

        public FileStats(String name) {
            this.name = name;
        }

        public void addPhysicalReads(int count) {
            physicalPageReads += count;
        }

        public void addLogicalReads(int count) {
            logicalPageReads += count;
        }

        public void addWrites(int count) {
            pageWrites += count;
        }

        public String name() {
            return name;
        }

        public int physicalPageReads() {
            return physicalPageReads;
        }

        public int logicalPageReads() {
            return logicalPageReads;
        }

        public int pageWrites() {
            return pageWrites;
        }

        public void clear() {
            physicalPageReads = 0;
            logicalPageReads = 0;
            pageWrites = 0;
        }
    }

    public class ItemStats {
        private final String name;
        private int gets;
        private int getRaws;
        private int puts;
        private int deserializations;
        private int serializations;

        public ItemStats(String name) {
            this.name = name;
        }

        public void addDeserialization(int count) {
            deserializations += count;
        }

        public void addSerialization(int count) {
            serializations += count;
        }

        public void addGet(int count) {
            gets += count;
        }

        public void addPut(int count) {
            puts += count;
        }

        public void addGetRaw(int count) {
            getRaws += count;
        }

        public int deserializations() {
            return deserializations;
        }

        public int serializations() {
            return serializations;
        }

        public int gets() {
            return gets;
        }

        public int puts() {
            return puts;
        }

        public int getGetRaws() {
            return getRaws;
        }

        public void clear() {
            gets = 0;
            getRaws = 0;
            puts = 0;
            deserializations = 0;
            serializations = 0;
        }
    }

    private final Map<String, FileStats> storageStats = new TreeMap<>();
    private final Map<String, ItemStats> itemStats = new TreeMap<>();

    public synchronized FileStats fileStats(@NotNull Path path) {
        String fileName = path.toString();
        int extensionStart = fileName.lastIndexOf(".");
        if (extensionStart < 0) {
            throw new IllegalArgumentException("The file must have an extension");
        }
        return fileStats(fileName.substring(extensionStart + 1));
    }

    public synchronized FileStats fileStats(@NotNull String name) {
        if (storageStats.containsKey(name)) {
            return storageStats.get(name);
        }
        FileStats fileInstrumentation = new FileStats(name);
        storageStats.put(name, fileInstrumentation);
        return fileInstrumentation;
    }

    public synchronized ItemStats itemStats(@NotNull String name) {
        if (itemStats.containsKey(name)) {
            return itemStats.get(name);
        }
        ItemStats serializationInstrumentation = new ItemStats(name);
        itemStats.put(name, serializationInstrumentation);
        return serializationInstrumentation;
    }

    public synchronized void reset() {
        for (FileStats fileStats : storageStats.values()) {
            fileStats.clear();
        }
        for (ItemStats itemStats : itemStats.values()) {
            itemStats.clear();
        }
    }

    public synchronized void show() {
        show(0);
    }

    public synchronized void show(int min) {
        System.err.println();
        System.err.println("File       phyrd   logrd    wrts     ");
        System.err.println("-------------------------------------");
        for (String fileName : storageStats.keySet()) {
            FileStats stats = storageStats.get(fileName);
            if (stats.physicalPageReads >= min || stats.logicalPageReads >= min || stats.pageWrites >= min) {
                System.err.printf("%-8s %7d %7d %7d%n", fileName, stats.physicalPageReads, stats.logicalPageReads, stats.pageWrites);
            }
        }

        System.err.println();

        System.err.println("Item                  get       put     deser       ser     ");
        System.err.println("---------------------------------------------------------");
        for (String name : itemStats.keySet()) {
            ItemStats stats = itemStats.get(name);
            if (stats.gets + stats.getRaws >= min || stats.puts >= min || stats.deserializations >= min || stats.serializations >= min) {
                System.err.printf("%-15s %9d %9d %9d %9d%n", name, stats.gets + stats.getRaws, stats.puts, stats.deserializations, stats.serializations);
            }
        }
    }

}
