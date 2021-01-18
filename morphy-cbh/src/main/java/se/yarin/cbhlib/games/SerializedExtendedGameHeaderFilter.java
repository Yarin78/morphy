package se.yarin.cbhlib.games;

public interface SerializedExtendedGameHeaderFilter {
    boolean matches(byte[] serializedExtendedGameHeader);

    static SerializedExtendedGameHeaderFilter chain(Iterable<SerializedExtendedGameHeaderFilter> filters) {
        return serializedExtendedGameHeader -> {
            for (SerializedExtendedGameHeaderFilter filter : filters) {
                if (!filter.matches(serializedExtendedGameHeader)) {
                    return false;
                }
            }
            return true;
        };
    }

}
