module se.yarin.morphy.cli {
    requires se.yarin.morphy.cbh;
    requires info.picocli;
    requires progressbar;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    opens se.yarin.morphy.cli.commands to info.picocli;
    opens se.yarin.morphy.cli.old.commands to info.picocli;
}
