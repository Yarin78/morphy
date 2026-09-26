module se.yarin.morphy.chessbase {
    requires transitive se.yarin.morphy.api;
    requires org.slf4j;
    requires static org.immutables.value;
    requires java.compiler;
    requires org.jetbrains.annotations;
    requires concurrent.locks;

    exports se.yarin.morphy.chessbase;
    exports se.yarin.morphy.chessbase.annotations;
    exports se.yarin.morphy.chessbase.convert;
    exports se.yarin.morphy.chessbase.text;
}
