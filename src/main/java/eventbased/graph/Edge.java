package eventbased.graph;

import lombok.Data;

import java.util.Objects;

/**
 * @author longfei
 */
@Data
public class Edge {
    private EdgeType edgeType;
    private Vertex head;
    private Vertex tail;

    private double passageFrequencyPenalty = 0.0;
    private int minimumRunTimeViolation = 0;
    private int changeEndTimeViolation = 0;
    private int rollingStockIndex = -1;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Edge edge = (Edge) o;
        return edgeType == edge.edgeType && Objects.equals(head, edge.head) && Objects.equals(tail, edge.tail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(edgeType, head, tail);
    }
}
