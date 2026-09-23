module se.yarin.morphy.api {
    requires org.slf4j;
    requires org.jetbrains.annotations;
    // Jackson annotations decorate the DTO records. They are needed at compile time only:
    // the service that serializes the DTOs runs on the classpath, so nothing in the module
    // graph needs Jackson at runtime.
    requires static com.fasterxml.jackson.annotation;

    exports se.yarin.chess;
    exports se.yarin.chess.annotations;
    exports se.yarin.chess.timeline;
    exports se.yarin.chess.pgn;
    exports se.yarin.morphy.model;
    exports se.yarin.morphy.api;

    uses se.yarin.morphy.api.DatabaseProvider;
}
