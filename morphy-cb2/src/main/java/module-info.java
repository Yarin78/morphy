module se.yarin.morphy.cb2 {
    requires transitive se.yarin.morphy.api;
    requires org.slf4j;
    requires org.jetbrains.annotations;

    exports se.yarin.morphy.cb2;

    provides se.yarin.morphy.api.DatabaseProvider with
            se.yarin.morphy.cb2.Database2CbhProvider;
}
