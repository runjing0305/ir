package graph.scgraph;

import static constant.Constants.OUTPUT_FLAG;
import static constant.Constants.PARTIAL_CANCELLATION_ALLOWED;

import constant.Constants;
import context.*;
import graph.AbstractArc;
import graph.Vertex;
import graph.rsdgraph.RollingStockDutyEdge;
import graph.rsdgraph.RollingStockDutyVertex;
import lombok.Getter;
import solution.Solution;
import solution.SolutionEvaluator;
import util.EvaluationUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @description:
 * @author: Shengcheng Shao
 * @date: 2022/7/28
 */
@Getter
public class SingleCommodityGraph {
    protected Solution curSol; // 当前解
    protected ProblemContext context; // 全问题情景
    protected List<RollingStockDutyVertex> vertexList = new ArrayList<>(); // 每个点代表一个Course的copy
    protected Map<String, RollingStockDutyVertex> name2Vertex = new HashMap<>();
    protected List<RollingStockDutyEdge> edges = new ArrayList<>(); // 每条边代表Course到Course之间的顺序执行关系
    protected Map<String, RollingStockDutyEdge> name2Edge = new HashMap<>();
    protected Set<RollingStockDutyEdge> realizedEdges = new HashSet<>(); // 记录已经发生的edge
    protected int minTime = Integer.MIN_VALUE;
    protected int maxTime = Integer.MAX_VALUE;

    public SingleCommodityGraph(ProblemContext context, Solution curSol) {
        this.context = context;
        this.curSol = curSol;
        makeGraph();
        indexing();
    }

    protected void makeGraph() {
        // 产生起点和终点
        genVirtualStartAndEndVertices();
        genCourseVertices();
        fillRollingStockRealizedSchedules();
        // 1、产生已发生的Schedule的连接边
        for (RollingStock rollingStock : context.getRollingStocks()) {
            genRealizedPathEdges(rollingStock);
            // 2、如果没有已发生的Schedule，或者没有把rolling stock的任务做完，则尝试按照Planned Schedule来连边
            if (rollingStock.getRealizedSchedules().isEmpty()
                || rollingStock.getRealizedSchedules().size() < rollingStock.getSchedules().size()) {
                genPlannedPathEdges(rollingStock);
            }
        }

        RollingStockDutyVertex startVertex = name2Vertex.get(Constants.VIRTUAL_START_VERTEX_NAME);
        RollingStockDutyVertex endVertex = name2Vertex.get(Constants.VIRTUAL_END_VERTEX_NAME);

        // 3、生成起点到终点的边
        if (!isEdgeExisting(startVertex, endVertex)) {
            RollingStockDutyEdge edge = genEdge(startVertex, endVertex);
            edge.setWeight(1.0);
        }

        // 抽取尚未发生的Course顶点
        List<RollingStockDutyVertex> unrealizedVertex = vertexList.stream().filter(vertex -> !vertex.isVirtual()
                && vertex.getOrigCourse().getRealizedNodeStatus().isEmpty()).collect(Collectors.toList());

        // 4、对未发生的Course，产生起点到顶点和顶点到终点的edge
        for (RollingStockDutyVertex vertex : unrealizedVertex) {
            genStart2UnrealizedVertexEdge(startVertex, vertex);
            genUnrealizedVertex2EndEdge(endVertex, vertex);
        }

        unrealizedVertex.sort(Comparator.comparingInt(RollingStockDutyVertex::getStartTime));
        // 5、生成unrealized vertex之间的一些互相连接的边，只要时间和地点对齐
        genEdgesBetweenTwoVertexLists(unrealizedVertex, unrealizedVertex);

        // 6、针对已经realized的rolling stock的当前schedule，产生其到unrealized vertex的边
        List<RollingStockDutyVertex> realizedCurrentVertex = new ArrayList<>();
        for (RollingStock rollingStock : context.getRollingStocks().stream().filter(rollingStock ->
            !rollingStock.getRealizedSchedules().isEmpty()).collect(Collectors.toList())) {
            List<Schedule> realizedSchedules = rollingStock.getRealizedSchedules();
            Schedule currentSchedule = realizedSchedules.get(realizedSchedules.size() - 1);
            realizedCurrentVertex.add(name2Vertex.get(currentSchedule.getCourseId() + "_0"));
        }
        genEdgesBetweenTwoVertexLists(unrealizedVertex, realizedCurrentVertex);
    }

    void genUnrealizedVertex2EndEdge(RollingStockDutyVertex endVertex, RollingStockDutyVertex vertex) {
        if (vertex.getEndNode().isDepot()
                && !isEdgeExisting(vertex, endVertex)) {
            RollingStockDutyEdge edge = genEdge(vertex, endVertex);
            edge.setWeight(1.0);
        } else if (!vertex.getEndNode().isDepot()
                && !isEdgeExisting(vertex, endVertex)) {
//            if (vertex.getOrigCourse().getPlannedNodes().stream().anyMatch(Node::isDepot)) {
//                // 如果可以中途停止在depot，则允许连到终点
//                RollingStockDutyEdge edge = genEdge(vertex, endVertex);
//                edge.setWeight(1.0);
//            }
        }
    }

    void genStart2UnrealizedVertexEdge(RollingStockDutyVertex startVertex, RollingStockDutyVertex vertex) {
        if (!isEdgeExisting(startVertex, vertex)
                && vertex.getStartNode().isDepot()) {
            RollingStockDutyEdge edge = genEdge(startVertex, vertex);
            edge.setWeight(1.0);
        }
    }

    private void genEE2Depot(RollingStockDutyVertex endVertex, RollingStockDutyVertex vertex) {
        // 先找到最近的depot点
        Node closestDepotNode = null;
        double minRunTime = Integer.MAX_VALUE;
        for (Node node : context.getNodes().stream().filter(Node::isDepot).collect(Collectors.toList())) {
            int runtime = (int) context.getTimeMatrix()[vertex.getEndNode().getIndex()][node.getIndex()];
            if (runtime > 0 && runtime < minRunTime) {
                closestDepotNode = node;
                minRunTime = runtime;
            }
        }
        // 考虑发空车到终点的course
        RollingStockDutyVertex eeVertex = new RollingStockDutyVertex("EE" + context.getEeSchedules().
                size() + "_0");
        assert closestDepotNode != null;
        eeVertex.setStartTime(vertex.getEndTime() + Constants.CHANGE_END_TIME);
        eeVertex.setEndTime((int) (vertex.getEndTime() + Constants.CHANGE_END_TIME +
                                context.getTimeMatrix()[vertex.getEndNode().getIndex()][closestDepotNode.getIndex()]));
        eeVertex.setStartNode(vertex.getEndNode());
        eeVertex.setEndNode(closestDepotNode);
        Schedule eeSchedule = calcEeSchedule(eeVertex);
        eeVertex.setOrigCourse(eeSchedule);
        vertexList.add(eeVertex);
        name2Vertex.put(eeVertex.getName(), eeVertex);
        RollingStockDutyEdge edge1 = genEdge(eeVertex, endVertex);
        edge1.setWeight(1.0);
        RollingStockDutyEdge edge2 = genEdge(vertex, eeVertex);
        edge2.setWeight(1.0);
    }

    void genEdgesBetweenTwoVertexLists(List<RollingStockDutyVertex> unrealizedVertex,
                                       List<RollingStockDutyVertex> realizedCurrentVertex) {
        for (int i = 0; i < realizedCurrentVertex.size(); i++) {
            RollingStockDutyVertex headVertex = realizedCurrentVertex.get(i);
            for (int j = i + 1; j < unrealizedVertex.size(); j++) {
                RollingStockDutyVertex tailVertex = unrealizedVertex.get(j);
                if (sufficientTimeForChangeEnd(headVertex, tailVertex)
                    && isHeadEndEqualsToTailStart(headVertex, tailVertex)
                        && !isEdgeExisting(headVertex, tailVertex)
                        && (headVertex.getEndNode().isDepot()
                        || isTimeNotTooLong(headVertex, tailVertex))
                ) {
                    RollingStockDutyEdge edge = genEdge(headVertex, tailVertex);
                    edge.setWeight(1.0);
                } else if (PARTIAL_CANCELLATION_ALLOWED
                        && !sufficientTimeForChangeEnd(headVertex, tailVertex)
                        && isHeadEndEqualsToTailStart(headVertex, tailVertex)
                        && !isEdgeExisting(headVertex, tailVertex)
                        && (headVertex.getEndNode().isDepot()
                        || isTimeNotTooLong(headVertex, tailVertex))) {
                    // 时间不可达成的情况，考虑利用局部取消来串行
                    RollingStockDutyEdge edge = new RollingStockDutyEdge();
                    edge.setName(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()));
                    edge.setHead(headVertex);
                    edge.setTail(tailVertex);
                    headVertex.getOutArcList().add(edge);
                    tailVertex.getInArcList().add(edge);
                    edge.setTimeDiff(Math.max(0, headVertex.getEndTime() - tailVertex.getStartTime()));
                    List<Node> headNodes = headVertex.getOrigCourse().getPlannedNodes();
                    List<Node> tailNodes = tailVertex.getOrigCourse().getPlannedNodes();
                    Set<Node> depotNodes = headNodes.stream().filter(Node::isDepot).filter(tailNodes::contains).
                            collect(Collectors.toSet());
                    if (depotNodes.isEmpty()) {
                        continue;
                    }
                    double minObj = Double.POSITIVE_INFINITY;
                    String partialCancelNode = null;
                    for (Node depotNode : depotNodes) {
                        int headIndex = headNodes.indexOf(depotNode);
                        int tailIndex = tailNodes.indexOf(depotNode);
                        List<Integer> headArrivals = curSol.getScheduleStationArrivalTimeMap().
                                get(headVertex.getOrigCourse());
                        List<Integer> headDepartures = curSol.getScheduleStationDepartureTimeMap().
                                get(headVertex.getOrigCourse());
                        int headArrivalTime = headArrivals.get(headIndex) == null? headDepartures.get(headIndex) :
                                headArrivals.get(headIndex);
                        List<Integer> tailArrivals = curSol.getScheduleStationArrivalTimeMap().
                                get(tailVertex.getOrigCourse());
                        List<Integer> tailDepartures = curSol.getScheduleStationDepartureTimeMap().
                                get(tailVertex.getOrigCourse());
                        int tailDepartureTime = tailDepartures.get(tailIndex) == null ? tailArrivals.get(tailIndex) :
                                tailDepartures.get(tailIndex);
                        if (tailDepartureTime - headArrivalTime >= Constants.TIME_DIFF_TOL) {
                            double obj = calcObj(headVertex, tailVertex, headNodes, tailNodes, headIndex, tailIndex);
                            if (obj < minObj) {
                                minObj = obj;
                                partialCancelNode = depotNode.getCode();
                            }
                        }
                    }
                    if (minObj < Double.POSITIVE_INFINITY) {
                        edge.setWeight(minObj);
                        edge.setPartialCancelNode(context.getCode2Node().get(partialCancelNode));
                        edges.add(edge);
                        name2Edge.put(edge.getName(), edge);
                    }
                }
            }
        }
    }

    private boolean isTimeNotTooLong(RollingStockDutyVertex headVertex, RollingStockDutyVertex tailVertex) {
        return tailVertex.getStartTime() - headVertex.getEndTime() <= Constants.NONE_DEPOT_TRAIN_WAIT_TIME;
    }

    private boolean isEdgeExisting(RollingStockDutyVertex headVertex, RollingStockDutyVertex tailVertex) {
        return name2Edge.containsKey(
                AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()));
    }

    private boolean isHeadEndEqualsToTailStart(RollingStockDutyVertex headVertex, RollingStockDutyVertex tailVertex) {
        return headVertex.getEndNode().equals(tailVertex.getStartNode());
    }

    private boolean sufficientTimeForChangeEnd(RollingStockDutyVertex headVertex, RollingStockDutyVertex tailVertex) {
        return headVertex.getEndTime() + EvaluationUtils.getChangeEndBetweenConsecutiveCourses(context, headVertex.
                getOrigCourse(), tailVertex.getOrigCourse()) <= tailVertex.getStartTime();
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
            Optional<Track> track = node.getTracks().stream().filter(track1 -> track1.getDirection().
                            equals(Track.Direction.BOTH) || track1.getDirection().equals(
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

    RollingStockDutyEdge genEdge(RollingStockDutyVertex headVertex, RollingStockDutyVertex tailVertex) {
        RollingStockDutyEdge edge = new RollingStockDutyEdge();
        edge.setName(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()));
        edge.setHead(headVertex);
        edge.setTail(tailVertex);
        headVertex.getOutArcList().add(edge);
        tailVertex.getInArcList().add(edge);
        edge.setTimeDiff(Math.max(0, headVertex.getEndTime() - tailVertex.getStartTime()));
        if (name2Edge.containsKey(edge.getName())) {
            if (OUTPUT_FLAG) {
                System.out.println(edge.getName() + " already exists");
            }
            // return null;
        }
        edges.add(edge);
        name2Edge.put(edge.getName(), edge);
        return edge;
    }

    void genPlannedPathEdges(RollingStock rollingStock) {
        RollingStockDutyVertex headVertex = name2Vertex.get(Constants.VIRTUAL_START_VERTEX_NAME);
        List<Schedule> plannedTrainSchedules = rollingStock.getSchedules().stream().filter(schedule -> schedule.
            getEventType().equals(Schedule.EventType.TRAIN)).collect(Collectors.toList());
        for (int i = 0; i < plannedTrainSchedules.size(); i++) {
            Schedule schedule = plannedTrainSchedules.get(i);
            RollingStockDutyVertex tailVertex = name2Vertex.get(schedule.getCourseId() + "_0");
            if (edgeTimeSufficient(headVertex, i, tailVertex)
                && !isEdgeExisting(headVertex, tailVertex)) {
                // 时间允许直接连接边，则按照计划时间产生边
                genEdge(headVertex, tailVertex);
            } else if (PARTIAL_CANCELLATION_ALLOWED && !edgeTimeSufficient(headVertex, i, tailVertex)
                && !isEdgeExisting(headVertex, tailVertex)) {
                // 时间不可达成的情况，考虑利用局部取消来串行
                RollingStockDutyEdge edge = new RollingStockDutyEdge();
                edge.setName(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()));
                edge.setHead(headVertex);
                edge.setTail(tailVertex);
                headVertex.getOutArcList().add(edge);
                tailVertex.getInArcList().add(edge);
                edge.setTimeDiff(Math.max(0, headVertex.getEndTime() - tailVertex.getStartTime()));
                List<Node> headNodes = headVertex.getOrigCourse().getPlannedNodes();
                List<Node> tailNodes = tailVertex.getOrigCourse().getPlannedNodes();
                Set<Node> depotNodes = headNodes.stream().filter(Node::isDepot).filter(tailNodes::contains).
                    collect(Collectors.toSet());
                if (depotNodes.isEmpty()) {
                    continue;
                }
                double minObj = Double.POSITIVE_INFINITY;
                String partialCancelNode = null;
                for (Node depotNode : depotNodes) {
                    int headIndex = headNodes.indexOf(depotNode);
                    int tailIndex = tailNodes.indexOf(depotNode);
                    List<Integer> headArrivals = curSol.getScheduleStationArrivalTimeMap().
                            get(headVertex.getOrigCourse());
                    List<Integer> headDepartures = curSol.getScheduleStationDepartureTimeMap().
                            get(headVertex.getOrigCourse());
                    int headArrivalTime = headArrivals.get(headIndex) == null? headDepartures.get(headIndex) :
                            headArrivals.get(headIndex);
                    List<Integer> tailArrivals = curSol.getScheduleStationArrivalTimeMap().
                            get(tailVertex.getOrigCourse());
                    List<Integer> tailDepartures = curSol.getScheduleStationDepartureTimeMap().
                            get(tailVertex.getOrigCourse());
                    int tailDepartureTime = tailDepartures.get(tailIndex) == null ? tailArrivals.get(tailIndex) :
                            tailDepartures.get(tailIndex);
                    if (tailDepartureTime - headArrivalTime >= Constants.TIME_DIFF_TOL) {
                        double obj = calcObj(headVertex, tailVertex, headNodes, tailNodes, headIndex, tailIndex);
                        if (obj < minObj) {
                            minObj = obj;
                            partialCancelNode = depotNode.getCode();
                        }
                    }
                }
                if (minObj < Double.POSITIVE_INFINITY) {
                    edge.setWeight(minObj);
                    edge.setPartialCancelNode(context.getCode2Node().get(partialCancelNode));
                    edges.add(edge);
                    name2Edge.put(edge.getName(), edge);
                }
            }
            headVertex = tailVertex;
        }
        RollingStockDutyVertex tailVertex = name2Vertex.get(Constants.VIRTUAL_END_VERTEX_NAME);
        if (isEdgeExisting(headVertex, tailVertex)) {
            return;
        }
        RollingStockDutyEdge edge = new RollingStockDutyEdge();
        edge.setName(AbstractArc.genArcName(headVertex.getName(), tailVertex.getName()));
        edge.setHead(headVertex);
        edge.setTail(tailVertex);
        headVertex.getOutArcList().add(edge);
        tailVertex.getInArcList().add(edge);
        edges.add(edge);
        name2Edge.put(edge.getName(), edge);
    }

    private double calcObj(RollingStockDutyVertex headVertex, RollingStockDutyVertex tailVertex,
                           List<Node> headNodes, List<Node> tailNodes, int headIndex, int tailIndex) {
        double obj = 0;
        Solution solution = new Solution(curSol);
        List<Boolean> headSkipSt = solution.getScheduleSkipStationMap().get(headVertex.getOrigCourse());
        for (int j = 0; j < headSkipSt.size(); j++) {
            if (j > headIndex) {
                headSkipSt.set(j, Boolean.TRUE);
            }
        }
        solution.getScheduleSkipStationMap().put(headVertex.getOrigCourse(), headSkipSt);
        List<Integer> headBsvList = new ArrayList<>();
        SolutionEvaluator.fillNodeListBsvList(solution, headVertex.getOrigCourse(), headBsvList, headNodes, context);
        for (int k = 0; k < headBsvList.size(); k++) {
            int multiplier = 1;
            if (k == 0) {
                multiplier = Constants.FIRST_SKIP_STOP_MULTIPLIER;
            } else if (k == 1) {
                multiplier = Constants.SECOND_SKIP_STOP_MULTIPLIER;
            }
            obj += multiplier * headBsvList.get(k);
        }
        List<Boolean> tailSkipSt = solution.getScheduleSkipStationMap().get(tailVertex.getOrigCourse());
        for (int j = 0; j < tailSkipSt.size(); j++) {
            if (j < tailIndex) {
                tailSkipSt.set(j, Boolean.TRUE);
            }
        }
        solution.getScheduleSkipStationMap().put(tailVertex.getOrigCourse(), tailSkipSt);
        List<Integer> tailBsvList = new ArrayList<>();
        SolutionEvaluator.fillNodeListBsvList(solution, tailVertex.getOrigCourse(), tailBsvList, tailNodes, context);
        for (int k = 0; k < tailBsvList.size(); k++) {
            int multiplier = 1;
            if (k == 0) {
                multiplier = Constants.FIRST_SKIP_STOP_MULTIPLIER;
            } else if (k == 1) {
                multiplier = Constants.SECOND_SKIP_STOP_MULTIPLIER;
            }
            obj += multiplier * tailBsvList.get(k);
        }
        return obj;
    }

    private boolean edgeTimeSufficient(RollingStockDutyVertex headVertex, int i, RollingStockDutyVertex tailVertex) {
        return firstEdgeTimeSufficient(headVertex, i, tailVertex) || otherEdgeTimeSufficient(headVertex, i, tailVertex);
    }

    private boolean otherEdgeTimeSufficient(RollingStockDutyVertex headVertex, int i, RollingStockDutyVertex tailVertex) {
        return i != 0
            && sufficientTimeForChangeEnd(headVertex, tailVertex);
    }

    private boolean firstEdgeTimeSufficient(RollingStockDutyVertex headVertex, int i, RollingStockDutyVertex tailVertex) {
        return i == 0 && headVertex.getEndTime() <= tailVertex.getStartTime();
    }


    protected void genRealizedPathEdges(RollingStock rollingStock) {
        Collections.sort(rollingStock.getRealizedSchedules());
        RollingStockDutyVertex headVertex = name2Vertex.get(Constants.VIRTUAL_START_VERTEX_NAME);
        for (Schedule schedule : rollingStock.getRealizedSchedules()) {
            RollingStockDutyVertex tailVertex = name2Vertex.get(schedule.getCourseId() + "_0");
            if (headVertex.getEndTime() <= tailVertex.getStartTime()) {
                realizedEdges.add(genEdge(headVertex, tailVertex));
                headVertex = tailVertex;
            } else {
                System.out.println("Head " + headVertex.getName() + " is later than tail " + tailVertex.getName());
            }
        }
        // 生成RollingStock当前所在顶点到终点的边,前提条件是headVertex的Course的终点是depot
        if (!headVertex.isVirtual() && headVertex.getOrigCourse().getEndNode().isDepot()) {
            RollingStockDutyVertex tailVertex = name2Vertex.get(Constants.VIRTUAL_END_VERTEX_NAME);
            genEdge(headVertex, tailVertex);
        }
    }

    void fillRollingStockRealizedSchedules() {
        for (Schedule schedule : context.getSchedules()) {
            if (!schedule.getRealizedNodeStatus().isEmpty()) {
                // 代表Course至少一部分站点已经被到达
                curSol.getSchedule2RollingStockMap().get(schedule).getRealizedSchedules().add(schedule);
            }
        }
    }

    private void genCourseVertices() {
        for (Schedule schedule : context.getSchedules()) {
            RollingStockDutyVertex courseVertex = new RollingStockDutyVertex(schedule.
                getCourseId() + "_0");
            courseVertex.setChange(0);
            List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() :
                    schedule.getRealizedNodes();
            List<Integer> arrivals = curSol.getScheduleStationArrivalTimeMap().get(schedule);
            List<Integer> departures = curSol.getScheduleStationDepartureTimeMap().get(schedule);
            courseVertex.setStartTime(departures.get(0));
            courseVertex.setEndTime(arrivals.get(arrivals.size() - 1));
            courseVertex.setStartNode(nodeList.get(0));
            courseVertex.setEndNode(nodeList.get(nodeList.size() - 1));
            courseVertex.setOrigCourse(schedule);
            vertexList.add(courseVertex);
            name2Vertex.put(courseVertex.getName(), courseVertex);
        }
    }

    private void adjustScheduleStartAndEnd(List<Schedule> filteredSchedules) {
        for (Schedule schedule : filteredSchedules) {
            // 根据解调整schedule的开始和结束时间
            List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() :
                schedule.getRealizedNodes();
            List<Integer> arrivals = curSol.getScheduleStationArrivalTimeMap().get(schedule);
            List<Integer> departures = curSol.getScheduleStationDepartureTimeMap().get(schedule);
            schedule.setStartTime(departures.get(0));
            schedule.setEndTime(arrivals.get(arrivals.size() - 1));
            schedule.setStartNode(nodeList.get(0));
            schedule.setEndNode(nodeList.get(nodeList.size() - 1));
        }
    }

    protected void genVirtualStartAndEndVertices() {
        RollingStockDutyVertex virtualStartVertex = new RollingStockDutyVertex(Constants.VIRTUAL_START_VERTEX_NAME);
        virtualStartVertex.setVirtual(true);
        virtualStartVertex.setStartTime(minTime);
        virtualStartVertex.setEndTime(minTime);
        vertexList.add(virtualStartVertex);
        name2Vertex.put(virtualStartVertex.getName(), virtualStartVertex);
        RollingStockDutyVertex virtualEndVertex = new RollingStockDutyVertex(Constants.VIRTUAL_END_VERTEX_NAME);
        virtualEndVertex.setVirtual(true);
        virtualEndVertex.setStartTime(maxTime);
        virtualEndVertex.setEndTime(maxTime);
        vertexList.add(virtualEndVertex);
        name2Vertex.put(virtualEndVertex.getName(), virtualEndVertex);
    }

    protected void indexing() {
        for (int i = 0; i < vertexList.size(); i++) {
            RollingStockDutyVertex vertex = vertexList.get(i);
            vertex.setIndex(i);
        }
        for (int i = 0; i < edges.size(); i++) {
            RollingStockDutyEdge edge = edges.get(i);
            edge.setIndex(i);
        }
    }
}
