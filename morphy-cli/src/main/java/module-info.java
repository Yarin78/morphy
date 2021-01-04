module se.yarin.morphy.cli {
    requires se.yarin.morphy.cbh;
    requires slf4j.api;
    requires org.apache.logging.log4j;
    requires info.picocli;
    requires progressbar;
    opens se.yarin.morphy.cli to info.picocli;
}
