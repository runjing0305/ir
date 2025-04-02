package context;

/**
 * @author longfei
 */
public enum ActivityType {
    // STOP
    STOP(0, "STOP", "Stop at the node"),

    // PASS
    PASS(1, "PASS", "Pass the node");

    private final int index;
    private final String value;
    private final String description;

    ActivityType(int index, String value, String description) {
        this.index = index;
        this.value = value;
        this.description = description;
    }

    public int getIndex() {
        return index;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }
}
