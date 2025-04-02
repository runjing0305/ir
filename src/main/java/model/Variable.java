package model;

import constant.Constants;
import context.*;
import graph.Edge;
import graph.Graph;
import gurobi.*;
import lombok.Getter;
import lombok.Setter;
import solution.HeadwayElement;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Variable （变量）
 * 创建变量
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class Variable {
    private GRBVar[][] xVars; // 每个Rolling Stock选择了图上的哪条边
    private Map<Schedule, List<GRBVar>> yVars; // 每个schedule选择了跳过或停留某个站点
    private GRBVar[] zVars; // 每个schedule的destination delay
    private Map<Schedule, List<GRBVar>> aVars; // 每个schedule在每个站点的到达时间
    private Map<Schedule, List<GRBVar>> dVars; // 每个schedule在每个站点的到达时间
    private Map<String, GRBVar[][][][]> rVars; // 每条link的前后两个schedule到达站点的状态

    /**
     * 基于问题情景、图和求解器创建变量
     *
     * @param problemContext 问题情景
     * @param graph 图
     * @param solver 求解器
     * @throws GRBException GUROBI异常
     */
    public void createVars(ProblemContext problemContext, Graph graph, GRBModel solver) throws GRBException {
        GRBLinExpr objExpr = new GRBLinExpr();
        xVars = new GRBVar[problemContext.getRollingStocks().size()][graph.getEdgeList().size()];
        yVars = new HashMap<>();
        zVars = new GRBVar[problemContext.getSchedules().size()];
        aVars = new HashMap<>();
        dVars = new HashMap<>();
        rVars = new HashMap<>();

        for (int i = 0; i < problemContext.getRollingStocks().size(); i++) {
            RollingStock rs = problemContext.getRollingStocks().get(i);
            for (int j = 0; j < graph.getEdgeList().size(); j++) {
                Edge edge = graph.getEdgeList().get(j);
                xVars[i][j] = solver.addVar(0.0, 1.0, 0.0, GRB.BINARY, "Rolling stock " + rs +
                        " chooses " + edge.getName());
            }
        }

        for (int i = 0; i < problemContext.getSchedules().size(); i++) {
            Schedule schedule = problemContext.getSchedules().get(i);
            yVars.put(schedule, new ArrayList<>());
            for (int j = 0; j < schedule.getPlannedNodes().size(); j++) {
                Node node = schedule.getPlannedNodes().get(j);
                yVars.get(schedule).add(solver.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, "Schedule " +
                        schedule.getCourseId() + " skips " + node.getCode() + " " + j));
//                if (schedule.getCategory().equals(Schedule.Category.OO) && schedule.getNodeStatus().get(node).
//                        equalsIgnoreCase("STOP")) {
                if (schedule.getNodeStatus().get(j + 1).equalsIgnoreCase("STOP")) {
                    objExpr.addTerm(node.getAvgBsv() * Constants.SECOND_SKIP_STOP_MULTIPLIER +
                                    Constants.INITIAL_SKIP_STATION_PENALTY,
                            yVars.get(schedule).get(j));
                }
            }
        }

        for (int i = 0; i < problemContext.getSchedules().size(); i++) {
            Schedule schedule = problemContext.getSchedules().get(i);
            zVars[i] = solver.addVar(0, Double.POSITIVE_INFINITY, 0.0, GRB.CONTINUOUS, "Schedule " + schedule.getCourseId() +
                    " delay time");
            if (schedule.getCategory().equals(Schedule.Category.OO)) {
                objExpr.addTerm(Constants.DELAY_PENALTY / Constants.SECONDS_IN_MINUTE, zVars[i]);
            }
        }

        for (int i = 0; i < problemContext.getSchedules().size(); i++) {
            Schedule schedule = problemContext.getSchedules().get(i);
            aVars.put(schedule, new ArrayList<>());
            dVars.put(schedule, new ArrayList<>());
            for (int j = 0; j < schedule.getPlannedNodes().size(); j++) {
                Node node = schedule.getPlannedNodes().get(j);
                aVars.get(schedule).add(solver.addVar(0, Double.POSITIVE_INFINITY, 0, GRB.CONTINUOUS,
                        schedule.getCourseId() + " " + node.getCode() + " " + j + " arrival time"));
                dVars.get(schedule).add(solver.addVar(0, Double.POSITIVE_INFINITY, 0, GRB.CONTINUOUS,
                        schedule.getCourseId() + " " + node.getCode() + " " + j + " departure time"));
            }
        }

        genRVars(problemContext, solver);
        solver.setObjective(objExpr, GRB.MINIMIZE);
    }

    private void genRVars(ProblemContext problemContext, GRBModel solver) throws GRBException {
        Set<Schedule> scheduleSet = new HashSet<>(problemContext.getSchedules());
        for (Link link : problemContext.getLinks()) {
            List<Schedule> schedules = link.getSchedules().stream().filter(scheduleSet::contains).
                    collect(Collectors.toList());
            List<HeadwayElement> headwayElements = new ArrayList<>();
            for (Schedule schedule : schedules) {
                HeadwayElement headwayElement = new HeadwayElement();
                List<Node> plannedNodes = schedule.getPlannedNodes();
                for (int i = 0; i < plannedNodes.size() - 1; i++) {
                    Node node1 = plannedNodes.get(i);
                    Integer node1Index = i;
                    Node node2 = plannedNodes.get(i + 1);
                    Integer node2Index = i + 1;
                    if (link.getName().equals(node1.getCode() + "_" + node2.getCode())) {
                        headwayElement.setLink(link);
                        if (!schedule.getEnterTimes().containsKey(node1Index)) {
                            continue;
                        }
//                        if (!schedule.getEnterTimes().containsKey(node2Index)) {
//                            continue;
//                        }
                        if (!schedule.getLeaveTimes().containsKey(node1Index)) {
                            continue;
                        }
//                        if (!schedule.getLeaveTimes().containsKey(node2Index)) {
//                            continue;
//                        }
                        headwayElement.setHeadArrival(schedule.getEnterTimes().get(node1Index));
                        headwayElement.setHeadDeparture(schedule.getLeaveTimes().get(node1Index));
//                        headwayElement.setTailArrival(schedule.getEnterTimes().get(node2Index));
//                        headwayElement.setTailDeparture(schedule.getLeaveTimes().get(node2Index));
                        headwayElement.setSchedule(schedule);
                        headwayElements.add(headwayElement);
                    }
                }
            }
            schedules.sort(Comparator.comparingInt(Schedule::getStartTime));
            headwayElements.sort(Comparator.comparingInt(HeadwayElement::getHeadArrival));
            for (int i = 0; i < headwayElements.size() - 1; i++) {
                HeadwayElement frontTrain = headwayElements.get(i);
                HeadwayElement behindTrain = headwayElements.get(i + 1);
                String name = link.getName() + "_" + frontTrain.getSchedule().getCourseId() + "_" + behindTrain.
                        getSchedule().getCourseId();
                GRBVar[][][][] mpVars = new GRBVar[2][2][2][2];
                for (int a = 0; a < 2; a++) {
                    for (int b = 0; b < 2; b++) {
                        for (int c = 0; c < 2; c++) {
                            for (int d = 0; d < 2; d++) {
                                mpVars[a][b][c][d] = solver.addVar(0, 1, 0, GRB.CONTINUOUS,
                                        name + "_" + a + "_" + b + "_" + c + "_" + d);
                            }
                        }
                    }
                }
                rVars.put(name, mpVars);
            }
        }

//        Map<Link, List<HeadwayElement>> link2HeadwayElements = new HashMap<>();
//        for (Schedule schedule : problemContext.getSchedules()) {
//            for (int j = 0; j < schedule.getPlannedNodes().size() - 1; j++) {
//                Node headNode = schedule.getPlannedNodes().get(j);
//                Node tailNode = schedule.getPlannedNodes().get(j + 1);
//                String linkName = headNode.getCode() + "_" + tailNode.getCode();
//                Link link = problemContext.getName2Link().get(linkName);
//                if (!schedule.getEnterTimes().containsKey(j)) {
//                    continue;
//                }
//                HeadwayElement headwayElement = new HeadwayElement(link, schedule, j);
//                headwayElement.setSchedule(schedule);
//                List<HeadwayElement> headwayElements = link2HeadwayElements.getOrDefault(link, new ArrayList<>());
//                headwayElements.add(headwayElement);
//                link2HeadwayElements.put(link, headwayElements);
//            }
//        }
//        for (Map.Entry<Link, List<HeadwayElement>> entry : link2HeadwayElements.entrySet()) {
//            Link link = entry.getKey();
//            List<HeadwayElement> headwayElements = entry.getValue();
//            headwayElements.sort(Comparator.comparingInt(HeadwayElement::getHeadArrival));
//            for (int i = 0; i < headwayElements.size() - 1; i++) {
//                HeadwayElement frontTrain = headwayElements.get(i);
//                HeadwayElement behindTrain = headwayElements.get(i + 1);
//                String name = link.getName() + "_" + frontTrain.getSchedule().getCourseId() + "_" + behindTrain.
//                        getSchedule().getCourseId();
//                GRBVar[][][][] mpVars = new GRBVar[2][2][2][2];
//                for (int a = 0; a < 2; a++) {
//                    for (int b = 0; b < 2; b++) {
//                        for (int c = 0; c < 2; c++) {
//                            for (int d = 0; d < 2; d++) {
//                                mpVars[a][b][c][d] = solver.addVar(0, 1, 0, GRB.CONTINUOUS,
//                                        name + "_" + a + "_" + b + "_" + c + "_" + d);
//                            }
//                        }
//                    }
//                }
//                rVars.put(name, mpVars);
//            }
//        }
    }
}
