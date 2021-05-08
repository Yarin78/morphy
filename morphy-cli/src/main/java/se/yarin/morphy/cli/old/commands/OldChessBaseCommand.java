package se.yarin.morphy.cli.old.commands;

import picocli.CommandLine;

@CommandLine.Command(name = "old", description = "Performs an operation on a ChessBase file (old)",
        mixinStandardHelpOptions = true,
        subcommands = { Games.class, Players.class, Tournaments.class, Check.class})
public class OldChessBaseCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("A subcommand must be specified; use --help");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new OldChessBaseCommand()).execute(args);
        System.exit(exitCode);
    }
}

