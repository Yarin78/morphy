module se.yarin.morphy.cbh {
    requires transitive se.yarin.morphy.api;
    requires org.slf4j;
    requires static org.immutables.value;
    requires me.tongfei.progressbar;
    requires java.compiler;
    requires org.jetbrains.annotations;
    requires concurrent.locks;
    requires com.github.albfernandez.juniversalchardet;
    requires org.jline.terminal;

    exports se.yarin.util.parser to
            se.yarin.morphy.cli;
    exports se.yarin.util;
    exports se.yarin.morphy;
    exports se.yarin.morphy.convert;
    exports se.yarin.morphy.entities;
    exports se.yarin.morphy.entities.filters;
    exports se.yarin.morphy.games;
    exports se.yarin.morphy.games.moves;
    exports se.yarin.morphy.games.filters;
    exports se.yarin.morphy.games.annotations;
    exports se.yarin.morphy.queries;
    exports se.yarin.morphy.queries.filter;
    exports se.yarin.morphy.queries.operations;
    exports se.yarin.morphy.queries.joins;
    exports se.yarin.morphy.queries.visualisation;
    exports se.yarin.morphy.boosters;
    exports se.yarin.morphy.storage;
    exports se.yarin.morphy.validation;
    exports se.yarin.morphy.exceptions;
    exports se.yarin.morphy.metrics;
    exports se.yarin.morphy.text;
    exports se.yarin.morphy.util;

    provides se.yarin.morphy.api.DatabaseProvider with
            se.yarin.morphy.DatabaseCbhProvider;
}
