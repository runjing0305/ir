package eventbased.model;

import constant.Constants;
import context.*;
import eventbased.graph.*;
import gurobi.*;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;
import solution.Solution;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static constant.Constants.*;

/**
 * @author longfei
 */
@Data
public class EventBasedModel {

    private static final Logger LOGGER = Logger.getLogger(EventBasedModel.class.getName());

    private static final double MINIMUM_RUN_TIME_PENALTY_COEFFICIENT = 0.0;
    private static final double CHANGE_END_TIME_PENALTY_COEFFICIENT = 1.0;
    private static final double DWELL_TIME_PENALTY_COEFFICIENT = 1000.0;

    private GRBEnv grbEnv;
    private GRBModel grbModel;
    private Map<String, GRBVar> grbVarMap;
    private Map<GRBVar, String> grbVarNameMap;
    private Map<String, Vertex> skippedStopVarVertexMap = new HashMap<>();
    private Map<Vertex, Map<SkippedStopType, GRBVar>> vertexSkippedStopVarMap = new HashMap<>();
    private Map<String, Integer> initialSolutionValueMap;

    private Map<String, Map<Integer, List<Vertex>>> vertexGroupMap;
    private Map<String, List<Vertex>> nodeIdVertexMap;
    private Map<Track.Direction, List<Vertex>> directionListMap;

    public EventBasedModel() {
        try {
            grbEnv = new GRBEnv(true);
            grbEnv.set("logFile", "mip1.log");
            grbEnv.start();

            // Create empty model
            grbModel = new GRBModel(grbEnv);
        } catch (GRBException e) {
            e.printStackTrace();
        }
    }

    public void buildModel(ProblemContext problemContext, Graph graph, List<NodeGraph> nodeGraphList, Solution solution) {
        this.vertexGroupMap = new HashMap<>();
        for (Map.Entry<VertexType, Set<Vertex>> entry : graph.getVertexTypeListMap().entrySet()) {
            if (VertexType.NODE_STOP_LEAVE == entry.getKey() || VertexType.NODE_PASS == entry.getKey() || VertexType.NODE_LEAVE == entry.getKey() || VertexType.NODE_STOP == entry.getKey()) {
                for (Vertex vertex : entry.getValue()) {
                    String trackStr = vertex.getTrackName();
                    if (VertexType.NODE_STOP_LEAVE == vertex.getVertexType()) {
                        boolean partialCancellation = graph.getHeadVertexEdgeListMap().get(vertex).stream().anyMatch(edge -> {
                            Vertex tmpTailVertex = edge.getTail();
                            return tmpTailVertex.getVertexType() == VertexType.COURSE_END && tmpTailVertex.isPartialCancellationCourseStartEnd();
                        });
                        if (!partialCancellation) {
                            partialCancellation = graph.getTailVertexEdgeListMap().get(vertex).stream().anyMatch(edge -> {
                                Vertex tmpHeadVertex = edge.getHead();
                                return tmpHeadVertex.getVertexType() == VertexType.COURSE_START && tmpHeadVertex.isPartialCancellationCourseStartEnd();
                            });
                        }
                        if (partialCancellation) {
                            trackStr = vertex.getPartialCancellationTrack();
                        }
                    }
                    String key = String.join("_", vertex.getId(), trackStr);
                    vertexGroupMap.computeIfAbsent(key, k -> new HashMap<>()).computeIfAbsent(vertex.getTime(), k -> new ArrayList<>()).add(vertex);
                }
            }
        }

        addVariables(problemContext, graph, nodeGraphList, solution);
        buildInitialSolution(problemContext, graph, nodeGraphList, solution);
        addConstraints(problemContext, graph, nodeGraphList, solution);
        addObjective(problemContext, graph, nodeGraphList);
        addInitialSolution(problemContext, graph);
    }

    public void writeModel() {
        try {
            this.grbModel.write("event_based_model.lp");
        } catch (GRBException e) {
            e.printStackTrace();
        }
    }

    public EventBasedModel addVariables(ProblemContext problemContext, Graph graph, List<NodeGraph> nodeGraphList, Solution solution) {
        this.addEdgeVariables(problemContext, graph);
        this.addSkippedStopVariables(problemContext, graph, solution);
        this.addNodeGraphEdgeVariables(problemContext, nodeGraphList);
        return this;
    }

    public EventBasedModel buildInitialSolution(ProblemContext problemContext, Graph graph, List<NodeGraph> nodeGraphList, Solution solution) {
        this.initialSolutionValueMap = new HashMap<>(this.grbVarMap.size());
        for (String varName : this.grbVarMap.keySet()) {
            this.initialSolutionValueMap.put(varName, 0);
        }

        List<Integer> wbReferenceTimeList = new ArrayList<>();
        List<Integer> ebReferenceTimeList = new ArrayList<>();

        for (RollingStock rollingStock : solution.getRollingStock2ScheduleListMap().keySet()) {
            Vertex trainVertex = graph.getTrainVertexMap().get(rollingStock.getIndex());
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

            String firstDutyId = dutyIdList.get(0);
            String lastDutyId = dutyIdList.get(dutyIdList.size() - 1);
            int firstDutyStartTime = dutyStartTimeMap.get(firstDutyId);
            int firstDutyEndTime = dutyEndTimeMap.get(firstDutyId);
            int lastDutyStartTime = dutyStartTimeMap.get(lastDutyId);
            int lastDutyEndTime = dutyEndTimeMap.get(lastDutyId);

            Vertex firstRollingStockStartVertex = graph.getDutyStartVertexMap().get(firstDutyId).stream().filter(vertex -> vertex.getTime() == firstDutyStartTime).findFirst().orElse(null);

            Vertex lastRollingStockEndVertex = graph.getDutyEndVertexMap().get(lastDutyId).stream().filter(vertex -> vertex.getTime() == lastDutyEndTime).findFirst().orElse(null);

            Edge tmpEdge = graph.getHeadVertexEdgeListMap().get(trainVertex).stream().filter(edge -> edge.getTail() == firstRollingStockStartVertex).findFirst().orElse(null);
            this.initialSolutionValueMap.put(generateEdgeVarName(tmpEdge), 1);

            tmpEdge = graph.getTailVertexEdgeListMap().get(trainVertex).stream().filter(edge -> edge.getHead() == lastRollingStockEndVertex).findFirst().orElse(null);
            this.initialSolutionValueMap.put(generateEdgeVarName(tmpEdge), 1);

            String prevDutyId = null;
            for (int i = 0; i < dutyIdList.size(); ++i) {
                String currentDutyId = dutyIdList.get(i);

                int currentDutyStartTime = dutyStartTimeMap.get(currentDutyId);
                Vertex currentDutyStartVertex = graph.getDutyStartVertexMap().get(currentDutyId).stream().filter(vertex -> vertex.getTime() == currentDutyStartTime).findFirst().orElse(null);

                if (prevDutyId != null) {
                    int prevDutyEndTime = dutyEndTimeMap.get(prevDutyId);
                    Vertex prevDutyEndVertex = graph.getDutyEndVertexMap().get(prevDutyId).stream().filter(vertex -> vertex.getTime() == prevDutyEndTime).findFirst().orElse(null);

                    tmpEdge = graph.getHeadVertexEdgeListMap().get(prevDutyEndVertex).stream().filter(edge -> edge.getTail() == currentDutyStartVertex).findFirst().orElse(null);
                    this.initialSolutionValueMap.put(generateEdgeVarName(tmpEdge), 1);
                }

                int currentDutyEndTime = dutyEndTimeMap.get(currentDutyId);
                Vertex currentDutyEndVertex = graph.getDutyEndVertexMap().get(currentDutyId).stream().filter(vertex -> vertex.getTime() == currentDutyEndTime).findFirst().orElse(null);

                List<Schedule> schedules = solution.getDuty2ScheduleListMap().get(currentDutyId);
                schedules.sort(Comparator.comparing(s -> solution.getScheduleStationDepartureTimeMap().get(s).get(0)));
                Schedule firstScheduleInDuty = schedules.get(0);
                Schedule lastScheduleInDuty = schedules.get(schedules.size() - 1);

                List<Integer> firstScheduleDepartureTimeList = solution.getScheduleStationDepartureTimeMap().get(firstScheduleInDuty);
                List<Integer> lastScheduleArrivalTimeList = solution.getScheduleStationArrivalTimeMap().get(lastScheduleInDuty);
                int firstScheduleStartTime = firstScheduleDepartureTimeList.get(0);
                int lastScheduleEndTime = lastScheduleArrivalTimeList.get(lastScheduleArrivalTimeList.size() - 1);

                Vertex firstCourseStartVertex = graph.getCourseStartVertexMap().get(firstScheduleInDuty.getCourseId()).stream().filter(vertex -> vertex.getTime() == firstScheduleStartTime).findFirst().orElse(null);
                Vertex lastCourseEndVertex = graph.getCourseEndVertexMap().get(lastScheduleInDuty.getCourseId()).stream().filter(vertex -> vertex.getTime() == lastScheduleEndTime).findFirst().orElse(null);

                tmpEdge = graph.getHeadVertexEdgeListMap().get(currentDutyStartVertex).stream().filter(edge -> edge.getTail() == firstCourseStartVertex).findFirst().orElse(null);
                this.initialSolutionValueMap.put(generateEdgeVarName(tmpEdge), 1);

                tmpEdge = graph.getHeadVertexEdgeListMap().get(lastCourseEndVertex).stream().filter(edge -> edge.getTail() == currentDutyEndVertex).findFirst().orElse(null);
                this.initialSolutionValueMap.put(generateEdgeVarName(tmpEdge), 1);

                Schedule prevSchedule = null;
                for (int j = 0; j < schedules.size(); ++j) {
                    Schedule currentSchedule = schedules.get(j);
                    if (Schedule.EventType.TRAIN != currentSchedule.getEventType()) {
                        continue;
                    }
                    int currentScheduleStartTime = solution.getScheduleStationDepartureTimeMap().get(currentSchedule).get(0);
                    int currentScheduleEndTime = solution.getScheduleStationArrivalTimeMap().get(currentSchedule).get(solution.getScheduleStationArrivalTimeMap().get(currentSchedule).size() - 1);

                    Vertex currentScheduleStartVertex = graph.getCourseStartVertexMap().get(currentSchedule.getCourseId()).stream().filter(vertex -> vertex.getTime() == currentScheduleStartTime).findFirst().orElse(null);
                    Vertex currentScheduleEndVertex = graph.getCourseEndVertexMap().get(currentSchedule.getCourseId()).stream().filter(vertex -> vertex.getTime() == currentScheduleEndTime).findFirst().orElse(null);

                    if (prevSchedule != null) {
                        int prevScheduleEndTime = solution.getScheduleStationArrivalTimeMap().get(prevSchedule).get(solution.getScheduleStationArrivalTimeMap().get(prevSchedule).size() - 1);
                        Vertex prevScheduleEndVertex = graph.getCourseEndVertexMap().get(prevSchedule.getCourseId()).stream().filter(vertex -> vertex.getTime() == prevScheduleEndTime).findFirst().orElse(null);

                        tmpEdge = graph.getHeadVertexEdgeListMap().get(prevScheduleEndVertex).stream().filter(edge -> edge.getTail() == currentScheduleStartVertex).findFirst().orElse(null);
                        this.initialSolutionValueMap.put(generateEdgeVarName(tmpEdge), 1);
                    }

                    Vertex prevNodeVertex = null;
                    List<Node> nodeList = currentSchedule.getRealizedNodes().isEmpty() ? currentSchedule.getPlannedNodes() : currentSchedule.getRealizedNodes();
                    List<Integer> scheduleArrivalTime = solution.getScheduleStationArrivalTimeMap().get(currentSchedule);
                    List<Integer> scheduleDepartureTime = solution.getScheduleStationDepartureTimeMap().get(currentSchedule);

                    for (int k = 0; k < nodeList.size(); ++k) {
                        Node currentNode = nodeList.get(k);

                        int arrivalTime = 0;
                        int departureTime = 0;
                        if (k == 0) {
//                            departureTime = schedule.getLeaveTimes().get(1);
                            departureTime = scheduleDepartureTime.get(0);
                        } else if (k == nodeList.size() - 1) {
                            arrivalTime = scheduleArrivalTime.get(k);
//                            arrivalTime = schedule.getEnterTimes().get(schedule.getEnterTimes().size() + 1);
                        } else {
//                            arrivalTime = schedule.getEnterTimes().get(nodeIndex + 1);
//                            departureTime = schedule.getLeaveTimes().get(nodeIndex + 1);
                            arrivalTime = scheduleArrivalTime.get(k);
                            departureTime = scheduleDepartureTime.get(k);
                        }

                        Vertex currentNodeVertex;
                        if (k == 0) {
                            int finalPlannedTime = departureTime;
                            currentNodeVertex = graph.getCourseNodeVertexMap().get(currentSchedule.getCourseId()).get(0).stream().filter(vertex -> vertex.getTime() == finalPlannedTime).findFirst().orElse(null);
                            Vertex finalCurrentNodeVertex = currentNodeVertex;
                            tmpEdge = graph.getHeadVertexEdgeListMap().get(currentScheduleStartVertex).stream().filter(edge -> edge.getTail() == finalCurrentNodeVertex).findFirst().orElse(null);
                            this.initialSolutionValueMap.put(generateEdgeVarName(tmpEdge), 1);
                        } else if (k == nodeList.size() - 1) {
                            int finalPlannedTime4 = arrivalTime;
                            currentNodeVertex = graph.getCourseNodeVertexMap().get(currentSchedule.getCourseId()).get(nodeList.size() - 1).stream().filter(vertex -> vertex.getTime() == finalPlannedTime4).findFirst().orElse(null);

                            Vertex finalPrevNodeVertex = prevNodeVertex;
                            tmpEdge = graph.getTailVertexEdgeListMap().get(currentNodeVertex).stream().filter(edge -> edge.getHead() == finalPrevNodeVertex).findFirst().orElse(null);
                            this.initialSolutionValueMap.put(generateEdgeVarName(tmpEdge), 1);

                            tmpEdge = graph.getHeadVertexEdgeListMap().get(currentNodeVertex).stream().filter(edge -> edge.getTail() == currentScheduleEndVertex).findFirst().orElse(null);
                            this.initialSolutionValueMap.put(generateEdgeVarName(tmpEdge), 1);
                        } else {
                            if (solution.getScheduleSkipStationMap().get(currentSchedule).get(k)) {
                                int finalPlannedTime1 = departureTime;
                                currentNodeVertex = graph.getCourseNodeVertexMap().get(currentSchedule.getCourseId()).get(k).stream().filter(vertex -> vertex.getTime() == finalPlannedTime1 && vertex.getVertexType() == VertexType.NODE_PASS).findFirst().orElse(null);
                                Vertex finalCurrentNodeVertex1 = currentNodeVertex;
                                tmpEdge = graph.getHeadVertexEdgeListMap().get(prevNodeVertex).stream().filter(edge -> edge.getTail() == finalCurrentNodeVertex1).findFirst().orElse(null);
                                this.initialSolutionValueMap.put(generateEdgeVarName(tmpEdge), 1);
                            } else {
                                int finalPlannedTime2 = arrivalTime;
                                currentNodeVertex = graph.getCourseNodeVertexMap().get(currentSchedule.getCourseId()).get(k).stream().filter(vertex -> vertex.getTime() == finalPlannedTime2 && vertex.getVertexType() == VertexType.NODE_STOP).findFirst().orElse(null);
                                Vertex finalCurrentNodeVertex2 = currentNodeVertex;
                                tmpEdge = graph.getHeadVertexEdgeListMap().get(prevNodeVertex).stream().filter(edge -> edge.getTail() == finalCurrentNodeVertex2).findFirst().orElse(null);
                                this.initialSolutionValueMap.put(generateEdgeVarName(tmpEdge), 1);
                                prevNodeVertex = currentNodeVertex;

                                int finalPlannedTime3 = departureTime;
                                currentNodeVertex = graph.getCourseNodeVertexMap().get(currentSchedule.getCourseId()).get(k).stream().filter(vertex -> vertex.getTime() == finalPlannedTime3 && vertex.getVertexType() == VertexType.NODE_LEAVE).findFirst().orElse(null);
                                Vertex finalCurrentNodeVertex3 = currentNodeVertex;
                                tmpEdge = graph.getHeadVertexEdgeListMap().get(prevNodeVertex).stream().filter(edge -> edge.getTail() == finalCurrentNodeVertex3).findFirst().orElse(null);
                                this.initialSolutionValueMap.put(generateEdgeVarName(tmpEdge), 1);
                            }
                        }

                        if (Schedule.Category.OO == currentSchedule.getCategory()) {
                            if (WEST_BOUND_REFERENCE_STATION.equals(currentNode.getCode()) && currentSchedule.getDirection() == Track.Direction.WB) {
                                if (k == 0) {
                                    wbReferenceTimeList.add(departureTime);
                                } else {
                                    wbReferenceTimeList.add(arrivalTime);
                                }
                            } else if (EAST_BOUND_REFERENCE_STATION.equals(currentNode.getCode()) && currentSchedule.getDirection() == Track.Direction.EB) {
                                if (k == 0) {
                                    ebReferenceTimeList.add(departureTime);
                                } else {
                                    ebReferenceTimeList.add(arrivalTime);
                                }
                            }
                        }
                        prevNodeVertex = currentNodeVertex;
                    }
                    prevSchedule = currentSchedule;
                }

                prevDutyId = currentDutyId;
            }
        }

        wbReferenceTimeList.sort(Comparator.comparingInt(x -> x));
        ebReferenceTimeList.sort(Comparator.comparingInt(x -> x));
        for (NodeGraph nodeGraph : nodeGraphList) {
            List<Integer> referenceTimeList = nodeGraph.getNodeCode().equals(WEST_BOUND_REFERENCE_STATION) ? wbReferenceTimeList : ebReferenceTimeList;

            List<Vertex> vertexList = new ArrayList<>(referenceTimeList.size() + 2);
            vertexList.add(nodeGraph.getDummyNodeStartVertex());
            for (int timePoint : referenceTimeList) {
                vertexList.add(nodeGraph.getVertexMap().get(timePoint));
            }
            vertexList.add(nodeGraph.getDummyNodeEndVertex());

            for (int i = 0; i < vertexList.size() - 1; ++i) {
                Vertex firstVertex = vertexList.get(i);
                Vertex secondVertex = vertexList.get(i + 1);

                Edge edge = nodeGraph.getHeadEdgeListMap().get(firstVertex).stream().filter(tmpEdge -> secondVertex == tmpEdge.getTail()).findFirst().orElse(null);

                if (edge == null) {
                    LOGGER.warning("Failed to find the edge in node graph");
                }

                String tmpVarName = generateNodeGraphEdgeVarName(nodeGraph, edge);
                this.initialSolutionValueMap.put(tmpVarName, 1);
            }
        }

        return this;
    }

    public EventBasedModel addConstraints(ProblemContext problemContext, Graph graph, List<NodeGraph> nodeGraphList, Solution solution) {
        this.addDegreeConstraints(problemContext, graph);
        this.addFlowBalanceConstraints(problemContext, graph);
        this.addTrackCapacityConstraints(problemContext, graph);
        this.addMinimumHeadwayConstraints(problemContext, graph, solution);
        this.addSkippedStopConstraints(problemContext, graph);
        this.addNodeGraphDegreeFlowBalanceConstraints(problemContext, graph, nodeGraphList);
        // TODO: Add First In First Out Lazy Constraint (Callback)

        return this;
    }

    public EventBasedModel addObjective(ProblemContext problemContext, Graph graph, List<NodeGraph> nodeGraphList) {
        try {
            double initSolTotalObj = 0.0;
            double initSolSkippedStopPenalty = 0.0;
            double initSolDestinationPenalty = 0.0;
            double initSolPassageFrequencyPenalty = 0.0;
            GRBLinExpr objExpr = new GRBLinExpr();

            for (Map.Entry<Vertex, Map<SkippedStopType, GRBVar>> entry : vertexSkippedStopVarMap.entrySet()) {
                Vertex vertex = entry.getKey();
                int value = vertex.getBsv();
                // Some stations do not have BSV
                if (value == 0) {
                    continue;
                }

                for (Map.Entry<SkippedStopType, GRBVar> entry1 : entry.getValue().entrySet()) {
                    objExpr.addTerm(value * entry1.getKey().getFactor(), entry1.getValue());
                    initSolSkippedStopPenalty += value * entry1.getKey().getFactor() * this.initialSolutionValueMap.get(this.grbVarNameMap.get(entry1.getValue()));
                }
            }

            for (Vertex vertex : graph.getVertexTypeListMap().get(VertexType.COURSE_END)) {
                Schedule schedule = problemContext.getCourseId2Schedule().get(vertex.getCourseId());
                if (Schedule.Category.EE == schedule.getCategory()) {
                    continue;
                }

                if (!vertex.isPartialCancellationCourseStartEnd()) {
                    continue;
                }

                for (Edge edge : graph.getTailVertexEdgeListMap().get(vertex)) {
                    String tmpVarName = generateEdgeVarName(edge);
                    GRBVar grbVar = this.grbVarMap.get(tmpVarName);
                    objExpr.addTerm(vertex.getPartialCancellationSkippedStopsPenalty(), grbVar);

                    initSolSkippedStopPenalty += vertex.getPartialCancellationSkippedStopsPenalty() * this.initialSolutionValueMap.get(tmpVarName);
                }
            }

            for (Vertex vertex : graph.getVertexTypeListMap().get(VertexType.COURSE_START)) {
                Schedule schedule = problemContext.getCourseId2Schedule().get(vertex.getCourseId());
                if (Schedule.Category.EE == schedule.getCategory()) {
                    continue;
                }

                if (!vertex.isPartialCancellationCourseStartEnd()) {
                    continue;
                }

                for (Edge edge : graph.getHeadVertexEdgeListMap().get(vertex)) {
                    String tmpVarName = generateEdgeVarName(edge);
                    GRBVar grbVar = this.grbVarMap.get(tmpVarName);
                    objExpr.addTerm(vertex.getPartialCancellationSkippedStopsPenalty(), grbVar);

                    initSolSkippedStopPenalty += vertex.getPartialCancellationSkippedStopsPenalty() * this.initialSolutionValueMap.get(tmpVarName);
                }
            }

            for (NodeGraph nodeGraph : nodeGraphList) {
                for (Edge edge : nodeGraph.getEdgeList()) {
                    double penalty = edge.getPassageFrequencyPenalty();
                    if (penalty > 0.0) {
                        String tmpVarName = generateNodeGraphEdgeVarName(nodeGraph, edge);
                        objExpr.addTerm(penalty, this.grbVarMap.get(tmpVarName));
                        initSolPassageFrequencyPenalty += penalty * this.initialSolutionValueMap.get(tmpVarName);
                    }
                }
            }

            for (Vertex vertex : graph.getVertexTypeListMap().get(VertexType.COURSE_END)) {
                if (Schedule.Category.EE == problemContext.getCourseId2Schedule().get(vertex.getCourseId()).getCategory()) {
                    continue;
                }
                double delayPenalty = vertex.getDestinationDelayPenalty();
                if (delayPenalty <= 0.0) {
                    continue;
                }
                for (Edge edge : graph.getTailVertexEdgeListMap().get(vertex)) {
                    String tmpVarName = generateEdgeVarName(edge);
                    GRBVar grbVar = this.grbVarMap.get(tmpVarName);
                    objExpr.addTerm(delayPenalty, grbVar);

                    initSolDestinationPenalty += delayPenalty * this.initialSolutionValueMap.get(tmpVarName);
                }
            }

            boolean addPenaltyForDwellTimeViolation = false;
            if (addPenaltyForDwellTimeViolation) {
                for (Edge edge : graph.getEdgeTypeListMap().get(EdgeType.SAME_STATION_NODE_TO_NODE)) {
                    Vertex headVertex = edge.getHead();
                    Vertex tailVertex = edge.getTail();

                    double dwellTimeViolation;
                    if (headVertex.isRealized() && tailVertex.isRealized()) {
                        continue;
                    }

                    int actualDwellTime = tailVertex.getTime() - headVertex.getTime();

                    int nodeSeq = headVertex.getNodeSeq();
                    String courseId = headVertex.getCourseId();
                    int expectedDwellTime = problemContext.getCourseId2Schedule().get(courseId).getDwellTimes().get(nodeSeq);

                    if (actualDwellTime < expectedDwellTime) {
                        dwellTimeViolation = expectedDwellTime - actualDwellTime;
                        String tmpVarName = generateEdgeVarName(edge);
                        GRBVar tmpGrbVar = this.grbVarMap.get(tmpVarName);
                        objExpr.addTerm(dwellTimeViolation * DWELL_TIME_PENALTY_COEFFICIENT, tmpGrbVar);
                    }
                }
            }

//            for (Edge edge : graph.getEdgeTypeListMap().get(EdgeType.COURSE_END_TO_COURSE_START)) {
//                int changeEndTimeViolate = edge.getChangeEndTimeViolation();
//                if (changeEndTimeViolate <= 0) {
//                    continue;
//                }
//                String tmpVarName = generateEdgeVarName(edge);
//                GRBVar grbVar = this.grbVarMap.get(tmpVarName);
//                objExpr.addTerm(changeEndTimeViolate * CHANGE_END_TIME_PENALTY_COEFFICIENT, grbVar);
//            }
//            for (Edge edge : graph.getEdgeTypeListMap().get(EdgeType.CROSS_STATION_NODE_TO_NODE)) {
//                int minimumRunTimeViolate = edge.getMinimumRunTimeViolation();
//                if (minimumRunTimeViolate <= 0) {
//                    continue;
//                }
//                String tmpVarName = generateEdgeVarName(edge);
//                GRBVar grbVar = this.grbVarMap.get(tmpVarName);
//                objExpr.addTerm(minimumRunTimeViolate * MINIMUM_RUN_TIME_PENALTY_COEFFICIENT, grbVar);
//            }
            this.grbModel.setObjective(objExpr, GRB.MINIMIZE);

            initSolTotalObj = initSolSkippedStopPenalty + initSolDestinationPenalty + initSolPassageFrequencyPenalty;
            LOGGER.info(String.format("Objective Value of Initial Solution: %f (%f, %f, %f)\n", initSolTotalObj, initSolSkippedStopPenalty, initSolDestinationPenalty, initSolPassageFrequencyPenalty));
        } catch (GRBException grbException) {
            grbException.printStackTrace();
        }

        return this;
    }

    public EventBasedModel addInitialSolution(ProblemContext problemContext, Graph graph) {
        try {
            for (Map.Entry<String, GRBVar> entry : this.grbVarMap.entrySet()) {
                double value = this.initialSolutionValueMap.get(entry.getKey());
                entry.getValue().set(GRB.DoubleAttr.Start, value);
            }
        } catch (GRBException exception) {
            exception.printStackTrace();
        }

        return this;
    }

    public EventBasedModel addEdgeVariables(ProblemContext problemContext, Graph graph) {
        try {
            int edgeVarNum = graph.getEdgeTypeListMap().values().stream().mapToInt(List::size).sum();
            this.grbVarMap = new HashMap<>(edgeVarNum);
            this.grbVarNameMap = new HashMap<>(edgeVarNum);
            for (List<Edge> edgeList : graph.getEdgeTypeListMap().values()) {
                for (Edge edge : edgeList) {
                    String varName = generateEdgeVarName(edge);
                    GRBVar tmpVar = this.grbModel.addVar(0.0, 1.0, 0.0, GRB.BINARY, varName);
                    grbVarMap.put(varName, tmpVar);
                    grbVarNameMap.put(tmpVar, varName);
                }
            }
        } catch (GRBException e) {
            e.printStackTrace();
        }

        LOGGER.info(String.format("Add Edge Variables Complete. Variables num: %d\n", this.grbVarMap.size()));

        return this;
    }

    public EventBasedModel addSkippedStopVariables(ProblemContext problemContext, Graph graph, Solution solution) {
        int variableNum = 0;
        try {
            for (Vertex vertex : graph.getVertexTypeListMap().get(VertexType.NODE_PASS)) {
                String courseId = vertex.getCourseId();
                int nodeSeq = vertex.getNodeSeq();

                Schedule schedule = problemContext.getCourseId2Schedule().get(courseId);
                if (Schedule.EventType.TRAIN != schedule.getEventType() || Schedule.Category.OO != schedule.getCategory()) {
                    continue;
                }

                boolean skipStation = solution.getScheduleSkipStationMap().get(schedule).get(nodeSeq - 1);
                if (skipStation) {
                    continue;
                }

                vertexSkippedStopVarMap.put(vertex, new HashMap<>());
                for (SkippedStopType skippedStopType : SkippedStopType.values()) {
                    String tmpVarName = generateSkippedStopVarName(vertex, skippedStopType);
                    GRBVar tmpVar = this.grbModel.addVar(0.0, 1.0, 0.0, GRB.BINARY, tmpVarName);
                    ++variableNum;
                    grbVarMap.put(tmpVarName, tmpVar);
                    grbVarNameMap.put(tmpVar, tmpVarName);
                    skippedStopVarVertexMap.put(tmpVarName, vertex);
                    vertexSkippedStopVarMap.get(vertex).put(skippedStopType, tmpVar);
                }
            }
        } catch (GRBException e) {
            e.printStackTrace();
        }
        LOGGER.info(String.format("Add Skipped Stop Variables Complete. Variables num: %d\n", variableNum));
        return this;
    }


    public EventBasedModel addNodeGraphEdgeVariables(ProblemContext problemContext, List<NodeGraph> nodeGraphList) {
        if (nodeGraphList == null || nodeGraphList.isEmpty()) {
            return this;
        }
        int variableNum = 0;
        try {
            for (NodeGraph nodeGraph : nodeGraphList) {
                for (Edge edge : nodeGraph.getEdgeList()) {
                    String varName = generateNodeGraphEdgeVarName(nodeGraph, edge);
                    GRBVar tmpVar = this.grbModel.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, varName);
                    grbVarMap.put(varName, tmpVar);
                    grbVarNameMap.put(tmpVar, varName);
                    ++variableNum;
                }
            }
        } catch (GRBException grbException) {
            grbException.printStackTrace();
        }
        LOGGER.info(String.format("Add Node Graph Edge Variables Complete. Variables num: %d\n", variableNum));
        return this;
    }

    public EventBasedModel addDegreeConstraints(ProblemContext problemContext, Graph graph) {
        int constraintNum = 0;
        try {
            GRBLinExpr tmpExpr = new GRBLinExpr();
            // For train vertex
            for (Vertex vertex : graph.getVertexTypeListMap().get(VertexType.TRAIN)) {
                tmpExpr.clear();
                double lhs = 0.0;
                for (Edge edge : graph.getHeadVertexEdgeListMap().get(vertex)) {
                    String varName = generateEdgeVarName(edge);
                    GRBVar tmpVar = this.grbVarMap.get(varName);
                    tmpExpr.addTerm(1.0, tmpVar);

                    lhs += 1.0 * this.initialSolutionValueMap.get(varName);
                }

                String constraintName = String.join("_", "DEG", "TRAIN", String.valueOf(vertex.getIndex()));
                if (lhs != 1.0) {
                    LOGGER.warning(String.format("%s is violated by the initial solution.\n", constraintName));
                }

                this.grbModel.addConstr(tmpExpr, GRB.EQUAL, 1.0, constraintName);
                constraintNum += 1;
            }

            // For duty start/end vertex, course start/end vertex
            for (VertexType vertexType : Arrays.asList(VertexType.DUTY_START, VertexType.DUTY_END, VertexType.COURSE_START, VertexType.COURSE_END)) {
                Map<String, List<Vertex>> vertexListMap = graph.getVertexTypeListMap().get(vertexType).stream().collect(Collectors.groupingBy(Vertex::getId));
                for (Map.Entry<String, List<Vertex>> entry : vertexListMap.entrySet()) {
                    tmpExpr.clear();

                    double lhs = 0.0;
                    for (Vertex vertex : entry.getValue()) {
                        for (Edge edge : graph.getTailVertexEdgeListMap().get(vertex)) {
                            String tmpVarName = generateEdgeVarName(edge);
                            tmpExpr.addTerm(1.0, this.grbVarMap.get(tmpVarName));

                            lhs += 1.0 * this.initialSolutionValueMap.get(tmpVarName);
                        }
                    }

                    String constraintName = String.join("_", "DEG", "RJ_S_E", vertexType.name(), entry.getKey());

                    if (lhs != 1.0) {
                        LOGGER.warning(String.format("%s is violated by the initial solution.\n", constraintName));
                    }

                    this.grbModel.addConstr(tmpExpr, GRB.EQUAL, 1.0, constraintName);
                    constraintNum += 1;
                }
            }
        } catch (GRBException e) {
            e.printStackTrace();
        }

        LOGGER.info(String.format("Add Degree Constraints Complete. Constraint num: %d\n", constraintNum));

        return this;
    }

    public EventBasedModel addFlowBalanceConstraints(ProblemContext problemContext, Graph graph) {
        int constraintNum = 0;
        try {
            GRBLinExpr grbLinExpr = new GRBLinExpr();
            for (Vertex vertex : graph.getVertexMap().values()) {
                grbLinExpr.clear();
                double lhs = 0.0;
                for (Edge edge : graph.getTailVertexEdgeListMap().get(vertex)) {
                    String tmpVarName = generateEdgeVarName(edge);
                    GRBVar tmpVar = this.grbVarMap.get(tmpVarName);
                    grbLinExpr.addTerm(1.0, tmpVar);
                    lhs += 1.0 * this.initialSolutionValueMap.get(tmpVarName);
                }

                for (Edge edge : graph.getHeadVertexEdgeListMap().get(vertex)) {
                    String tmpVarName = generateEdgeVarName(edge);
                    GRBVar tmpVar = this.grbVarMap.get(tmpVarName);
                    grbLinExpr.addTerm(-1.0, tmpVar);
                    lhs += -1.0 * this.initialSolutionValueMap.get(tmpVarName);
                }

                String constraintName = String.join("_", "FB", String.valueOf(vertex.getIndex()));
                if (lhs != 0.0) {
                    LOGGER.warning(String.format("%s is violated by the initial solution.\n", constraintName));
                }

                this.grbModel.addConstr(grbLinExpr, GRB.EQUAL, 0.0, constraintName);
                constraintNum += 1;
            }
        } catch (GRBException grbException) {
            grbException.printStackTrace();
        }

        LOGGER.info(String.format("Add Flow Balance Constraints Complete. Constraint num: %d\n", constraintNum));
        return this;
    }

    public EventBasedModel addTrackCapacityConstraints(ProblemContext problemContext, Graph graph) {
        int constraintNum = 0;
        double lhs;
        try {
            GRBLinExpr grbLinExpr = new GRBLinExpr();
            for (Node node : problemContext.getNodes()) {
                for (Track track : node.getTracks()) {
                    Track.Direction direction = track.getDirection();
                    String key = String.join("_", node.getCode(), track.getName());
                    Map<Integer, List<Vertex>> timeVertexGroupMap = vertexGroupMap.getOrDefault(key, null);
                    if (timeVertexGroupMap == null) {
                        continue;
                    }

                    List<Integer> timePointList = new ArrayList<>(timeVertexGroupMap.keySet());
                    timePointList.sort(Integer::compareTo);

                    for (int i = 0; i < timePointList.size(); ++i) {
                        int time1 = timePointList.get(i);

                        List<Vertex> vertexList1 = timeVertexGroupMap.get(time1);

                        grbLinExpr.clear();
                        lhs = 0.0;
                        StringBuilder stringBuilder = new StringBuilder();
                        for (Vertex vertex1 : vertexList1) {
                            if (VertexType.NODE_STOP == vertex1.getVertexType()) {
                                continue;
                            }

                            for (Edge edge : graph.getHeadVertexEdgeListMap().get(vertex1)) {
                                String tmpVarName = generateEdgeVarName(edge);
                                GRBVar tmpVar = this.grbVarMap.get(tmpVarName);
                                grbLinExpr.addTerm(1.0, tmpVar);
                                lhs += 1.0 * this.initialSolutionValueMap.get(tmpVarName);

                                stringBuilder.append(" ").append(vertex1.getUniqueKey());
                            }
                        }

                        for (int j = i + 1; j < timePointList.size(); ++j) {
                            int time2 = timePointList.get(j);

                            if (time2 - time1 >= MINIMUM_SEPARATION_TIME) {
                                break;
                            }

                            List<Vertex> vertexList2 = timeVertexGroupMap.get(time2);
                            for (Vertex vertex2 : vertexList2) {
                                if (VertexType.NODE_LEAVE == vertex2.getVertexType()) {
                                    continue;
                                }

                                if (vertex2.isRealized()) {
                                    continue;
                                }

                                for (Edge edge : graph.getTailVertexEdgeListMap().get(vertex2)) {
                                    String tmpVarName = generateEdgeVarName(edge);
                                    GRBVar tmpVar = this.grbVarMap.get(tmpVarName);
                                    grbLinExpr.addTerm(1.0, tmpVar);

                                    lhs += 1.0 * this.initialSolutionValueMap.get(tmpVarName);

                                    stringBuilder.append(" ").append(vertex2.getUniqueKey());
                                }
                            }
                        }

                        String constraintName = String.join("_", "TrackCapacity", node.getCode(), track.getName(), direction.name(), String.valueOf(time1));
                        if (lhs > 1.0) {
                            LOGGER.warning(String.format("%s is violated by the initial solution.\n", constraintName));
                            LOGGER.warning(stringBuilder.toString());
                        }

                        this.grbModel.addConstr(grbLinExpr, GRB.LESS_EQUAL, 1.0, constraintName);
                        constraintNum += 1;
                    }

                    for (int i = 0; i < timePointList.size(); ++i) {
                        int time1 = timePointList.get(i);

                        List<Vertex> vertexList1 = timeVertexGroupMap.get(time1);

                        for (Vertex vertex1 : vertexList1) {
                            if (VertexType.NODE_STOP != vertex1.getVertexType()) {
                                continue;
                            }

                            for (Edge edge : graph.getHeadVertexEdgeListMap().get(vertex1)) {
                                if (EdgeType.SAME_STATION_NODE_TO_NODE != edge.getEdgeType()) {
                                    continue;
                                }

                                int tmpEndTime = edge.getTail().getTime();

                                for (int j = i; j < timePointList.size(); ++j) {
                                    int tmpTimePoint = timePointList.get(j);

                                    if (tmpTimePoint > tmpEndTime) {
                                        break;
                                    }

                                    List<Vertex> vertexList2 = timeVertexGroupMap.get(tmpTimePoint);

                                    for (Vertex vertex2 : vertexList2) {
                                        if (vertex2 == vertex1) {
                                            continue;
                                        }
                                        if (VertexType.NODE_LEAVE == vertex2.getVertexType()) {
                                            continue;
                                        }
                                        if (vertex2.isRealized()) {
                                            continue;
                                        }
                                        grbLinExpr.clear();
                                        lhs = 0.0;
                                        StringBuilder stringBuilder = new StringBuilder();
                                        StringBuilder stringBuilder2 = new StringBuilder();
                                        String tmpVarName = generateEdgeVarName(edge);
                                        GRBVar tmpVar = this.grbVarMap.get(tmpVarName);
                                        grbLinExpr.addTerm(1.0, tmpVar);
                                        lhs += 1.0 * this.initialSolutionValueMap.get(tmpVarName);
                                        stringBuilder.append(" ").append(vertex1.getUniqueKey()).append(" -> ").append(edge.getTail().getUniqueKey());
                                        if (this.initialSolutionValueMap.get(tmpVarName) > 0.0) {
                                            stringBuilder2.append(" ").append(vertex1.getUniqueKey()).append(" -> ").append(edge.getTail().getUniqueKey());
                                        }

                                        for (Edge edge2 : graph.getHeadVertexEdgeListMap().get(vertex2)) {
                                            String tmpVarName2 = generateEdgeVarName(edge2);
                                            GRBVar tmpVar2 = this.grbVarMap.get(tmpVarName2);
                                            grbLinExpr.addTerm(1.0, tmpVar2);
                                            stringBuilder.append(" ").append(vertex2.getUniqueKey()).append(" -> ").append(edge2.getTail().getUniqueKey());

                                            lhs += 1.0 * this.initialSolutionValueMap.get(tmpVarName2);
                                            if (this.initialSolutionValueMap.get(tmpVarName2) > 0.0) {
                                                stringBuilder2.append(" ").append(vertex2.getUniqueKey()).append(" -> ").append(edge2.getTail().getUniqueKey());
                                            }
                                        }
                                        String constraintName = String.join("_", "TrackCapacity", node.getCode(), track.getName(), direction.name(), String.valueOf(time1), String.valueOf(edge.getTail().getTime()), vertex2.getId());

                                        this.grbModel.addConstr(grbLinExpr, GRB.LESS_EQUAL, 1.0, constraintName);
                                        if (lhs > 1.0) {
                                            LOGGER.warning(String.format("%s is violated by the initial solution.\n", constraintName));
                                            LOGGER.warning(stringBuilder.toString());
                                            LOGGER.warning(stringBuilder2.toString());
                                        }
                                        constraintNum += 1;
                                    }
                                }
                            }
                        }
                    }

                    for (int i = 0; i < timePointList.size(); ++i) {
                        int time1 = timePointList.get(i);

                        List<Vertex> vertexList1 = timeVertexGroupMap.get(time1);

                        for (Vertex vertex1 : vertexList1) {
                            if (VertexType.NODE_STOP_LEAVE != vertex1.getVertexType()) {
                                continue;
                            }

                            for (Edge edge : graph.getHeadVertexEdgeListMap().get(vertex1)) {
                                Vertex tailVertex = edge.getTail();
                                if (tailVertex.getVertexType() != VertexType.COURSE_END) {
                                    continue;
                                }

                                boolean firstRealized = vertex1.isRealized();

                                String tmpVarName = generateEdgeVarName(edge);
                                GRBVar tmpVar = this.grbVarMap.get(tmpVarName);

                                if (!problemContext.getCode2Node().get(vertex1.getId()).isDepot()) {
                                    for (Edge nextEdge : graph.getHeadVertexEdgeListMap().get(tailVertex)) {

                                        double departureTime;
                                        if (EdgeType.COURSE_END_TO_COURSE_START == nextEdge.getEdgeType()) {
                                            departureTime = nextEdge.getTail().getTime();
                                        } else {
                                            departureTime = 2 * 86400;
                                        }

                                        String tmpVarName2 = generateEdgeVarName(nextEdge);
                                        GRBVar tmpVar2 = this.grbVarMap.get(tmpVarName2);

                                        for (int j = i; j < timePointList.size(); ++j) {
                                            int time2 = timePointList.get(j);

                                            if (time2 - departureTime >= MINIMUM_SEPARATION_TIME) {
                                                break;
                                            }

                                            List<Vertex> vertexList2 = timeVertexGroupMap.get(time2);
                                            for (Vertex vertex2 : vertexList2) {
                                                if (vertex2 == vertex1 || VertexType.NODE_LEAVE == vertex2.getVertexType()) {
                                                    continue;
                                                }

                                                if (vertex2.isRealized() && firstRealized) {
                                                    continue;
                                                }

                                                for (Edge arrivalEdge : graph.getTailVertexEdgeListMap().get(vertex2)) {
                                                    if (arrivalEdge.getRollingStockIndex() == nextEdge.getRollingStockIndex()) {
                                                        continue;
                                                    }
                                                    String tmpVarName3 = generateEdgeVarName(arrivalEdge);
                                                    GRBVar tmpVar3 = this.grbVarMap.get(tmpVarName3);

                                                    grbLinExpr.clear();
                                                    grbLinExpr.addTerm(1.0, tmpVar);
                                                    grbLinExpr.addTerm(1.0, tmpVar2);
                                                    grbLinExpr.addTerm(1.0, tmpVar3);

                                                    lhs = 0.0;
                                                    lhs += 1.0 * this.initialSolutionValueMap.get(tmpVarName);
                                                    lhs += 1.0 * this.initialSolutionValueMap.get(tmpVarName2);
                                                    lhs += 1.0 * this.initialSolutionValueMap.get(tmpVarName3);


                                                    String constraintName = String.join("_", "TrackCapacity", edge.getHead().getId(), edge.getTail().getId(), nextEdge.getHead().getId(), nextEdge.getTail().getId(), arrivalEdge.getHead().getId(), arrivalEdge.getTail().getId());
                                                    this.grbModel.addConstr(grbLinExpr, GRB.LESS_EQUAL, 2.0, constraintName);
                                                    if (lhs > 2.0) {
                                                        System.out.println(edge);
                                                        System.out.println(nextEdge);
                                                        System.out.println(arrivalEdge);
                                                        LOGGER.warning(String.format("%s is violated by the initial solution.\n", constraintName));
//                                                        LOGGER.warning(stringBuilder.toString());
//                                                        LOGGER.warning(stringBuilder2.toString());
                                                    }
                                                    constraintNum += 1;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (GRBException grbException) {
            grbException.printStackTrace();
        }

        LOGGER.info(String.format("Add Track Capacity Constraints Complete. Constraint num: %d\n", constraintNum));
        return this;
    }

    public EventBasedModel addMinimumHeadwayConstraints(ProblemContext problemContext, Graph graph, Solution solution) {
        int constraintNum = 0;
        double lhs;
        try {
            Map<String, List<Edge>> startEndEdgeListMap = graph.getEdgeTypeListMap().get(EdgeType.CROSS_STATION_NODE_TO_NODE)
                    .stream().collect(Collectors.groupingBy(edge -> Link.generateLinkName(edge.getHead().getId(), edge.getTail().getId())));

            Map<Edge, Pair<Integer, Integer>> edgeTimeBandMap;
            GRBLinExpr grbLinExpr = new GRBLinExpr();

            for (Map.Entry<String, List<Edge>> entry : startEndEdgeListMap.entrySet()) {
                edgeTimeBandMap = new HashMap<>();
                Map<Integer, List<Edge>> rollingStockEdgeListMap = entry.getValue().stream().collect(Collectors.groupingBy(Edge::getRollingStockIndex));
                List<Integer> rollingStockIndices = new ArrayList<>(rollingStockEdgeListMap.keySet());

                Map<Integer, Map<Integer, List<Edge>>> rollingStockTimeBandEdgeListMap = new HashMap<>();
                for (Map.Entry<Integer, List<Edge>> entry1 : rollingStockEdgeListMap.entrySet()) {
                    Map<Integer, List<Edge>> timeBandEdgeListMap = new HashMap<>();
                    for (Edge edge : entry1.getValue()) {
                        Pair<Integer, Integer> timeBand = generateEdgeTimeBand(problemContext, graph, edge);
                        edgeTimeBandMap.put(edge, timeBand);

                        for (int i = timeBand.getKey(); i <= timeBand.getValue(); ++i) {
                            timeBandEdgeListMap.computeIfAbsent(i, k -> new ArrayList<>()).add(edge);
                        }
                    }

                    rollingStockTimeBandEdgeListMap.put(entry1.getKey(), timeBandEdgeListMap);
                }

                Link link = problemContext.getName2Link().get(entry.getKey());

                for (Map.Entry<Integer, List<Edge>> entry1 : rollingStockEdgeListMap.entrySet()) {
                    int rollingStockIndex = entry1.getKey();
                    for (Edge edge : entry1.getValue()) {
                        Pair<Integer, Integer> timeBand = edgeTimeBandMap.get(edge);
                        Vertex headVertex = edge.getHead();
                        Vertex tailVertex = edge.getTail();

                        for (Integer otherRollingStockIndex : rollingStockIndices) {
                            if (rollingStockIndex == otherRollingStockIndex) {
                                continue;
                            }

                            Map<Integer, List<Edge>> otherTimeBandEdgeListMap = rollingStockTimeBandEdgeListMap.get(otherRollingStockIndex);

                            for (int tmpTimeIndex = timeBand.getKey() - 3; tmpTimeIndex <= timeBand.getValue() + 3; ++tmpTimeIndex) {
                                if (!otherTimeBandEdgeListMap.containsKey(tmpTimeIndex)) {
                                    continue;
                                }
                                List<Edge> otherEdgeList = otherTimeBandEdgeListMap.get(tmpTimeIndex);
                                for (Edge otherEdge : otherEdgeList) {
                                    Vertex otherHeadVertex = otherEdge.getHead();
                                    Vertex otherTailVertex = otherEdge.getTail();

                                    if (headVertex.isRealized() && otherHeadVertex.isRealized()) {
                                        continue;
                                    }

                                    List<Edge> prevEdgeList = new ArrayList<>();
                                    prevEdgeList.add(null);
                                    List<Edge> otherPrevEdgeList = new ArrayList<>();
                                    otherPrevEdgeList.add(null);

                                    if (headVertex.getVertexType() == VertexType.NODE_LEAVE) {
                                        prevEdgeList = graph.getTailVertexEdgeListMap().get(headVertex);
                                    }

                                    if (otherHeadVertex.getVertexType() == VertexType.NODE_LEAVE) {
                                        otherPrevEdgeList = graph.getTailVertexEdgeListMap().get(otherHeadVertex);
                                    }

                                    for (Edge prevEdge : prevEdgeList) {
                                        for (Edge otherPrevEdge : otherPrevEdgeList) {
                                            if (isMinimumHeadwayViolated(headVertex, tailVertex, otherHeadVertex, otherTailVertex, prevEdge, otherPrevEdge, link, solution, problemContext)) {
                                                grbLinExpr.clear();
                                                String tmpVarName1 = generateEdgeVarName(edge);
                                                String tmpVarName2 = generateEdgeVarName(otherEdge);
                                                GRBVar tmpVar1 = this.grbVarMap.get(tmpVarName1);
                                                GRBVar tmpVar2 = this.grbVarMap.get(tmpVarName2);

                                                grbLinExpr.addTerm(1.0, tmpVar1);
                                                grbLinExpr.addTerm(1.0, tmpVar2);

                                                lhs = 0.0;
                                                lhs += 1.0 * this.initialSolutionValueMap.get(tmpVarName1);
                                                lhs += 1.0 * this.initialSolutionValueMap.get(tmpVarName2);

                                                int tmpVarNum = 2;
                                                if (prevEdge != null) {
                                                    String tmpVarName3 = generateEdgeVarName(prevEdge);
                                                    GRBVar tmpVar3 = this.grbVarMap.get(tmpVarName3);
                                                    grbLinExpr.addTerm(1.0, tmpVar3);
                                                    lhs += 1.0 * this.initialSolutionValueMap.get(tmpVarName3);
                                                    ++tmpVarNum;
                                                }
                                                if (otherPrevEdge != null) {
                                                    String tmpVarName4 = generateEdgeVarName(otherPrevEdge);
                                                    GRBVar tmpVar4 = this.grbVarMap.get(tmpVarName4);
                                                    grbLinExpr.addTerm(1.0, tmpVar4);
                                                    lhs += 1.0 * this.initialSolutionValueMap.get(tmpVarName4);
                                                    ++tmpVarNum;
                                                }

                                                String constraintName = String.join("_", "MINIMUM_HEADWAY", tmpVarName1, tmpVarName2);
                                                grbModel.addConstr(grbLinExpr, GRB.LESS_EQUAL, tmpVarNum - 1, constraintName);
                                                ++constraintNum;
                                                if (lhs > tmpVarNum - 1) {
                                                    LOGGER.warning("Minimum Headway Constraints are violated by initial solution.\n");
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (GRBException grbException) {
            grbException.printStackTrace();
        }

        LOGGER.info(String.format("Add Minimum Headway Constraints Complete. Constraint num: %d\n", constraintNum));
        return this;
    }

    public EventBasedModel addSkippedStopConstraints(ProblemContext problemContext, Graph graph) {
        int constraintNum = 0;
        try {
            for (Map.Entry<Vertex, Map<SkippedStopType, GRBVar>> entry : vertexSkippedStopVarMap.entrySet()) {
                Vertex vertex = entry.getKey();

                GRBLinExpr grbLinExpr = new GRBLinExpr();
                double lhs = 0.0;

                for (GRBVar grbVar : entry.getValue().values()) {
                    grbLinExpr.addTerm(1.0, grbVar);
                    lhs += 1.0 * this.initialSolutionValueMap.get(this.grbVarNameMap.get(grbVar));
                }

                for (Edge edge : graph.getHeadVertexEdgeListMap().get(vertex)) {
                    String tmpVarName = generateEdgeVarName(edge);
                    GRBVar tmpGrbVar = this.grbVarMap.get(tmpVarName);

                    grbLinExpr.addTerm(-1.0, tmpGrbVar);
                    lhs += (-1.0) * this.initialSolutionValueMap.get(tmpVarName);
                }

                String constraintName = String.join("_", "SKIP_STOP", String.valueOf(vertex.getIndex()), vertex.getId());
                grbModel.addConstr(grbLinExpr, GRB.EQUAL, 0.0, constraintName);
                if (lhs != 0.0) {
                    LOGGER.warning("Skipped Constraints are violated.\n");
                }
                ++constraintNum;
            }

            Map<String, List<Vertex>> vertexGroup = vertexSkippedStopVarMap.keySet().stream().collect(Collectors.groupingBy(Vertex::getCourseId));

            for (Map.Entry<String, List<Vertex>> entry : vertexGroup.entrySet()) {
                GRBLinExpr grbLinExpr1 = new GRBLinExpr();
                GRBLinExpr grbLinExpr2 = new GRBLinExpr();
                double lhs1 = 0.0;
                double lhs2 = 0.0;

                for (Vertex vertex : entry.getValue()) {
                    GRBLinExpr grbLinExpr3 = new GRBLinExpr();
                    double lhs3 = 0.0;

                    Map<SkippedStopType, GRBVar> vertexSkippedStopVars = vertexSkippedStopVarMap.get(vertex);
                    GRBVar firstSkippedStopVar = vertexSkippedStopVars.get(SkippedStopType.FIRST);
                    GRBVar secondSkippedStopVar = vertexSkippedStopVars.get(SkippedStopType.SECOND);
                    GRBVar remainSkippedStopVar = vertexSkippedStopVars.get(SkippedStopType.REMAIN);

                    grbLinExpr1.addTerm(1.0, firstSkippedStopVar);
                    lhs1 += 1.0 * this.initialSolutionValueMap.get(this.grbVarNameMap.get(firstSkippedStopVar));

                    grbLinExpr2.addTerm(1.0, firstSkippedStopVar);
                    grbLinExpr2.addTerm(-1.0, secondSkippedStopVar);
                    lhs2 += 1.0 * this.initialSolutionValueMap.get(this.grbVarNameMap.get(firstSkippedStopVar));
                    lhs2 += (-1.0) * this.initialSolutionValueMap.get(this.grbVarNameMap.get(secondSkippedStopVar));

                    grbLinExpr3.addTerm(-1.0, remainSkippedStopVar);
                    lhs3 += (-1.0) * this.initialSolutionValueMap.get(this.grbVarNameMap.get(remainSkippedStopVar));
                    for (Vertex vertex1 : entry.getValue()) {
                        GRBVar tmpSecondSkippedStopVar = vertexSkippedStopVarMap.get(vertex1).get(SkippedStopType.SECOND);
                        grbLinExpr3.addTerm(1.0, tmpSecondSkippedStopVar);
                        lhs3 += 1.0 * this.initialSolutionValueMap.get(this.grbVarNameMap.get(tmpSecondSkippedStopVar));
                    }

                    String constraintName3 = String.join("_", "COURSE_SKIP_STOPS_REMAIN", entry.getKey(), String.valueOf(vertex.getIndex()), vertex.getId(), String.valueOf(vertex.getNodeSeq()));
                    grbModel.addConstr(grbLinExpr3, GRB.GREATER_EQUAL, 0.0, constraintName3);
                    if (lhs3 < 0.0) {
                        LOGGER.warning("Skipped Stops Constraints are violated.\n");
                    }
                    ++constraintNum;
                }

                String constraintName1 = String.join("_", "COURSE_SKIP_STOPS_FIRST_UPPER", entry.getKey());
                grbModel.addConstr(grbLinExpr1, GRB.LESS_EQUAL, 1.0, constraintName1);
                if (lhs1 > 0.0) {
                    LOGGER.warning("Skipped Stops Constraints are violated.\n");
                }
                ++constraintNum;

                String constraintName2 = String.join("_", "COURSE_SKIP_STOPS_SECOND_UPPER", entry.getKey());
                grbModel.addConstr(grbLinExpr2, GRB.GREATER_EQUAL, 0.0, constraintName2);
                if (lhs2 < 0.0) {
                    LOGGER.warning("Skipped Stops Constraints are violated.\n");
                }
                ++constraintNum;
            }
        } catch (GRBException grbException) {
            grbException.printStackTrace();
        }
        LOGGER.info(String.format("Add Skipped Stop Constraints Complete. Constraint num: %d\n", constraintNum));

        return this;
    }

    public EventBasedModel addNodeGraphDegreeFlowBalanceConstraints(ProblemContext problemContext, Graph graph, List<NodeGraph> nodeGraphList) {
        try {
            int constraintNum = 0;
            for (NodeGraph nodeGraph : nodeGraphList) {
                Vertex dummyStartVertex = nodeGraph.getDummyNodeStartVertex();
                GRBLinExpr grbLinExpr = new GRBLinExpr();

                double lhs = 0.0;
                for (Edge edge : nodeGraph.getHeadEdgeListMap().get(dummyStartVertex)) {
                    String tmpVarName = generateNodeGraphEdgeVarName(nodeGraph, edge);
                    grbLinExpr.addTerm(1.0, this.grbVarMap.get(tmpVarName));
                    lhs += 1.0 * this.initialSolutionValueMap.get(tmpVarName);
                }

                this.grbModel.addConstr(grbLinExpr, GRB.LESS_EQUAL, 1.0, null);
                if (lhs > 1.0) {
                    LOGGER.warning("Node Graph Degree/Flow Balance Constraints are Violated.\n");
                }
                ++constraintNum;

                for (Vertex vertex : nodeGraph.getVertexList()) {
                    GRBLinExpr grbLinExpr1 = new GRBLinExpr();
                    GRBLinExpr grbLinExpr2 = new GRBLinExpr();
                    double lhs1 = 0.0;
                    double lhs2 = 0.0;

                    for (Vertex graphVertex : nodeGraph.getVertexVertexMap().get(vertex)) {
                        for (Edge arrivalEdge : graph.getTailVertexEdgeListMap().get(graphVertex)) {
                            String tmpVarName = generateEdgeVarName(arrivalEdge);
                            GRBVar tmpVar = this.grbVarMap.get(tmpVarName);
                            grbLinExpr1.addTerm(1.0, tmpVar);
                            grbLinExpr2.addTerm(1.0, tmpVar);

                            lhs1 += 1.0 * this.initialSolutionValueMap.get(tmpVarName);
                            lhs2 += 1.0 * this.initialSolutionValueMap.get(tmpVarName);
                        }
                    }

                    for (Edge edge : nodeGraph.getTailEdgeListMap().get(vertex)) {
                        String tmpVarName = generateNodeGraphEdgeVarName(nodeGraph, edge);
                        GRBVar tmpVar = this.grbVarMap.get(tmpVarName);

                        grbLinExpr1.addTerm(-1.0, tmpVar);
                        lhs1 += (-1.0) * this.initialSolutionValueMap.get(tmpVarName);
                    }

                    for (Edge edge : nodeGraph.getHeadEdgeListMap().get(vertex)) {
                        String tmpVarName = generateNodeGraphEdgeVarName(nodeGraph, edge);
                        GRBVar tmpVar = this.grbVarMap.get(tmpVarName);

                        grbLinExpr2.addTerm(-1.0, tmpVar);
                        lhs2 += (-1.0) * this.initialSolutionValueMap.get(tmpVarName);
                    }

                    this.grbModel.addConstr(grbLinExpr1, GRB.EQUAL, 0.0, null);
                    this.grbModel.addConstr(grbLinExpr2, GRB.EQUAL, 0.0, null);
                    constraintNum += 2;

                    if (lhs1 != 0.0 || lhs2 != 0.0) {
                        LOGGER.warning("Node Graph Degree/Flow Balance Constraints are Violated.\n");
                    }
                }
            }

            LOGGER.info(String.format("Add Node Graph Degree/Flow Balance Constraints Complete. Constraint num: %d", constraintNum));
        } catch (GRBException e) {
            e.printStackTrace();
        }

        return this;
    }

    public void recoverySolutionData(ProblemContext problemContext, Graph graph, List<NodeGraph> nodeGraphList, Solution solution, Set<String> updatedCourses) {
        try {
            Map<String, Double> solutionValueMap = new HashMap<>();
            for (Map.Entry<String, GRBVar> entry : this.grbVarMap.entrySet()) {
                solutionValueMap.put(entry.getKey(), entry.getValue().get(GRB.DoubleAttr.X));
            }

            for (VertexType vertexType : Arrays.asList(VertexType.NODE_PASS, VertexType.NODE_LEAVE, VertexType.NODE_STOP_LEAVE, VertexType.NODE_STOP, VertexType.COURSE_END, VertexType.COURSE_START)) {
                for (Vertex vertex : graph.getVertexTypeListMap().get(vertexType)) {
                    String courseId = vertex.getCourseId();
                    if (updatedCourses.contains(courseId)) {
                        continue;
                    }

                    for (Edge edge : graph.getHeadVertexEdgeListMap().get(vertex)) {
                        String tmpVarName = generateEdgeVarName(edge);
                        if (this.initialSolutionValueMap.get(tmpVarName) != (int) solutionValueMap.get(tmpVarName).doubleValue()) {
                            updatedCourses.add(courseId);
                            break;
                        }
                    }

                    if (updatedCourses.contains(courseId)) {
                        continue;
                    }

                    for (Edge edge : graph.getTailVertexEdgeListMap().get(vertex)) {
                        String tmpVarName = generateEdgeVarName(edge);
                        if (this.initialSolutionValueMap.get(tmpVarName) != (int) solutionValueMap.get(tmpVarName).doubleValue()) {
                            updatedCourses.add(courseId);
                            break;
                        }
                    }
                }
            }

            System.out.println(updatedCourses.size());

            for (RollingStock rollingStock : solution.getRollingStock2ScheduleListMap().keySet()) {
                List<Schedule> scheduleList = solution.getRollingStock2ScheduleListMap().get(rollingStock);

                Vertex trainVertex = graph.getTrainVertexMap().get(rollingStock.getIndex());
                List<Edge> trainDutyStartEdgeList = graph.getHeadVertexEdgeListMap().get(trainVertex);
                Edge selectedEdge = trainDutyStartEdgeList.stream().filter(edge -> solutionValueMap.get(generateEdgeVarName(edge)) > 0.0).findFirst().orElse(null);
                if (selectedEdge == null) {
                    LOGGER.warning("Failed to find a selected edge.");
                    throw new NullPointerException("Failed to find an edge between tran and duty start.");
                }

                Vertex dutyStartVertex = selectedEdge.getTail();
                while (dutyStartVertex != trainVertex) {
                    List<Edge> dutyStartCourseStartEdgeList = graph.getHeadVertexEdgeListMap().get(dutyStartVertex);
                    selectedEdge = dutyStartCourseStartEdgeList.stream().filter(edge -> solutionValueMap.get(generateEdgeVarName(edge)) > 0.0).findFirst().orElse(null);

                    if (selectedEdge == null) {
                        LOGGER.warning("Failed to find a selected edge.");
                        throw new NullPointerException("Failed to find an edge between duty start and course start.");
                    }

                    Vertex courseStartVertex = selectedEdge.getTail();
                    while (courseStartVertex.getVertexType() == VertexType.COURSE_START) {
                        List<Edge> courseStartNodeEdgeList = graph.getHeadVertexEdgeListMap().get(courseStartVertex);
                        selectedEdge = courseStartNodeEdgeList.stream().filter(edge -> solutionValueMap.get(generateEdgeVarName(edge)) > 0.0).findFirst().orElse(null);

                        if (selectedEdge == null) {
                            LOGGER.warning("Failed to find a selected edge");
                            throw new NullPointerException("Failed to find an edge between duty start and course start.");
                        }

                        Schedule currentSchedule = problemContext.getCourseId2Schedule().get(courseStartVertex.getCourseId());

                        List<Integer> arrivalTimeList = solution.getScheduleStationArrivalTimeMap().get(currentSchedule);
                        List<Integer> departureTimeList = solution.getScheduleStationDepartureTimeMap().get(currentSchedule);
                        List<Boolean> skipStationList = solution.getScheduleSkipStationMap().get(currentSchedule);
                        int nodeIndex = 0;

                        Vertex currentNodeVertex = selectedEdge.getTail();
                        if (courseStartVertex.isPartialCancellationCourseStartEnd()) {
                            nodeIndex = currentNodeVertex.getNodeSeq() - 1;
                            solution.getSchedulePartialCancellationStartIndexMap().put(currentSchedule, nodeIndex);
                        }

                        while (currentNodeVertex.getVertexType() != VertexType.COURSE_END) {
                            VertexType currentVertexType = currentNodeVertex.getVertexType();
                            int time = currentNodeVertex.getTime();
                            if (VertexType.NODE_STOP_LEAVE == currentVertexType) {
                                arrivalTimeList.set(nodeIndex, time);
                                departureTimeList.set(nodeIndex, time);

                                ++nodeIndex;
                            } else if (VertexType.NODE_PASS == currentVertexType) {
                                arrivalTimeList.set(nodeIndex, time);
                                departureTimeList.set(nodeIndex, time);
                                skipStationList.set(nodeIndex, true);
                                ++nodeIndex;
                            } else if (VertexType.NODE_STOP == currentVertexType) {
                                arrivalTimeList.set(nodeIndex, time);
                            } else if (VertexType.NODE_LEAVE == currentVertexType) {
                                departureTimeList.set(nodeIndex, time);
                                ++nodeIndex;
                            }

                            List<Edge> nodeEdgeList = graph.getHeadVertexEdgeListMap().get(currentNodeVertex);
                            selectedEdge = nodeEdgeList.stream().filter(edge -> solutionValueMap.get(generateEdgeVarName(edge)) > 0.0).findFirst().orElse(null);
                            if (selectedEdge == null) {
                                LOGGER.warning("Failed to find a selected edge");
                                throw new NullPointerException("Failed to find an edge between node and node.");
                            }

                            currentNodeVertex = selectedEdge.getTail();
                        }
                        if (currentNodeVertex.isPartialCancellationCourseStartEnd()) {
                            solution.getSchedulePartialCancellationEndIndexMap().put(currentSchedule, selectedEdge.getHead().getNodeSeq() - 1);
                        }

                        List<Edge> courseEndCourseStartEdgeList = graph.getHeadVertexEdgeListMap().get(currentNodeVertex);
                        selectedEdge = courseEndCourseStartEdgeList.stream().filter(edge -> solutionValueMap.get(generateEdgeVarName(edge)) > 0.0).findFirst().orElse(null);
                        if (selectedEdge == null) {
                            LOGGER.warning("Failed to find a selected edge");
                            throw new NullPointerException("Failed to find an edge between course end and course start.");
                        }

                        courseStartVertex = selectedEdge.getTail();
                    }

                    List<Edge> dutyEndDutyStartEdgeList = graph.getHeadVertexEdgeListMap().get(courseStartVertex);
                    selectedEdge = dutyEndDutyStartEdgeList.stream().filter(edge -> solutionValueMap.get(generateEdgeVarName(edge)) > 0.0).findFirst().orElse(null);

                    if (selectedEdge == null) {
                        LOGGER.warning("Failed to find a selected edge.");
                        throw new NullPointerException("Failed to find an edge between duty end and duty start");
                    }

                    dutyStartVertex = selectedEdge.getTail();
                }
            }
        } catch (GRBException grbException) {
            grbException.printStackTrace();
        }
    }

    private boolean isMinimumHeadwayViolated(Vertex firstHeadVertex, Vertex firstTailVertex, Vertex secondHeadVertex, Vertex secondTailVertex, Edge firstPrevEdge, Edge secondPrevEdge, Link link, Solution solution, ProblemContext problemContext) {
        VertexType firstHeadVertexType = firstHeadVertex.getVertexType();
        VertexType firstTailVertexType = firstTailVertex.getVertexType();

        VertexType secondHeadVertexType = secondHeadVertex.getVertexType();
        VertexType secondTailVertexType = secondTailVertex.getVertexType();

        boolean firstHeadVertexRealized = firstHeadVertex.isRealized();
        boolean firstTailVertexRealized = firstTailVertex.isRealized();
        boolean secondHeadVertexRealized = secondHeadVertex.isRealized();
        boolean secondTailVertexRealized = secondTailVertex.isRealized();

        if (firstHeadVertexRealized && secondHeadVertexRealized) {
            return false;
        }

        boolean firstArrivalRealized = false;
        if (VertexType.NODE_STOP_LEAVE == firstHeadVertexType) {
            firstArrivalRealized = firstHeadVertex.isStopLeaveArrivalRealized();
        } else if (VertexType.NODE_PASS == firstHeadVertexType) {
            firstArrivalRealized = firstHeadVertexRealized;
        } else if (VertexType.NODE_LEAVE == firstHeadVertexType) {
            firstArrivalRealized = firstPrevEdge.getHead().isRealized();
        } else {
            throw new IllegalStateException("Illegal State in isMinimumHeadwayViolated");
        }

        boolean secondArrivalRealized = false;
        if (VertexType.NODE_STOP_LEAVE == secondHeadVertexType) {
            secondArrivalRealized = secondHeadVertex.isStopLeaveArrivalRealized();
        } else if (VertexType.NODE_PASS == secondHeadVertexType) {
            secondArrivalRealized = secondHeadVertexRealized;
        } else if (VertexType.NODE_LEAVE == secondHeadVertexType) {
            secondArrivalRealized = secondPrevEdge.getHead().isRealized();
        } else {
            throw new IllegalStateException("Illegal State in isMinimumHeadwayViolated");
        }

        int firstHeadDepartureTime = firstHeadVertex.getTime();
        int firstHeadArrivalTime = -1;
        if (firstHeadVertexType == VertexType.NODE_STOP_LEAVE) {
            if (problemContext.getCode2Node().get(firstHeadVertex.getId()).isDepot()) {
                firstHeadArrivalTime = firstHeadDepartureTime;
            } else {
                firstHeadArrivalTime = firstHeadVertex.getStopLeaveArrivalTime();
            }
            Schedule tmpSchedule = problemContext.getCourseId2Schedule().get(firstHeadVertex.getCourseId());
            if (!tmpSchedule.getRealizedEnterTimes().isEmpty() && tmpSchedule.getRealizedEnterTimes().get(1) != 0) {
                firstArrivalRealized = true;
            }
        } else if (firstHeadVertexType == VertexType.NODE_LEAVE) {
            if (firstPrevEdge == null) {
                throw new IllegalStateException("Illegal State in isMinimumHeadwayViolated");
            }
            firstHeadArrivalTime = firstPrevEdge.getHead().getTime();
        } else if (firstHeadVertexType == VertexType.NODE_PASS) {
            firstHeadArrivalTime = firstHeadDepartureTime;
        } else {
            throw new IllegalStateException("Illegal State in isMinimumHeadwayViolated");
        }

        int secondHeadDepartureTime = secondHeadVertex.getTime();
        int secondHeadArrivalTime = -1;
        if (secondHeadVertexType == VertexType.NODE_STOP_LEAVE) {
            if (problemContext.getCode2Node().get(secondHeadVertex.getId()).isDepot()) {
                secondHeadArrivalTime = secondHeadDepartureTime;
            } else {
                secondHeadArrivalTime = secondHeadVertex.getStopLeaveArrivalTime();
            }
            Schedule tmpSchedule = problemContext.getCourseId2Schedule().get(secondHeadVertex.getCourseId());
            if (!tmpSchedule.getRealizedEnterTimes().isEmpty() && tmpSchedule.getRealizedEnterTimes().get(1) != 0) {
                secondArrivalRealized = true;
            }
        } else if (secondHeadVertexType == VertexType.NODE_LEAVE) {
            if (secondPrevEdge == null) {
                throw new IllegalStateException("Illegal State in isMinimumHeadwayViolated");
            }
            secondHeadArrivalTime = secondPrevEdge.getHead().getTime();
        } else if (secondHeadVertexType == VertexType.NODE_PASS) {
            secondHeadArrivalTime = secondHeadDepartureTime;
        } else {
            throw new IllegalStateException("Illegal State in isMinimumHeadwayViolated");
        }

        ActivityType firstHeadVertexStatus = getActivityTypeBasedOnVertexType(firstHeadVertexType);
        ActivityType firstTailVertexStatus = getActivityTypeBasedOnVertexType(firstTailVertexType);
        ActivityType secondHeadVertexStatus = getActivityTypeBasedOnVertexType(secondHeadVertexType);
        ActivityType secondTailVertexStatus = getActivityTypeBasedOnVertexType(secondTailVertexType);

        int actualHeadway = -1;
        int minimumHeadway = -1;
        if (firstHeadArrivalTime <= secondHeadArrivalTime) {
            if (firstHeadVertexRealized && secondArrivalRealized) {
                return false;
            }
            if (firstArrivalRealized && secondHeadVertexRealized && !firstHeadVertexRealized) {
                return false;
            }
            if (firstArrivalRealized && secondArrivalRealized && !firstHeadVertexRealized) {
                return false;
            }
            actualHeadway = secondHeadArrivalTime - firstHeadDepartureTime;
            minimumHeadway = link.getMinimumHeadway()[firstHeadVertexStatus == ActivityType.PASS ? 0 : 1][firstTailVertexStatus == ActivityType.PASS ? 0 : 1][secondHeadVertexStatus == ActivityType.PASS ? 0 : 1][secondTailVertexStatus == ActivityType.PASS ? 0 : 1];
            if (firstArrivalRealized && secondArrivalRealized) {
                if (secondHeadArrivalTime - firstHeadArrivalTime < minimumHeadway) {
                    return false;
                }
            }
        } else {
            if (secondHeadVertexRealized && firstArrivalRealized) {
                return false;
            }
            if (secondArrivalRealized && firstHeadVertexRealized && !secondHeadVertexRealized) {
                return false;
            }
            if (secondArrivalRealized && firstArrivalRealized && !secondHeadVertexRealized) {
                return false;
            }
            actualHeadway = firstHeadArrivalTime - secondHeadDepartureTime;
            minimumHeadway = link.getMinimumHeadway()[secondHeadVertexStatus == ActivityType.PASS ? 0 : 1][secondTailVertexStatus == ActivityType.PASS ? 0 : 1][firstHeadVertexStatus == ActivityType.PASS ? 0 : 1][firstTailVertexStatus == ActivityType.PASS ? 0 : 1];
            if (firstArrivalRealized && secondArrivalRealized) {
                if (firstHeadArrivalTime - secondHeadArrivalTime < minimumHeadway) {
                    return false;
                }
            }
        }

        return actualHeadway < minimumHeadway;
    }

    private ActivityType getActivityTypeBasedOnVertexType(VertexType vertexType) {
        ActivityType activityType = ActivityType.PASS;
        if (VertexType.NODE_STOP_LEAVE == vertexType || VertexType.NODE_LEAVE == vertexType || VertexType.NODE_STOP == vertexType) {
            return ActivityType.STOP;
        }

        return activityType;
    }

    public String generateEdgeVarName(Edge edge) {
        return String.join("_", "x", String.valueOf(edge.getHead().getIndex()), String.valueOf(edge.getTail().getIndex()));
    }

    public String generateSkippedStopVarName(Vertex vertex, SkippedStopType skippedStopType) {
        return String.join("_", "y", String.valueOf(vertex.getIndex()), vertex.getId(), vertex.getCourseId(), String.valueOf(vertex.getNodeSeq()), skippedStopType.getId());
    }

    public String generateNodeGraphEdgeVarName(NodeGraph nodeGraph, Edge edge) {
        String nodeGraphId = nodeGraph.generateNodeGraphId();
        return String.join("_", "z", nodeGraphId, String.valueOf(edge.getHead().getIndex()), String.valueOf(edge.getTail().getIndex()));
    }

    public Pair<Integer, Integer> generateEdgeTimeBand(ProblemContext problemContext, Graph graph, Edge edge) {
        if (EdgeType.CROSS_STATION_NODE_TO_NODE != edge.getEdgeType()) {
            throw new IllegalStateException("The edge type is illegal");
        }

        Vertex headVertex = edge.getHead();
        VertexType headVertexType = headVertex.getVertexType();

        int headDepartureTime = headVertex.getTime();

        int headArrivalTime = Integer.MAX_VALUE;
        if (headVertexType == VertexType.NODE_STOP_LEAVE) {
            if (problemContext.getCode2Node().get(headVertex.getId()).isDepot()) {
                headArrivalTime = headDepartureTime;
            } else {
                headArrivalTime = headVertex.getStopLeaveArrivalTime();
            }
        } else if (headVertexType == VertexType.NODE_LEAVE) {
            for (Edge prevEdge : graph.getTailVertexEdgeListMap().get(headVertex)) {
                headArrivalTime = Math.min(prevEdge.getHead().getTime(), headArrivalTime);
            }
        } else if (headVertexType == VertexType.NODE_PASS) {
            headArrivalTime = headDepartureTime;
        } else {
            throw new IllegalStateException("Illegal State in isMinimumHeadwayViolated");
        }

        int startTime = Math.min(headArrivalTime, headDepartureTime);
        int endTime = Math.max(headArrivalTime, headDepartureTime);

        return Pair.of(startTime / 600, endTime / 600);
    }
}
