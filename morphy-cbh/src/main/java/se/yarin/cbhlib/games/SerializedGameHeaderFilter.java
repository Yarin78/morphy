package se.yarin.cbhlib.games;

public interface SerializedGameHeaderFilter {
    boolean matches(byte[] serializedGameHeader);

    static SerializedGameHeaderFilter chain(Iterable<SerializedGameHeaderFilter> filters) {
        return serializedGameHeader -> {
            for (SerializedGameHeaderFilter filter : filters) {
                if (!filter.matches(serializedGameHeader)) {
                    return false;
                }
            }
            return true;
        };
    }
}
