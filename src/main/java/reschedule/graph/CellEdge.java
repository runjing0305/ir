package reschedule.graph;

import constant.Constants;
import context.RollingStock;
import graph.AbstractArc;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CellEdge extends AbstractArc<CellVertex> {
    public enum Type {
        INNER,
        INTER,
    }

    protected double weight;
    protected int minimumRuntime;

    private Type type = Type.INNER;

    private int startTime = 0;
    private int endTime = Constants.BIG_M;
    private int arrivalTimeInterval = 50;
    private int departureTimeInterval = 50;
    RollingStock rollingStock = null;

    public CellEdge(CellVertex head, CellVertex tail, double weight, int minimumRunTime) {
        this.head = head;
        head.getOutArcList().add(this);
        this.tail = tail;
        tail.getInArcList().add(this);
        this.weight = weight;
        this.minimumRuntime = minimumRunTime;
        this.name = toString();
    }
}
