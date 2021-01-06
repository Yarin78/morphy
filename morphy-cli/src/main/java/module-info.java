module se.yarin.morphy.cli {
    requires se.yarin.morphy.cbh;
    requires info.picocli;
    requires progressbar;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    opens se.yarin.morphy.cli to info.picocli;
}
