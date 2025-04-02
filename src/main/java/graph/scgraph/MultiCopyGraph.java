package graph.scgraph;

import constant.Constants;
import context.Node;
import context.ProblemContext;
import context.RollingStock;
import context.Schedule;
import graph.AbstractArc;
import graph.rsdgraph.RollingStockDutyEdge;
import graph.rsdgraph.RollingStockDutyVertex;
import solution.Solution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static constant.Constants.COURSE_START_TIME_CHANGE;

/**
 * @description:
 * @author: Shengcheng Shao
 * @date: 2022/8/4
 */
public class MultiCopyGraph extends SingleCommodityGraph{
    public MultiCopyGraph(ProblemContext context, Solution curSol) {
        super(context, curSol);
    }

    protected void makeGraph() {
        // 产生起点和终点
        genVirtualStartAndEndVertices();
        // 对每个Course产生Copy
        genCourseCopyVertices();
        // 将已经发生的Schedule（包括已经到第一站但没有发动的车），放到Rolling Stock中
        fillRollingStockRealizedSchedules();
        // 1、产生已发生的Schedule的连接边
        int edgeSize = edges.size();
        System.out.println("0: " + edgeSize);
        for (RollingStock rollingStock : context.getRollingStocks()) {
            genRealizedPathEdges(rollingStock);
            // 2、如果没有已发生的Schedule，或者没有把rolling stock的任务做完，则尝试按照Planned Schedule来连边
            if (rollingStock.getRealizedSchedules().isEmpty()
                    || rollingStock.getRealizedSchedules().size() < rollingStock.getSchedules().size()) {
                genPlannedPathEdges(rollingStock);
            }
        }
        edgeSize = edges.size();
        System.out.println("1: " + edgeSize);
        RollingStockDutyVertex startVertex = name2Vertex.get(Constants.VIRTUAL_START_VERTEX_NAME);
        RollingStockDutyVertex endVertex = name2Vertex.get(Constants.VIRTUAL_END_VERTEX_NAME);

        // 3、生成起点到终点的边
        if (!name2Edge.containsKey(AbstractArc.genArcName(startVertex.getName(), endVertex.getName()))) {
            RollingStockDutyEdge edge = genEdge(startVertex, endVertex);
            edge.setWeight(1.0);
        }
        edgeSize = edges.size();
        System.out.println("2: " + edgeSize);
        // 抽取尚未发生的Course顶点
        List<RollingStockDutyVertex> unrealizedVertex = vertexList.stream().filter(vertex -> !vertex.isVirtual()
                && vertex.getOrigCourse().getRealizedNodeStatus().isEmpty()).collect(Collectors.toList());

        // 4、对未发生的Course，产生起点到顶点和顶点到终点的edge
        for (RollingStockDutyVertex vertex : unrealizedVertex) {
            genStart2UnrealizedVertexEdge(startVertex, vertex);
            genUnrealizedVertex2EndEdge(endVertex, vertex);
        }
        edgeSize = edges.size();
        System.out.println("3: " + edgeSize);
        unrealizedVertex.sort(Comparator.comparingInt(RollingStockDutyVertex::getStartTime));
        // 5、生成unrealized vertex之间的一些互相连接的边，只要时间和地点对齐
        genEdgesBetweenTwoVertexLists(unrealizedVertex, unrealizedVertex);

        edgeSize = edges.size();
        System.out.println("4: " + edgeSize);
        // 6、针对已经realized的rolling stock的当前schedule，产生其到unrealized vertex的边
        List<RollingStockDutyVertex> realizedCurrentVertex = new ArrayList<>();
        for (RollingStock rollingStock : context.getRollingStocks().stream().filter(rollingStock ->
                !rollingStock.getRealizedSchedules().isEmpty()).collect(Collectors.toList())) {
            List<Schedule> realizedSchedules = rollingStock.getRealizedSchedules();
            Schedule currentSchedule = realizedSchedules.get(realizedSchedules.size() - 1);
            realizedCurrentVertex.add(name2Vertex.get(currentSchedule.getCourseId() + "_0"));
        }
        genEdgesBetweenTwoVertexLists(unrealizedVertex, realizedCurrentVertex);

        edgeSize = edges.size();
        System.out.println("5: " + edgeSize);
        // 7.去除掉没有入度和出度的孤立点？
    }

    private void genCourseCopyVertices() {
        for (Schedule schedule : context.getSchedules()) {
            for (int change : COURSE_START_TIME_CHANGE) {
                if (!schedule.getRealizedNodes().isEmpty()
                        && schedule.getRealizedEnterTimes().get(schedule.getRealizedNodes().size()) != 0
                        && change > 0) {
                    continue;
                }
                RollingStockDutyVertex courseVertex = new RollingStockDutyVertex(schedule.
                        getCourseId() + "_" + change);
                courseVertex.setChange(change);
                List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() :
                        schedule.getRealizedNodes();
                List<Integer> arrivals = new ArrayList<>(curSol.getScheduleStationArrivalTimeMap().get(schedule));
                List<Integer> departures = new ArrayList<>(curSol.getScheduleStationDepartureTimeMap().get(schedule));
                for (int i = 0; i < arrivals.size(); i++) {
                    if (arrivals.get(i) != null) {
                        arrivals.set(i, arrivals.get(i) + change);
                    }
                }
                for (int i = 0; i < departures.size(); i++) {
                    if (departures.get(i) != null) {
                        departures.set(i, departures.get(i) + change);
                    }
                }
                courseVertex.setStartTime(departures.get(0));
                courseVertex.setEndTime(arrivals.get(arrivals.size() - 1));
                courseVertex.setStartNode(nodeList.get(0));
                courseVertex.setEndNode(nodeList.get(nodeList.size() - 1));
                courseVertex.setOrigCourse(schedule);
                vertexList.add(courseVertex);
                name2Vertex.put(courseVertex.getName(), courseVertex);
            }
        }
    }
}
