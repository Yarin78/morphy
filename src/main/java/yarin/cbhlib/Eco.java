package yarin.cbhlib;

public class Eco {
    private boolean isSet;
    private int eco;
    private int subEco;

    private Eco(int eco, int subEco) {
        if (eco == 0) {
            isSet = false;
        } else {
            isSet = true;
            this.eco = eco - 1;
            this.subEco = subEco;
        }
    }

    public static Eco parse(int value) {
        return new Eco(value / 128, value % 128);
    }

    public int toInt() {
        return isSet ? (eco+1)*128+subEco : 0;
    }

    @Override
    public String toString() {
        if (!isSet) {
            return "";
        }
        String s = String.format("%c%02d", (char) ('A' + eco / 100), eco % 100);
        if (subEco > 0) {
            s += String.format("/%02d", subEco);
        }
        return s;
    }
}
