package reschedule.graph;

import constant.Constants;
import context.Schedule;
import graph.AbstractNode;
import graph.Vertex;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CellVertex extends AbstractNode<CellEdge> {
    public enum Type {
        STOP,
        PASS
    }
    private Vertex.Type type;
    private boolean isVirtual = false; // 代表顶点是否是虚拟顶点
    private int startTime = 0;
    private int endTime = Constants.BIG_M;
    private int arrivalTimeInterval  = 60;
    private int departureTimeInterval = 30;
    private double bsv = 0.0;
    private int minDwellTime = 0;
    Schedule schedule;
    int seq = 0;
    public CellVertex(String argName) {
        name = argName;
    }

    public String toString() {
        return name;
    }

}
