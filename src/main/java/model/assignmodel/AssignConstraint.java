package model.assignmodel;

import constant.Constants;
import context.*;
import graph.rsdgraph.RollingStockDutyEdge;
import graph.rsdgraph.RollingStockDutyVertex;
import gurobi.*;
import solution.HeadwayElement;
import solution.Solution;
import solution.TrackElement;

import java.util.*;

/**
 * @description:
 * @author: Shengcheng Shao
 * @date: 2022/8/8
 */
public class AssignConstraint {
    AssignVariable var;
    public AssignConstraint(AssignVariable var) {
        this.var = var;
    }

    public void createCons(ProblemContext context, GRBModel solver, Solution solution) throws GRBException {
        for (int i = 0; i < context.getSchedules().size(); i++) {
            Schedule schedule = context.getSchedules().get(i);
            List<GRBVar> changeVars = var.getXVars().get(schedule);
            GRBLinExpr expr = new GRBLinExpr();
            for (GRBVar var : changeVars) {
                expr.addTerm(1.0, var);
            }
            expr.addTerm(1.0, var.getYVars().get(schedule));
            solver.addConstr(expr, GRB.EQUAL, 1, schedule.getCourseId() + " usage constraint");
        }

        genTrackConstraint(context, solver, solution);
        genHeadwayConstraint(context, solver, solution);
    }

    private void genHeadwayConstraint(ProblemContext context, GRBModel solver, Solution solution) throws GRBException {
        Map<Link, List<HeadwayElement>> link2HeadwayElements = new HashMap<>();
        genLink2HeadwayElements(context, solution, link2HeadwayElements);
        for (Map.Entry<Link, List<HeadwayElement>> entry : link2HeadwayElements.entrySet()) {
            genLinkWiseConstraint(context, solver, entry);
        }
    }

    private void genLinkWiseConstraint(ProblemContext context, GRBModel solver, Map.Entry<Link, List<HeadwayElement>> entry) throws GRBException {
        Link link = entry.getKey();
        List<HeadwayElement> headwayElements = entry.getValue();
        Collections.sort(headwayElements);
        for (int i = 0; i < headwayElements.size() - 1; i++) {
            HeadwayElement frontTrain = headwayElements.get(i);
            for (int k = i + 1; k < headwayElements.size(); k ++) {
                HeadwayElement behindTrain = headwayElements.get(k);
                if (frontTrain.getSchedule() == behindTrain.getSchedule()) {
                    continue;
                }
                if (frontTrain.isHeadNodeDepartureRealized() && behindTrain.isHeadNodeArrivalRealized()) {
                    continue;
                }
                int minimumHeadWay = link.getMinimumHeadway()[frontTrain.getHeadStatus()][frontTrain.getTailStatus()]
                        [behindTrain.getHeadStatus()][behindTrain.getTailStatus()];
                int actHeadway = behindTrain.getHeadArrival() - frontTrain.getHeadDeparture();
                if (actHeadway < minimumHeadWay) {
                    // 如果有违反度，则生成约束
                    GRBLinExpr expr6 = new GRBLinExpr();
                    int index1 = 0;
                    for (; index1 < Constants.COURSE_START_TIME_CHANGE.length; index1++) {
                        if (Constants.COURSE_START_TIME_CHANGE[index1] == frontTrain.getChange()) {
                            break;
                        }
                    }
                    int index2 = 0;
                    for (; index2 < Constants.COURSE_START_TIME_CHANGE.length; index2++) {
                        if (Constants.COURSE_START_TIME_CHANGE[index2] == behindTrain.getChange()) {
                            break;
                        }
                    }
                    expr6.addTerm(1, var.getXVars().get(frontTrain.getSchedule()).get(index1));
                    expr6.addTerm(1, var.getXVars().get(behindTrain.getSchedule()).get(index2));
                    String consName = frontTrain.getSchedule() + "_" + Constants.COURSE_START_TIME_CHANGE[index1] +
                            " and " + behindTrain.getSchedule() + "_" + Constants.COURSE_START_TIME_CHANGE[index2] +
                            " headway conflict cons";
                    solver.addConstr(expr6, GRB.LESS_EQUAL, 1, consName);
                } else if (behindTrain.getHeadArrival() - frontTrain.getHeadDeparture() > context.getMaxHeadway() * 2) {
                    break;
                }
            }
        }
    }

    private void genLink2HeadwayElements(ProblemContext context, Solution solution, Map<Link, List<HeadwayElement>> link2HeadwayElements) {
        for (Schedule schedule : context.getSchedules()) {
            if (!solution.getSchedule2RollingStockMap().containsKey(schedule)) {
                continue;
            }

            List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.
                    getRealizedNodes();
            List<GRBVar> xVars = var.getXVars().get(schedule);
            for (int xIndex = 0; xIndex < xVars.size(); xIndex++) {
                int change = Constants.COURSE_START_TIME_CHANGE[xIndex];
                for (int j = 0; j < nodeList.size() - 1; j++) {
                    Node headNode = nodeList.get(j);
                    Node tailNode = nodeList.get(j + 1);
                    String linkName = headNode.getCode() + "_" + tailNode.getCode();
                    Link link = context.getName2Link().get(linkName);

                    // TODO: Head/Tail Node Enter/Leave Realized
                    boolean headNodeArrivalRealized = !schedule.getRealizedEnterTimes().isEmpty() && schedule.getRealizedEnterTimes().get(j + 1) != 0;
                    boolean headNodeDepartureRealized = !schedule.getRealizedLeaveTimes().isEmpty() && schedule.getRealizedLeaveTimes().get(j + 1) != 0;
                    boolean tailNodeArrivalRealized = !schedule.getRealizedEnterTimes().isEmpty() && schedule.getRealizedEnterTimes().get(j + 2) != 0;
                    boolean tailNodeDepartureRealized = !schedule.getRealizedLeaveTimes().isEmpty() && schedule.getRealizedLeaveTimes().get(j + 2) != 0;

                    HeadwayElement headwayElement = new HeadwayElement(link, schedule, j, solution,
                            headNodeArrivalRealized, headNodeDepartureRealized, tailNodeArrivalRealized,
                            tailNodeDepartureRealized);
                    headwayElement.setSchedule(schedule);
                    headwayElement.setHeadArrival(headwayElement.getHeadArrival() + change);
                    headwayElement.setHeadDeparture(headwayElement.getHeadDeparture() + change);
                    headwayElement.setChange(change);
                    List<HeadwayElement> headwayElements = link2HeadwayElements.getOrDefault(link, new ArrayList<>());
                    headwayElements.add(headwayElement);
                    link2HeadwayElements.put(link, headwayElements);
                }
            }
        }
    }

    private void genTrackConstraint(ProblemContext context, GRBModel solver, Solution solution) throws GRBException {
        // 生成track constraint
        Map<Track, List<TrackElement>> trackListMap = new HashMap<>();
        genTrackListMap(context, solution, trackListMap);

        for (Map.Entry<Track, List<TrackElement>> entry : trackListMap.entrySet()) {
            genTrackWiseConstraint(solver, solution, entry);
        }
    }

    private void genTrackWiseConstraint(GRBModel solver, Solution solution, Map.Entry<Track, List<TrackElement>> entry) throws GRBException {
        List<TrackElement> trackElements = entry.getValue();
        Collections.sort(trackElements);
        for (int j = 0; j < trackElements.size() - 1; j++) {
            TrackElement te1 = trackElements.get(j);
            for (int k = j + 1; k < trackElements.size(); k ++) {
                TrackElement te2 = trackElements.get(k);
                if (te1.getSchedule() == te2.getSchedule()) {
                    continue;
                }
                if (te1.isNodeRealized() && te2.isNodeRealized()) {
                    continue;
                }
                if (solution.getSchedule2RollingStockMap().get(te1.getSchedule())
                        == solution.getSchedule2RollingStockMap().get(te2.getSchedule())) {
                    continue;
                }
                int minimumSeparationTime = Constants.MINIMUM_SEPARATION_TIME;
                if (te1.getDeparture() > te2.getArrival() - minimumSeparationTime) {
                    // 如果有违反度，则生成约束
                    GRBLinExpr expr5 = new GRBLinExpr();
                    int index1 = 0;
                    for (; index1 < Constants.COURSE_START_TIME_CHANGE.length; index1++) {
                        if (Constants.COURSE_START_TIME_CHANGE[index1] == te1.getTimeChange()) {
                            break;
                        }
                    }
                    int index2 = 0;
                    for (; index2 < Constants.COURSE_START_TIME_CHANGE.length; index2++) {
                        if (Constants.COURSE_START_TIME_CHANGE[index2] == te2.getTimeChange()) {
                            break;
                        }
                    }
                    expr5.addTerm(1, var.getXVars().get(te1.getSchedule()).get(index1));
                    expr5.addTerm(1, var.getXVars().get(te2.getSchedule()).get(index2));
                    String consName = te1.getSchedule() + "_" + Constants.COURSE_START_TIME_CHANGE[index1] +
                            " and " + te2.getSchedule() + "_" + Constants.COURSE_START_TIME_CHANGE[index2] +
                            " track capacity conflict cons";
                    solver.addConstr(expr5, GRB.LESS_EQUAL, 1, consName);
                } else if (te2.getArrival() - te1.getDeparture() > Constants.MINIMUM_SEPARATION_TIME * 2) {
                    break;
                }
            }
        }
    }

    private void genTrackListMap(ProblemContext context, Solution solution, Map<Track, List<TrackElement>> trackListMap) {
        for (int i = 0; i < context.getSchedules().size(); i++) {
            Schedule schedule = context.getSchedules().get(i);
            List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.
                    getRealizedNodes();
            List<GRBVar> xVars = var.getXVars().get(schedule);
            for (int xIndex = 0; xIndex < xVars.size(); xIndex++) {
                int change = Constants.COURSE_START_TIME_CHANGE[xIndex];
                for (int j = 0; j < nodeList.size(); j++) {
                    Node node = nodeList.get(j);
                    String trackStr;
                    boolean nodeRealized = !schedule.getRealizedEnterTimes().isEmpty()
                            && schedule.getRealizedEnterTimes().get(j + 1) != 0;
                    if (solution.getScheduleStationTrackMap().containsKey(schedule)) {
                        trackStr = solution.getScheduleStationTrackMap().get(schedule).get(j);
                    } else {
                        trackStr = schedule.getTracks().get(j + 1);
                    }
                    Track track;
                    if (node.getName2Track().containsKey(trackStr)) {
                        track = node.getName2Track().get(trackStr);
                    } else {
                        throw new NullPointerException();
                    }
                    int arrivalTime = solution.getScheduleStationArrivalTimeMap().get(schedule).get(j) == null ?
                            solution.getScheduleStationDepartureTimeMap().get(schedule).get(j) :
                            solution.getScheduleStationArrivalTimeMap().get(schedule).get(j);
                    arrivalTime += change;
                    int departureTime = solution.getScheduleStationDepartureTimeMap().get(schedule).get(j) == null ?
                            solution.getScheduleStationArrivalTimeMap().get(schedule).get(j) :
                            solution.getScheduleStationDepartureTimeMap().get(schedule).get(j);
                    departureTime += change;
                    // 对于最后一个Node
                    // 如果是Depot，30秒之后，进入停留区，不再占Track
                    // 如果不是Depot，直到下一个Course开始之后才离开，释放Track
                    if (j == nodeList.size() - 1) {
                        if (node.isDepot()) {
                            departureTime = arrivalTime;
                        } else {
                            // 寻找下一个Schedule
                            RollingStock rollingStock = solution.getSchedule2RollingStockMap().get(schedule);
                            List<Schedule> schedules = solution.getRollingStock2ScheduleListMap().get(rollingStock);

                            List<Schedule> sortedSchedules = new ArrayList<>(schedules);
                            sortedSchedules.sort(Comparator.comparing(s -> solution.
                                    getScheduleStationDepartureTimeMap().get(s).get(0)));

                            int index = 0;
                            for (; index < sortedSchedules.size(); ++index) {
                                if (schedule == sortedSchedules.get(index)) {
                                    break;
                                }
                            }

                            if (index == sortedSchedules.size() - 1) {
                                departureTime = Integer.MAX_VALUE;
                            } else {
                                Schedule nextSchedule = sortedSchedules.get(index + 1);
                                departureTime = Math.max(departureTime, solution.getScheduleStationDepartureTimeMap().get(nextSchedule).get(0) +
                                        change);
                            }
                        }
                    }
                    TrackElement te = new TrackElement(track, node, schedule, j, arrivalTime, departureTime, nodeRealized);
                    te.setTimeChange(change);
                    List<TrackElement> trackElements = trackListMap.getOrDefault(track, new ArrayList<>());
                    trackElements.add(te);
                    trackListMap.put(track, trackElements);
                }
            }
        }
    }
}
