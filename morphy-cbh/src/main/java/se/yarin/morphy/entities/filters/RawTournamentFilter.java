package se.yarin.morphy.entities.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.entities.EntityIndexHeader;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.TournamentIndex;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.games.GameHeaderIndex;
import se.yarin.morphy.storage.ItemStorageFilter;
import se.yarin.util.parser.Expr;
import se.yarin.util.parser.Interpreter;
import se.yarin.util.parser.Parser;
import se.yarin.util.parser.Scanner;

import java.nio.ByteBuffer;

public class RawTournamentFilter implements ItemStorageFilter<Tournament> {
    @NotNull
    private final Expr expr;

    public RawTournamentFilter(@NotNull String filterExpression) {
        Scanner scanner = new Scanner(filterExpression);
        this.expr = new Parser(scanner.scanTokens()).parse();
    }

    @Override
    public boolean matches(@NotNull Tournament tournament) {
        // The raw filter is a bit special as the filter can only happen when scanning a range of tournaments
        // It means that it will not work when looking at individual tournaments
        return true;
    }

    @Override
    public boolean matchesSerialized(@NotNull ByteBuffer buf) {
        buf.mark();
        byte[] bytes = new byte[TournamentIndex.SERIALIZED_TOURNAMENT_SIZE];
        buf.get(bytes, 0, bytes.length);
        buf.reset();
        Interpreter interpreter = new Interpreter(bytes);
        return (boolean) interpreter.evaluate(expr);
    }
}