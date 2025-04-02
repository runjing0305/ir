package graph.rsdgraph;

import constant.Constants;
import context.Duty;
import context.Node;
import context.ProblemContext;
import context.Schedule;
import graph.AbstractArc;
import solution.Solution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * RollingStockDutyGraph （Rolling Stock Duty的Graph）
 * 决策Rolling Stock Duty怎么将各个Course串起来
 *
 * @author s00536729
 * @since 2022-07-13
 */
public class RollingStockDutyGraph {
    private Solution curSol; // 当前解
    private Duty duty; // Rolling Stock的Duty
    private ProblemContext context; // 全问题情景
    private List<RollingStockDutyVertex> vertexList = new ArrayList<>(); // 每个点代表一个Course的copy
    private Map<String, RollingStockDutyVertex> name2Vertex = new HashMap<>();
    private List<RollingStockDutyEdge> edges = new ArrayList<>(); // 每条边代表Course到Course之间的顺序执行关系，包括Partial Cancellation和Full Cancellation
    private Map<String, RollingStockDutyEdge> name2Edge = new HashMap<>();
    private int[] courseStartTimeChange = new int[] {-30, 0, 30, 60}; // 代表几种Course起始时间变动的可能性，负数代表提前

    public RollingStockDutyGraph(Duty duty, ProblemContext context, Solution curSol) {
        this.duty = duty;
        this.context = context;
        this.curSol = curSol;
        makeGraph();
    }

    private void makeGraph() {
        // 产生Duty起点和终点
        genVirtualStartAndEndVertices();
        List<Schedule> filteredSchedules = context.getSchedules().stream().filter(schedule -> schedule.getStartTime()
            >= duty.getStartTime() && schedule.getEndTime() <= duty.getEndTime() && !context.getFixedCourseSet().
            contains(schedule.getCourseId())).collect(Collectors.toList());
        genScheduleChangeCopyVertices(filteredSchedules);
        Set<Schedule> scheduleSet = duty.getPlannedSchedules()
            .stream()
            .filter(schedule -> schedule.getEventType().equals(Schedule.EventType.TRAIN))
            .collect(Collectors.toCollection(TreeSet::new));
        // 刷新duty的以发生的Course
        for (Schedule schedule : scheduleSet) {
            if (!schedule.getRealizedNodeStatus().isEmpty()) {
                // 代表Course至少一部分站点已经被到达
                duty.getRealizedSchedules().add(schedule);
            }
        }
        // 产生已发生的Schedule的连接边
        genRealizedPathEdges();
        if (duty.getRealizedSchedules().isEmpty()
            || duty.getRealizedSchedules().size() < duty.getPlannedSchedules().size()) {
            // 如果没有已发生的Schedule，则尝试按照Planned Schedule来连边
            genPlannedPathEdges();
        }
        if (edges.size() == duty.getPlannedSchedules().size() + 1) {
            // 如果planned course刚好够用，则不再需要额外生成边
            context.getFixedCourseSet().addAll(duty.getPlannedSchedules().stream().map(Schedule::getCourseId).collect(
                Collectors.toList()));
            return;
        }
        // 因为发空车的原因，目前这些边都没起作用
        genInterScheduleEdges(filteredSchedules);
        genEndEdges(filteredSchedules);
    }

    private void genPlannedPathEdges() {
        Collections.sort(duty.getPlannedSchedules());
        RollingStockDutyVertex headVertex = name2Vertex.get(Constants.VIRTUAL_START_VERTEX_NAME);
        List<Schedule> plannedTrainSchedules = duty.getPlannedSchedules().stream().filter(schedule -> schedule.
            getEventType().equals(Schedule.EventType.TRAIN)).collect(Collectors.toList());
        for (int i = 0; i < plannedTrainSchedules.size(); i++) {
            Schedule schedule = plannedTrainSchedules.get(i);
            RollingStockDutyVertex tailVertex = name2Vertex.get(schedule.getCourseId() + "_change_0");
            if (tailVertex == null) {
                // 如果tailVertex找不到，说明由于延误或者其他情况，导致其脱离了duty的时间窗，需要额外安排一辆空车
                RollingStockDutyVertex courseVertex = new RollingStockDutyVertex(schedule.
                    getCourseId() + "_EE_rescheduled");
                courseVertex.setStartTime(headVertex.getEndTime());
                courseVertex.setEndTime(plannedTrainSchedules.get(i + 1).getStartTime() - 30);
                courseVertex.setStartNode(schedule.getStartNode());
                courseVertex.setEndNode(schedule.getEndNode());
                courseVertex.setOrigCourse(schedule);
                vertexList.add(courseVertex);
                name2Vertex.put(courseVertex.getName(), courseVertex);
                tailVertex = courseVertex;
            }
            if (headVertex.getEndTime() <= tailVertex.getStartTime() + Constants.TIME_DIFF_TOL
                && !name2Edge.containsKey(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()))) {
                RollingStockDutyEdge edge = new RollingStockDutyEdge();
                edge.setName(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()));
                edge.setHead(headVertex);
                edge.setTail(tailVertex);
                edge.setTimeDiff(Math.max(0, headVertex.getEndTime() - tailVertex.getStartTime()));
                edges.add(edge);
                name2Edge.put(edge.getName(), edge);
            }
            headVertex = tailVertex;
        }
        RollingStockDutyVertex tailVertex = name2Vertex.get(Constants.VIRTUAL_END_VERTEX_NAME);
        if (headVertex.getEndTime() <= tailVertex.getStartTime()) {
            RollingStockDutyEdge edge = new RollingStockDutyEdge();
            edge.setName(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()));
            edge.setHead(headVertex);
            edge.setTail(tailVertex);
            edges.add(edge);
            name2Edge.put(edge.getName(), edge);
        }
    }

    private void genEndEdges(List<Schedule> filteredSchedules) {
        Set<Schedule> unrealizedSchedules = filteredSchedules.stream().filter(schedule -> schedule.
            getRealizedEnterTimes().isEmpty()).collect(Collectors.toSet());
        List<RollingStockDutyVertex> filteredVertexList = vertexList.stream().filter(rsdVertex -> !rsdVertex.
            isVirtual() && unrealizedSchedules.contains(rsdVertex.getOrigCourse())).collect(Collectors.toList());
        filteredVertexList.sort(Comparator.comparingInt(RollingStockDutyVertex::getStartTime));
        RollingStockDutyVertex tailVertex = name2Vertex.get(Constants.VIRTUAL_END_VERTEX_NAME);
        for (RollingStockDutyVertex headVertex : filteredVertexList) {
            if (headVertex.getEndTime() < duty.getEndTime()) {
                // 首先headVertex结束的时间必须比duty结束时间早
                if (headVertex.getEndNode().equals(duty.getEndNode())) {
                    // 先考虑不带empty ride(EE)的情形，即headVertex终止地点和duty结束地点相同的约束
                    RollingStockDutyEdge edge = new RollingStockDutyEdge();
                    edge.setName(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()));
                    edge.setHead(headVertex);
                    edge.setTail(tailVertex);
                    edges.add(edge);
                    name2Edge.put(edge.getName(), edge);
                } else if (duty.getEndTime() - headVertex.getEndTime() >= calcEmptyRideTime(headVertex.getEndNode(),
                    duty.getEndNode())) {
                    // 另一方面，如果headVertex终止时间足够开一辆empty ride到duty结束地点
                    // RollingStockDutyEdge edge = new RollingStockDutyEdge();
                    // edge.setName(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()));
                    // edge.setHead(headVertex);
                    // edge.setTail(tailVertex);
                    // edges.add(edge);
                    // name2Edge.put(edge.getName(), edge);
                }
            }
        }
    }

    private int calcEmptyRideTime(Node start, Node end) {
        return 0;
    }

    private void genRealizedPathEdges() {
        Collections.sort(duty.getRealizedSchedules());
        RollingStockDutyVertex headVertex = name2Vertex.get(Constants.VIRTUAL_START_VERTEX_NAME);
        for (Schedule schedule : duty.getRealizedSchedules()) {
            RollingStockDutyVertex tailVertex = name2Vertex.get(schedule.getCourseId() + "_change_0");
            if (tailVertex == null) {
                // 这种情况说明已经实现的course超出了原有的duty的时间窗，目前我们认为这在Duty角度是合理的
                // 但是可能导致的问题是会有一些Rolling Stock没办法将这些Duty串起来
                RollingStockDutyVertex courseVertex = new RollingStockDutyVertex(schedule.
                    getCourseId() + "_change_" + 0);
                // 注意在Solution
                courseVertex.setStartTime(curSol.getScheduleStationDepartureTimeMap().get(schedule).get(0));
                List<Integer> frontArrivals = curSol.getScheduleStationArrivalTimeMap().get(schedule);
                courseVertex.setEndTime(frontArrivals.get(frontArrivals.size() - 1));
                courseVertex.setStartNode(schedule.getRealizedNodes().get(0));
                courseVertex.setEndNode(schedule.getEndNode());
                courseVertex.setOrigCourse(schedule);
                vertexList.add(courseVertex);
                name2Vertex.put(courseVertex.getName(), courseVertex);
                tailVertex = courseVertex;
            }
            if (headVertex.getEndTime() <= tailVertex.getStartTime()) {
                RollingStockDutyEdge edge = new RollingStockDutyEdge();
                edge.setName(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()));
                edge.setHead(headVertex);
                edge.setTail(tailVertex);
                headVertex = tailVertex;
                edges.add(edge);
                name2Edge.put(edge.getName(), edge);
            } else {
                System.out.println("head: " + headVertex.getName() + ", tail: " + tailVertex.getName());
            }
        }
        RollingStockDutyVertex tailVertex = name2Vertex.get(Constants.VIRTUAL_END_VERTEX_NAME);
        if (duty.getRealizedSchedules().size() < duty.getPlannedSchedules().size()) {
            // 如果已发生的course少于计划的course，则不直接产生终点连接
            return;
        }
        if (headVertex.getEndNode().equals(tailVertex.getStartNode())) {
            RollingStockDutyEdge edge = new RollingStockDutyEdge();
            edge.setName(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()));
            edge.setHead(headVertex);
            edge.setTail(tailVertex);
            edges.add(edge);
            name2Edge.put(edge.getName(), edge);
        }
    }

    private void genInterScheduleEdges(List<Schedule> filteredSchedules) {
        Set<Schedule> unrealizedSchedules = filteredSchedules.stream().filter(schedule -> schedule.
            getRealizedEnterTimes().isEmpty()).collect(Collectors.toSet());
        List<RollingStockDutyVertex> filteredVertexList = vertexList.stream().filter(rsdVertex -> !rsdVertex.
            isVirtual() && unrealizedSchedules.contains(rsdVertex.getOrigCourse())).collect(Collectors.toList());
        filteredVertexList.sort(Comparator.comparingInt(RollingStockDutyVertex::getStartTime));
        for (int i = 0; i < filteredVertexList.size() - 1; i++) {
            RollingStockDutyVertex headVertex = filteredVertexList.get(i);
            for (int j = i + 1; j < filteredVertexList.size(); j++) {
                RollingStockDutyVertex tailVertex = filteredVertexList.get(j);
                // 先考虑不带取消的情形，即采用时间前后顺序，同时前course终止地点和后course开始地点相同的约束
                if (headVertex.getEndNode().equals(tailVertex.getStartNode()) && headVertex.getEndTime() +
                    Constants.CHANGE_END_TIME <= tailVertex.getStartTime()
                    && !name2Edge.containsKey(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()))) {
                    RollingStockDutyEdge edge = new RollingStockDutyEdge();
                    edge.setName(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()));
                    edge.setHead(headVertex);
                    edge.setTail(tailVertex);
                    edges.add(edge);
                    name2Edge.put(edge.getName(), edge);
                }
                // TODO: 考虑partial cancellation等复杂一些的情形
            }
        }
    }

    private void genScheduleChangeCopyVertices(List<Schedule> filteredSchedules) {
        for (Schedule schedule : filteredSchedules) {
            if (schedule.getRealizedNodeStatus().isEmpty()) {
                // course尚未实现
                for (int change : courseStartTimeChange) {
                    RollingStockDutyVertex courseVertex = new RollingStockDutyVertex(schedule.
                        getCourseId() + "_change_" + change);
                    courseVertex.setStartTime(schedule.getStartTime() + change);
                    courseVertex.setEndTime(schedule.getEndTime() + change);
                    courseVertex.setStartNode(schedule.getStartNode());
                    courseVertex.setEndNode(schedule.getEndNode());
                    courseVertex.setOrigCourse(schedule);
                    vertexList.add(courseVertex);
                    name2Vertex.put(courseVertex.getName(), courseVertex);
                }
            } else {
                // course已经实现，或者已经实现了一半
                RollingStockDutyVertex courseVertex = new RollingStockDutyVertex(schedule.
                    getCourseId() + "_change_" + 0);
                // 注意在Solution
                courseVertex.setStartTime(curSol.getScheduleStationDepartureTimeMap().get(schedule).get(0));
                List<Integer> frontArrivals = curSol.getScheduleStationArrivalTimeMap().get(schedule);
                courseVertex.setEndTime(frontArrivals.get(frontArrivals.size() - 1));
                courseVertex.setStartNode(schedule.getRealizedNodes().get(0));
                courseVertex.setEndNode(schedule.getEndNode());
                courseVertex.setOrigCourse(schedule);
                vertexList.add(courseVertex);
                name2Vertex.put(courseVertex.getName(), courseVertex);
            }
        }
    }

    private void genVirtualStartAndEndVertices() {
        // generate virtual start & end node
        RollingStockDutyVertex virtualStartVertex = new RollingStockDutyVertex(Constants.VIRTUAL_START_VERTEX_NAME);
        virtualStartVertex.setVirtual(true);
        virtualStartVertex.setStartTime(duty.getStartTime());
        virtualStartVertex.setEndTime(duty.getStartTime());
        virtualStartVertex.setStartNode(duty.getStartNode());
        virtualStartVertex.setEndNode(duty.getStartNode());
        vertexList.add(virtualStartVertex);
        name2Vertex.put(virtualStartVertex.getName(), virtualStartVertex);
        RollingStockDutyVertex virtualEndVertex = new RollingStockDutyVertex(Constants.VIRTUAL_END_VERTEX_NAME);
        virtualEndVertex.setVirtual(true);
        virtualEndVertex.setStartTime(duty.getEndTime());
        virtualEndVertex.setEndTime(duty.getEndTime());
        virtualEndVertex.setStartNode(duty.getEndNode());
        virtualEndVertex.setEndNode(duty.getEndNode());
        vertexList.add(virtualEndVertex);
        name2Vertex.put(virtualEndVertex.getName(), virtualEndVertex);
    }
}
