package se.yarin.morphy.cli;

import picocli.CommandLine;
import se.yarin.cbhlib.Database;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "cb", description = "Performs an operation on a ChessBase file",
        mixinStandardHelpOptions = true,
        subcommands = { Games.class, Players.class})
class ChessBaseCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("A subcommand must be specified; use --help");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ChessBaseCommand()).execute(args);
        System.exit(exitCode);
    }
}

@CommandLine.Command(name = "games", mixinStandardHelpOptions = true)
class Games implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "The ChessBase file to load")
    private File cbhFile;

    @Override
    public Integer call() throws IOException {
        Database db = Database.open(cbhFile);
        System.out.println(db.getHeaderBase().size() + " games");
        db.close();
        return 0;
    }
}

@CommandLine.Command(name = "players", mixinStandardHelpOptions = true)
class Players implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "The ChessBase file to load")
    private File cbhFile;

    @Override
    public Integer call() throws IOException {
        Database db = Database.open(cbhFile);
        System.out.println(db.getPlayerBase().getCount() + " players");
        db.close();
        return 0;
    }
}
