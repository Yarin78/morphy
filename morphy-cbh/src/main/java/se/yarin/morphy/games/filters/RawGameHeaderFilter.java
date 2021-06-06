package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.games.GameHeaderIndex;
import se.yarin.morphy.storage.ItemStorageFilter;
import se.yarin.util.parser.Expr;
import se.yarin.util.parser.Interpreter;
import se.yarin.util.parser.Parser;
import se.yarin.util.parser.Scanner;

import java.nio.ByteBuffer;

public class RawGameHeaderFilter implements ItemStorageFilter<GameHeader>, GameFilter {
    @NotNull private final Expr expr;
    @NotNull private final String filterExpression;

    public RawGameHeaderFilter(@NotNull String filterExpression) {
        Scanner scanner = new Scanner(filterExpression);
        this.filterExpression = filterExpression;
        this.expr = new Parser(scanner.scanTokens()).parse();
    }

    @Override
    public boolean matches(@NotNull GameHeader gameHeader) {
        // The raw filter is a bit special as the filter can only happen when scanning a range of games
        // It means that it will not work when looking at individual games
        return true;
    }

    @Override
    public boolean matchesSerialized(@NotNull ByteBuffer buf) {
        buf.mark();
        byte[] bytes = new byte[GameHeaderIndex.Prolog.DEFAULT_SERIALIZED_ITEM_SIZE];
        buf.get(bytes, 0, bytes.length);
        buf.reset();
        Interpreter interpreter = new Interpreter(bytes);
        return (boolean) interpreter.evaluate(expr);
    }

    public @Nullable ItemStorageFilter<GameHeader> gameHeaderFilter() { return this; }

    @Override
    public String toString() {
        return "cbh_raw(" + filterExpression + ")";
    }
}
