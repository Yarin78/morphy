package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.games.ExtendedGameHeader;
import se.yarin.morphy.games.ExtendedGameHeaderStorage;
import se.yarin.morphy.storage.ItemStorageFilter;
import se.yarin.util.parser.Expr;
import se.yarin.util.parser.Interpreter;
import se.yarin.util.parser.Parser;
import se.yarin.util.parser.Scanner;

import java.nio.ByteBuffer;

public class RawExtendedHeaderFilter implements ItemStorageFilter<ExtendedGameHeader>, GameFilter {
    @NotNull private final Expr expr;
    @NotNull private final String filterExpression;

    public RawExtendedHeaderFilter(@NotNull String filterExpression) {
        Scanner scanner = new Scanner(filterExpression);
        this.filterExpression = filterExpression;
        this.expr = new Parser(scanner.scanTokens()).parse();
    }

    @Override
    public boolean matches(@NotNull ExtendedGameHeader gameHeader) {
        // The raw filter is a bit special as the filter can only happen when scanning a range of games
        // It means that it will not work when looking at individual games
        return true;
    }

    @Override
    public boolean matchesSerialized(@NotNull ByteBuffer buf) {
        buf.mark();
        byte[] bytes = new byte[ExtendedGameHeaderStorage.ExtProlog.DEFAULT_SERIALIZED_ITEM_SIZE];
        // TODO: If the underlying data has a shorter extended game header, this will copy part of the next game header into the buffer
        buf.get(bytes, 0, bytes.length);
        buf.reset();
        Interpreter interpreter = new Interpreter(bytes);
        return (boolean) interpreter.evaluate(expr);
    }

    public @Nullable ItemStorageFilter<ExtendedGameHeader> extendedGameHeaderFilter() { return this; }

    @Override
    public String toString() {
        return "cbj_raw(" + filterExpression + ")";
    }
}
