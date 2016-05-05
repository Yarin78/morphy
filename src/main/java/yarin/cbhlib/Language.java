package yarin.cbhlib;

public enum Language
{
    English(0),
    German(1),
    France(2),
    Spanish(3),
    Italian(4),
    Dutch(5),
    Portugese(6),
    Polish(7),
    All(255);

    private int value;

    Language(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
