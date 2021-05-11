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
    }

    public class SerializationStats {
        private final String name;
        private int deserializations;
        private int serializations;

        public SerializationStats(String name) {
            this.name = name;
        }


        public void addDeserialization(int count) {
            deserializations += count;
        }

        public void addSerialization(int count) {
            serializations += count;
        }

        public int deserializations() {
            return deserializations;
        }

        public int serializations() {
            return serializations;
        }
    }

    private final Map<String, FileStats> fileStats = new TreeMap<>();
    private final Map<String, SerializationStats> serializationStats = new TreeMap<>();

    public synchronized FileStats fileStats(@NotNull Path path) {
        String fileName = path.toString();
        int extensionStart = fileName.lastIndexOf(".");
        if (extensionStart < 0) {
            throw new IllegalArgumentException("The file must have an extension");
        }
        return fileStats(fileName.substring(extensionStart + 1));
    }

    public synchronized FileStats fileStats(@NotNull String name) {
        if (fileStats.containsKey(name)) {
            return fileStats.get(name);
        }
        FileStats fileInstrumentation = new FileStats(name);
        fileStats.put(name, fileInstrumentation);
        return fileInstrumentation;
    }

    public synchronized SerializationStats serializationStats(@NotNull String name) {
        if (serializationStats.containsKey(name)) {
            return serializationStats.get(name);
        }
        SerializationStats serializationInstrumentation = new SerializationStats(name);
        serializationStats.put(name, serializationInstrumentation);
        return serializationInstrumentation;
    }

    public synchronized void show() {
        System.err.println();
        System.err.println("File       phyrd   logrd    wrts     ");
        System.err.println("-------------------------------------");
        for (String fileName : fileStats.keySet()) {
            FileStats stats = fileStats.get(fileName);
            System.err.printf("%-8s %7d %7d %7d%n", fileName, stats.physicalPageReads, stats.logicalPageReads, stats.pageWrites);
        }

        System.err.println();

        System.err.println("Item                  ser     deser     ");
        System.err.println("-------------------------------------");
        for (String name : serializationStats.keySet()) {
            SerializationStats stats = serializationStats.get(name);
            System.err.printf("%-15s %9d %9d%n", name, stats.serializations, stats.deserializations);
        }
    }

}
