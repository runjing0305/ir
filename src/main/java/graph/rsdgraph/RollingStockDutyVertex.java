package graph.rsdgraph;

import context.Node;
import context.Schedule;
import graph.AbstractNode;
import lombok.Getter;
import lombok.Setter;

/**
 * RollingStockDutyVertex （RSD顶点）
 * 代表虚拟起点终点或待执行的Course，具有开始和结束时间戳
 *
 * @author s00536729
 * @since 2022-07-13
 */
@Getter
@Setter
public class RollingStockDutyVertex  extends AbstractNode<RollingStockDutyEdge> {
    private boolean isVirtual = false; // 代表顶点是否是虚拟顶点
    private int startTime; // Course的开始时间可能会发生偏移
    private Node startNode; // Course的开始地点
    private int endTime; // Course的结束时间可能会偏移
    private Node endNode; // Course的结束地点
    private int change; // 相较origCourse的时间偏移量
    private Schedule origCourse; // 用来计算headway， track capacity和minimum run time等违反

    public RollingStockDutyVertex(String name) {
        this.name = name;
    }
}
