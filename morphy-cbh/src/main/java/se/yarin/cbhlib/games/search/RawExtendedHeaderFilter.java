package se.yarin.cbhlib.games.search;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.games.SerializedExtendedGameHeaderFilter;
import se.yarin.cbhlib.util.parser.Expr;
import se.yarin.cbhlib.util.parser.Interpreter;
import se.yarin.cbhlib.util.parser.Parser;
import se.yarin.cbhlib.util.parser.Scanner;

public class RawExtendedHeaderFilter extends SearchFilterBase implements SerializedExtendedGameHeaderFilter {
    private final Expr expr;

    public RawExtendedHeaderFilter(Database database, String filterExpression) {
        super(database);

        Scanner scanner = new Scanner(filterExpression);
        this.expr = new Parser(scanner.scanTokens()).parse();
    }

    @Override
    public boolean matches(byte[] serializedGameHeader) {
        Interpreter interpreter = new Interpreter(serializedGameHeader);
        return (boolean) interpreter.evaluate(expr);
    }

    @Override
    public boolean matches(Game game) {
        // The raw filter is a bit special as the filter can only happen when scanning a range of games
        // It means that it will not work when looking at individual games
        // Make sure to not match also in case the cbj file doesn't even exist (will cause weird errors)
        return game.getDatabase().getExtendedHeaderBase().canGetRaw();
    }
}
