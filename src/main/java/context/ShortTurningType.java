package context;

public enum ShortTurningType {
    RELEVANT(0, "1"),

    OPPOSITE(1, "2"),

    BOTH(2, "3"),

    SATELLITE_AREA(3, "SA");


    ShortTurningType(int index, String value) {
        this.index = index;
        this.value = value;
    }

    public final int index;
    public final String value;
}
