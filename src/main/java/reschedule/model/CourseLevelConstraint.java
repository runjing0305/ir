package reschedule.model;

import gurobi.*;
import constant.Constants;
import context.*;
import context.scenario.LateDepartureScenario;
import context.scenario.LinkScenario;
import context.scenario.StationExtendedDwellScenario;
import context.scenario.TrainExtendedDwellScenario;
import reschedule.graph.*;
import entity.PadtllWchapxrTargetFrequency;
import graph.Vertex;
import solution.Solution;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CourseLevelConstraint {
    private final CourseLevelVariable var;

    public CourseLevelConstraint(CourseLevelVariable var) {
        this.var = var;
    }

    public void createCons(ProblemContext problemContext, CellGraph graph, GRBModel solver) throws GRBException {
        genFlowBalanceCons(graph, solver);
        createMinimumRunTimeCons(problemContext, graph, solver);
        createMinimumHeadWayCons(problemContext, graph, solver);
        createMinimumDwellTimeCons(problemContext, graph, solver);
        createScheduleDelayCons(graph, solver);
        createTrackCapacityCons(problemContext, graph, solver);
        createTargetFrequencyRelaCons(problemContext, graph, solver);
        createTrainExtendedDwellScenario(problemContext, graph, solver);
        createLateDepartureScenario(problemContext);
        createLinkExtendedRunTimeScenario(problemContext, graph, solver);
        createStationExtendedDwellScenario(problemContext, graph, solver);
        createRealizedScheduleScenario(graph, solver);
        createRollingStockCons(graph, solver);
        createScheduleSequence(problemContext, graph, solver);
        createSkipStationRankingCons(graph, solver);
        createPassStationDwellTimeCons(graph, solver);
    }

    public void fixStationStatus(CellGraph graph, GRBModel solver, Solution solution) throws GRBException {
        for (Schedule schedule : graph.getScheduleList()) {
            for (int seq = 0; seq < CellGraph.getNodeList(schedule).size(); ++seq) {
                String name = CellGraph.getScheduleNodeStr(schedule, seq);
                boolean skip = solution.getScheduleSkipStationMap().get(schedule).get(seq);
                fixEdgeVariable(graph, name, skip, solver);
            }
        }
    }

    private void createPassStationDwellTimeCons(CellGraph graph, GRBModel solver) throws GRBException {
        for (Schedule schedule : graph.getScheduleList()) {
            for (int i = 0; i < CellGraph.getNodeList(schedule).size(); ++i) {
                String headNode = CellGraph.getScheduleNodeStr(schedule, i);
                if (!graph.getScheduleAndNode2Vertices().containsKey(headNode) || isLeaveTimeRealized(schedule, i)) {
                    continue;
                }
                GRBVar arrival = var.getAVars().get(headNode);
                GRBVar departure = var.getDVars().get(headNode);
                GRBLinExpr expr = new GRBLinExpr();
                expr.addTerm(-1, departure);
                expr.addTerm(1, arrival);
                Set<CellVertex> headVertices = graph.getScheduleAndNode2Vertices().get(headNode);
                for (CellVertex cellVertex : headVertices) {
                    if (!cellVertex.getType().equals(Vertex.Type.PASS)) {
                        continue;
                    }
                    for (CellEdge edge : cellVertex.getOutArcList()) {
                        GRBVar xVar = var.getXVar(edge.getRollingStock(), edge);
                        if (xVar == null) {
                            continue;
                        }
                        expr.addTerm(-Constants.BIG_M, xVar);
                    }
                }
                solver.addConstr(expr, GRB.GREATER_EQUAL, -Constants.BIG_M, "Pass_Departure_" + headNode);
            }
        }
    }

    private void createSkipStationRankingCons(CellGraph graph, GRBModel solver) throws GRBException {
        for (List<CellVertex> item : graph.getSchedule2SkipPenaltyVertexList().values()) {
            for (CellVertex vertex : item) {
                if (!var.getBsvRankingVar().containsKey(vertex.getName())
                        || var.getBsvRankingVar().get(vertex.getName()) == null) {
                    continue;
                }
                List<CellVertex> filterList = item.stream().filter(
                        v -> v.getBsv() > vertex.getBsv() && !v.equals(vertex)).collect(Collectors.toList());
                createStationBsvFirstRankingCons(vertex, filterList, solver);
                createStationThridRankingCons(vertex, filterList, solver);
                createRankingSumCons(vertex, solver);
            }
        }
    }

    /**
     * 创建当前结点是否是course跳站节点中bsv最大节点的约束
     *
     * @param vertex   当前节点
     * @param vertices 比当前节点bsv大的节点集合
     * @param solver
     * @throws GRBException
     */
    private void createStationBsvFirstRankingCons(CellVertex vertex,
                                                  List<CellVertex> vertices, GRBModel solver) throws GRBException {
        GRBLinExpr expr = new GRBLinExpr();
        expr.addTerm(1, var.getBsvRankingVar().get(vertex.getName())[0]);
        // 当前跳站节点是否被选
        for (CellEdge edge : vertex.getInArcList()) {
            GRBVar xVar = var.getXVar(edge.getRollingStock(), edge);
            if (xVar == null) {
                continue;
            }
            expr.addTerm(-1, xVar);
        }

        // 是否存在比当前节点bsv大的节点被选
        for (CellVertex v : vertices) {
            for (CellEdge edge : v.getInArcList()) {
                GRBVar xVar = var.getXVar(edge.getRollingStock(), edge);
                if (xVar == null) {
                    continue;
                }
                expr.addTerm(1, xVar);
            }
        }
        solver.addConstr(expr, GRB.GREATER_EQUAL, 0, vertex.getName() + "_greater_bsv_station");
    }

    private void createStationThridRankingCons(CellVertex vertex,
                                               List<CellVertex> vertices, GRBModel solver) throws GRBException {
        if (vertices.size() < 2) {
            var.getBsvRankingVar().get(vertex.getName())[2].set(GRB.DoubleAttr.UB, 0);
            return;
        }
        GRBLinExpr expr = new GRBLinExpr();
        GRBLinExpr reverseExpr = new GRBLinExpr();
        expr.addTerm(1, var.getBsvRankingVar().get(vertex.getName())[2]);
        reverseExpr.addTerm(1, var.getBsvRankingVar().get(vertex.getName())[2]);
        // 当前节点是否被选
        for (CellEdge edge : vertex.getInArcList()) {
            GRBVar xVar = var.getXVar(edge.getRollingStock(), edge);
            if (xVar == null) {
                continue;
            }
            expr.addTerm(-1, xVar);
        }

        // 是否存在比当前节点bsv大的节点被选
        for (CellVertex v : vertices) {
            for (CellEdge edge : v.getInArcList()) {
                GRBVar xVar = var.getXVar(edge.getRollingStock(), edge);
                if (xVar == null) {
                    continue;
                }
                expr.addTerm((double) -1 / vertices.size(), xVar);
                reverseExpr.addTerm((double) -1 / vertices.size(), xVar);
            }
        }
        solver.addConstr(expr, GRB.GREATER_EQUAL, (double) (-2 / vertices.size()) - 1,
                vertex.getName() + "is_the_third");
        solver.addConstr(reverseExpr, GRB.LESS_EQUAL, (double) (-2 / vertices.size()) + 1,
                vertex.getName() + "is_not_the_third");
    }

    private void createRankingSumCons(CellVertex vertex, GRBModel solver) throws GRBException {
        GRBLinExpr expr = new GRBLinExpr();
        expr.addTerm(1, var.getBsvRankingVar().get(vertex.getName())[0]);
        expr.addTerm(1, var.getBsvRankingVar().get(vertex.getName())[1]);
        expr.addTerm(1, var.getBsvRankingVar().get(vertex.getName())[2]);
        for (CellEdge edge : vertex.getInArcList()) {
            GRBVar xVar = var.getXVar(edge.getRollingStock(), edge);
            if (xVar == null) {
                continue;
            }
            expr.addTerm(-1, xVar);
        }
        solver.addConstr(expr, GRB.EQUAL, 0, vertex.getName() + "_RankingSumCons");
    }

    private void createRollingStockCons(CellGraph graph, GRBModel solver) throws GRBException {
        List<CellEdge> edges = graph.getCellEdges().stream().filter(
                cellEdge -> cellEdge.getType().equals(CellEdge.Type.INTER)).collect(Collectors.toList());
        for (CellEdge edge : edges) {
            String headStr = CellGraph.getScheduleNodeStr(edge.getHead().getSchedule(), edge.getHead().getSeq());
            String tail = CellGraph.getScheduleNodeStr(edge.getTail().getSchedule(), edge.getTail().getSeq());
            GRBVar headDeparture = var.getDVars().getOrDefault(headStr, null);
            GRBVar tailArrival = var.getAVars().getOrDefault(tail, null);
            if (headDeparture == null || tailArrival == null) {
                continue;
            }
            GRBLinExpr expr = new GRBLinExpr();
            expr.addTerm(1, tailArrival);
            expr.addTerm(-1, headDeparture);
            solver.addConstr(expr, GRB.EQUAL, 0, "RollingStock_link_" + headStr + "_" + tail);

            createScheduleStartTimeCons(graph, solver, tail);
        }
    }

    /**
     * course的开始节点离开时间和到达时间相等
     *
     * @param solver
     * @param nodeStr
     */
    private void createScheduleStartTimeCons(CellGraph graph, GRBModel solver, String nodeStr) throws GRBException {
        GRBVar arrival = var.getAVars().getOrDefault(nodeStr, null);
        GRBVar departure = var.getDVars().getOrDefault(nodeStr, null);
        if (arrival == null || departure == null) {
            return;
        }
        GRBLinExpr expr = new GRBLinExpr();
        expr.addTerm(1, arrival);
        expr.addTerm(-1, departure);
        solver.addConstr(expr, GRB.EQUAL, 0, "Schedule_StartNode_TimeCons_" + nodeStr);
    }

    private void createScheduleSequence(ProblemContext problemContext,
                                        CellGraph graph, GRBModel solver) throws GRBException {
        for (Node node : problemContext.getNodes()) {
            if (!graph.getName2Cell().containsKey(node.getCode())) {
                continue;
            }
            Cell cell = graph.getName2Cell().get(node.getCode());
            for (int i = 1; i < cell.getProOccTimeItems().size(); ++i) {
                TimeItem headItem = cell.getProOccTimeItems().get(i - 1);
                String headStr = CellGraph.getScheduleNodeStr(headItem.getSchedule(), headItem.getNodeSeq());
                TimeItem tailItem = cell.getProOccTimeItems().get(i);
                String tailStr = CellGraph.getScheduleNodeStr(tailItem.getSchedule(), tailItem.getNodeSeq());
                GRBVar headArrival = var.getAVars().getOrDefault(headStr, null);
                GRBVar tailArrival = var.getAVars().getOrDefault(tailStr, null);
                if (headArrival == null || tailArrival == null) {
                    continue;
                }
                GRBLinExpr expr = new GRBLinExpr();
                expr.addTerm(1, tailArrival);
                expr.addTerm(-1, headArrival);
                solver.addConstr(expr, GRB.GREATER_EQUAL, 0,
                        "ScheduleSequence_" + node.getCode() + "_" + headStr + "_" + tailStr);
            }
        }
    }

    private void createTargetFrequencyRelaCons(ProblemContext problemContext,
                                               CellGraph graph, GRBModel solver) throws GRBException {
        createDirectionTargetFrequencyCons(problemContext, graph, solver, Track.Direction.WB, "PADTLL");
        createDirectionTargetFrequencyCons(problemContext, graph, solver, Track.Direction.EB, "WCHAPXR");
    }

    private void createDirectionTargetFrequencyCons(ProblemContext problemContext, CellGraph graph, GRBModel solver,
                                                    Track.Direction direction, String station) throws GRBException {
        if (!graph.getName2Cell().containsKey(station)) {
            return;
        }
        Cell cell = graph.getName2Cell().get(station);
        int minTimeInterval = cell.getMinOccInterval();
        List<TimeItem> proOccTimeItems = cell.getProOccTimeItems(direction);
        for (int i = 1; i < proOccTimeItems.size() - 1; ++i) {
            TimeItem headItem = proOccTimeItems.get(i - 1);
            TimeItem tailItem = proOccTimeItems.get(i);
            String headStr = CellGraph.getScheduleNodeStr(headItem.getSchedule(), headItem.getNodeSeq());
            String tailStr = CellGraph.getScheduleNodeStr(tailItem.getSchedule(), tailItem.getNodeSeq());
            if (!var.getAVars().containsKey(headStr)
                    || !var.getAVars().containsKey(tailStr)
                    || !var.getHVars().containsKey(tailStr)) {
                continue;
            }
            GRBVar headArrival = var.getAVars().get(headStr);
            GRBVar tailArrival = var.getAVars().get(tailStr);
            GRBVar frequencyVar = var.getHVars().get(tailStr);
            if (headArrival == null || tailArrival == null || frequencyVar == null) {
                continue;
            }
            double rhs = thresholdHeadway(problemContext, tailItem.getStartTime());
            GRBLinExpr expr = new GRBLinExpr();
            expr.addTerm(1, tailArrival);
            expr.addTerm(-1, headArrival);
            expr.addTerm(-1, frequencyVar);
            addEstimateTimeInterval(expr, graph, headStr, minTimeInterval);
            addEstimateTimeInterval(expr, graph, tailStr, tailItem.getStartTime() - headItem.getStartTime());
            solver.addConstr(expr, GRB.LESS_EQUAL, rhs, station + "_frequencyGap_" + tailStr);
        }
    }

    private void addEstimateTimeInterval(GRBLinExpr expr, CellGraph graph,
                                         String nodeStr, int time) throws GRBException {
        if (!graph.getScheduleAndNode2Vertices().containsKey(nodeStr)) {
            return;
        }
        for (CellVertex vertex : graph.getScheduleAndNode2Vertices().get(nodeStr)) {
            if (!vertex.getType().equals(Vertex.Type.PASS)) {
                continue;
            }
            for (CellEdge edge : vertex.getInArcList()) {
                GRBVar xVar = var.getXVar(edge.getRollingStock(), edge);
                if (xVar == null) {
                    continue;
                }
                expr.addTerm(time, xVar);
            }
        }
    }

    private int thresholdHeadway(ProblemContext problemContext, int arrivalTime) {
        int ret = 0;
        for (PadtllWchapxrTargetFrequency targetFrequency : problemContext.getCode2Node().get("PADTLL").
                getTargetFrequency().get(Track.Direction.WB)) {
            if (targetFrequency.getStartTimeSeconds() <= arrivalTime
                    && targetFrequency.getEndTimeSeconds() >= arrivalTime) {
                ret = targetFrequency.getThresholdHeadwaySeconds();
                break;
            }
        }
        return ret;
    }

    private void createRealizedScheduleScenario(CellGraph graph, GRBModel solver) throws GRBException {
        for (Schedule schedule : graph.getScheduleList()) {
            for (int seq = 0; seq < schedule.getRealizedNodes().size(); ++seq) {
                String name = CellGraph.getScheduleNodeStr(schedule, seq);
                if (schedule.getRealizedEnterTimes().containsKey(seq + 1) &&
                        schedule.getRealizedEnterTimes().get(seq + 1) != 0) {
                    if (seq != 0) {
                        fixArrivalVariable(name, schedule.getRealizedEnterTimes().get(seq + 1));
                    } else {
                        updateArrivalLowerBound(name, schedule.getRealizedEnterTimes().get(seq + 1));
                    }
                }

                if (seq != schedule.getRealizedNodes().size() - 1 &&
                        schedule.getRealizedLeaveTimes().containsKey(seq + 1) &&
                        schedule.getRealizedLeaveTimes().get(seq + 1) != 0) {
                    fixDepartureVariable(name, schedule.getRealizedLeaveTimes().get(seq + 1));
                }
            }
        }
    }

    private void updateArrivalLowerBound(String scheduleNodeStr, int value) throws GRBException {
        GRBVar arrival = var.getAVars().getOrDefault(scheduleNodeStr, null);
        if (arrival != null) {
            arrival.set(GRB.DoubleAttr.LB, value);
        }
    }

    private void fixArrivalVariable(String scheduleNodeStr, int value) throws GRBException {
        if (var.getAVars().containsKey(scheduleNodeStr)) {
            GRBVar arrival = var.getAVars().get(scheduleNodeStr);
            if (arrival != null) {
                arrival.set(GRB.DoubleAttr.LB, value);
                arrival.set(GRB.DoubleAttr.UB, value);
            }
        }
    }

    private void fixDepartureVariable(String scheduleNodeStr, int value) throws GRBException {
        if (!var.getDVars().containsKey(scheduleNodeStr)) {
            return;
        }
        GRBVar departure = var.getDVars().get(scheduleNodeStr);
        if (departure != null) {
            departure.set(GRB.DoubleAttr.LB, value);
            departure.set(GRB.DoubleAttr.UB, value);
        }
    }

    private void fixEdgeVariable(CellGraph graph, String scheduleNodeStr,
                                 boolean skip, GRBModel solver) throws GRBException {
        if (!graph.getScheduleAndNode2Vertices().containsKey(scheduleNodeStr)) {
            return;
        }
        Set<CellVertex> cellVertices = graph.getScheduleAndNode2Vertices().get(scheduleNodeStr);
        for (CellVertex cellVertex : cellVertices) {
            if ((skip && cellVertex.getType().equals(Vertex.Type.PASS))
                    || (!skip && cellVertex.getType().equals(Vertex.Type.STOP))) {
                GRBLinExpr expr = new GRBLinExpr();
                for (CellEdge edge : cellVertex.getInArcList()) {
                    GRBVar xVar = var.getXVar(edge.getRollingStock(), edge);
                    if (xVar == null) {
                        continue;
                    }
                    expr.addTerm(1, xVar);
                }
                solver.addConstr(expr, GRB.EQUAL, 1, "FixVertex_" + cellVertex.getName());
            }
        }
    }

    private void createStationExtendedDwellScenario(ProblemContext problemContext,
                                                    CellGraph graph, GRBModel solver) throws GRBException {
        for (StationExtendedDwellScenario scenario : problemContext.getScenario().getStationExtendedDwellScenarios()) {
            if (!graph.getName2Cell().containsKey(scenario.getNode().getCode())) {
                continue;
            }
            Cell cell = graph.getName2Cell().get(scenario.getNode().getCode());
            for (TimeItem item : cell.getProOccTimeItems()) {
                if (item.getStartTime() < scenario.getStartTimeSeconds()
                        || item.getStartTime() > scenario.getEndTimeSeconds() ||
                        isLeaveTimeRealized(item.getSchedule(), item.getNodeSeq())) {
                    continue;
                }
                String nodeStr = CellGraph.getScheduleNodeStr(item.getSchedule(), item.getNodeSeq());
                if (!var.getDVars().containsKey(nodeStr) || !var.getAVars().containsKey(nodeStr)) {
                    continue;
                }
                GRBVar arrival = var.getAVars().get(nodeStr);
                GRBVar departure = var.getDVars().get(nodeStr);
                if (arrival == null || departure == null) {
                    continue;
                }
                GRBLinExpr expr = new GRBLinExpr();
                expr.addTerm(1, departure);
                expr.addTerm(-1, arrival);
                Set<CellVertex> vertices = graph.getScheduleAndNode2Vertices().get(nodeStr);
                for (CellVertex cellVertex : vertices) {
                    if (cellVertex.getType().equals(Vertex.Type.PASS)) {
                        continue;
                    }
                    for (CellEdge edge : cellVertex.getOutArcList()) {
                        GRBVar xVar = var.getXVar(edge.getRollingStock(), edge);
                        if (xVar == null) {
                            continue;
                        }
                        expr.addTerm(-scenario.getExtendedRunTimeSeconds(), xVar);
                    }
                }
                solver.addConstr(expr, GRB.GREATER_EQUAL, 0,
                        "StationExtendedDwell_" + nodeStr);
            }
        }
    }

    private void createLinkExtendedRunTimeScenario(ProblemContext problemContext,
                                                   CellGraph graph, GRBModel solver) throws GRBException {
        for (LinkScenario linkScenario : problemContext.getScenario().getLinkScenarios()) {
            if (!graph.getName2Cell().containsKey(linkScenario.getLink().getName())) {
                continue;
            }
            Cell cell = graph.getName2Cell().get(linkScenario.getLink().getName());
            for (TimeItem item : cell.getProOccTimeItems()) {
                if (item.getStartTime() < linkScenario.getStartTime() ||
                        item.getStartTime() > linkScenario.getEndTime()) {
                    continue;
                }
                String headNodeStr = CellGraph.getScheduleNodeStr(item.getSchedule(), item.getNodeSeq());
                String tailNodeStr = CellGraph.getScheduleNodeStr(item.getSchedule(), item.getNodeSeq() + 1);
                if (!var.getDVars().containsKey(headNodeStr)
                        || !var.getAVars().containsKey(tailNodeStr)) {
                    continue;
                }
                GRBVar headDeparture = var.getDVars().get(headNodeStr);
                GRBVar tailArrival = var.getAVars().get(tailNodeStr);
                if (headDeparture == null || tailArrival == null) {
                    continue;
                }
                GRBLinExpr expr = new GRBLinExpr();
                expr.addTerm(1, tailArrival);
                expr.addTerm(-1, headDeparture);
                solver.addConstr(expr, GRB.GREATER_EQUAL, linkScenario.getExtendedRunTime(),
                        "ExtendedRunTimeScenario_" + headNodeStr);
            }
        }
    }

    private void createTrainExtendedDwellScenario(ProblemContext problemContext,
                                                  CellGraph graph, GRBModel solver) throws GRBException {
        for (TrainExtendedDwellScenario scenario : problemContext.getScenario().getTrainExtendedDwellScenarios()) {
            for (int i = 0; i < CellGraph.getNodeList(scenario.getSchedule()).size(); ++i) {
                if (!CellGraph.getNodeList(scenario.getSchedule()).get(i).equals(scenario.getNode())) {
                    continue;
                }
                String name = CellGraph.getScheduleNodeStr(scenario.getSchedule(), i);
                if (!graph.getScheduleAndNode2Vertices().containsKey(name)) {
                    System.out.println("graph.getScheduleAndNode2Vertices() do not contain key " + name
                            + " " + scenario.getNode().getCode() + " " + CellGraph.getNodeList(scenario.getSchedule()).get(i).getCode());
                    continue;
                }
                GRBVar arrival = var.getAVars().get(name);
                GRBVar departure = var.getDVars().get(name);
                GRBLinExpr expr = new GRBLinExpr();
                expr.addTerm(1, departure);
                expr.addTerm(-1, arrival);
                Set<CellVertex> vertices = graph.getScheduleAndNode2Vertices().get(name);
                for (CellVertex cellVertex : vertices) {
                    if (cellVertex.getType().equals(Vertex.Type.PASS)) {
                        continue;
                    }
                    for (CellEdge edge : cellVertex.getOutArcList()) {
                        GRBVar xVar = var.getXVar(edge.getRollingStock(), edge);
                        if (xVar == null) {
                            continue;
                        }
                        expr.addTerm(-scenario.getExtendedRunTimeSeconds(), xVar);
                    }
                }
                solver.addConstr(expr, GRB.GREATER_EQUAL, 0, "ExtendedDwellScenarios_"
                        + scenario.getSchedule().getCourseId() + "_"
                        + scenario.getNode().getCode() + "_" + i);
            }
        }
    }

    private void createLateDepartureScenario(ProblemContext problemContext) throws GRBException {
        for (LateDepartureScenario scenario : problemContext.getScenario().getLateDepartureScenarios()) {
            String name = CellGraph.getScheduleNodeStr(scenario.getSchedule(), 0);
            if (!var.getDVars().containsKey(name)
                    || (scenario.getSchedule().getRealizedLeaveTimes().containsKey(1) &&
                    scenario.getSchedule().getRealizedLeaveTimes().get(1) != 0)) {
                continue;
            }
            GRBVar variable = var.getDVars().get(name);
            int leaveTime = scenario.getSchedule().getLeaveTimes().get(1) + scenario.getDepartureDelaySeconds();
            variable.set(GRB.DoubleAttr.LB, leaveTime);
        }
    }

    private void genFlowBalanceCons(CellGraph graph, GRBModel solver) throws GRBException {
        for (RollingStock rs : graph.getRollingStocks()) {
            for (CellVertex vertex : graph.getVertexList()) {
                if (!vertex.isVirtual()) {
                    GRBLinExpr expr = new GRBLinExpr();
                    for (CellEdge edge : vertex.getInArcList()) {
                        GRBVar xVar = var.getXVar(rs, edge);
                        if (xVar == null) {
                            continue;
                        }
                        expr.addTerm(1, xVar);
                    }
                    for (CellEdge edge : vertex.getOutArcList()) {
                        GRBVar xVar = var.getXVar(rs, edge);
                        if (xVar == null) {
                            continue;
                        }
                        expr.addTerm(-1, xVar);
                    }
                    solver.addConstr(expr, GRB.EQUAL, 0, "RollingStock_" +
                            rs.getIndex() + "_at_" + vertex.getName() + "_flowBalanceCons");
                }
            }
        }
    }

    private void createMinimumRunTimeCons(ProblemContext problemContext,
                                          CellGraph graph, GRBModel solver) throws GRBException {
        for (Schedule schedule : graph.getScheduleList()) {
            for (int i = 0; i < CellGraph.getNodeList(schedule).size() - 1; ++i) {
                if (isLeaveTimeRealized(schedule, i) && isEnterTimeRealized(schedule, i + 1)) {
                    continue;
                }
                String headNode = CellGraph.getScheduleNodeStr(schedule, i);
                String tailNode = CellGraph.getScheduleNodeStr(schedule, i + 1);
                GRBVar headDeparture = var.getDVars().get(headNode);
                GRBVar tailArrival = var.getAVars().get(tailNode);
                GRBLinExpr expr = new GRBLinExpr();
                expr.addTerm(1, tailArrival);
                expr.addTerm(-1, headDeparture);
                Set<CellVertex> headVertices = graph.getScheduleAndNode2Vertices().get(headNode);
                for (CellVertex cellVertex : headVertices) {
                    for (CellEdge edge : cellVertex.getOutArcList()) {
                        for (RollingStock rollingStock : problemContext.getRollingStocks()) {
                            GRBVar xVar = var.getXVar(rollingStock, edge);
                            if (xVar == null) {
                                continue;
                            }
                            expr.addTerm(-edge.getMinimumRuntime(), xVar);
                        }
                    }
                }
                solver.addConstr(expr, GRB.GREATER_EQUAL, 0, "MinRuntime_" + headNode);
            }
        }
    }


    private void createMinimumHeadWayCons(ProblemContext problemContext,
                                          CellGraph graph, GRBModel solver) throws GRBException {
        for (Link link : problemContext.getLinks()) {
            if (!graph.getName2Cell().containsKey(link.getName())) {
                continue;
            }
            Cell cell = graph.getName2Cell().get(link.getName());
            for (int i = 1; i < cell.getProOccTimeItems().size(); ++i) {
                TimeItem headItem = cell.getProOccTimeItems().get(i - 1);
                TimeItem tailItem = cell.getProOccTimeItems().get(i);
                if (isEnterTimeRealized(tailItem.getSchedule(), tailItem.getNodeSeq())) {
                    continue;
                }
                String headScheduleNode = CellGraph.getScheduleNodeStr(headItem.getSchedule(), headItem.getNodeSeq());
                String tailScheduleNode = CellGraph.getScheduleNodeStr(tailItem.getSchedule(), tailItem.getNodeSeq());
                GRBVar headDeparture = var.getDVars().get(headScheduleNode);
                GRBVar tailArrival = var.getAVars().get(tailScheduleNode);
                if (headDeparture == null || tailArrival == null) {
                    continue;
                }
                Set<CellVertex> headVertices = graph.getScheduleAndNode2Vertices().get(headScheduleNode);
                for (CellVertex vertex : headVertices) {
                    for (CellEdge edge : vertex.getOutArcList()) {
                        GRBVar xVar = var.getXVar(edge.getRollingStock(), edge);
                        if (xVar == null) {
                            continue;
                        }
                        Set<CellVertex> tailVertices = graph.getScheduleAndNode2Vertices().get(tailScheduleNode);
                        for (CellVertex tailVertex : tailVertices) {
                            for (CellEdge behindEdge : tailVertex.getOutArcList()) {
                                GRBVar behindXvar = var.getXVar(behindEdge.getRollingStock(), behindEdge);
                                if (behindXvar == null) {
                                    continue;
                                }
                                int headWay = link.getMinimumHeadway()[link.vertexType2Index(edge.getHead().getType())]
                                        [link.vertexType2Index(edge.getTail().getType())]
                                        [link.vertexType2Index(behindEdge.getHead().getType())]
                                        [link.vertexType2Index(behindEdge.getTail().getType())];
                                GRBLinExpr expr = new GRBLinExpr();
                                expr.addTerm(1, tailArrival);
                                expr.addTerm(-1, headDeparture);
                                expr.addTerm(-headWay, xVar);
                                expr.addTerm(-headWay, behindXvar);

                                solver.addConstr(expr, GRB.GREATER_EQUAL, -headWay, "MinimumHeadWayCons_link_" +
                                        edge.getName() + "_" + behindEdge.getName());
                            }
                        }
                    }
                }
            }
        }
    }

    private int getMinEdgeStartTime(List<CellEdge> edges) {
        int startTime = Constants.BIG_M;
        for (CellEdge item : edges) {
            startTime = Math.min(startTime, item.getStartTime());
        }
        return startTime;
    }

    private int getMaxEdgeEndTime(List<CellEdge> edges) {
        int endTime = 0;
        for (CellEdge item : edges) {
            endTime = Math.min(endTime, item.getStartTime());
        }
        return endTime;
    }

    private boolean isNodeRealized(Schedule schedule, int seq) {
        return ((schedule.getRealizedEnterTimes().containsKey(seq + 1)
                && schedule.getRealizedEnterTimes().get(seq + 1) != 0) ||
                (schedule.getRealizedLeaveTimes().containsKey(seq + 1)
                        && schedule.getRealizedLeaveTimes().get(seq + 1) != 0));
    }

    private void createMinimumDwellTimeCons(ProblemContext problemContext,
                                            CellGraph graph, GRBModel solver) throws GRBException {
        for (Schedule schedule : graph.getScheduleList()) {
            for (int i = 0; i < CellGraph.getNodeList(schedule).size(); ++i) {
                String headNode = CellGraph.getScheduleNodeStr(schedule, i);
                if (!graph.getScheduleAndNode2Vertices().containsKey(headNode) || isLeaveTimeRealized(schedule, i)) {
                    continue;
                }
                GRBVar arrival = var.getAVars().get(headNode);
                GRBVar departure = var.getDVars().get(headNode);
                GRBLinExpr expr = new GRBLinExpr();
                expr.addTerm(1, departure);
                expr.addTerm(-1, arrival);
                Set<CellVertex> headVertices = graph.getScheduleAndNode2Vertices().get(headNode);
                for (CellVertex cellVertex : headVertices) {
                    for (CellEdge edge : cellVertex.getOutArcList()) {
                        for (RollingStock rollingStock : problemContext.getRollingStocks()) {
                            GRBVar xVar = var.getXVar(rollingStock, edge);
                            if (xVar == null) {
                                continue;
                            }
                            expr.addTerm(-cellVertex.getMinDwellTime(), xVar);
                        }
                    }
                }
                solver.addConstr(expr, GRB.GREATER_EQUAL, 0, "MinDwellTime_" + headNode);
            }
        }
    }

    private void createTrackCapacityCons(ProblemContext problemContext,
                                         CellGraph graph, GRBModel solver) throws GRBException {
        for (Node node : problemContext.getNodes()) {
            if (!graph.getName2Cell().containsKey(node.getCode())) {
                continue;
            }
            Cell cell = graph.getName2Cell().get(node.getCode());
            for (int k = 0; k < node.getTracks().size(); ++k) {
                Track track = node.getTracks().get(k);
                List<TimeItem> timeItemList = cell.getProOccTimeItems().stream().filter(
                        timeItem -> timeItem.getTrack() != null && track.getName().
                                equals(timeItem.getTrack())).collect(Collectors.toList());
                for (int i = 1; i < timeItemList.size(); ++i) {
                    TimeItem headItem = timeItemList.get(i - 1);
                    String headStr = CellGraph.getScheduleNodeStr(headItem.getSchedule(), headItem.getNodeSeq());
                    TimeItem tailItem = timeItemList.get(i);
                    if (isEnterTimeRealized(tailItem.getSchedule(), tailItem.getNodeSeq()) ||
                            headItem.getRollingStock() == tailItem.getRollingStock()) {
                        continue;
                    }
                    String tailStr = CellGraph.getScheduleNodeStr(tailItem.getSchedule(), tailItem.getNodeSeq());
                    GRBVar headVar;
                    if (node.isDepot() &&
                            headItem.getNodeSeq() == CellGraph.getNodeList(headItem.getSchedule()).size() - 1) {
                        headVar = var.getAVars().get(headStr);
                    } else {
                        headVar = var.getDVars().get(headStr);
                    }
                    GRBVar tailArrival = var.getAVars().get(tailStr);
                    if (headVar == null || tailArrival == null) {
                        continue;
                    }
                    GRBLinExpr expr = new GRBLinExpr();
                    expr.addTerm(1, tailArrival);
                    expr.addTerm(-1, headVar);
                    solver.addConstr(expr, GRB.GREATER_EQUAL, Constants.MINIMUM_SEPARATION_TIME,
                            "TrackCapacityCons_" + track.getName() + "_" + headStr + "_" + tailStr);
                }
            }
        }
    }

    private void createScheduleDelayCons(CellGraph graph, GRBModel solver) throws GRBException {
        final double freeTime = 3 * Constants.SECONDS_IN_MINUTE - 1;
        for (Schedule schedule : graph.getScheduleList()) {
            int endId = CellGraph.getNodeList(schedule).size() - 1;
            String name = CellGraph.getScheduleNodeStr(schedule, endId);
            if (!var.getAVars().containsKey(name) || !var.getScheduleDelayVar().containsKey(schedule)
                    || isEnterTimeRealized(schedule, CellGraph.getNodeList(schedule).size() - 1)) {
                continue;
            }
            GRBVar arrival = var.getAVars().get(name);
            GRBVar delay = var.getScheduleDelayVar().get(schedule);
            if (arrival == null || delay == null) {
                continue;
            }
            GRBLinExpr expr = new GRBLinExpr();
            expr.addTerm(1, arrival);
            expr.addTerm(-1, delay);
            solver.addConstr(expr, GRB.LESS_EQUAL,
                    schedule.getEnterTimes().get(schedule.getPlannedNodes().size()) + freeTime,
                    "ScheduleDelayCons_" + schedule.getCourseId());
        }
    }

    private boolean isEnterTimeRealized(Schedule schedule, int seq) {
        return !schedule.getRealizedNodes().isEmpty() && (seq == 0 && schedule.getRealizedLeaveTimes().containsKey(seq + 1)
                && schedule.getRealizedLeaveTimes().get(seq + 1) != 0) ||
                (seq != 0 && schedule.getRealizedEnterTimes().containsKey(seq + 1)
                        && schedule.getRealizedEnterTimes().get(seq + 1) != 0);
    }

    private boolean isLeaveTimeRealized(Schedule schedule, int seq) {
        return !schedule.getRealizedNodes().isEmpty()
                && schedule.getRealizedLeaveTimes().containsKey(seq + 1)
                && schedule.getRealizedLeaveTimes().get(seq + 1) != 0;
    }
}
