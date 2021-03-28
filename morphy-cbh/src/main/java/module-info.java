module se.yarin.morphy.cbh {
    requires slf4j.api;
    requires static lombok;
    requires static org.immutables.value;
    requires progressbar;
    requires java.compiler;
    requires org.jetbrains.annotations;

    exports se.yarin.chess;
    exports se.yarin.chess.annotations;
    exports se.yarin.chess.timeline;

    exports se.yarin.cbhlib;
    exports se.yarin.cbhlib.annotations;
    exports se.yarin.cbhlib.entities;
    exports se.yarin.cbhlib.games;
    exports se.yarin.cbhlib.games.search;
    exports se.yarin.cbhlib.media;
    exports se.yarin.cbhlib.moves;
    exports se.yarin.cbhlib.exceptions;

    exports se.yarin.cbhlib.storage to se.yarin.morphy.cli, se.yarin.morphy.tools;
    exports se.yarin.cbhlib.storage.transaction to se.yarin.morphy.cli;
    exports se.yarin.cbhlib.validation to se.yarin.morphy.cli;
    exports se.yarin.cbhlib.util to se.yarin.morphy.cli, se.yarin.morphy.tools;
    exports se.yarin.util.parser to se.yarin.morphy.cli;
    exports se.yarin.util;
}
