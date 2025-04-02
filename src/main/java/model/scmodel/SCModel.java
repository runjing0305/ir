package model.scmodel;

import constant.Constants;
import context.*;
import graph.Vertex;
import graph.rsdgraph.RollingStockDutyEdge;
import graph.rsdgraph.RollingStockDutyVertex;
import graph.scgraph.SingleCommodityGraph;
import gurobi.GRB;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBModel;
import model.Build;
import model.Model;
import solution.Solution;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @description:
 * @author: Shengcheng Shao
 * @date: 2022/7/28
 */
public class SCModel extends Model implements Build {
    private ProblemContext context;
    private SingleCommodityGraph graph;
    private SCVariable var;
    private SCConstraint cons;
    private int resultStatus;
    private long elapsedTime;
    private Solution oldSol;

    public SCModel(ProblemContext context, SingleCommodityGraph graph, Solution oldSol) throws GRBException {
        super();
        this.context = context;
        this.graph = graph;
        this.env = new GRBEnv("RAS.log");
        this.solver = new GRBModel(env);
        this.oldSol = oldSol;
    }

    @Override
    public void createVars() throws GRBException {
        var = new SCVariable();
        var.createVars(context, graph, solver);
    }

    @Override
    public void createCons() throws GRBException {
        cons = new SCConstraint(var);
        cons.createCons(context, graph, solver);
    }

    @Override
    public Solution genSol() throws GRBException {
        Solution solution = new Solution(oldSol);
        solution.setSchedule2RollingStockMap(new HashMap<>());
        solution.setRollingStock2ScheduleListMap(new HashMap<>());
        solution.setResultStatus(resultStatus);
        solution.setElapsedTime(elapsedTime);

        Map<Schedule, Schedule> nextMap = new HashMap<>();
        Set<Schedule> startSchedules = new HashSet<>();
        for (RollingStockDutyEdge edge : graph.getEdges()) {
            if (var.getXVars()[edge.getIndex()].get(GRB.DoubleAttr.X) > 1e-6) {
                RollingStockDutyVertex headVertex = edge.getHead();
                Schedule headSchedule = headVertex.getOrigCourse();
                RollingStockDutyVertex tailVertex = edge.getTail();
                Schedule tailSchedule = tailVertex.getOrigCourse();
                if (headSchedule != null && tailSchedule != null) {
                    nextMap.put(headSchedule, tailSchedule);
                } else if (tailSchedule != null) {
                    startSchedules.add(tailSchedule);
                } else if (headSchedule == null) {
                    if (Constants.OUTPUT_FLAG) {
                        System.out.println("Virtual edge train num: " +
                                var.getXVars()[edge.getIndex()].get(GRB.DoubleAttr.X));
                    }
                }
            }
        }

        Map<Schedule, List<Schedule>> pathMap = new HashMap<>();
        for (Schedule schedule : startSchedules) {
            List<Schedule> path = new ArrayList<>();
            path.add(schedule);
            pathMap.put(schedule, path);
            Schedule cur = schedule;
            while (nextMap.containsKey(cur)) {
                cur = nextMap.get(cur);
                path.add(cur);
            }
            if (!cur.getEndNode().isDepot()) {
//                fillEEtoDepot(solution, path, cur);
            }
            pathMap.put(schedule, path);
        }
        if (Constants.OUTPUT_FLAG) {
            for (RollingStockDutyVertex vertex : graph.getVertexList()) {
                if (!vertex.isVirtual() && var.getYVars()[vertex.getIndex()].get(GRB.DoubleAttr.X) > 1e-6) {
                    System.out.println(vertex.getOrigCourse().getCourseId() + " with category: " +
                            vertex.getOrigCourse().getCategory() + " is not finished");
                }
            }
        }
        Set<Schedule> unrealizedSchedules = new HashSet<>();
        int rsIndex = 0;
        for (Map.Entry<Schedule, List<Schedule>> pathEntry : pathMap.entrySet()) {
            solution.getRollingStock2ScheduleListMap().put(context.getRollingStocks().get(rsIndex),
                    pathEntry.getValue());
            if (Constants.OUTPUT_FLAG) {
                System.out.print(rsIndex + ",");
                for (int i = 0; i < pathEntry.getValue().size(); i++) {
                    System.out.print(pathEntry.getValue().get(i).getCourseId());
                    if (i < pathEntry.getValue().size() - 1) {
                        System.out.print("->");
                    } else {
                        System.out.println();
                    }
                }
            }
            for (Schedule subSchedule : pathEntry.getValue()) {
                solution.getSchedule2RollingStockMap().put(subSchedule, context.getRollingStocks().get(rsIndex));
                if (subSchedule.getRealizedNodeStatus().isEmpty()
                        || subSchedule.getRealizedNodeStatus().containsValue("UNREALIZED")) {
                    unrealizedSchedules.add(subSchedule);
                }
            }
            rsIndex++;
        }
        return solution;
    }

    private void fillEEtoDepot(Solution solution, List<Schedule> path, Schedule cur) {
        Node closestDepotNode = null;
        double minRunTime = Integer.MAX_VALUE;
        for (Node node : context.getNodes().stream().filter(Node::isDepot).collect(Collectors.toList())) {
            int runtime = (int) context.getTimeMatrix()[cur.getEndNode().getIndex()][node.getIndex()];
            if (runtime > 0 && runtime < minRunTime) {
                closestDepotNode = node;
                minRunTime = runtime;
            }
        }
        // 考虑发空车到终点的course
        RollingStockDutyVertex eeVertex = new RollingStockDutyVertex("EE" + context.getEeSchedules().
                size() + "_0");
        assert closestDepotNode != null;
        eeVertex.setStartTime(cur.getEndTime() + Constants.CHANGE_END_TIME);
        eeVertex.setEndTime((int) (cur.getEndTime() + Constants.CHANGE_END_TIME +
                context.getTimeMatrix()[cur.getEndNode().getIndex()][closestDepotNode.getIndex()]));
        eeVertex.setStartNode(cur.getEndNode());
        eeVertex.setEndNode(closestDepotNode);
        Schedule eeSchedule = calcEeSchedule(eeVertex, solution);
        path.add(eeSchedule);
    }

    private Schedule calcEeSchedule(RollingStockDutyVertex eeVertex, Solution solution) {
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
        solution.getScheduleSkipStationMap().put(eeSchedule, skipStations);
        solution.getScheduleStationArrivalTimeMap().put(eeSchedule, arrivals);
        solution.getScheduleStationDepartureTimeMap().put(eeSchedule, departures);
        solution.getScheduleStationTrackMap().put(eeSchedule, tracks);
        context.getSchedules().add(eeSchedule);
        context.getCourseId2Schedule().put(eeSchedule.getCourseId(), eeSchedule);
        context.getEeSchedules().add(eeSchedule);
        return eeSchedule;
    }
}
