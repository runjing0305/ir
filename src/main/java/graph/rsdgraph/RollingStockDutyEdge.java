package graph.rsdgraph;

import context.Node;
import graph.AbstractArc;
import lombok.Getter;
import lombok.Setter;

/**
 * RollingStockDutyEdge （RollingStockDuty边）
 * 每条边代表Course到Course之间的顺序执行关系，包括Partial Cancellation和Full Cancellation
 *
 * @author s00536729
 * @since 2022-07-13
 */
@Getter
@Setter
public class RollingStockDutyEdge extends AbstractArc<RollingStockDutyVertex> {
    private double weight; // 目前假设如果按照plan走，则weight为0，否则weight为局部取消惩罚
    private double timeDiff; // 代表头顶点的结束时间和尾顶点的开始时间之间的gap，当gap为负，说明需要进行局部取消
    private Node partialCancelNode; // 代表在哪个点进行局部取消（short turning）
}
