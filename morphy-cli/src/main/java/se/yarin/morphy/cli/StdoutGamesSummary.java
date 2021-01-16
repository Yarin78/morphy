package se.yarin.morphy.cli;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.yarin.cbhlib.entities.PlayerEntity;
import se.yarin.cbhlib.exceptions.ChessBaseException;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.search.GameSearcher;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameResult;
import se.yarin.morphy.cli.columns.GameColumn;

import java.net.CookieHandler;
import java.util.*;

public class StdoutGamesSummary implements GameConsumer {
    private static final Logger log = LogManager.getLogger();

    private final boolean showTotal;
    private final Collection<GameColumn> columns;

    private static final String DEFAULT_COLUMNS = "id,name,rating,result,moves,eco,tournament,date";

    public StdoutGamesSummary(boolean showTotal) {
        this(showTotal, parseColumns(DEFAULT_COLUMNS));
    }

    public StdoutGamesSummary(boolean showTotal, Collection<GameColumn> columns) {
        this.showTotal = showTotal;
        this.columns = columns;

        if (this.columns.size() == 0) {
            throw new IllegalArgumentException("No columns specified");
        }
    }

    public static List<GameColumn> parseColumns(String columnSpec) {
        HashMap<String, Integer> specColumns = new HashMap<>();
        boolean isRelative = true;
        for (String col : columnSpec.split(",")) {
            String strippedCol = col.replaceAll("^[\\+\\-]", "").strip();
            int val;
            if (col.startsWith("+")) {
                val = 1;
            } else if (col.startsWith("-")) {
                val = -1;
            } else {
                isRelative = false;
                val = 1;
            }
            if (strippedCol.endsWith("*")) {
                String prefix = strippedCol.substring(0, strippedCol.length() - 1);
                for (GameColumn gc : GameColumn.ALL) {
                    if (gc.getId().startsWith(prefix)) {
                        specColumns.put(gc.getId(), val);
                    }
                }
            } else {
                specColumns.put(strippedCol, val);
            }
        }
        if (isRelative) {
            for (String defaultCol : DEFAULT_COLUMNS.split(",")) {
                if (specColumns.getOrDefault(defaultCol, 1) != -1) {
                    specColumns.put(defaultCol, 1);
                }
            }
        }

        ArrayList<GameColumn> visibleColumns = new ArrayList<>();
        HashSet<String> usedCols = new HashSet<>();
        for (GameColumn gameColumn : GameColumn.ALL) {
            if (specColumns.getOrDefault(gameColumn.getId(), 0) == 1) {
                visibleColumns.add(gameColumn);
            }
            if (specColumns.containsKey(gameColumn.getId())) {
                usedCols.add(gameColumn.getId());
            }
        }

        for (String col : specColumns.keySet()) {
            if (!usedCols.contains(col)) {
                log.warn("Unknown column: " + col);
                log.warn("Available columns: " + GameColumn.allColumnsString());
            }
        }

        return visibleColumns;
    }

    @Override
    public void init() {
        outputHeader();
    }

    @Override
    public void done(GameSearcher.SearchResult result) {
        if (showTotal) {
            if (result.getTotalHits() == 0) {
                System.out.printf("No hits (%.2f s)%n", result.getElapsedTime() / 1000.0);
            } else {
                System.out.println();
                if (result.getConsumedHits() < result.getTotalHits()) {
                    System.out.printf("%d out of %d hits displayed (%.2f s)%n", result.getHits().size(), result.getTotalHits(), result.getElapsedTime() / 1000.0);
                } else {
                    System.out.printf("%d hits  (%.2f s)%n", result.getTotalHits(), result.getElapsedTime() / 1000.0);
                }
            }
        }
    }

    private void outputHeader() {
        StringBuilder sb = new StringBuilder();
        GameColumn lastColumn = null;
        for (GameColumn column : columns) {
            int marginBefore = Math.max(column.marginLeft(), lastColumn == null ? 0 : lastColumn.marginRight());
            sb.append(" ".repeat(marginBefore));
            lastColumn = column;
            sb.append(column.getHeader());
            sb.append(" ".repeat(column.width() - column.getHeader().length()));
        }
        if (lastColumn != null) {
            sb.append(" ".repeat(lastColumn.marginRight()));
        }
        System.out.println(sb.toString());
        System.out.println("-".repeat(sb.length()));
    }

    @Override
    public void accept(GameSearcher.Hit hit) {
        StringBuilder sb = new StringBuilder();
        GameColumn lastColumn = null;
        for (GameColumn column : columns) {
            int marginBefore = Math.max(column.marginLeft(), lastColumn == null ? 0 : lastColumn.marginRight());
            sb.append(" ".repeat(marginBefore));
            lastColumn = column;

            GameModel model;
            try {
                model = hit.getModel();
            } catch (ChessBaseException e) {
                model = null;
            }
            String value = column.getValue(hit.getDatabase(), hit.getGameHeader(), model);
            if (value.length() > column.width()) {
                value = value.substring(0, column.width());
            }
            sb.append(value);
            sb.append(" ".repeat(column.width() - value.length()));
        }
        if (lastColumn != null) {
            sb.append(" ".repeat(lastColumn.marginRight()));
        }
        System.out.println(sb.toString());
    }

}
