package graph;

import lombok.Getter;
import lombok.Setter;

/**
 * Edge （边）
 * 图中的边
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class Edge extends AbstractArc<Vertex> {
    protected double weight; // 权重
    protected int minimumRuntime; // 最小运行时间

    /**
     * 边的构造器
     *
     * @param head 边的头顶点
     * @param tail 边的尾顶点
     * @param weight 边的权重
     * @param minimumRunTime 边的最小运行时间
     */
    public Edge(Vertex head, Vertex tail, double weight, int minimumRunTime) {
        this.head = head;
        head.getOutEdges().add(this);
        this.tail = tail;
        tail.getInEdges().add(this);
        this.weight = weight;
        this.minimumRuntime = minimumRunTime;
        this.name = toString();
    }
}
