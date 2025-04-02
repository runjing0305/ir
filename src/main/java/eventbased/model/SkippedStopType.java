package eventbased.model;

/**
 * @author longfei
 */

public enum SkippedStopType {

    /**
     * First element
     */
    FIRST("FIRST", 35.0),

    /**
     * Second element
     */
    SECOND("SECOND", 15.0),

    /**
     * Remaining elements
     */
    REMAIN("REMAIN", 1.0);

    private final String id;
    private final double factor;

    SkippedStopType(String id, double factor) {
        this.id = id;
        this.factor = factor;
    }

    public String getId() {
        return id;
    }

    public double getFactor() {
        return factor;
    }
}
