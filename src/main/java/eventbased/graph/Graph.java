package eventbased.graph;

import constant.Constants;
import context.*;
import entity.BaseStationValue;
import eventbased.model.EventBasedModel;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;
import solution.Solution;
import util.EvaluationUtils;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.IntStream;

/**
 * @author longfei
 */
@Data
public class Graph {
    private static final Logger LOGGER = Logger.getLogger(Graph.class.getName());

    private static final int TIME_RANGE = 3600;
    private static final int TIME_HORIZON_EXTENDED_TIME = 1800;
    private static final int BEFORE_COPY_NUM = 3;
    private static final int AFTER_COPY_NUM = 3;

    private static final int EVENT_BASED_MODEL_DELTA_TIME = 300;

    private Map<VertexType, Set<Vertex>> vertexTypeListMap = new HashMap<>();
    private Map<String, Vertex> vertexMap = new HashMap<>();
    private Map<Integer, Vertex> trainVertexMap = new HashMap<>();
    private Map<String, Set<Vertex>> dutyStartVertexMap = new HashMap<>();
    private Map<String, Set<Vertex>> dutyEndVertexMap = new HashMap<>();
    private Map<String, Set<Vertex>> courseStartVertexMap = new HashMap<>();
    private Map<String, Set<Vertex>> courseEndVertexMap = new HashMap<>();
    private Map<String, Map<Integer, Set<Vertex>>> courseNodeVertexMap = new HashMap<>();
    private Map<EdgeType, List<Edge>> edgeTypeListMap = new HashMap<>();
    private Map<Vertex, List<Edge>> headVertexEdgeListMap = new HashMap<>();
    private Map<Vertex, List<Edge>> tailVertexEdgeListMap = new HashMap<>();

    private int vertexIndex = 0;
    private int timeHorizonStart = Integer.MAX_VALUE;
    private int timeHorizonEnd = 0;
    private boolean buildGraphBasedOnPlannedSchedules = true;

    private int currentTime = 0;
    private int beforeCopyNum = BEFORE_COPY_NUM;
    private int afterCopyNum = AFTER_COPY_NUM;
    private int deltaTime = EVENT_BASED_MODEL_DELTA_TIME;

    private List<Pair<Integer, Integer>> wbReferenceStationArrivalTimeBandList = new ArrayList<>();
    private List<Pair<Integer, Integer>> ebReferenceStationArrivalTimeBandList = new ArrayList<>();

    private Map<RollingStock, Map<String, Integer>> rollingStockDutyStartTimeMap = new HashMap<>();
    private Map<RollingStock, Map<String, Integer>> rollingStockDutyEndTimeMap = new HashMap<>();

    private Map<RollingStock, List<PartialCancellationCandidate>> rollingStockPartialCancellationCandidateListMap = new HashMap<>();

    public Graph() {
    }

    public Graph(int beforeCopyNum, int afterCopyNum, int deltaTime) {
        this.beforeCopyNum = beforeCopyNum;
        this.afterCopyNum = afterCopyNum;
        this.deltaTime = deltaTime;
    }

    public void build(ProblemContext problemContext, Solution initialSolution, Set<String> updatedCourses) {
        this.calTimeHorizon(problemContext, initialSolution);

        if (this.buildGraphBasedOnPlannedSchedules) {
            this.sortDutyAndSchedule(problemContext, initialSolution);
            this.findPartialCancellationCandidates(problemContext, initialSolution);
            this.buildEdgeListBasedOnPlannedSchedules(problemContext, initialSolution, null, null, updatedCourses);
            for (Map.Entry<RollingStock, List<PartialCancellationCandidate>> entry : this.rollingStockPartialCancellationCandidateListMap.entrySet()) {
                for (PartialCancellationCandidate partialCancellationCandidate : entry.getValue()) {
                    this.buildEdgeListBasedOnPlannedSchedules(problemContext, initialSolution, entry.getKey(), partialCancellationCandidate, updatedCourses);
                }
            }
            this.removeDuplicatedEdges();
            this.buildHeadTailVertexEdgeListMap();
            this.removeUnbalancedVertexEdge();
            this.calculateDestinationDelayPenalty(problemContext, initialSolution);
            this.calculateBsv(problemContext, initialSolution);
            this.calculateMinimumRunTimeViolation(problemContext, initialSolution);
            this.calculateChangeEndTimeViolation(problemContext, initialSolution);
            this.calculatePartialCancellationSkippedStopsPenalty(problemContext, initialSolution);

            this.reportVertexInfo();
            this.reportEdgeInfo();
        } else {
            this.buildVertexList(problemContext);
            this.reportVertexInfo();
            this.buildEdgeList(problemContext, initialSolution);
        }
    }

    public void calTimeHorizon(ProblemContext problemContext, Solution solution) {
        for (Duty duty : problemContext.getDuties()) {
            timeHorizonStart = Math.min(duty.getStartTime(), timeHorizonStart);
            timeHorizonEnd = Math.max(duty.getEndTime(), timeHorizonEnd);
        }

        for (Map.Entry<RollingStock, List<Schedule>> entry : solution.getRollingStock2ScheduleListMap().entrySet()) {
            for (Schedule schedule : entry.getValue()) {
                int start = solution.getScheduleStationDepartureTimeMap().get(schedule).get(0);
                List<Integer> arrivalTimeList = solution.getScheduleStationArrivalTimeMap().get(schedule);
                int end = arrivalTimeList.get(arrivalTimeList.size() - 1);

                timeHorizonStart = Math.min(start, timeHorizonStart);
                timeHorizonEnd = Math.max(end, timeHorizonEnd);

                List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.getRealizedNodes();
                if (!schedule.getRealizedEnterTimes().isEmpty()) {
                    for (int i = 0; i < nodeList.size(); ++i) {
                        int realizedEnterTime = schedule.getRealizedEnterTimes().get(i + 1);
                        if (realizedEnterTime != 0) {
                            currentTime = Math.max(currentTime, realizedEnterTime);
                        }
                    }
                }
                if (!schedule.getRealizedLeaveTimes().isEmpty()) {
                    for (int i = 0; i < nodeList.size(); ++i) {
                        int realizedLeaveTime = schedule.getRealizedLeaveTimes().get(i + 1);
                        if (realizedLeaveTime != 0) {
                            currentTime = Math.max(currentTime, realizedLeaveTime);
                        }
                    }
                }
            }
        }

        timeHorizonStart = Math.max(timeHorizonStart, 0);
        timeHorizonStart = timeHorizonStart / this.deltaTime * this.deltaTime;
        timeHorizonEnd = (timeHorizonEnd / this.deltaTime + 1) * this.deltaTime + TIME_HORIZON_EXTENDED_TIME;
    }

    public void buildVertexList(ProblemContext problemContext) {
        this.buildTrainVertexList(problemContext);
        LOGGER.info("Build Train Vertex Complete.\n");
        this.buildRollingStockStartEndVertexList(problemContext);
        LOGGER.info("Build Rolling Stock Start End Vertex Complete.\n");
        this.buildCourseStartEndVertexList(problemContext);
        LOGGER.info("Build Course Start End Vertex Complete.\n");
        this.buildNodeVertexList(problemContext);
        LOGGER.info("Build Node Vertex Complete.\n");
        LOGGER.info("Build Vertex Complete.\n");
    }

    private void buildTrainVertexList(ProblemContext problemContext) {
        // Train Vertex
        Vertex trainVertex = new Vertex(vertexIndex, VertexType.TRAIN.name(), null, VertexType.TRAIN, -1, null, null, -1, false);
        ++vertexIndex;
        vertexTypeListMap.computeIfAbsent(VertexType.TRAIN, k -> new HashSet<>()).add(trainVertex);
    }

    private void buildRollingStockStartEndVertexList(ProblemContext problemContext) {
        for (Duty duty : problemContext.getDuties()) {
            int startTime = duty.getStartTime();
            int endTime = duty.getEndTime();

            for (VertexType vertexType : Arrays.asList(VertexType.DUTY_START, VertexType.DUTY_END)) {
                int plannedTime = (VertexType.DUTY_START == vertexType) ? startTime : endTime;
                int earliestTime = Math.max(plannedTime - TIME_RANGE, 0);
                int latestTime = Math.min(plannedTime + TIME_RANGE, timeHorizonEnd);

                for (int time = earliestTime; time <= latestTime; time += this.deltaTime) {
                    Vertex dutyVertex = new Vertex(vertexIndex, duty.getDutyId(), null, vertexType, time, null, null, -1, false);
                    ++vertexIndex;

                    vertexTypeListMap.computeIfAbsent(vertexType, k -> new HashSet<>()).add(dutyVertex);
                }
            }
        }
    }

    private void buildCourseStartEndVertexList(ProblemContext problemContext) {
        for (Schedule schedule : problemContext.getSchedules()) {
            if (Schedule.EventType.TRAIN != schedule.getEventType()) {
                continue;
            }

            int startTime = schedule.getStartTime();
            int endTime = schedule.getEndTime();

            for (VertexType vertexType : Arrays.asList(VertexType.COURSE_START, VertexType.COURSE_END)) {
                int plannedTime = (VertexType.COURSE_START == vertexType) ? startTime : endTime;
                int earliestTime = Math.max(plannedTime - TIME_RANGE, 0);
                int latestTime = Math.min(plannedTime + TIME_RANGE, timeHorizonEnd);

                for (int time = earliestTime; time <= latestTime; time += this.deltaTime) {
                    Vertex courseVertex = new Vertex(vertexIndex, schedule.getCourseId(), null, vertexType, time, schedule.getDirection(), schedule.getCourseId(), -1, false);
                    ++vertexIndex;
                    vertexTypeListMap.computeIfAbsent(vertexType, k -> new HashSet<>()).add(courseVertex);
                }
            }
        }
    }

    private void buildNodeVertexList(ProblemContext problemContext) {
        int timeIntervalNum = (timeHorizonEnd - timeHorizonStart) / this.deltaTime;

        Map<Node, Integer> nodeEarliestTimeMap = new HashMap<>(problemContext.getNodes().size());
        Map<Node, Integer> nodeLatestTimeMap = new HashMap<>(problemContext.getNodes().size());
        Map<Node, Boolean> allPassActivityMap = new HashMap<>(problemContext.getNodes().size());
        for (Schedule schedule : problemContext.getSchedules()) {
            for (int i = 0; i < schedule.getPlannedNodes().size(); ++i) {
                Node tmpNode = schedule.getPlannedNodes().get(i);
                int time;
                if (i == 0) {
                    time = schedule.getLeaveTimes().get(1);
                } else if (i == schedule.getPlannedNodes().size() - 1) {
                    time = schedule.getEnterTimes().get(schedule.getEnterTimes().size() + 1);
                } else {
                    time = schedule.getEnterTimes().get(i + 1);
                }

                if (ActivityType.STOP.getValue().equals(schedule.getNodeStatus().getOrDefault(i + 1, ActivityType.STOP.getValue()))) {
                    allPassActivityMap.put(tmpNode, false);
                }

                nodeEarliestTimeMap.put(tmpNode, Math.min(time, nodeEarliestTimeMap.getOrDefault(tmpNode, timeHorizonEnd)));
                nodeLatestTimeMap.put(tmpNode, Math.max(time, nodeLatestTimeMap.getOrDefault(tmpNode, 0)));
            }
        }

        // Create node vertex
        for (Map.Entry<String, Node> nodeEntry : problemContext.getCode2Node().entrySet()) {
            boolean firstNodeInCourse = false;
            for (Schedule schedule : nodeEntry.getValue().getSchedules()) {
                if (Schedule.EventType.TRAIN != schedule.getEventType()) {
                    continue;
                }

                if (schedule.getPlannedNodes().get(0) == nodeEntry.getValue()) {
                    firstNodeInCourse = true;
                    break;
                }
            }

            for (Map.Entry<String, Track> trackEntry : nodeEntry.getValue().getName2Track().entrySet()) {
                for (VertexType vertexType : Arrays.asList(VertexType.NODE_STOP, VertexType.NODE_LEAVE, VertexType.NODE_PASS, VertexType.NODE_STOP_LEAVE)) {
                    if (VertexType.NODE_STOP_LEAVE == vertexType && !firstNodeInCourse) {
                        continue;
                    }

                    if (Node.Type.JUNCTION == nodeEntry.getValue().getType()) {
                        if (VertexType.NODE_STOP == vertexType || VertexType.NODE_LEAVE == vertexType) {
                            continue;
                        }
                    }

                    if (allPassActivityMap.getOrDefault(nodeEntry.getValue(), true)) {
                        if (VertexType.NODE_STOP == vertexType || VertexType.NODE_LEAVE == vertexType) {
                            continue;
                        }
                    }

                    for (int i = 0; i < timeIntervalNum; ++i) {
                        int tmpTime = timeHorizonStart + i * this.deltaTime;

                        if (tmpTime < nodeEarliestTimeMap.getOrDefault(nodeEntry.getValue(), 0) || tmpTime > nodeLatestTimeMap.getOrDefault(nodeEntry.getValue(), timeHorizonEnd)) {
                            continue;
                        }

                        Track.Direction direction = trackEntry.getValue().getDirection();
                        Vertex nodeVertex = new Vertex(vertexIndex, nodeEntry.getValue().getCode(), trackEntry.getValue().getName(), vertexType, tmpTime, direction, null, -1, false);
                        vertexIndex++;
                        vertexTypeListMap.computeIfAbsent(vertexType, k -> new HashSet<>()).add(nodeVertex);
                    }
                }
            }
        }
    }

    private void reportVertexInfo() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(String.format("\n%20s | %20s\n", "Vertex Type", "Vertex Number"));
        int totalNum = 0;
        for (Map.Entry<VertexType, Set<Vertex>> entry : vertexTypeListMap.entrySet()) {
            stringBuilder.append(String.format("%20s | %20s\n", entry.getKey(), entry.getValue().size()));
            totalNum += entry.getValue().size();
        }

        stringBuilder.append(String.format("%20s | %20s\n", "Total", totalNum));

        LOGGER.info(stringBuilder.toString());
    }

    private void reportEdgeInfo() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(String.format("\n%40s | %20s\n", "Edge Type", "Edge Number"));
        int totalNum = 0;
        for (Map.Entry<EdgeType, List<Edge>> entry : edgeTypeListMap.entrySet()) {
            stringBuilder.append(String.format("%40s | %20s\n", entry.getKey(), entry.getValue().size()));
            totalNum += entry.getValue().size();
        }

        stringBuilder.append(String.format("%40s | %20s\n", "Total", totalNum));

        LOGGER.info(stringBuilder.toString());
    }

    public void buildEdgeList(ProblemContext problemContext, Solution solution) {
        for (EdgeType edgeType : EdgeType.values()) {
            if (EdgeType.CROSS_STATION_NODE_TO_NODE == edgeType) {
                continue;
            }

            for (VertexType headVertexType : edgeType.getHeadVertexTypeSet()) {
                for (VertexType tailVertexType : edgeType.getTailVertexTypeSet()) {
                    for (Vertex headVertex : vertexTypeListMap.getOrDefault(headVertexType, new HashSet<>())) {
                        for (Vertex tailVertex : vertexTypeListMap.getOrDefault(tailVertexType, new HashSet<>())) {
                            if (edgeType.getEdgeInfeasibleList().stream().anyMatch(edgeFeasible -> edgeFeasible.isInfeasible(headVertex, tailVertex, problemContext, solution))) {
                                continue;
                            }

                            Edge edge = new Edge();
                            edge.setHead(headVertex);
                            edge.setTail(tailVertex);
                            edge.setEdgeType(edgeType);
                            edgeTypeListMap.computeIfAbsent(edgeType, k -> new ArrayList<>()).add(edge);
                            headVertexEdgeListMap.computeIfAbsent(headVertex, k -> new ArrayList<>()).add(edge);
                            tailVertexEdgeListMap.computeIfAbsent(tailVertex, k -> new ArrayList<>()).add(edge);
                        }
                    }
                }
            }
        }

        // Node -> Node (Cross stations)
        for (Map.Entry<String, Link> linkEntry : problemContext.getName2Link().entrySet()) {
            String headNodeCode = linkEntry.getValue().getStartNode().getCode();
            String tailNodeCode = linkEntry.getValue().getEndNode().getCode();
            Track.Direction direction = linkEntry.getValue().getDirection();

            EdgeType edgeType = EdgeType.CROSS_STATION_NODE_TO_NODE;
            for (VertexType headVertexType : edgeType.getHeadVertexTypeSet()) {
                for (VertexType tailVerType : edgeType.getTailVertexTypeSet()) {
                    for (Vertex headVertex : vertexTypeListMap.getOrDefault(headVertexType, new HashSet<>())) {
                        if (!headNodeCode.equals(headVertex.getId()) || direction != headVertex.getDirection()) {
                            continue;
                        }

                        for (Vertex tailVertex : vertexTypeListMap.getOrDefault(tailVerType, new HashSet<>())) {
                            if (!tailNodeCode.equals(tailVertex.getId()) || direction != tailVertex.getDirection()) {
                                continue;
                            }

                            if (edgeType.getEdgeInfeasibleList().stream().anyMatch(edgeInfeasible -> edgeInfeasible.isInfeasible(headVertex, tailVertex, problemContext, solution))) {
                                continue;
                            }

                            Edge edge = new Edge();
                            edge.setHead(headVertex);
                            edge.setTail(tailVertex);
                            edge.setEdgeType(edgeType);
                            edgeTypeListMap.computeIfAbsent(edgeType, k -> new ArrayList<>()).add(edge);
                            headVertexEdgeListMap.computeIfAbsent(headVertex, k -> new ArrayList<>()).add(edge);
                            tailVertexEdgeListMap.computeIfAbsent(tailVertex, k -> new ArrayList<>()).add(edge);
                        }
                    }
                }
            }
        }
    }

    public void sortDutyAndSchedule(ProblemContext problemContext, Solution solution) {
        for (RollingStock rollingStock : solution.getRollingStock2ScheduleListMap().keySet()) {
            List<String> dutyIdList = solution.getRollingStock2DutyListMap().get(rollingStock);
            Map<String, Integer> dutyStartTimeMap = new HashMap<>();
            Map<String, Integer> dutyEndTimeMap = new HashMap<>();
            for (String dutyId : dutyIdList) {
                List<Schedule> scheduleList = solution.getDuty2ScheduleListMap().get(dutyId);
                int startTime = Integer.MAX_VALUE;
                int endTime = 0;
                for (Schedule schedule : scheduleList) {
                    List<Integer> departureTimeList = solution.getScheduleStationDepartureTimeMap().get(schedule);
                    List<Integer> arrivalTimeList = solution.getScheduleStationArrivalTimeMap().get(schedule);

                    startTime = Math.min(startTime, departureTimeList.get(0));
                    endTime = Math.max(endTime, arrivalTimeList.get(arrivalTimeList.size() - 1));
                }

                dutyStartTimeMap.put(dutyId, startTime);
                dutyEndTimeMap.put(dutyId, endTime);
            }
            dutyIdList.sort(Comparator.comparingInt(dutyStartTimeMap::get));

            this.rollingStockDutyStartTimeMap.put(rollingStock, dutyStartTimeMap);
            this.rollingStockDutyEndTimeMap.put(rollingStock, dutyEndTimeMap);

            for (String dutyId : dutyIdList) {
                List<Schedule> schedules = solution.getDuty2ScheduleListMap().get(dutyId);
                schedules.sort(Comparator.comparing(s -> solution.getScheduleStationDepartureTimeMap().get(s).get(0)));
            }
        }
    }

    public void findPartialCancellationCandidates(ProblemContext problemContext, Solution solution) {
        if (!Constants.ALLOW_PARTIAL_CANCELLATION_IN_EVENT_BASED_MODEL) {
            return;
        }
        int counter = 0;
        for (RollingStock rollingStock : solution.getRollingStock2ScheduleListMap().keySet()) {
            List<String> dutyIdList = solution.getRollingStock2DutyListMap().get(rollingStock);

            for (int i = 0; i < dutyIdList.size(); ++i) {
                String dutyId = dutyIdList.get(i);

                List<Schedule> dutyScheduleList = solution.getDuty2ScheduleListMap().get(dutyId);
                for (int scheduleIndex = 0; scheduleIndex < dutyScheduleList.size(); ++scheduleIndex) {
                    Schedule schedule = dutyScheduleList.get(scheduleIndex);
                    if (Schedule.EventType.TRAIN != schedule.getEventType()) {
                        continue;
                    }

                    if (scheduleIndex == dutyScheduleList.size() - 1) {
                        continue;
                    }

                    Schedule nextSchedule = dutyScheduleList.get(scheduleIndex + 1);

                    List<Integer> scheduleArrivalTimeList = solution.getScheduleStationArrivalTimeMap().get(schedule);

                    List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.getRealizedNodes();
                    for (int nodeIndex = 0; nodeIndex < nodeList.size(); ++nodeIndex) {
                        Node currentNode = nodeList.get(nodeIndex);
                        boolean departureRealized = false;
                        if (!schedule.getRealizedLeaveTimes().isEmpty() && schedule.getRealizedLeaveTimes().get(nodeIndex + 1) != 0) {
                            departureRealized = true;
                        }

                        int indexInNextSchedule = canShortTurn(schedule, nextSchedule, currentNode, nodeIndex, departureRealized);
                        if (indexInNextSchedule <= 0) {
                            continue;
                        }

                        double estimatedBenefit = estimateShortTurnBenefit(solution, schedule, nextSchedule, dutyScheduleList, nodeIndex, indexInNextSchedule, scheduleIndex);

                        if (estimatedBenefit > 0.0) {
                            PartialCancellationCandidate partialCancellationCandidate = new PartialCancellationCandidate();
                            partialCancellationCandidate.setSchedule(schedule);
                            partialCancellationCandidate.setNextSchedule(nextSchedule);
                            partialCancellationCandidate.setNode(currentNode);
                            partialCancellationCandidate.setNodeIndex(nodeIndex);
                            partialCancellationCandidate.setIndexInNextSchedule(indexInNextSchedule);
                            partialCancellationCandidate.setDutyId(dutyId);
                            partialCancellationCandidate.setDutyIndex(i);
                            partialCancellationCandidate.setScheduleIndex(scheduleIndex);

                            int oldDestinationArrivalTime = scheduleArrivalTimeList.get(scheduleArrivalTimeList.size() - 1);
                            int newDestinationArrivalTime = scheduleArrivalTimeList.get(nodeIndex);

                            int shiftTime = oldDestinationArrivalTime - newDestinationArrivalTime;

                            partialCancellationCandidate.setShiftTime(shiftTime);
                            rollingStockPartialCancellationCandidateListMap.computeIfAbsent(rollingStock, k -> new ArrayList<>()).add(partialCancellationCandidate);
                            ++counter;
                        }
                    }
                }
            }
        }
        LOGGER.info(String.format("Partial Cancellation Candidates Number: %d", counter));
    }

    public void buildEdgeListBasedOnPlannedSchedules(ProblemContext problemContext, Solution solution, RollingStock partialCancellationRollingStock, PartialCancellationCandidate partialCancellationCandidate, Set<String> updateCourses) {
        for (RollingStock rollingStock : solution.getRollingStock2ScheduleListMap().keySet()) {
            if (partialCancellationRollingStock != null && rollingStock != partialCancellationRollingStock) {
                continue;
            }

            Vertex trainVertex = new Vertex(vertexIndex, String.valueOf(rollingStock.getIndex()), null, VertexType.TRAIN, -1, null, null, -1, false);
            trainVertex = checkDuplicatedVertex(trainVertex, null, -1, rollingStock.getIndex());

            Set<Vertex> prevDutyEndVertexSet = new HashSet<>();
            List<String> dutyIdList = solution.getRollingStock2DutyListMap().get(rollingStock);
            Map<String, Integer> dutyStartTimeMap = this.rollingStockDutyStartTimeMap.get(rollingStock);
            Map<String, Integer> dutyEndTimeMap = this.rollingStockDutyEndTimeMap.get(rollingStock);

            for (int i = 0; i < dutyIdList.size(); ++i) {
                String dutyId = dutyIdList.get(i);
                int dutyStartTime = dutyStartTimeMap.get(dutyId);
                int dutyEndTime = dutyEndTimeMap.get(dutyId);

                if (partialCancellationCandidate != null) {
                    if (i > partialCancellationCandidate.getDutyIndex()) {
                        dutyStartTime -= partialCancellationCandidate.getShiftTime();
                        dutyEndTime -= partialCancellationCandidate.getShiftTime();
                    } else if (partialCancellationCandidate.getDutyIndex() == i) {
                        dutyEndTime -= partialCancellationCandidate.getShiftTime();
                    }
                }

                boolean dutyStartRealized = false;
                boolean dutyEndRealized = false;
                List<Schedule> schedules = solution.getDuty2ScheduleListMap().get(dutyId);
                Schedule firstScheduleInDuty = schedules.get(0);
                Schedule lastScheduleInDuty = schedules.get(schedules.size() - 1);

                if (!firstScheduleInDuty.getRealizedLeaveTimes().isEmpty() && firstScheduleInDuty.getRealizedLeaveTimes().get(1) != 0) {
                    dutyStartRealized = true;
                }

                if (!lastScheduleInDuty.getRealizedEnterTimes().isEmpty() && lastScheduleInDuty.getRealizedEnterTimes().get(lastScheduleInDuty.getRealizedNodes().size()) != 0) {
                    dutyEndRealized = true;
                }

                Set<Vertex> currentDutyEndVertexSet = new HashSet<>();

                for (VertexType vertexType : Arrays.asList(VertexType.DUTY_START, VertexType.DUTY_END)) {
                    int plannedTime = (VertexType.DUTY_START == vertexType) ? dutyStartTime : dutyEndTime;
                    List<Integer> timePointList = new ArrayList<>(this.beforeCopyNum + this.afterCopyNum + 1);
                    timePointList.add(plannedTime);
                    boolean dutyVertexRealized = false;
                    if (VertexType.DUTY_START == vertexType && dutyStartRealized) {
                        dutyVertexRealized = true;
                    }
                    if (VertexType.DUTY_END == vertexType && dutyEndRealized) {
                        dutyVertexRealized = true;
                    }
                    if (!dutyVertexRealized) {
                        int earliestTime = Math.max(plannedTime - this.beforeCopyNum * this.deltaTime, 0);
                        if (earliestTime <= currentTime + 300) {
                            earliestTime = plannedTime;
                        }
                        int latestTime = Math.min(plannedTime + this.afterCopyNum * this.deltaTime, timeHorizonEnd);
                        earliestTime = earliestTime / this.deltaTime * this.deltaTime;
                        earliestTime = Math.max(earliestTime, currentTime + this.deltaTime);
                        latestTime = latestTime / this.deltaTime * this.deltaTime;
                        if (this.beforeCopyNum > 0 || this.afterCopyNum > 0) {
                            for (int time = earliestTime; time <= latestTime; time += this.deltaTime) {
                                if (time == plannedTime) {
                                    continue;
                                }
                                timePointList.add(time);
                            }
                        }
                    }
                    for (int timePoint : timePointList) {
                        Vertex dutyVertex = new Vertex(vertexIndex, dutyId, null, vertexType, timePoint, null, null, -1, dutyVertexRealized);
                        dutyVertex = checkDuplicatedVertex(dutyVertex, null, -1, -1);

                        if (VertexType.DUTY_END == vertexType) {
                            currentDutyEndVertexSet.add(dutyVertex);
                            dutyEndVertexMap.computeIfAbsent(dutyId, k -> new HashSet<>()).add(dutyVertex);
                        }

                        if (VertexType.DUTY_START == vertexType) {
                            dutyStartVertexMap.computeIfAbsent(dutyId, k -> new HashSet<>()).add(dutyVertex);
                        }

                        if (i == 0 && VertexType.DUTY_START == vertexType) {
                            // Edge: Train -> Duty start
                            EdgeType edgeType = EdgeType.TRAIN_TO_DUTY_START;
                            if (!edgeType.isInfeasible(trainVertex, dutyVertex, problemContext, solution)) {
                                Edge trainToDutyStartEdge = new Edge();
                                trainToDutyStartEdge.setHead(trainVertex);
                                trainToDutyStartEdge.setTail(dutyVertex);
                                trainToDutyStartEdge.setEdgeType(edgeType);
                                trainToDutyStartEdge.setRollingStockIndex(rollingStock.getIndex());
                                edgeTypeListMap.computeIfAbsent(edgeType, k -> new ArrayList<>()).add(trainToDutyStartEdge);
                            }
                        } else if (i == solution.getRollingStock2DutyListMap().get(rollingStock).size() - 1 && VertexType.DUTY_END == vertexType) {
                            // Edge: Duty end -> Train
                            EdgeType edgeType = EdgeType.DUTY_END_TO_TRAIN;
                            if (!edgeType.isInfeasible(dutyVertex, trainVertex, problemContext, solution)) {
                                Edge dutyEndToTrainEdge = new Edge();
                                dutyEndToTrainEdge.setHead(dutyVertex);
                                dutyEndToTrainEdge.setTail(trainVertex);
                                dutyEndToTrainEdge.setEdgeType(edgeType);
                                dutyEndToTrainEdge.setRollingStockIndex(rollingStock.getIndex());
                                edgeTypeListMap.computeIfAbsent(edgeType, k -> new ArrayList<>()).add(dutyEndToTrainEdge);
                            }
                        }

                        if (VertexType.DUTY_START == vertexType) {
                            // Edge: Duty start -> Course start
                            Schedule firstSchedule = solution.getDuty2ScheduleListMap().get(dutyId).get(0);
                            boolean courseStartRealized = !firstSchedule.getRealizedLeaveTimes().isEmpty() && firstSchedule.getRealizedLeaveTimes().get(1) != 0;
                            Vertex courseStartVertex = new Vertex(vertexIndex, firstSchedule.getCourseId(), null, VertexType.COURSE_START, timePoint, firstSchedule.getDirection(), firstSchedule.getCourseId(), -1, courseStartRealized);
                            courseStartVertex = checkDuplicatedVertex(courseStartVertex, firstSchedule.getCourseId(), -1, -1);

                            EdgeType edgeType = EdgeType.DUTY_START_TO_COURSE_START;
                            if (!edgeType.isInfeasible(dutyVertex, courseStartVertex, problemContext, solution)) {
                                Edge dutyStartToCourseStartEdge = new Edge();
                                dutyStartToCourseStartEdge.setEdgeType(edgeType);
                                dutyStartToCourseStartEdge.setHead(dutyVertex);
                                dutyStartToCourseStartEdge.setTail(courseStartVertex);
                                dutyStartToCourseStartEdge.setRollingStockIndex(rollingStock.getIndex());
                                edgeTypeListMap.computeIfAbsent(edgeType, k -> new ArrayList<>()).add(dutyStartToCourseStartEdge);
                            }

                            // Edge: Duty end -> Duty start
                            if (!prevDutyEndVertexSet.isEmpty()) {
                                edgeType = EdgeType.DUTY_END_TO_DUTY_START;
                                for (Vertex prevDutyEndVertex : prevDutyEndVertexSet) {
                                    if (edgeType.isInfeasible(prevDutyEndVertex, dutyVertex, problemContext, solution)) {
                                        continue;
                                    }

                                    Edge dutyEndToDutyStartEdge = new Edge();
                                    dutyEndToDutyStartEdge.setEdgeType(edgeType);
                                    dutyEndToDutyStartEdge.setHead(prevDutyEndVertex);
                                    dutyEndToDutyStartEdge.setTail(dutyVertex);
                                    dutyEndToDutyStartEdge.setRollingStockIndex(rollingStock.getIndex());
                                    edgeTypeListMap.computeIfAbsent(edgeType, k -> new ArrayList<>()).add(dutyEndToDutyStartEdge);
                                }
                            }
                        } else {
                            // Edge: Course end -> Duty end
                            List<Schedule> dutyScheduleList = solution.getDuty2ScheduleListMap().get(dutyId);
                            Schedule lastSchedule = dutyScheduleList.get(dutyScheduleList.size() - 1);
                            boolean courseEndRealized = !lastSchedule.getRealizedEnterTimes().isEmpty() && lastSchedule.getRealizedEnterTimes().get(lastSchedule.getRealizedNodes().size()) != 0;
                            Vertex courseEndVertex = new Vertex(vertexIndex, lastSchedule.getCourseId(), null, VertexType.COURSE_END, timePoint, lastSchedule.getDirection(), lastSchedule.getCourseId(), -1, courseEndRealized);
                            courseEndVertex = checkDuplicatedVertex(courseEndVertex, lastSchedule.getCourseId(), -1, -1);

                            if (!EdgeType.COURSE_END_TO_DUTY_END.isInfeasible(courseEndVertex, dutyVertex, problemContext, solution)) {
                                Edge courseEndToDutyEndEdge = new Edge();
                                EdgeType edgeType = EdgeType.COURSE_END_TO_DUTY_END;
                                courseEndToDutyEndEdge.setEdgeType(edgeType);
                                courseEndToDutyEndEdge.setHead(courseEndVertex);
                                courseEndToDutyEndEdge.setTail(dutyVertex);
                                edgeTypeListMap.computeIfAbsent(edgeType, k -> new ArrayList<>()).add(courseEndToDutyEndEdge);
                            }
                        }
                    }
                }

                prevDutyEndVertexSet.clear();
                prevDutyEndVertexSet.addAll(currentDutyEndVertexSet);

                Set<Vertex> prevCourseEndVertexSet = new HashSet<>();
                List<Schedule> dutyScheduleList = solution.getDuty2ScheduleListMap().get(dutyId);
                dutyScheduleList.sort(Comparator.comparing(schedule -> solution.getScheduleStationDepartureTimeMap().get(schedule).get(0)));
                for (int scheduleIndex = 0; scheduleIndex < dutyScheduleList.size(); ++scheduleIndex) {
                    Schedule schedule = dutyScheduleList.get(scheduleIndex);
                    if (Schedule.EventType.TRAIN != schedule.getEventType()) {
                        continue;
                    }

                    int courseStartTime = solution.getScheduleStationDepartureTimeMap().get(schedule).get(0);
                    int courseEndTime = solution.getScheduleStationArrivalTimeMap().get(schedule).get(solution.getScheduleStationArrivalTimeMap().get(schedule).size() - 1);
                    if (partialCancellationCandidate != null) {
                        int tmpShiftTime = partialCancellationCandidate.getShiftTime();
                        if (i > partialCancellationCandidate.getDutyIndex()) {
                            courseStartTime -= tmpShiftTime;
                            courseEndTime -= tmpShiftTime;
                        } else if (partialCancellationCandidate.getDutyIndex() == i) {
                            if (partialCancellationCandidate.getScheduleIndex() == scheduleIndex) {
                                courseEndTime -= tmpShiftTime;
                            } else if (partialCancellationCandidate.getScheduleIndex() < scheduleIndex) {
                                courseStartTime -= tmpShiftTime;
                                courseEndTime -= tmpShiftTime;
                            }
                        }
                    }
                    Set<Vertex> currentCourseEndVertexSet = new HashSet<>();
                    Set<Vertex> currentCourseStartVertexSet = new HashSet<>();

                    boolean courseStartRealized = !schedule.getRealizedLeaveTimes().isEmpty() && schedule.getRealizedLeaveTimes().get(1) != 0;
                    boolean courseEndRealized = !schedule.getRealizedEnterTimes().isEmpty() && schedule.getRealizedEnterTimes().get(schedule.getRealizedNodes().size()) != 0;

                    for (VertexType vertexType : Arrays.asList(VertexType.COURSE_START, VertexType.COURSE_END)) {
                        int plannedTime = (VertexType.COURSE_START == vertexType) ? courseStartTime : courseEndTime;
                        List<Integer> timePointList = new ArrayList<>();
                        timePointList.add(plannedTime);

                        boolean tmpCourseVertexRealized = false;
                        if (VertexType.COURSE_START == vertexType && courseStartRealized) {
                            tmpCourseVertexRealized = true;
                        }
                        if (VertexType.COURSE_END == vertexType && courseEndRealized) {
                            tmpCourseVertexRealized = true;
                        }

                        if (!tmpCourseVertexRealized && updateCourses.contains(schedule.getCourseId())) {
                            int earliestTime = Math.max(plannedTime - this.beforeCopyNum * this.deltaTime, 0);
                            if (earliestTime <= currentTime + 300) {
                                earliestTime = plannedTime;
                            }
                            int latestTime = Math.min(plannedTime + this.afterCopyNum * this.deltaTime, timeHorizonEnd);
                            earliestTime = earliestTime / this.deltaTime * this.deltaTime;
                            earliestTime = Math.max(earliestTime, currentTime + this.deltaTime);
                            latestTime = latestTime / this.deltaTime * this.deltaTime;
                            if (this.beforeCopyNum > 0 || this.afterCopyNum > 0) {
                                for (int time = earliestTime; time <= latestTime; time += this.deltaTime) {
                                    if (time == plannedTime) {
                                        continue;
                                    }
                                    timePointList.add(time);
                                }
                            }
                        }

                        for (int timePoint : timePointList) {
                            Vertex courseVertex = new Vertex(vertexIndex, schedule.getCourseId(), null, vertexType, timePoint, schedule.getDirection(), schedule.getCourseId(), -1, tmpCourseVertexRealized);
                            courseVertex = checkDuplicatedVertex(courseVertex, schedule.getCourseId(), -1, -1);

                            if (partialCancellationCandidate != null) {
                                if (partialCancellationCandidate.getDutyIndex() == i) {
                                    if (partialCancellationCandidate.getScheduleIndex() == scheduleIndex && VertexType.COURSE_END == courseVertex.getVertexType()) {
                                        courseVertex.setPartialCancellationCourseStartEnd(true);
                                    }
                                    if (partialCancellationCandidate.getScheduleIndex() + 1 == scheduleIndex && VertexType.COURSE_START == courseVertex.getVertexType()) {
                                        courseVertex.setPartialCancellationCourseStartEnd(true);
                                    }
                                }
                            }

                            if (VertexType.COURSE_END == vertexType) {
                                currentCourseEndVertexSet.add(courseVertex);
                                courseEndVertexMap.computeIfAbsent(schedule.getCourseId(), k -> new HashSet<>()).add(courseVertex);
                            } else {
                                currentCourseStartVertexSet.add(courseVertex);
                                courseStartVertexMap.computeIfAbsent(schedule.getCourseId(), k -> new HashSet<>()).add(courseVertex);

                                // Edge: Course end -> Course start
                                if (!prevCourseEndVertexSet.isEmpty()) {
                                    EdgeType edgeType = EdgeType.COURSE_END_TO_COURSE_START;
                                    for (Vertex lastCourseEndVertex : prevCourseEndVertexSet) {
                                        if (edgeType.isInfeasible(lastCourseEndVertex, courseVertex, problemContext, solution)) {
                                            continue;
                                        }

                                        Edge courseEndToCourseStartEdge = new Edge();
                                        courseEndToCourseStartEdge.setHead(lastCourseEndVertex);
                                        courseEndToCourseStartEdge.setTail(courseVertex);
                                        courseEndToCourseStartEdge.setEdgeType(edgeType);
                                        courseEndToCourseStartEdge.setRollingStockIndex(rollingStock.getIndex());
                                        edgeTypeListMap.computeIfAbsent(edgeType, k -> new ArrayList<>()).add(courseEndToCourseStartEdge);
                                    }
                                }
                            }
                        }
                    }
                    prevCourseEndVertexSet.clear();
                    prevCourseEndVertexSet.addAll(currentCourseEndVertexSet);

                    Set<Vertex> prevNodeVertexSet = new HashSet<>();
                    List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.getRealizedNodes();
                    List<Integer> scheduleArrivalTime = solution.getScheduleStationArrivalTimeMap().get(schedule);
                    List<Integer> scheduleDepartureTime = solution.getScheduleStationDepartureTimeMap().get(schedule);
                    List<Boolean> skipStationList = solution.getScheduleSkipStationMap().get(schedule);
                    List<String> trackList = solution.getScheduleStationTrackMap().get(schedule);

                    int scheduleStartNodeIndex = 0;
                    int scheduleEndNodeIndex = nodeList.size() - 1;
                    if (partialCancellationCandidate != null) {
                        if (i == partialCancellationCandidate.getDutyIndex()) {
                            if (scheduleIndex == partialCancellationCandidate.getScheduleIndex()) {
                                scheduleEndNodeIndex = partialCancellationCandidate.getNodeIndex();
                            } else if (scheduleIndex == partialCancellationCandidate.getScheduleIndex() + 1) {
                                scheduleStartNodeIndex = partialCancellationCandidate.getIndexInNextSchedule();
                            }
                        }
                    }

                    for (int nodeIndex = scheduleStartNodeIndex; nodeIndex <= scheduleEndNodeIndex; ++nodeIndex) {
                        Set<Vertex> currentNodeVertexSet = new HashSet<>();

                        Node currentNode = nodeList.get(nodeIndex);
                        String currentNodeCode = currentNode.getCode();
                        int arrivalTime = scheduleArrivalTime.get(nodeIndex);
                        int departureTime = scheduleDepartureTime.get(nodeIndex);
                        if (partialCancellationCandidate != null) {
                            if (i > partialCancellationCandidate.getDutyIndex()) {
                                arrivalTime -= partialCancellationCandidate.getShiftTime();
                                departureTime -= partialCancellationCandidate.getShiftTime();
                            } else if (i == partialCancellationCandidate.getDutyIndex()) {
                                if (scheduleIndex > partialCancellationCandidate.getScheduleIndex()) {
                                    arrivalTime -= partialCancellationCandidate.getShiftTime();
                                    departureTime -= partialCancellationCandidate.getShiftTime();
                                } else if (scheduleIndex == partialCancellationCandidate.getScheduleIndex()) {
                                    if (nodeIndex >= partialCancellationCandidate.getNodeIndex()) {
                                        departureTime = arrivalTime;
                                    }
                                }
                            }
                        }
                        int dwellTime = departureTime - arrivalTime;
                        String activityStr = skipStationList.get(nodeIndex) ? ActivityType.PASS.getValue() : ActivityType.STOP.getValue();
                        String trackName = trackList.get(nodeIndex);
                        Track.Direction direction = schedule.getDirection();
                        boolean arrivalRealized = false;
                        boolean departureRealized = false;
                        if (!schedule.getRealizedEnterTimes().isEmpty() && schedule.getRealizedEnterTimes().get(nodeIndex + 1) != 0) {
                            arrivalRealized = true;
                        }
                        if (!schedule.getRealizedLeaveTimes().isEmpty() && schedule.getRealizedLeaveTimes().get(nodeIndex + 1) != 0) {
                            departureRealized = true;
                        }

                        int arrivalTimeBandStart = Integer.MAX_VALUE;
                        int arrivalTimeBandEnd = 0;
                        for (VertexType vertexType : Arrays.asList(VertexType.NODE_PASS, VertexType.NODE_LEAVE, VertexType.NODE_STOP, VertexType.NODE_STOP_LEAVE)) {
                            if (VertexType.NODE_STOP_LEAVE == vertexType) {
                                if (nodeIndex > scheduleStartNodeIndex && nodeIndex < scheduleEndNodeIndex) {
                                    continue;
                                }
                            }

                            if (nodeIndex == scheduleStartNodeIndex || nodeIndex == scheduleEndNodeIndex) {
                                if (VertexType.NODE_STOP_LEAVE != vertexType) {
                                    continue;
                                }
                            }

                            if (ActivityType.PASS.getValue().equals(activityStr)) {
                                if (VertexType.NODE_STOP == vertexType || VertexType.NODE_LEAVE == vertexType) {
                                    continue;
                                }
                            }

                            if (ActivityType.STOP.getValue().equals(activityStr)) {
                                if (arrivalRealized && VertexType.NODE_PASS == vertexType) {
                                    continue;
                                }
                            }

                            if (!Constants.ALLOW_TO_SKIP_STOPS_IN_EVENT_BASED_MODEL) {
                                if (ActivityType.STOP.getValue().equals(activityStr)) {
                                    if (VertexType.NODE_PASS == vertexType) {
                                        continue;
                                    }
                                }
                            }

                            int plannedTime;
                            if (nodeIndex == scheduleStartNodeIndex) {
                                plannedTime = departureTime;
                            } else if (nodeIndex == scheduleEndNodeIndex) {
                                plannedTime = arrivalTime;
                            } else {
                                plannedTime = (VertexType.NODE_LEAVE == vertexType) ? departureTime : arrivalTime;
                            }

                            List<Integer> timePointList = new ArrayList<>();
                            timePointList.add(plannedTime);
                            boolean vertexRealized = false;
                            if (VertexType.NODE_LEAVE == vertexType && departureRealized) {
                                vertexRealized = true;
                            }
                            if (VertexType.NODE_STOP == vertexType && arrivalRealized) {
                                vertexRealized = true;
                            }
                            if (VertexType.NODE_PASS == vertexType && arrivalRealized) {
                                vertexRealized = true;
                            }
                            if (VertexType.NODE_STOP_LEAVE == vertexType) {
                                if (nodeIndex == 0 && departureRealized) {
                                    vertexRealized = true;
                                }
                                if (nodeIndex == nodeList.size() - 1 && arrivalRealized) {
                                    vertexRealized = true;
                                }
                            }
                            if (!vertexRealized && updateCourses.contains(schedule.getCourseId())) {
                                int earliestTime = Math.max(plannedTime - this.beforeCopyNum * this.deltaTime, 0);
                                if (earliestTime <= currentTime + 300) {
                                    earliestTime = plannedTime;
                                }
                                int latestTime = Math.min(plannedTime + this.afterCopyNum * this.deltaTime, timeHorizonEnd);
                                earliestTime = earliestTime / this.deltaTime * this.deltaTime;
                                earliestTime = Math.max(earliestTime, currentTime + this.deltaTime);
                                latestTime = latestTime / this.deltaTime * this.deltaTime;

                                if (this.beforeCopyNum > 0 || this.afterCopyNum > 0) {
                                    for (int time = earliestTime; time <= latestTime; time += this.deltaTime) {
                                        if (time == plannedTime) {
                                            continue;
                                        }

                                        timePointList.add(time);
                                    }
                                }
                            }
                            for (int timePoint : timePointList) {
                                Vertex nodeVertex = new Vertex(vertexIndex, currentNodeCode, trackName, vertexType, timePoint, direction, schedule.getCourseId(), nodeIndex + 1, vertexRealized);
                                nodeVertex = checkDuplicatedVertex(nodeVertex, schedule.getCourseId(), nodeIndex, -1);

                                if (nodeIndex == scheduleStartNodeIndex && VertexType.NODE_STOP_LEAVE == vertexType) {
                                    nodeVertex.setStopLeaveArrivalTime(timePoint - dwellTime);
                                    nodeVertex.setStopLeaveArrivalRealized(arrivalRealized);

                                    if (partialCancellationCandidate != null) {
                                        if (partialCancellationCandidate.getDutyIndex() == i && partialCancellationCandidate.getScheduleIndex() + 1 == scheduleIndex) {
                                            if (Constants.GIDEAPK_NODE_CODE.equals(currentNodeCode) || Constants.CHDWLHT_NODE_CODE.equals(currentNodeCode)) {
                                                nodeVertex.setPartialCancellationTrack(null);
                                            }

                                            nodeVertex.setPartialCancellationTrack(trackName);
                                        }
                                    }
                                }
                                if (nodeIndex == scheduleEndNodeIndex && VertexType.NODE_STOP_LEAVE == vertexType) {
                                    nodeVertex.setStopLeaveDepartureTime(timePoint + dwellTime);
                                    nodeVertex.setStopLeaveDepartureRealized(departureRealized);

                                    if (partialCancellationCandidate != null) {
                                        if (partialCancellationCandidate.getDutyIndex() == i && partialCancellationCandidate.getScheduleIndex() == scheduleIndex) {
                                            if (Constants.GIDEAPK_NODE_CODE.equals(currentNodeCode) || Constants.CHDWLHT_NODE_CODE.equals(currentNodeCode)) {
                                                nodeVertex.setPartialCancellationTrack(null);
                                            }

                                            String shortTurnStr = currentNode.getStWb();
                                            if (direction == Track.Direction.EB) {
                                                shortTurnStr = currentNode.getStEb();
                                            }
                                            if (ShortTurningType.OPPOSITE.name().equals(shortTurnStr)) {
                                                nodeVertex.setPartialCancellationTrack(solution.getScheduleStationTrackMap().get(schedules.get(scheduleIndex + 1)).get(partialCancellationCandidate.getIndexInNextSchedule()));
                                            } else {
                                                nodeVertex.setPartialCancellationTrack(trackName);
                                            }
                                        }
                                    }
                                }

                                currentNodeVertexSet.add(nodeVertex);
                                if (VertexType.NODE_LEAVE != nodeVertex.getVertexType()) {
                                    arrivalTimeBandStart = Math.min(nodeVertex.getTime(), arrivalTimeBandStart);
                                    arrivalTimeBandEnd = Math.max(nodeVertex.getTime(), arrivalTimeBandEnd);
                                }

                                if (nodeIndex == scheduleStartNodeIndex) {
                                    // Edge: Course start -> Node
                                    for (Vertex courseStartVertex : currentCourseStartVertexSet) {
                                        if (EdgeType.COURSE_START_TO_NODE.isInfeasible(courseStartVertex, nodeVertex, problemContext, solution)) {
                                            continue;
                                        }

                                        Edge courseStartToNodeEdge = new Edge();
                                        courseStartToNodeEdge.setHead(courseStartVertex);
                                        courseStartToNodeEdge.setTail(nodeVertex);
                                        courseStartToNodeEdge.setEdgeType(EdgeType.COURSE_START_TO_NODE);
                                        courseStartToNodeEdge.setRollingStockIndex(rollingStock.getIndex());
                                        edgeTypeListMap.computeIfAbsent(EdgeType.COURSE_START_TO_NODE, k -> new ArrayList<>()).add(courseStartToNodeEdge);
                                    }
                                } else if (nodeIndex == scheduleEndNodeIndex) {
                                    // Edge: Node -> Course end
                                    for (Vertex courseEndVertex : currentCourseEndVertexSet) {
                                        if (EdgeType.NODE_TO_COURSE_END.isInfeasible(nodeVertex, courseEndVertex, problemContext, solution)) {
                                            continue;
                                        }

                                        Edge nodeToCourseEndEdge = new Edge();
                                        nodeToCourseEndEdge.setHead(nodeVertex);
                                        nodeToCourseEndEdge.setTail(courseEndVertex);
                                        nodeToCourseEndEdge.setEdgeType(EdgeType.NODE_TO_COURSE_END);
                                        nodeToCourseEndEdge.setRollingStockIndex(rollingStock.getIndex());
                                        edgeTypeListMap.computeIfAbsent(EdgeType.NODE_TO_COURSE_END, k -> new ArrayList<>()).add(nodeToCourseEndEdge);
                                    }
                                }

                                if (!prevNodeVertexSet.isEmpty()) {
                                    // Edge: Node -> Node (Cross station)
                                    for (Vertex lastNodeVertex : prevNodeVertexSet) {
                                        if (VertexType.NODE_STOP == lastNodeVertex.getVertexType()) {
                                            continue;
                                        }

                                        if (VertexType.NODE_LEAVE == nodeVertex.getVertexType()) {
                                            continue;
                                        }

                                        if (EdgeType.CROSS_STATION_NODE_TO_NODE.isInfeasible(lastNodeVertex, nodeVertex, problemContext, solution)) {
                                            continue;
                                        }

                                        Edge crossStationNodeToNodeEdge = new Edge();
                                        crossStationNodeToNodeEdge.setHead(lastNodeVertex);
                                        crossStationNodeToNodeEdge.setTail(nodeVertex);
                                        crossStationNodeToNodeEdge.setEdgeType(EdgeType.CROSS_STATION_NODE_TO_NODE);
                                        crossStationNodeToNodeEdge.setRollingStockIndex(rollingStock.getIndex());
                                        edgeTypeListMap.computeIfAbsent(EdgeType.CROSS_STATION_NODE_TO_NODE, k -> new ArrayList<>()).add(crossStationNodeToNodeEdge);
                                    }
                                }
                            }
                        }
                        if (Schedule.Category.OO == schedule.getCategory()) {
                            if (Constants.EAST_BOUND_REFERENCE_STATION.equals(currentNodeCode) && Track.Direction.EB == schedule.getDirection()) {
                                ebReferenceStationArrivalTimeBandList.add(Pair.of(arrivalTimeBandStart, arrivalTimeBandEnd));
                            } else if (Constants.WEST_BOUND_REFERENCE_STATION.equals(currentNodeCode) && Track.Direction.WB == schedule.getDirection()) {
                                wbReferenceStationArrivalTimeBandList.add(Pair.of(arrivalTimeBandStart, arrivalTimeBandEnd));
                            }
                        }
                        prevNodeVertexSet.clear();
                        prevNodeVertexSet.addAll(currentNodeVertexSet);

                        // Edge: Node -> Node (Same station)
                        for (Vertex currentNodeVertex1 : currentNodeVertexSet) {
                            if (VertexType.NODE_STOP != currentNodeVertex1.getVertexType()) {
                                continue;
                            }

                            for (Vertex currentNodeVertex2 : currentNodeVertexSet) {
                                if (VertexType.NODE_LEAVE != currentNodeVertex2.getVertexType()) {
                                    continue;
                                }

                                if (EdgeType.SAME_STATION_NODE_TO_NODE.isInfeasible(currentNodeVertex1, currentNodeVertex2, problemContext, solution)) {
                                    continue;
                                }

                                Edge sameStationNodeToNodeEdge = new Edge();
                                sameStationNodeToNodeEdge.setHead(currentNodeVertex1);
                                sameStationNodeToNodeEdge.setTail(currentNodeVertex2);
                                sameStationNodeToNodeEdge.setEdgeType(EdgeType.SAME_STATION_NODE_TO_NODE);
                                sameStationNodeToNodeEdge.setRollingStockIndex(rollingStock.getIndex());
                                edgeTypeListMap.computeIfAbsent(EdgeType.SAME_STATION_NODE_TO_NODE, k -> new ArrayList<>()).add(sameStationNodeToNodeEdge);
                            }
                        }
                    }
                }
            }
        }
        ebReferenceStationArrivalTimeBandList.sort(Comparator.comparingInt(Pair::getLeft));
        wbReferenceStationArrivalTimeBandList.sort(Comparator.comparingInt(Pair::getLeft));
    }

    public void removeDuplicatedEdges() {
        for (Map.Entry<EdgeType, List<Edge>> entry : edgeTypeListMap.entrySet()) {
            List<Edge> edgeList = entry.getValue();

            Map<Vertex, Map<Vertex, Edge>> headTailEdgeMap = new HashMap<>();
            for (Edge edge : edgeList) {
                headTailEdgeMap.computeIfAbsent(edge.getHead(), k -> new HashMap<>()).put(edge.getTail(), edge);
            }

            List<Edge> newEdgeList = new ArrayList<>();
            for (Map.Entry<Vertex, Map<Vertex, Edge>> entry1 : headTailEdgeMap.entrySet()) {
                newEdgeList.addAll(entry1.getValue().values());
            }

            entry.setValue(newEdgeList);
        }
    }

    public void buildHeadTailVertexEdgeListMap() {
        for (Map.Entry<EdgeType, List<Edge>> entry : edgeTypeListMap.entrySet()) {
            for (Edge edge : entry.getValue()) {
                Vertex head = edge.getHead();
                Vertex tail = edge.getTail();

                this.headVertexEdgeListMap.computeIfAbsent(head, k -> new ArrayList<>()).add(edge);
                this.tailVertexEdgeListMap.computeIfAbsent(tail, k -> new ArrayList<>()).add(edge);
            }
        }
    }

    public void removeUnbalancedVertexEdge() {
        int prevVertexNum = -1;
        int currentVertexNum = this.vertexMap.size();
        int iteration = 0;

        List<Integer> vertexHeadEdgeCounterList = new ArrayList<>(vertexIndex);
        List<Integer> vertexTailEdgeCounterList = new ArrayList<>(vertexIndex);
        List<Boolean> vertexFlagList = new ArrayList<>(vertexIndex);
        IntStream.range(0, vertexIndex).forEach(index -> {
            vertexHeadEdgeCounterList.add(0);
            vertexTailEdgeCounterList.add(0);
            vertexFlagList.add(true);
        });
        this.headVertexEdgeListMap.forEach((key, value) ->
                vertexHeadEdgeCounterList.set(key.getIndex(), value.size()));
        this.tailVertexEdgeListMap.forEach((key, value) ->
                vertexTailEdgeCounterList.set(key.getIndex(), value.size()));

        Map<Edge, Boolean> edgeFlagMap = new HashMap<>(edgeTypeListMap.values().stream().mapToInt(List::size).sum());
        for (Map.Entry<EdgeType, List<Edge>> entry : edgeTypeListMap.entrySet()) {
            for (Edge edge : entry.getValue()) {
                edgeFlagMap.put(edge, true);
            }
        }

        while (prevVertexNum != currentVertexNum) {
            prevVertexNum = currentVertexNum;
            for (Vertex vertex : vertexMap.values()) {
                int index = vertex.getIndex();
                if (!vertexFlagList.get(index)) {
                    continue;
                }

                boolean zeroHeadEdge = false;
                if (vertexHeadEdgeCounterList.get(index) <= 0) {
                    vertexFlagList.set(index, false);
                    --currentVertexNum;
                    zeroHeadEdge = true;

                    if (this.tailVertexEdgeListMap.containsKey(vertex)) {
                        List<Edge> tailEdgeList = this.tailVertexEdgeListMap.get(vertex);
                        for (Edge tailEdge : tailEdgeList) {
                            if (!edgeFlagMap.get(tailEdge)) {
                                continue;
                            }

                            edgeFlagMap.put(tailEdge, false);
                            int headVertexIndex = tailEdge.getHead().getIndex();
                            int tmpCounter = vertexHeadEdgeCounterList.get(headVertexIndex) - 1;
                            vertexHeadEdgeCounterList.set(headVertexIndex, tmpCounter);
                        }
                    }
                }
                if (vertexTailEdgeCounterList.get(index) <= 0) {
                    vertexFlagList.set(index, false);
                    if (!zeroHeadEdge) {
                        --currentVertexNum;
                    }

                    if (this.headVertexEdgeListMap.containsKey(vertex)) {
                        List<Edge> headEdgeList = this.headVertexEdgeListMap.get(vertex);
                        for (Edge headEdge : headEdgeList) {
                            if (!edgeFlagMap.get(headEdge)) {
                                continue;
                            }

                            edgeFlagMap.put(headEdge, false);
                            int tailVertexIndex = headEdge.getTail().getIndex();
                            int tmpCounter = vertexTailEdgeCounterList.get(tailVertexIndex) - 1;
                            vertexTailEdgeCounterList.set(tailVertexIndex, tmpCounter);
                        }
                    }
                }
            }
            ++iteration;
            LOGGER.info(String.format("Remove Unbalanced Vertex and Edge. Iteration: %d, Prev Vertex Num: %d, Current Vertex Num: %d\n", iteration, prevVertexNum, currentVertexNum));
        }

        this.vertexMap.entrySet().removeIf(entry -> !vertexFlagList.get(entry.getValue().getIndex()));

        for (Map.Entry<VertexType, Set<Vertex>> entry : this.vertexTypeListMap.entrySet()) {
            entry.getValue().removeIf(ele -> !this.vertexMap.containsKey(ele.getUniqueKey()));
        }

        for (Map.Entry<EdgeType, List<Edge>> entry : edgeTypeListMap.entrySet()) {
            entry.getValue().removeIf(edge -> !this.vertexMap.containsKey(edge.getHead().getUniqueKey()) || !this.vertexMap.containsKey(edge.getTail().getUniqueKey()));
        }

        this.headVertexEdgeListMap.clear();
        this.tailVertexEdgeListMap.clear();
        this.buildHeadTailVertexEdgeListMap();
    }

    public void calculateDestinationDelayPenalty(ProblemContext problemContext, Solution solution) {
        for (Vertex vertex : this.vertexTypeListMap.get(VertexType.COURSE_END)) {
            int courseEndTime = vertex.getTime();
            String courseId = vertex.getCourseId();
            double destinationDelayPenalty = 0.0;

            Schedule schedule = problemContext.getCourseId2Schedule().get(courseId);

            if (!vertex.isRealized() && Schedule.Category.OO == schedule.getCategory()) {
                List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.getRealizedNodes();
                int plannedEndTime = schedule.getEnterTimes().get(nodeList.size());

                if (vertex.isPartialCancellationCourseStartEnd()) {
                    plannedEndTime = schedule.getEnterTimes().get(vertex.getNodeSeq());
                }

                int timeDiff = courseEndTime - plannedEndTime;
                if (timeDiff >= 3 * Constants.SECONDS_IN_MINUTE) {
                    destinationDelayPenalty = timeDiff * 1.0 / Constants.SECONDS_IN_MINUTE * Constants.DELAY_PENALTY;
                }
            }

            vertex.setDestinationDelayPenalty(destinationDelayPenalty);
        }
    }

    public void calculateBsv(ProblemContext problemContext, Solution solution) {
        for (Vertex vertex : this.vertexTypeListMap.get(VertexType.NODE_PASS)) {
            String courseId = vertex.getCourseId();
            int nodeSeq = vertex.getNodeSeq();

            Schedule schedule = problemContext.getCourseId2Schedule().get(courseId);
            if (Schedule.EventType.TRAIN != schedule.getEventType() || Schedule.Category.OO != schedule.getCategory()) {
                continue;
            }

            String originStatus = schedule.getNodeStatus().get(nodeSeq);
            if (!ActivityType.STOP.getValue().equals(originStatus)) {
                continue;
            }


            int time = vertex.getTime();
            Track.Direction direction = vertex.getDirection();
            Node node = problemContext.getCode2Node().get(vertex.getId());

            int bsv = EvaluationUtils.getBsv(node, time, direction);
            if (bsv > 0) {
                vertex.setBsv(bsv);
            }
        }
    }

    public void calculateMinimumRunTimeViolation(ProblemContext problemContext, Solution solution) {
        for (Edge edge : edgeTypeListMap.get(EdgeType.CROSS_STATION_NODE_TO_NODE)) {
            Vertex headVertex = edge.getHead();
            Vertex tailVertex = edge.getTail();

            String headId = headVertex.getId();
            String tailId = tailVertex.getId();

            String linkName = Link.generateLinkName(headId, tailId);
            Link link = problemContext.getName2Link().get(linkName);
            graph.Vertex.Type headType = graph.Vertex.Type.PASS;
            if (VertexType.NODE_STOP == headVertex.getVertexType() || VertexType.NODE_LEAVE == headVertex.getVertexType() || VertexType.NODE_STOP_LEAVE == headVertex.getVertexType()) {
                headType = graph.Vertex.Type.STOP;
            }

            graph.Vertex.Type tailType = graph.Vertex.Type.PASS;
            if (VertexType.NODE_STOP == tailVertex.getVertexType() || VertexType.NODE_LEAVE == tailVertex.getVertexType() || VertexType.NODE_STOP_LEAVE == tailVertex.getVertexType()) {
                tailType = graph.Vertex.Type.STOP;
            }

            int minimumRunTime = link.calcMinimumRunTime(headType, tailType);

            int timeDiff = tailVertex.getTime() - headVertex.getTime();
            if (timeDiff < minimumRunTime) {
                edge.setMinimumRunTimeViolation(minimumRunTime - timeDiff);
            }
        }
    }

    public void calculateChangeEndTimeViolation(ProblemContext problemContext, Solution solution) {
        for (Edge edge : edgeTypeListMap.get(EdgeType.COURSE_END_TO_COURSE_START)) {
            Vertex headVertex = edge.getHead();
            Vertex tailVertex = edge.getTail();

            String firstCourseId = headVertex.getCourseId();
            String secondCourseId = tailVertex.getCourseId();

            Schedule firstSchedule = problemContext.getCourseId2Schedule().get(firstCourseId);
            Schedule secondSchedule = problemContext.getCourseId2Schedule().get(secondCourseId);

            int expectedChangeEndTime = EvaluationUtils.getChangeEndBetweenConsecutiveCourses(problemContext, firstSchedule, secondSchedule);

            int timeDiff = tailVertex.getTime() - headVertex.getTime();
            if (timeDiff < expectedChangeEndTime) {
                edge.setChangeEndTimeViolation(expectedChangeEndTime - timeDiff);
            }
        }
    }

    public void calculatePartialCancellationSkippedStopsPenalty(ProblemContext problemContext, Solution solution) {
        for (Vertex vertex : this.vertexTypeListMap.get(VertexType.COURSE_END)) {
            if (!vertex.isPartialCancellationCourseStartEnd()) {
                continue;
            }

            String courseId = vertex.getCourseId();
            Schedule schedule = problemContext.getCourseId2Schedule().get(courseId);

            if (Schedule.Category.OO == schedule.getCategory()) {
                Track.Direction direction = schedule.getDirection();
                List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.getRealizedNodes();
                List<Integer> arrivalTimeList = solution.getScheduleStationArrivalTimeMap().get(schedule);

                int seq = vertex.getNodeSeq();

                List<Integer> bsvList = new ArrayList<>();
                for (int i = seq; i < nodeList.size(); ++i) {
                    Node node = nodeList.get(i);
                    int arrivalTime = arrivalTimeList.get(i);
                    int bsv = EvaluationUtils.getBsv(node, arrivalTime, direction);
                    if (bsv > 0) {
                        bsvList.add(bsv);
                    }
                }

                double penalty = EvaluationUtils.getSkippedStopsPenalty(bsvList);
                vertex.setPartialCancellationSkippedStopsPenalty(penalty);
            }
        }

        for (Vertex vertex : this.vertexTypeListMap.get(VertexType.COURSE_START)) {
            if (!vertex.isPartialCancellationCourseStartEnd()) {
                continue;
            }

            String courseId = vertex.getCourseId();
            Schedule schedule = problemContext.getCourseId2Schedule().get(courseId);

            if (Schedule.Category.OO == schedule.getCategory()) {
                Track.Direction direction = schedule.getDirection();
                List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.getRealizedNodes();
                List<Integer> arrivalTimeList = solution.getScheduleStationArrivalTimeMap().get(schedule);

                int seq = vertex.getNodeSeq();

                List<Integer> bsvList = new ArrayList<>();
                for (int i = 0; i < seq - 1; ++i) {
                    Node node = nodeList.get(i);
                    int arrivalTime = arrivalTimeList.get(i);

                    int bsv = EvaluationUtils.getBsv(node, arrivalTime, direction);
                    if (bsv > 0) {
                        bsvList.add(bsv);
                    }
                }
                double penalty = EvaluationUtils.getSkippedStopsPenalty(bsvList);
                vertex.setPartialCancellationSkippedStopsPenalty(penalty);
            }
        }
    }

    public int canShortTurn(Schedule currentSchedule, Schedule nextSchedule, Node currentNode, int nodeIndex, boolean departureRealized) {
        if (departureRealized) {
            return 0;
        }

        if (currentSchedule.getDirection() == nextSchedule.getDirection()) {
            return 0;
        }

        if (Track.Direction.EB == currentSchedule.getDirection()) {
            if (currentNode.getStEb() == null) {
                return 0;
            }
        } else if (Track.Direction.WB == currentSchedule.getDirection()) {
            if (currentNode.getStWb() == null) {
                return 0;
            }
        }

        List<Node> nodeList = currentSchedule.getRealizedNodes().isEmpty() ? currentSchedule.getPlannedNodes() : currentSchedule.getRealizedNodes();
        if (nodeIndex == 0 || nodeIndex == nodeList.size() - 1) {
            return 0;
        }

        int indexInNextSchedule = -1;
        nodeList = nextSchedule.getRealizedNodes().isEmpty() ? nextSchedule.getPlannedNodes() : nextSchedule.getRealizedNodes();

        String currentNodeCode = currentNode.getCode();
        for (int i = 0; i < nodeList.size(); ++i) {
            if (currentNodeCode.equals(nodeList.get(i).getCode())) {
                indexInNextSchedule = i;
                break;
            }
        }

        return indexInNextSchedule;
    }

    public double estimateShortTurnBenefit(Solution solution, Schedule schedule, Schedule nextSchedule, List<Schedule> scheduleList, int nodeIndex, int nodeIndexInNextSchedule, int scheduleIndex) {
        List<Integer> scheduleArrivalTimeList = solution.getScheduleStationArrivalTimeMap().get(schedule);
        List<Integer> scheduleDepartureTimeList = solution.getScheduleStationDepartureTimeMap().get(schedule);
        Track.Direction scheduleDirection = schedule.getDirection();

        int oldCourseEndTime = scheduleArrivalTimeList.get(scheduleArrivalTimeList.size() - 1);
        int currentNodeArrivalTime = scheduleArrivalTimeList.get(nodeIndex);
        int currentNodeDepartureTime = scheduleDepartureTimeList.get(nodeIndex);

        int expectedEndTime = schedule.getEnterTimes().get(schedule.getPlannedNodes().size());
        double oldDelayPenalty = 0.0;
        if (Schedule.Category.OO == schedule.getCategory()) {
            oldDelayPenalty = calculateDelayPenalty(expectedEndTime, oldCourseEndTime);
        }

        int newExpectedEndTime = schedule.getEnterTimes().get(nodeIndex + 1);
        double newDelayPenalty = 0.0;
        if (Schedule.Category.OO == schedule.getCategory()) {
            newDelayPenalty = calculateDelayPenalty(newExpectedEndTime, currentNodeArrivalTime);
        }

        int shiftTime = oldCourseEndTime - currentNodeDepartureTime;

        for (int index = scheduleIndex + 1; index < scheduleList.size(); ++index) {
            Schedule tmpSchedule = scheduleList.get(index);
            int tmpExpectedEndTime = tmpSchedule.getEnterTimes().get(tmpSchedule.getPlannedNodes().size());
            List<Integer> tmpScheduleArrivalTimeList = solution.getScheduleStationArrivalTimeMap().get(schedule);
            int tmpOldArrivalTime = tmpScheduleArrivalTimeList.get(tmpScheduleArrivalTimeList.size() - 1);

            if (Schedule.Category.OO == tmpSchedule.getCategory()) {
                oldDelayPenalty += calculateDelayPenalty(tmpExpectedEndTime, tmpOldArrivalTime);
                newDelayPenalty += calculateDelayPenalty(tmpExpectedEndTime, tmpOldArrivalTime - shiftTime);
            }
        }

        double delayPenaltyBenefit = oldDelayPenalty - newDelayPenalty;
//        if (delayPenaltyBenefit <= 0) {
//            return 0.0;
//        }

        double skippedStopsPenalty = 0.0;
        List<Integer> bsvList = new ArrayList<>();
        List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.getRealizedNodes();
        if (Schedule.Category.OO == schedule.getCategory()) {
            Map<Integer, String> nodeStatusMap = schedule.getNodeStatus();
            for (int index = nodeIndex + 1; index < nodeList.size(); ++index) {
                Node tmpNode = nodeList.get(index);
                if (!"STOP".equalsIgnoreCase(nodeStatusMap.get(index + 1))) {
                    continue;
                }
                int tmpArrivalTime = scheduleArrivalTimeList.get(index);
                int tmpBsv = EvaluationUtils.getBsv(tmpNode, tmpArrivalTime, scheduleDirection);
                if (tmpBsv > 0) {
                    bsvList.add(tmpBsv);
                }
            }

            skippedStopsPenalty += EvaluationUtils.getSkippedStopsPenalty(bsvList);
        }

        if (Schedule.Category.OO == nextSchedule.getCategory()) {
            bsvList.clear();
            nodeList = nextSchedule.getRealizedNodes().isEmpty() ? nextSchedule.getPlannedNodes() : nextSchedule.getRealizedNodes();
            List<Integer> nextScheduleArrivalTimeList = solution.getScheduleStationArrivalTimeMap().get(nextSchedule);
            Map<Integer, String> nodeStatusMap = nextSchedule.getNodeStatus();
            for (int index = 0; index < nodeIndexInNextSchedule; ++index) {
                Node tmpNode = nodeList.get(index);
                if (!"STOP".equalsIgnoreCase(nodeStatusMap.get(index + 1))) {
                    continue;
                }
                int tmpArrivalTime = nextScheduleArrivalTimeList.get(index);
                int tmpBsv = EvaluationUtils.getBsv(tmpNode, tmpArrivalTime, nextSchedule.getDirection());
                if (tmpBsv > 0) {
                    bsvList.add(tmpBsv);
                }
            }
            skippedStopsPenalty += EvaluationUtils.getSkippedStopsPenalty(bsvList);
        }

        double benefit = delayPenaltyBenefit - skippedStopsPenalty;
//        LOGGER.info(String.format("Estimated partial cancellation benefit: %f\n", benefit));

        return benefit;
    }

    public double calculateDelayPenalty(int expectedEndTime, int actualEndTime) {
        int delay = actualEndTime - expectedEndTime;
        if (delay >= 3 * Constants.SECONDS_IN_MINUTE) {
            return delay * Constants.DELAY_PENALTY / Constants.SECONDS_IN_MINUTE;
        }

        return 0.0;
    }

    public Vertex checkDuplicatedVertex(Vertex vertex, String courseId, int nodeIndex, int rollingStockIndex) {
        String vertexUniqueKey = vertex.getUniqueKey();
        if (vertexMap.containsKey(vertexUniqueKey)) {
            return vertexMap.get(vertexUniqueKey);
        } else {
            ++vertexIndex;
            vertexMap.put(vertexUniqueKey, vertex);
            vertexTypeListMap.computeIfAbsent(vertex.getVertexType(), k -> new HashSet<>()).add(vertex);

            if (nodeIndex >= 0) {
                courseNodeVertexMap.computeIfAbsent(courseId, k -> new HashMap<>()).computeIfAbsent(nodeIndex, k -> new HashSet<>()).add(vertex);
            }

            switch (vertex.getVertexType()) {
                case COURSE_START: {
                    courseStartVertexMap.computeIfAbsent(courseId, k -> new HashSet<>()).add(vertex);
                    break;
                }
                case COURSE_END: {
                    courseEndVertexMap.computeIfAbsent(courseId, k -> new HashSet<>()).add(vertex);
                    break;
                }
                case TRAIN: {
                    trainVertexMap.put(rollingStockIndex, vertex);
                    break;
                }
            }

            return vertex;
        }
    }
}
