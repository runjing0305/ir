package graph.rsgraph;

import context.ProblemContext;
import context.RollingStock;
import graph.rsdgraph.RollingStockDutyEdge;
import graph.rsdgraph.RollingStockDutyVertex;
import lombok.Getter;
import solution.Solution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RSGraph （功能简述）
 * 功能详细描述
 *
 * @author s00536729
 * @since 2022-07-22
 */
@Getter
public class RSGraph {
    private Solution curSol; // 当前解
    private Map<RollingStock, SingleRSGraph> rs2GraphMap = new HashMap<>(); // 列车组
    private ProblemContext context; // 全问题情景
    private List<RollingStockDutyVertex> vertexList = new ArrayList<>(); // 每个点代表一个Course的copy
    private Map<String, RollingStockDutyVertex> name2Vertex = new HashMap<>();
    private List<RollingStockDutyEdge> edges = new ArrayList<>(); // 每条边代表Course到Course之间的顺序执行关系
    private Map<String, RollingStockDutyEdge> name2Edge = new HashMap<>();

    public RSGraph(ProblemContext context, Solution curSol) {
        this.context = context;
        this.curSol = curSol;
        makeGraph();
    }

    private void makeGraph() {
        for (RollingStock rollingStock : context.getRollingStocks()) {
            if (curSol.getRollingStock2ScheduleListMap().getOrDefault(rollingStock, new ArrayList<>()).isEmpty()) {
                continue;
            }
            SingleRSGraph graph = new SingleRSGraph(rollingStock, context, curSol);
            rs2GraphMap.put(rollingStock, graph);
            vertexList.addAll(graph.getVertexList());
            name2Vertex.putAll(graph.getName2Vertex());
            edges.addAll(graph.getEdges());
            name2Edge.putAll(graph.getName2Edge());
        }
    }
}
