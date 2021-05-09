package se.yarin.morphy.cli.games;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.yarin.morphy.Game;
import se.yarin.morphy.cli.columns.GameColumn;

import java.util.*;

public class StdoutGamesSummary extends GameConsumerBase {
    private static final Logger log = LogManager.getLogger();

    private final boolean showTotal;
    private final Collection<GameColumn> columns;

    public static final String DEFAULT_COLUMNS = "id,name,rating,result,num-moves,eco,tournament,date";

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
    public void finish() {
        if (showTotal) {
            if (totalFoundGames == 0) {
                System.out.printf("No hits (%.2f s)%n", totalSearchTime / 1000.0);
            } else {
                System.out.println();
                if (totalConsumedGames < totalFoundGames) {
                    System.out.printf("%d out of %d hits displayed (%.2f s)%n",
                            totalConsumedGames, totalFoundGames, totalSearchTime / 1000.0);
                } else {
                    System.out.printf("%d hits  (%.2f s)%n",
                            totalFoundGames, totalSearchTime / 1000.0);
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
    public void accept(Game game) {
        StringBuilder sb = new StringBuilder();
        GameColumn lastColumn = null;
        for (GameColumn column : columns) {
            int marginBefore = Math.max(column.marginLeft(), lastColumn == null ? 0 : lastColumn.marginRight());
            sb.append(" ".repeat(marginBefore));
            lastColumn = column;

            String value = column.getValue(game);
            if (column.trimValueToWidth() && value.length() > column.width()) {
                value = value.substring(0, column.width());
            }
            sb.append(value);
            sb.append(" ".repeat(Math.max(0, column.width() - value.length())));
        }
        if (lastColumn != null) {
            sb.append(" ".repeat(lastColumn.marginRight()));
        }
        System.out.println(sb.toString());
    }

}
