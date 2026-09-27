module se.yarin.morphy.cli {
    requires se.yarin.morphy.api;
    requires se.yarin.morphy.cbh;
    requires se.yarin.morphy.cb2;
    requires info.picocli;
    requires me.tongfei.progressbar;
    requires org.slf4j;
    requires ch.qos.logback.classic;
    requires org.jetbrains.annotations;

    opens se.yarin.morphy.cli.commands to
            info.picocli;
}
