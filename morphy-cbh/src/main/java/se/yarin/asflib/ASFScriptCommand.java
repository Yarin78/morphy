package se.yarin.asflib;

public record ASFScriptCommand(int millis, String type, String command) {
  @Override
  public String toString() {
    return "ASFScriptCommand{"
        + "millis="
        + millis
        + ", type='"
        + type
        + '\''
        + ", command='"
        + command
        + '\''
        + '}';
  }
}
