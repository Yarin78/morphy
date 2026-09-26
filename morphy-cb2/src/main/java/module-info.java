module se.yarin.morphy.cb2 {
    requires transitive se.yarin.morphy.chessbase;
    requires org.slf4j;
    requires org.jetbrains.annotations;

    exports se.yarin.morphy.cb2;
    exports se.yarin.morphy.cb2.annotations;
    exports se.yarin.morphy.cb2.entities;
    exports se.yarin.morphy.cb2.games;
    exports se.yarin.morphy.cb2.indexes;
    exports se.yarin.morphy.cb2.moves;
    exports se.yarin.morphy.cb2.storage;

    provides se.yarin.morphy.api.DatabaseProvider with
            se.yarin.morphy.cb2.Database2CbhProvider;
}
