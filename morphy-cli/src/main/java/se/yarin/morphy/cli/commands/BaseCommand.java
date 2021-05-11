package se.yarin.morphy.cli.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.stream.Stream;

public abstract class BaseCommand {
    @CommandLine.Parameters(index = "0", description = "The ChessBase file to load, or a folder to search in multiple databases, or a txt file containing a list of database to load")
    private File file;

    @CommandLine.Option(names = {"-R", "--recursive"}, description = "Scan the folder recursively for databases")
    private boolean recursive = false;

    @CommandLine.Option(names = "-v", description = "Output info logging; use twice for debug logging")
    private boolean[] verbose;

    @CommandLine.Option(names = "--iostats", description = "Show instrumentation statistics")
    private boolean iostats = false;

    protected void setupGlobalOptions() {
        if (verbose != null) {
            String level = verbose.length == 1 ? "info" : "debug";
            org.apache.logging.log4j.core.LoggerContext context = ((org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false));
            ConfigurationSource configurationSource = ConfigurationSource.fromResource("log4j2-" + level + ".xml", null);
            Configuration configuration = ConfigurationFactory.getInstance().getConfiguration(context, configurationSource);
            context.reconfigure(configuration);
        }
        Locale.setDefault(Locale.US);
    }

    public int verboseLevel() {
        return verbose == null ? 0 : verbose.length;
    }

    public boolean showInstrumentation() { return iostats; }

    protected Stream<File> getDatabaseStream() throws IOException {
        if (file.isDirectory()) {
            return Files.walk(file.toPath(), recursive ? 30 : 1)
                    .filter(path -> path.toString().toLowerCase().endsWith(".cbh"))
                    .filter(path -> !path.getFileName().toString().startsWith("._"))
                    .map(Path::toFile);
        }

        if (!file.isFile()) {
            throw new IllegalArgumentException("Database does not exist: " + file);
        }

        if (file.getPath().toLowerCase().endsWith(".txt")) {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            ArrayList<File> files = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                files.add(new File(line));
            }
            return files.stream();
        }

        return Stream.of(file);
    }
}
