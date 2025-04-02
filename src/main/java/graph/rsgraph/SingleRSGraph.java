package graph.rsgraph;

import constant.Constants;
import context.Link;
import context.Node;
import context.ProblemContext;
import context.RollingStock;
import context.Schedule;
import context.Track;
import graph.AbstractArc;
import graph.Vertex;
import graph.rsdgraph.RollingStockDutyEdge;
import graph.rsdgraph.RollingStockDutyVertex;
import lombok.Getter;
import solution.Solution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * SingleRSGraph （功能简述）
 * RollingStockGraph （Rolling Stock的Graph）
 * 决策Rolling Stock怎么将各个Course串起来
 *
 * @author s00536729
 * @since 2022-07-21
 */
@Getter
public class SingleRSGraph {
    private Solution curSol; // 当前解
    private RollingStock rollingStock; // 列车组
    private ProblemContext context; // 全问题情景
    private List<RollingStockDutyVertex> vertexList = new ArrayList<>(); // 每个点代表一个Course的copy
    private Map<String, RollingStockDutyVertex> name2Vertex = new HashMap<>();
    private List<RollingStockDutyEdge> edges = new ArrayList<>(); // 每条边代表Course到Course之间的顺序执行关系
    private Map<String, RollingStockDutyEdge> name2Edge = new HashMap<>();
    private int[] courseStartTimeChange = new int[] {-30, 0, 30, 60}; // 代表几种Course起始时间变动的可能性，负数代表提前

    public SingleRSGraph(RollingStock rollingStock, ProblemContext context, Solution curSol) {
        this.rollingStock = rollingStock;
        this.context = context;
        this.curSol = curSol;
        makeGraph();
        indexing();
    }

    private void indexing() {
        for (int i = 0; i < vertexList.size(); i++) {
            RollingStockDutyVertex vertex = vertexList.get(i);
            vertex.setIndex(i);
        }
        for (int i = 0; i < edges.size(); i++) {
            RollingStockDutyEdge edge = edges.get(i);
            edge.setIndex(i);
        }
    }

    private void makeGraph() {
        List<Schedule> filteredSchedules = new ArrayList<>(curSol.getRollingStock2ScheduleListMap().get(rollingStock));
        // filteredSchedules.addAll(curSol.getVioSchedules());
        adjustScheduleStartAndEndTime(filteredSchedules);
        // 产生RS的起点和终点
        genVirtualStartAndEndVertices();
        genScheduleChangeCopyVertices(filteredSchedules);
        fillRollingStockRealizedSchedules();
        // 产生已发生的Schedule的连接边
        genRealizedPathEdges();
        // 如果没有已发生的Schedule，或者没有把rolling stock的任务做完，则尝试按照Planned Schedule来连边
        if (rollingStock.getRealizedSchedules().isEmpty()
            || rollingStock.getRealizedSchedules().size() < rollingStock.getSchedules().size()) {
            genPlannedPathEdges();
        }
        // 如果planned course刚好够用，则不再需要额外生成边，直接返回
        if (edges.size() == rollingStock.getSchedules().size() + 1) {
            context.getFixedCourseSet().addAll(rollingStock.getSchedules().stream().map(Schedule::getCourseId).collect(
                Collectors.toList()));
            return;
        }
        // 否则对尚未完成的任务尝试产生schedule相互连接边和到终点的连接边
        Set<Schedule> unrealizedSchedules = filteredSchedules.stream().filter(schedule -> schedule.
            getRealizedEnterTimes().isEmpty()).collect(Collectors.toSet());
        List<RollingStockDutyVertex> filteredVertexList = vertexList.stream().filter(rsdVertex -> !rsdVertex.
            isVirtual() && unrealizedSchedules.contains(rsdVertex.getOrigCourse())).collect(Collectors.toList());
        filteredVertexList.add(name2Vertex.get(rollingStock.getRealizedSchedules().
            get(rollingStock.getRealizedSchedules().size() - 1).getCourseId() + "_0"));
        if (Constants.OUTPUT_FLAG) {
            System.out.println("Rolling Stock index: " + rollingStock.getIndex() + " cannot finish!");
        }
        filteredVertexList.sort(Comparator.comparingInt(RollingStockDutyVertex::getStartTime));
        genInterScheduleEdges(filteredVertexList);
        genEndEdges(filteredVertexList);
    }

    private void fillRollingStockRealizedSchedules() {
        Set<Schedule> scheduleSet = curSol.getRollingStock2ScheduleListMap().get(rollingStock)
            .stream()
            .filter(schedule -> schedule.getEventType().equals(Schedule.EventType.TRAIN))
            .collect(Collectors.toCollection(TreeSet::new));
        // 刷新duty的以发生的Course
        for (Schedule schedule : scheduleSet) {
            if (!schedule.getRealizedNodeStatus().isEmpty()) {
                // 代表Course至少一部分站点已经被到达
                rollingStock.getRealizedSchedules().add(schedule);
            }
        }
    }

    private void adjustScheduleStartAndEndTime(List<Schedule> filteredSchedules) {
        for (Schedule schedule : filteredSchedules) {
            // 根据解调整schedule的开始和结束时间
            List<Integer> arrivals = curSol.getScheduleStationArrivalTimeMap().get(schedule);
            List<Integer> departures = curSol.getScheduleStationDepartureTimeMap().get(schedule);
            schedule.setStartTime(departures.get(0));
            schedule.setEndTime(arrivals.get(arrivals.size() - 1));
        }
    }

    private void genEndEdges(List<RollingStockDutyVertex> filteredVertexList) {
        RollingStockDutyVertex tailVertex = name2Vertex.get(Constants.VIRTUAL_END_VERTEX_NAME);
        for (RollingStockDutyVertex headVertex : filteredVertexList) {
            if (headVertex.getEndNode().equals(tailVertex.getStartNode())
                && headVertex.getEndTime()  <= tailVertex.getStartTime()
                && !name2Edge.containsKey(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()))) {
                // 先考虑不带empty ride(EE)的情形，即headVertex终止地点和duty结束地点相同的约束
                RollingStockDutyEdge edge = new RollingStockDutyEdge();
                edge.setName(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()) );
                edge.setHead(headVertex);
                edge.setTail(tailVertex);
                headVertex.getOutArcList().add(edge);
                tailVertex.getInArcList().add(edge);
                edges.add(edge);
                name2Edge.put(edge.getName(), edge);
            } else if (!headVertex.getEndNode().equals(tailVertex.getStartNode())
                && getEndTimePlusEmptyRideTime(tailVertex, headVertex) + Constants.CHANGE_END_TIME <=
                tailVertex.getStartTime()
                && !name2Edge.containsKey(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()))) {
                // 考虑发empty ride(EE)到终点的情形
                RollingStockDutyVertex eeVertex = new RollingStockDutyVertex("EE" + context.getEeSchedules().
                    size() + "_0");
                eeVertex.setStartTime((int) (tailVertex.getStartTime() - Constants.CHANGE_END_TIME -
                    context.getTimeMatrix()[headVertex.getEndNode().getIndex()][tailVertex.
                        getStartNode().getIndex()]));
                eeVertex.setEndTime(tailVertex.getStartTime() - Constants.CHANGE_END_TIME);
                eeVertex.setStartNode(headVertex.getEndNode());
                eeVertex.setEndNode(tailVertex.getStartNode());
                Schedule eeSchedule = calcEeSchedule(eeVertex);
                eeVertex.setOrigCourse(eeSchedule);
                vertexList.add(eeVertex);
                name2Vertex.put(eeVertex.getName(), eeVertex);
                RollingStockDutyEdge edge1 = new RollingStockDutyEdge();
                edge1.setName(AbstractArc.genArcName(headVertex.getName(), eeVertex.getName()));
                edge1.setHead(headVertex);
                edge1.setTail(eeVertex);
                headVertex.getOutArcList().add(edge1);
                eeVertex.getInArcList().add(edge1);
                edges.add(edge1);
                name2Edge.put(edge1.getName(), edge1);
                RollingStockDutyEdge edge2 = new RollingStockDutyEdge();
                edge2.setName(AbstractArc.genArcName(eeVertex.getName(), tailVertex.getName()));
                edge2.setHead(eeVertex);
                edge2.setTail(tailVertex);
                eeVertex.getOutArcList().add(edge2);
                tailVertex.getInArcList().add(edge2);
                edges.add(edge2);
                name2Edge.put(edge2.getName(), edge2);
            }
        }
    }

    private Schedule calcEeSchedule(RollingStockDutyVertex eeVertex) {
        Schedule eeSchedule = new Schedule();
        eeSchedule.setCourseId(eeVertex.getName().replace("_0", ""));
        eeSchedule.setStartNode(eeVertex.getStartNode());
        eeSchedule.setEndNode(eeVertex.getEndNode());
        eeSchedule.setStartTime(eeVertex.getStartTime());
        eeSchedule.setEndTime(eeVertex.getEndTime());
        eeSchedule.setCategory(Schedule.Category.EE);
        eeSchedule.setEventType(Schedule.EventType.TRAIN);
        List<Vertex> path = context.getPathMap().get(eeVertex.getStartNode().getIndex()).get(eeVertex.getEndNode()
            .getIndex());
        Track.Direction direction = context.getName2Link().get(path.get(0).getName() + "_" + path.get(1).getName()).
            getDirection();
        eeSchedule.setDirection(direction);
        List<String> tracks = new ArrayList<>();
        List<Boolean> skipStations = new ArrayList<>();
        List<Integer> arrivals = new ArrayList<>();
        List<Integer> departures = new ArrayList<>();
        Node lastNode = null;
        for (int i = 0; i < path.size(); i++) {
            Vertex vertex = path.get(i);
            Node node = context.getCode2Node().get(vertex.getName());
            if (i < path.size() - 1) {
                direction = context.getName2Link().get(path.get(i).getName() + "_" + path.get(i + 1).getName()).
                    getDirection();
            }
            Track.Direction finalDirection = direction;
            Optional<Track> track = node.getTracks().stream().filter(track1 -> track1.getDirection().equals(
                finalDirection)).
                findFirst();
            if (track.isPresent()) {
                tracks.add(track.get().getName());
            } else {
                System.out.println("No matching track!");
                tracks.add(node.getTracks().get(0).getName());
            }
            eeSchedule.getTracks().put(i + 1, tracks.get(i));
            eeSchedule.getPlannedNodes().add(node);
            if (i == 0 || i == path.size() - 1) {
                eeSchedule.getNodeStatus().put(i + 1, "STOP");
                skipStations.add(Boolean.FALSE);
            } else {
                eeSchedule.getNodeStatus().put(i + 1, "PASS");
                skipStations.add(Boolean.TRUE);
            }
            if (i == 0) {
                // 起点无到达时刻，但是有离开站点时间
                arrivals.add(null);
                departures.add(eeSchedule.getStartTime());
                eeSchedule.getLeaveTimes().put(i + 1, eeSchedule.getStartTime());
            } else if (i == path.size() - 1) {
                // 终点无离站时间，但是有到站时间
                Link link = context.getName2Link().get(lastNode.getCode() + "_" + node.getCode());
                int arrivalTime = departures.get(i - 1) + link.getMinimumRunTime()[0][1];
                arrivals.add(arrivalTime);
                eeSchedule.getEnterTimes().put(i + 1, arrivalTime);
                departures.add(null);
            } else {
                // 中间点有到站和离站时间，且二者相同
                Link link = context.getName2Link().get(lastNode.getCode() + "_" + node.getCode());
                int arrivalTime;
                if (i == 1) {
                    arrivalTime = departures.get(0) + link.getMinimumRunTime()[1][0];
                } else {
                    arrivalTime = departures.get(i - 1) + link.getMinimumRunTime()[0][0];
                }
                arrivals.add(arrivalTime);
                departures.add(arrivalTime);
                eeSchedule.getEnterTimes().put(i + 1, arrivalTime);
                eeSchedule.getLeaveTimes().put(i + 1, arrivalTime);
            }
            lastNode = node;
        }
        curSol.getScheduleSkipStationMap().put(eeSchedule, skipStations);
        curSol.getScheduleStationArrivalTimeMap().put(eeSchedule, arrivals);
        curSol.getScheduleStationDepartureTimeMap().put(eeSchedule, departures);
        curSol.getScheduleStationTrackMap().put(eeSchedule, tracks);
        context.getSchedules().add(eeSchedule);
        context.getCourseId2Schedule().put(eeSchedule.getCourseId(), eeSchedule);
        context.getEeSchedules().add(eeSchedule);
        return eeSchedule;
    }

    private double getEndTimePlusEmptyRideTime(RollingStockDutyVertex tailVertex, RollingStockDutyVertex headVertex) {
        return headVertex.getEndTime() + context.getTimeMatrix()[headVertex.getEndNode()
            .getIndex()][tailVertex.getStartNode().getIndex()];
    }

    private void genInterScheduleEdges(List<RollingStockDutyVertex> filteredVertexList) {
        for (int i = 0; i < filteredVertexList.size() - 1; i++) {
            RollingStockDutyVertex headVertex = filteredVertexList.get(i);
            for (int j = i + 1; j < filteredVertexList.size(); j++) {
                RollingStockDutyVertex tailVertex = filteredVertexList.get(j);
                if (headVertex.getEndNode().equals(tailVertex.getStartNode()) && headVertex.getEndTime() +
                    Constants.CHANGE_END_TIME <= tailVertex.getStartTime()
                    && !name2Edge.containsKey(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()))) {
                    // 先考虑不带取消的情形，即采用时间前后顺序，同时前course终止地点和后course开始地点相同的约束
                    RollingStockDutyEdge edge = new RollingStockDutyEdge();
                    edge.setName(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()));
                    edge.setHead(headVertex);
                    edge.setTail(tailVertex);
                    headVertex.getOutArcList().add(edge);
                    tailVertex.getInArcList().add(edge);
                    edges.add(edge);
                    name2Edge.put(edge.getName(), edge);
                } else if (!headVertex.getEndNode().equals(tailVertex.getStartNode())
                    && getEndTimePlusEmptyRideTime(tailVertex, headVertex) + 2 * Constants.CHANGE_END_TIME <=
                    tailVertex.getStartTime()
                    && !name2Edge.containsKey(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()))) {
                    // 考虑空车调度的情形，一旦两个点之间有充足的时间（timeMatrix），可以发一部空车过去
                    RollingStockDutyVertex eeVertex = new RollingStockDutyVertex("EE" + context.getEeSchedules().
                        size() + "_0");
                    eeVertex.setStartTime((int) (tailVertex.getStartTime() - Constants.CHANGE_END_TIME -
                        context.getTimeMatrix()[headVertex.getEndNode().getIndex()][tailVertex.
                            getStartNode().getIndex()]));
                    eeVertex.setEndTime(tailVertex.getStartTime() - Constants.CHANGE_END_TIME);
                    eeVertex.setStartNode(headVertex.getEndNode());
                    eeVertex.setEndNode(tailVertex.getStartNode());
                    Schedule eeSchedule = calcEeSchedule(eeVertex);
                    eeVertex.setOrigCourse(eeSchedule);
                    vertexList.add(eeVertex);
                    name2Vertex.put(eeVertex.getName(), eeVertex);
                    RollingStockDutyEdge edge1 = new RollingStockDutyEdge();
                    edge1.setName(AbstractArc.genArcName(headVertex.getName(), eeVertex.getName()));
                    edge1.setHead(headVertex);
                    edge1.setTail(eeVertex);
                    headVertex.getOutArcList().add(edge1);
                    eeVertex.getInArcList().add(edge1);
                    edges.add(edge1);
                    name2Edge.put(edge1.getName(), edge1);
                    RollingStockDutyEdge edge2 = new RollingStockDutyEdge();
                    edge2.setName(AbstractArc.genArcName(eeVertex.getName(), tailVertex.getName()));
                    edge2.setHead(eeVertex);
                    edge2.setTail(tailVertex);
                    eeVertex.getOutArcList().add(edge2);
                    tailVertex.getInArcList().add(edge2);
                    edges.add(edge2);
                    name2Edge.put(edge2.getName(), edge2);
                }
                // TODO: 考虑partial cancellation等复杂一些的情形
            }
        }
    }

    private void genPlannedPathEdges() {
        RollingStockDutyVertex headVertex = name2Vertex.get(Constants.VIRTUAL_START_VERTEX_NAME);
        List<Schedule> plannedTrainSchedules = rollingStock.getSchedules().stream().filter(schedule -> schedule.
            getEventType().equals(Schedule.EventType.TRAIN)).collect(Collectors.toList());
        for (int i = 0; i < plannedTrainSchedules.size(); i++) {
            Schedule schedule = plannedTrainSchedules.get(i);
            RollingStockDutyVertex tailVertex = name2Vertex.get(schedule.getCourseId() + "_0");
            if (edgeTimeSufficient(headVertex, i, tailVertex)
                && !name2Edge.containsKey(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()))) {
                // 时间允许直接连接边，则按照计划时间产生边
                RollingStockDutyEdge edge = new RollingStockDutyEdge();
                edge.setName(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()));
                edge.setHead(headVertex);
                edge.setTail(tailVertex);
                headVertex.getOutArcList().add(edge);
                tailVertex.getInArcList().add(edge);
                edge.setTimeDiff(Math.max(0, headVertex.getEndTime() - tailVertex.getStartTime()));
                edges.add(edge);
                name2Edge.put(edge.getName(), edge);
            } else if (!edgeTimeSufficient(headVertex, i, tailVertex)
                && !name2Edge.containsKey(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()))) {
                // 时间不可达成的情况，放到后面考虑利用空车来串行
            }
            headVertex = tailVertex;
        }
        RollingStockDutyVertex tailVertex = name2Vertex.get(Constants.VIRTUAL_END_VERTEX_NAME);
        if (headVertex.getEndTime() <= tailVertex.getStartTime()) {
            RollingStockDutyEdge edge = new RollingStockDutyEdge();
            edge.setName(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()));
            edge.setHead(headVertex);
            edge.setTail(tailVertex);
            headVertex.getOutArcList().add(edge);
            tailVertex.getInArcList().add(edge);
            edges.add(edge);
            name2Edge.put(edge.getName(), edge);
        }
    }

    private boolean edgeTimeSufficient(RollingStockDutyVertex headVertex, int i, RollingStockDutyVertex tailVertex) {
        return firstEdgeTimeSufficient(headVertex, i, tailVertex) || otherEdgeTimeSufficient(headVertex, i, tailVertex);
    }

    private boolean otherEdgeTimeSufficient(RollingStockDutyVertex headVertex, int i, RollingStockDutyVertex tailVertex) {
        return i != 0
            && headVertex.getEndTime() + Constants.TIME_DIFF_TOL <= tailVertex.getStartTime();
    }

    private boolean firstEdgeTimeSufficient(RollingStockDutyVertex headVertex, int i, RollingStockDutyVertex tailVertex) {
        return i == 0 && headVertex.getEndTime() <= tailVertex.getStartTime();
    }

    private void genRealizedPathEdges() {
        Collections.sort(rollingStock.getRealizedSchedules());
        RollingStockDutyVertex headVertex = name2Vertex.get(Constants.VIRTUAL_START_VERTEX_NAME);
        for (Schedule schedule : rollingStock.getRealizedSchedules()) {
            RollingStockDutyVertex tailVertex = name2Vertex.get(schedule.getCourseId() + "_0");
            if (tailVertex == null) {
                // 这种情况说明已经实现的course超出了原有的duty的时间窗，目前我们认为这在Duty角度是合理的
                // 但是可能导致的问题是会有一些Rolling Stock没办法将这些Duty串起来
                RollingStockDutyVertex courseVertex = new RollingStockDutyVertex(schedule.
                    getCourseId() + "_" + 0);
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
                headVertex.getOutArcList().add(edge);
                tailVertex.getInArcList().add(edge);
                headVertex = tailVertex;
                edges.add(edge);
                name2Edge.put(edge.getName(), edge);
            } else {
                System.out.println("head: " + headVertex.getName() + ", tail: " + tailVertex.getName());
            }
        }
        RollingStockDutyVertex tailVertex = name2Vertex.get(Constants.VIRTUAL_END_VERTEX_NAME);
        if (rollingStock.getRealizedSchedules().size() < rollingStock.getSchedules().size()) {
            // 如果已发生的course少于计划的course，则不直接产生终点连接
            return;
        }
        if (headVertex.getEndNode().equals(tailVertex.getStartNode())) {
            RollingStockDutyEdge edge = new RollingStockDutyEdge();
            edge.setName(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()));
            edge.setHead(headVertex);
            edge.setTail(tailVertex);
            headVertex.getOutArcList().add(edge);
            tailVertex.getInArcList().add(edge);
            edges.add(edge);
            name2Edge.put(edge.getName(), edge);
        }
    }

    private void genScheduleChangeCopyVertices(List<Schedule> filteredSchedules) {
        for (Schedule schedule : filteredSchedules) {
            if (schedule.getRealizedNodeStatus().isEmpty() && schedule.getLateDeparture() == null) {
                // course尚未实现且无LateDeparture
                for (int change : courseStartTimeChange) {
                    RollingStockDutyVertex courseVertex = new RollingStockDutyVertex(schedule.
                        getCourseId() + "_" + change);
                    courseVertex.setChange(change);
                    courseVertex.setStartTime(schedule.getStartTime() + change);
                    courseVertex.setEndTime(schedule.getEndTime() + change);
                    courseVertex.setStartNode(schedule.getStartNode());
                    courseVertex.setEndNode(schedule.getEndNode());
                    courseVertex.setOrigCourse(schedule);
                    vertexList.add(courseVertex);
                    name2Vertex.put(courseVertex.getName(), courseVertex);
                }
            } else {
                // course已经实现，或者已经实现了一半，或者由于LateDeparture需要固定时间
                RollingStockDutyVertex courseVertex = new RollingStockDutyVertex(schedule.
                    getCourseId() + "_" + 0);
                // 注意在Solution
                courseVertex.setChange(0);
                courseVertex.setStartTime(curSol.getScheduleStationDepartureTimeMap().get(schedule).get(0));
                List<Integer> frontArrivals = curSol.getScheduleStationArrivalTimeMap().get(schedule);
                courseVertex.setEndTime(frontArrivals.get(frontArrivals.size() - 1));
                if (schedule.getLateDeparture() != null) {
                    // Late Departure
                    courseVertex.setStartNode(schedule.getPlannedNodes().get(0));
                } else {
                    // course至少部分实现
                    courseVertex.setStartNode(schedule.getRealizedNodes().get(0));
                }
                courseVertex.setEndNode(schedule.getEndNode());
                courseVertex.setOrigCourse(schedule);
                vertexList.add(courseVertex);
                name2Vertex.put(courseVertex.getName(), courseVertex);
            }
        }
    }

    private void genVirtualStartAndEndVertices() {
        RollingStockDutyVertex virtualStartVertex = new RollingStockDutyVertex(Constants.VIRTUAL_START_VERTEX_NAME);
        virtualStartVertex.setVirtual(true);
        vertexList.add(virtualStartVertex);
        name2Vertex.put(virtualStartVertex.getName(), virtualStartVertex);
        RollingStockDutyVertex virtualEndVertex = new RollingStockDutyVertex(Constants.VIRTUAL_END_VERTEX_NAME);
        virtualEndVertex.setVirtual(true);
        vertexList.add(virtualEndVertex);
        name2Vertex.put(virtualEndVertex.getName(), virtualEndVertex);
        Schedule headSchedule;
        Schedule tailSchedule;
        if (!curSol.getRollingStock2ScheduleListMap().getOrDefault(rollingStock, new ArrayList<>()).isEmpty()) {
            List<Schedule> schedules = curSol.getRollingStock2ScheduleListMap().get(rollingStock);
            headSchedule = schedules.get(0);
            tailSchedule = schedules.get(schedules.size() - 1);
            virtualStartVertex.setChange(0);
            virtualStartVertex.setStartTime(headSchedule.getStartTime());
            virtualStartVertex.setEndTime(headSchedule.getStartTime());
            virtualStartVertex.setStartNode(headSchedule.getStartNode());
            virtualStartVertex.setEndNode(headSchedule.getStartNode());
            virtualEndVertex.setChange(0);
            virtualEndVertex.setStartTime(tailSchedule.getEndTime());
            virtualEndVertex.setEndTime(tailSchedule.getEndTime());
            virtualEndVertex.setStartNode(tailSchedule.getEndNode());
            virtualEndVertex.setEndNode(tailSchedule.getEndNode());
        }
    }
}
