package yarin.asflib;

public class ASFScriptCommand {
    private final int millis;
    private final String type;
    private final String command;

    public int getMillis() {
        return millis;
    }

    public String getType() {
        return type;
    }

    public String getCommand() {
        return command;
    }

    public ASFScriptCommand(int millis, String type, String command) {
        this.millis = millis;
        this.type = type;
        this.command = command;
    }

    @Override
    public String toString() {
        return "ASFScriptCommand{" +
                "millis=" + millis +
                ", type='" + type + '\'' +
                ", command='" + command + '\'' +
                '}';
    }
}
