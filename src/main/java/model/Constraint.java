package model;


import constant.Constants;
import context.*;
import graph.Edge;
import graph.Graph;
import graph.Vertex;
import gurobi.*;
import solution.TrackElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Constraint （模型约束）
 * 创建模型约束
 *
 * @author s00536729
 * @since 2022-07-01
 */
public class Constraint {
    private final Variable var;

    /**
     * 构造器
     *
     * @param var 决策变量
     */
    public Constraint(Variable var) {
        this.var = var;
    }

    /**
     * 基于问题情景、图和求解器构造模型约束
     *
     * @param problemContext 问题情景
     * @param graph 图
     * @param solver 求解器
     * @throws GRBException GUROBI异常
     */
    public void createCons(ProblemContext problemContext, Graph graph, GRBModel solver) throws GRBException {
        // 多商品网络流约束
        for (int i = 0; i < problemContext.getRollingStocks().size(); i++) {
            RollingStock rs = problemContext.getRollingStocks().get(i);
            genSourceCons(graph, solver, i, rs, problemContext);
            genSinkCons(graph, solver, i, rs);
            genFlowBalanceCons(graph, solver, i, rs);
        }
        // 顶点的入度和出度上限
        for (Vertex vertex : graph.getVertexList()) {
            if (!vertex.isVirtual()) {
                genVertexInCons(problemContext, solver, vertex);
                genVertexOutCons(problemContext, solver, vertex);
            }
        }
        // 跳站的约束
        for (Schedule schedule : problemContext.getSchedules()) {
            for (int j = 0; j < schedule.getPlannedNodes().size(); j++) {
                Node node = schedule.getPlannedNodes().get(j);
                genSkipNodeCons(problemContext, graph, solver, schedule, j, node);
            }
        }
        // 到达和离开站点的时间的约束
        for (Schedule schedule : problemContext.getSchedules()) {
            for (int j = 0; j < schedule.getPlannedNodes().size(); j++) {
                Node node = schedule.getPlannedNodes().get(j);
                genDepArrDiffLBCons(solver, schedule, j, node);
                genDepArrDiffUBCons(solver, schedule, j, node);
            }
        }
        // 边上的最小列车运行时间的约束
        for (Edge edge : graph.getEdgeList()) {
            genEdgeMinimumRunTimeCons(problemContext, solver, edge);
        }

        // 列车的到达终点的时延约束以及列车离开起点的时间不晚于预计出发时间的约束
        for (int i = 0; i < problemContext.getSchedules().size(); i++) {
            Schedule schedule = problemContext.getSchedules().get(i);
            genScheduleDesDelayCons(solver, i, schedule);
            GRBLinExpr expr = new GRBLinExpr();
            expr.addTerm(1, var.getDVars().get(schedule).get(0));
            solver.addConstr(expr, GRB.GREATER_EQUAL, schedule.getStartTime(), schedule.
                    getCourseId() + " start time cons");
        }
        // Headway约束
        genHeadwayCons(problemContext, solver);
        // TrackCapacity约束
        genTrackCapacityCons(problemContext, solver);
    }

    private void genTrackCapacityCons(ProblemContext problemContext, GRBModel solver) throws GRBException {
        Map<Track, List<TrackElement>> trackListMap = new HashMap<>();
        for (Schedule schedule : problemContext.getSchedules()) {
            for (int j = 0; j < schedule.getPlannedNodes().size(); j++) {
                Node node = schedule.getPlannedNodes().get(j);
                boolean nodeRealized = !schedule.getRealizedEnterTimes().isEmpty() && schedule.getRealizedEnterTimes().get(j + 1) != 0;
                String trackStr = schedule.getTracks().get(j + 1);
                Track track;
                Track.Direction direction;
                if (j == 0) {
                    direction = schedule.getDirection();
                } else {
                    Node lastNode = schedule.getPlannedNodes().get(j - 1);
                    Link link = problemContext.getName2Link().get(lastNode.getCode() + "_" + node.getCode());
                    direction = link.getDirection();
                }
                if (node.getName2Track().containsKey(trackStr)) {
                    track = node.getName2Track().get(trackStr);
                    if (!track.getDirection().equals(Track.Direction.BOTH) && !track.getDirection().equals(direction)) {
                        track.setDirection(Track.Direction.BOTH);
                    }
                } else {
                    System.out.println("Track " + trackStr + " at " + node.getCode() + " doesn't exist");
                    continue;
                }
                if (!schedule.getEnterTimes().containsKey(j + 1) || !schedule.getLeaveTimes().containsKey(j + 1)) {
                    continue;
                }
                TrackElement te = new TrackElement(track, node, schedule, j, schedule.getEnterTimes().get(j + 1),
                        schedule.getLeaveTimes().get(j + 1), nodeRealized);
                List<TrackElement> trackElements = trackListMap.getOrDefault(track, new ArrayList<>());
                trackElements.add(te);
                trackListMap.put(track, trackElements);
            }
        }
        for (Map.Entry<Track, List<TrackElement>> entry : trackListMap.entrySet()) {
            List<TrackElement> trackElements = entry.getValue();
            trackElements.sort((o1, o2) -> {
                if (o1.getArrival() < o2.getArrival()) {
                    return -1;
                } else if (o1.getArrival() > o2.getArrival()) {
                    return 1;
                } else {
                    return Integer.compare(o1.getDeparture(), o2.getDeparture());
                }
            });
            for (int j = 0; j < trackElements.size() - 1; j++) {
                TrackElement te1 = trackElements.get(j);
                TrackElement te2 = trackElements.get(j + 1);
                GRBLinExpr expr = new GRBLinExpr();
                expr.addTerm(1, var.getAVars().get(te2.getSchedule()).get(te2.getNodeIndex()));
                expr.addTerm(-1, var.getDVars().get(te1.getSchedule()).get(te1.getNodeIndex()));
                solver.addConstr(expr, GRB.GREATER_EQUAL, 0, te1.
                        getSchedule().getCourseId() + " " + te2.getSchedule().getCourseId() + " at " +
                        te1.getNode().getCode() + " track " + te1.getTrack().getName() + " capacity cons");

            }
        }
    }

    private void genScheduleDesDelayCons(GRBModel solver, int i, Schedule schedule) throws GRBException {
        GRBLinExpr expr = new GRBLinExpr();
        expr.addTerm(1, var.getZVars()[i]);
        expr.addTerm(-1, var.getAVars().get(schedule).get(schedule.getPlannedNodes().size() - 1));
        solver.addConstr(expr, GRB.GREATER_EQUAL, -schedule.getEndTime(),
                schedule.getCourseId() + " destination delay cons");
    }

    private void genEdgeMinimumRunTimeCons(ProblemContext problemContext, GRBModel solver, Edge edge) throws GRBException {
        if (edge.getHead().isVirtual() || edge.getTail().isVirtual()) {
            return;
        }

        GRBLinExpr expr = new GRBLinExpr();
        Vertex headVertex = edge.getHead();
        String[] headVertexNameSplit = headVertex.getName().split("_");
        String headCourseId = headVertexNameSplit[0];
        Schedule headSchedule = problemContext.getCourseId2Schedule().get(headCourseId);
        int headNodeIndex = Integer.parseInt(headVertexNameSplit[2]);
        Vertex tailVertex = edge.getTail();
        String[] tailVertexNameSplit = tailVertex.getName().split("_");
        String tailCourseId = tailVertexNameSplit[0];
        Schedule tailSchedule = problemContext.getCourseId2Schedule().get(tailCourseId);
        int tailNodeIndex = Integer.parseInt(tailVertexNameSplit[2]);
        expr.addTerm(1, var.getAVars().get(tailSchedule).get(tailNodeIndex));
        expr.addTerm(-1, var.getDVars().get(headSchedule).get(headNodeIndex));
        for (int i = 0; i < problemContext.getRollingStocks().size(); i++) {
            expr.addTerm(-edge.getMinimumRuntime(), var.getXVars()[i][edge.getIndex()]);
        }
        solver.addConstr(expr, GRB.GREATER_EQUAL, 0.0, edge.getName() +
                " minimum run time cons");
    }

    private void genDepArrDiffUBCons(GRBModel solver, Schedule schedule, int j, Node node) throws GRBException {
        GRBLinExpr expr = new GRBLinExpr();
        expr.addTerm(1, var.getDVars().get(schedule).get(j));
        expr.addTerm(-1, var.getAVars().get(schedule).get(j));
        expr.addTerm(Constants.BIG_M, var.getYVars().get(schedule).get(j));
        solver.addConstr(expr, GRB.LESS_EQUAL, Constants.BIG_M, schedule.
                getCourseId() + " departure-arrival time diff upper bound at " + node.getCode() + " " + j);
    }

    private void genDepArrDiffLBCons(GRBModel solver, Schedule schedule, int j, Node node) throws GRBException {
        GRBLinExpr expr = new GRBLinExpr();
        expr.addTerm(1, var.getDVars().get(schedule).get(j));
        expr.addTerm(-1, var.getAVars().get(schedule).get(j));
        expr.addTerm(Constants.INNER_SCHEDULE_NODE_DWELL_TIME, var.getYVars().get(schedule).get(j));
        solver.addConstr(expr, GRB.GREATER_EQUAL, Constants.INNER_SCHEDULE_NODE_DWELL_TIME, schedule.getCourseId() +
                " departure-arrival time diff lower bound at " +
                node.getCode() + " " + j);
    }

    private void genSkipNodeCons(ProblemContext problemContext, Graph graph, GRBModel solver, Schedule schedule,
                                 int j, Node node) throws GRBException {
        GRBLinExpr expr = new GRBLinExpr();
        expr.addTerm(1, var.getYVars().get(schedule).get(j));
        Vertex vertex = graph.getName2Vertex().get(graph.genVertexName(schedule, node, Vertex.Type.STOP, j));
        if (vertex == null) {
            return;
        }
        for (int i = 0; i < problemContext.getRollingStocks().size(); i++) {
            for (Edge edge : vertex.getInEdges()) {
                expr.addTerm(1, var.getXVars()[i][edge.getIndex()]);
            }
        }
        solver.addConstr(expr, GRB.EQUAL, 1, schedule.getCourseId() + " at " +
                node.getCode() + " " + j + " skip cons");
    }

    private void genVertexOutCons(ProblemContext problemContext, GRBModel solver, Vertex vertex) throws GRBException {
        GRBLinExpr expr = new GRBLinExpr();
        for (Edge edge : vertex.getOutEdges()) {
            for (int i = 0; i < problemContext.getRollingStocks().size(); i++) {
                expr.addTerm(1, var.getXVars()[i][edge.getIndex()]);
            }
        }
        solver.addConstr(expr, GRB.LESS_EQUAL, 1, vertex.getName() + " getOut cons");
    }

    private void genVertexInCons(ProblemContext problemContext, GRBModel solver, Vertex vertex) throws GRBException {
        GRBLinExpr expr = new GRBLinExpr();
        for (Edge edge : vertex.getInEdges()) {
            for (int i = 0; i < problemContext.getRollingStocks().size(); i++) {
                expr.addTerm(1, var.getXVars()[i][edge.getIndex()]);
            }
        }
        solver.addConstr(expr, GRB.LESS_EQUAL, 1, vertex.getName() + " getIn cons");
    }

    private void genFlowBalanceCons(Graph graph, GRBModel solver, int i, RollingStock rs) throws GRBException {
        for (Vertex vertex : graph.getVertexList()) {
            if (!vertex.isVirtual()) {
                GRBLinExpr expr = new GRBLinExpr();
                for (Edge edge : vertex.getInEdges()) {
                    expr.addTerm(1, var.getXVars()[i][edge.getIndex()]);
                }
                for (Edge edge : vertex.getOutEdges()) {
                    expr.addTerm(-1 ,var.getXVars()[i][edge.getIndex()]);
                }
                solver.addConstr(expr, GRB.EQUAL, 0, "Rolling stock " +
                        rs.getIndex() + " at " + vertex.getName() + " flow balance cons");
            }
        }
    }

    private void genSinkCons(Graph graph, GRBModel solver, int i, RollingStock rs) throws GRBException {
        Vertex sink = graph.getName2Vertex().get(Constants.VIRTUAL_END_VERTEX_NAME);
        GRBLinExpr expr = new GRBLinExpr();
        for (Edge edge : sink.getInEdges()) {
            expr.addTerm(1, var.getXVars()[i][edge.getIndex()]);
        }
        solver.addConstr(expr, GRB.EQUAL, 1, "Rolling stock " + rs.getIndex() + " sink cons");
    }

    private void genSourceCons(Graph graph, GRBModel solver, int i, RollingStock rs, ProblemContext problemContext) throws GRBException {
        Vertex source = graph.getName2Vertex().get(Constants.VIRTUAL_START_VERTEX_NAME);
        GRBLinExpr expr = new GRBLinExpr();
        for (Edge edge : source.getOutEdges()) {
            double coef = 1;
            coef = updateCoefByStartPos(rs, problemContext, edge, coef);
            expr.addTerm(coef, var.getXVars()[i][edge.getIndex()]);
        }
        solver.addConstr(expr, GRB.EQUAL, 1, "Rolling stock " + rs.getIndex() + " source cons");
    }

    private double updateCoefByStartPos(RollingStock rs, ProblemContext problemContext, Edge edge, double coef) {
        if (!edge.getTail().isVirtual()) {
            String code = edge.getTail().getName().split("_")[1];
            Node node = problemContext.getCode2Node().get(code);
            if (node != rs.getStartPos()) {
                coef = 0;
            }
        }
        return coef;
    }

    private void genHeadwayCons(ProblemContext problemContext, GRBModel solver) throws GRBException {
        Map<String, Schedule> courseId2Schedule = problemContext.getCourseId2Schedule();

        for (Map.Entry<String, GRBVar[][][][]> entry : var.getRVars().entrySet()) {
            String name = entry.getKey();
            String[] names = name.split("_");
            String nodeName1 = names[0];
            String nodeName2 = names[1];
            String courseId1 = names[2];
            String courseId2 = names[3];
            Schedule schedule1 = courseId2Schedule.get(courseId1);
            Schedule schedule2 = courseId2Schedule.get(courseId2);

            Link link = problemContext.getName2Link().get(nodeName1+"_"+nodeName2);
            int startNodeIndex1 = schedule1.getPlannedLinks().indexOf(link);
            int startNodeIndex2 = schedule2.getPlannedLinks().indexOf(link);
            GRBVar dVar = var.getDVars().get(schedule1).get(startNodeIndex1);
            GRBVar aVar = var.getAVars().get(schedule2).get(startNodeIndex2);
            GRBVar yVar11 = var.getYVars().get(schedule1).get(startNodeIndex1);
            GRBVar yVar12 = var.getYVars().get(schedule1).get(startNodeIndex1 + 1);
            GRBVar yVar21 = var.getYVars().get(schedule2).get(startNodeIndex2);
            GRBVar yVar22 = var.getYVars().get(schedule2).get(startNodeIndex2 + 1);
            GRBLinExpr expr = new GRBLinExpr();
            expr.addTerm(1, dVar);
            expr.addTerm(-1, aVar);
            for (int a = 0; a < 2; a++) {
                for (int b = 0; b < 2; b++) {
                    for (int c = 0; c < 2; c++) {
                        for (int d = 0; d < 2; d++) {
                            expr.addTerm(link.getMinimumHeadway()[a][b][c][d], entry.getValue()[a][b][c][d]);
                        }
                    }
                }
            }
            solver.addConstr(expr, GRB.LESS_EQUAL, 0, link.getName() + "_" + courseId1 + "_" + courseId2 +
                    " headway cons");
            for (int a = 0; a < 2; a++) {
                for (int b = 0; b < 2; b++) {
                    for (int c = 0; c < 2; c++) {
                        for (int d = 0; d < 2; d++) {
                            double lb = 0.0;
                            GRBLinExpr expr1 = new GRBLinExpr();
                            expr1.addTerm(-1, entry.getValue()[a][b][c][d]);
                            if (a == 0) {
                                expr1.addTerm(1, yVar11);
                            } else {
                                expr1.addTerm(-1, yVar11);
                                lb -= 1;
                            }
                            solver.addConstr(expr, GRB.GREATER_EQUAL, lb, link.getName() + "_" + courseId1 +
                                    "_" + courseId2 + "_0" + a + b + c + d);
                        }
                    }
                }
            }

            for (int a = 0; a < 2; a++) {
                for (int b = 0; b < 2; b++) {
                    for (int c = 0; c < 2; c++) {
                        for (int d = 0; d < 2; d++) {
                            double lb = 0;
                            GRBLinExpr expr1 = new GRBLinExpr();
                            expr1.addTerm(-1, entry.getValue()[a][b][c][d]);
                            if (b == 0) {
                                expr1.addTerm(1, yVar12);
                            } else {
                                expr1.addTerm(-1, yVar12);
                                lb -= 1;
                            }
                            solver.addConstr(expr1, GRB.GREATER_EQUAL, lb, link.getName() + "_" + courseId1 +
                                    "_" + courseId2 + "_1" + a + b + c + d);
                        }
                    }
                }
            }

            for (int a = 0; a < 2; a++) {
                for (int b = 0; b < 2; b++) {
                    for (int c = 0; c < 2; c++) {
                        for (int d = 0; d < 2; d++) {
                            GRBLinExpr expr1 = new GRBLinExpr();
                            expr1.addTerm(-1, entry.getValue()[a][b][c][d]);
                            double lb = 0;
                            if (c == 0) {
                                expr1.addTerm(1, yVar21);
                            } else {
                                expr1.addTerm(-1, yVar21);
                                lb -= 1;
                            }
                            solver.addConstr(expr1, GRB.GREATER_EQUAL, lb, link.getName() + "_" + courseId1 +
                                    "_" + courseId2 + "_2" + a + b + c + d);
                        }
                    }
                }
            }

            for (int a = 0; a < 2; a++) {
                for (int b = 0; b < 2; b++) {
                    for (int c = 0; c < 2; c++) {
                        for (int d = 0; d < 2; d++) {
                            GRBLinExpr expr1 = new GRBLinExpr();
                            double lb = 0;
                            expr1.addTerm(-1, entry.getValue()[a][b][c][d]);
                            if (d == 0) {
                                expr1.addTerm(1, yVar22);
                            } else {
                                expr1.addTerm(-1, yVar22);
                                lb -= 1;
                            }
                            solver.addConstr(expr1, GRB.GREATER_EQUAL, lb, link.getName() + "_" + courseId1 +
                                    "_" + courseId2 + "_3" + a + b + c + d);
                        }
                    }
                }
            }

            for (int a = 0; a < 2; a++) {
                for (int b = 0; b < 2; b++) {
                    for (int c = 0; c < 2; c++) {
                        for (int d = 0; d < 2; d++) {
                            GRBLinExpr expr1 = new GRBLinExpr();
                            double ub = 3;
                            expr1.addTerm(-1, entry.getValue()[a][b][c][d]);
                            if (a == 0) {
                                expr1.addTerm(1, yVar11);
                            } else {
                                expr1.addTerm(-1, yVar11);
                                ub -= 1;
                            }
                            if (b == 0) {
                                expr1.addTerm(1, yVar12);
                            } else {
                                expr1.addTerm(-1, yVar12);
                                ub -= 1;
                            }
                            if (c == 0) {
                                expr1.addTerm(1, yVar21);
                            } else {
                                expr1.addTerm(-1, yVar21);
                                ub -= 1;
                            }
                            if (d == 0) {
                                expr1.addTerm(1, yVar22);
                            } else {
                                expr1.addTerm(-1, yVar22);
                                ub -= 1;
                            }
                            solver.addConstr(expr1, GRB.LESS_EQUAL, ub, link.getName() + "_" + courseId1 +
                                    "_" + courseId2 + "_4" + a + b + c + d);
                        }
                    }
                }
            }
            var.getDVars().get(schedule1);
        }
    }
}
