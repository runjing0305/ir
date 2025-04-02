package reschedule.model;

import constant.Constants;
import context.*;
import context.scenario.RealizedScheduleScenario;
import reschedule.graph.*;
import gurobi.*;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
@Setter
public class CourseLevelVariable {
    public static final int TIME_STEP = 10;
    private static final double DELAY_PENALTY_MULTIPLIER = 1.5;
    GRBLinExpr objective;
    private Map<String, GRBVar> aVars = new HashMap<>();
    private Map<String, GRBVar> dVars = new HashMap<>();
    private Map<String, GRBVar> xVars = new HashMap<>();
    private Map<String, GRBVar[]> yVars = new HashMap<>();
    private Map<String, GRBVar> kVars = new HashMap<>(); // t时刻路线c是否占用站点i
    private Map<String, GRBVar[]> bsvRankingVar = new HashMap<>();

    private Map<String, GRBVar> hVars = new HashMap<>();
    private Map<Schedule, GRBVar> scheduleDelayVar = new HashMap<>();

    public void createVars(ProblemContext problemContext, CellGraph graph, GRBModel solver) throws GRBException {
        createRollingStock2EdgeVar(graph, solver);
        createStationScheduleTimeVar(problemContext, graph, solver);
        createScheduleDelayVar(graph, solver);
        createTargetFrequencyVar(graph, solver);
        createStationBsvRanking(graph, solver);
        objective = new GRBLinExpr();
        createObjective(problemContext, graph);
        solver.setObjective(objective);
        fixVars(graph);
    }

    public String getEdgeTimeVarName(String edgeName, int t) {
        return edgeName + "_" + t;
    }

    public String getVertexTimeVarName(CellVertex vertex, int t) {
        return vertex.getName() + "_" + t;
    }

    public String getRs2EdgeVarName(RollingStock rollingStock, CellEdge edge) {
        return rollingStock.getIndex() + "_" + edge.getName();
    }

    public GRBVar getXVar(RollingStock rollingStock, CellEdge edge) {
        String name = getRs2EdgeVarName(rollingStock, edge);
        return xVars.getOrDefault(name, null);
    }


    public GRBVar getAVar(CellVertex vertex, int t) {
        return aVars.getOrDefault(getVertexTimeVarName(vertex, t), null);
    }

    public GRBVar getDVar(CellVertex vertex, int t) {
        return dVars.getOrDefault(getVertexTimeVarName(vertex, t), null);
    }

    public GRBVar getYVar(String scheduleNodeStr, int trackId) {
        if (!yVars.containsKey(scheduleNodeStr) || trackId < 0 || trackId >= yVars.get(scheduleNodeStr).length) {
            return null;
        } else {
            return yVars.get(scheduleNodeStr)[trackId];
        }
    }

    public GRBVar getKVar(Schedule schedule, Node node, int t, int seq) {
        return kVars.getOrDefault(getScheduleNodeOccupiedVarName(schedule, node, t, seq), null);
    }

    private String getScheduleNodeOccupiedVarName(Schedule schedule, Node node, int t, int seq) {
        return schedule.getCourseId() + "_" + node.getCode() + "_" + t + "_" + seq;
    }

    private void createStationBsvRanking(CellGraph graph, GRBModel solver) throws GRBException {
        final int rankingSize = 3;
        for (Map.Entry<Schedule, List<CellVertex>> item : graph.getSchedule2SkipPenaltyVertexList().entrySet()) {
            for (CellVertex vertex : item.getValue()) {
                GRBVar[] mpVars = new GRBVar[rankingSize];
                for (int i = 0; i < rankingSize; ++i) {
                    mpVars[i] = solver.addVar(0, 1, 0, GRB.BINARY, vertex.getName() + "_" + i);
                }
                bsvRankingVar.put(vertex.getName(), mpVars);
            }
        }
    }

    private void fixVars(CellGraph graph) throws GRBException {
        for (Map.Entry<RollingStock, List<CellEdge>> item : graph.getRolling2FixedEdge().entrySet()) {
            RollingStock rollingStock = item.getKey();
            for (CellEdge edge : item.getValue()) {
                String name = getRs2EdgeVarName(rollingStock, edge);
                if (!xVars.containsKey(name)) {
                    continue;
                }
                xVars.get(name).set(GRB.DoubleAttr.LB, 1);
            }
        }
    }

    private void createTargetFrequencyVar(CellGraph graph, GRBModel solver) throws GRBException {
        createDirectionTargetFrequencyVar(graph, solver, Track.Direction.WB, "PADTLL");
        createDirectionTargetFrequencyVar(graph, solver, Track.Direction.EB, "WCHAPXR");
    }

    private void createDirectionTargetFrequencyVar(CellGraph graph, GRBModel solver,
                                                   Track.Direction direction, String station) throws GRBException {
        if (!graph.getName2Cell().containsKey(station)) {
            return;
        }
        Cell cell = graph.getName2Cell().get(station);
        for (TimeItem item : cell.getProOccTimeItems(direction)) {
            String scheduleNodeStr = CellGraph.getScheduleNodeStr(item.getSchedule(), item.getNodeSeq());
            GRBVar variable = solver.addVar(0, Constants.BIG_M, 0, GRB.CONTINUOUS,
                    direction + "_TargetFrequencyVar_" + scheduleNodeStr);
            hVars.put(scheduleNodeStr, variable);
        }
    }

    private void createScheduleDelayVar(CellGraph graph, GRBModel GRBModel) throws GRBException {
        for (Schedule schedule : graph.getScheduleList()) {
            GRBVar variable = GRBModel.addVar(0, Double.POSITIVE_INFINITY, 0, GRB.INTEGER,
                    "DelayTime_" + schedule.getCourseId());
            scheduleDelayVar.put(schedule, variable);
        }
    }

    private void createStationScheduleTimeVar(ProblemContext problemContext,
                                              CellGraph graph, GRBModel GRBModel) throws GRBException {
        int currentTime = getCurrentTime(problemContext);
        final double aheadTime = 5 * Constants.SECONDS_IN_MINUTE;
        for (Schedule schedule : graph.getScheduleList()) {
            for (int seq = 0; seq < CellGraph.getNodeList(schedule).size(); ++seq) {
                String name = CellGraph.getScheduleNodeStr(schedule, seq);
                GRBVar variable = GRBModel.addVar(0, Double.POSITIVE_INFINITY, 0, GRB.INTEGER,
                        "ArrivalTime_" + name);
                if (!schedule.getRealizedEnterTimes().containsKey(seq + 1)
                        || schedule.getRealizedEnterTimes().get(seq + 1) == 0) {
                    variable.set(GRB.DoubleAttr.LB, currentTime);
                }
                aVars.put(name, variable);

                GRBVar departureVar = GRBModel.addVar(0, Double.POSITIVE_INFINITY, 0, GRB.INTEGER,
                        "DepartureTime_" + name);
                if ((!schedule.getRealizedLeaveTimes().containsKey(seq + 1)
                        || schedule.getRealizedLeaveTimes().get(seq + 1) == 0)
                        && seq != CellGraph.getNodeList(schedule).size() - 1) {
                    double startTime = seq == 0 ?
                            Math.max(schedule.getStartTime() - aheadTime, currentTime) : currentTime;
                    departureVar.set(GRB.DoubleAttr.LB, startTime);
                }
                dVars.put(name, departureVar);
            }
        }
    }

    private void createRollingStock2EdgeVar(CellGraph graph, GRBModel solver) throws GRBException {
        xVars = new HashMap<>();
        for (Map.Entry<RollingStock, List<CellEdge>> item : graph.getRs2CellEdges().entrySet()) {
            RollingStock rollingStock = item.getKey();
            for (CellEdge cellEdge : item.getValue()) {
                GRBVar var = solver.addVar(0, 1, 0, GRB.BINARY, "RollingStock_" + rollingStock.getIndex()
                        + "_" + cellEdge.getName());
                String name = getRs2EdgeVarName(rollingStock, cellEdge);
                xVars.put(name, var);
            }
        }
    }


    private void createTrackVariable(ProblemContext problemContext, CellGraph graph,
                                     GRBModel solver) throws GRBException {
        for (Schedule schedule : graph.getScheduleList()) {
            for (int i = 0; i < CellGraph.getNodeList(schedule).size(); ++i) {
                Node node = CellGraph.getNodeList(schedule).get(i);
                String scheduleNodeStr = CellGraph.getScheduleNodeStr(schedule, i);
                if (!graph.getScheduleAndNode2Tracks().containsKey(scheduleNodeStr)) {
                    continue;
                }
                Set<Track> tracks = graph.getScheduleAndNode2Tracks().get(scheduleNodeStr);
                GRBVar[] vars = new GRBVar[node.getTracks().size()];
                for (int k = 0; k < node.getTracks().size(); ++k) {
                    Track track = node.getTracks().get(k);
                    if (!tracks.contains(track)) {
                        continue;
                    }
                    vars[k] = solver.addVar(0, 1, 0, GRB.BINARY, "Track_" + track + "_" + scheduleNodeStr);
                }
                yVars.put(scheduleNodeStr, vars);
            }
        }
    }

    private void createObjective(ProblemContext problemContext, CellGraph graph) {
        addSkipPenalty(problemContext, graph);
        addDelayPenalty(problemContext, graph);
        addFrequencyPenalty();
    }

    private void addFrequencyPenalty() {
        for (GRBVar variable : hVars.values()) {
            objective.addTerm(Constants.FREQUENCY_PENALTY / Constants.SECONDS_IN_MINUTE, variable);
        }
    }

    private void addSkipPenalty(ProblemContext problemContext, CellGraph graph) {
        for (Map.Entry<Schedule, List<CellVertex>> item : graph.getSchedule2SkipPenaltyVertexList().entrySet()) {
            for (CellVertex cellVertex : item.getValue()) {
                if (!bsvRankingVar.containsKey(cellVertex.getName())) {
                    continue;
                }
                GRBVar[] mpVars = bsvRankingVar.get(cellVertex.getName());
                objective.addTerm(Constants.FIRST_SKIP_STOP_MULTIPLIER * cellVertex.getBsv(), mpVars[0]);
                objective.addTerm(Constants.SECOND_SKIP_STOP_MULTIPLIER * cellVertex.getBsv(), mpVars[1]);
                objective.addTerm(cellVertex.getBsv(), mpVars[2]);
            }
        }
    }

    private void addDelayPenalty(ProblemContext problemContext, CellGraph graph) {
        for (Schedule schedule : graph.getScheduleList()) {
            if (CellGraph.getNodeList(schedule).isEmpty() || !scheduleDelayVar.containsKey(schedule)) {
                continue;
            }
            GRBVar delayVar = scheduleDelayVar.get(schedule);
            if (delayVar != null) {
                objective.addTerm(DELAY_PENALTY_MULTIPLIER * Constants.DELAY_PENALTY / Constants.SECONDS_IN_MINUTE,
                        delayVar);
            }
        }
    }

    private int getCurrentTime(ProblemContext problemContext) {
        List<RealizedScheduleScenario> realizedScheduleScenarios =
                problemContext.getScenario().getRealizedScheduleScenarios();
        RealizedScheduleScenario maxRealizedArrival = realizedScheduleScenarios.stream().
                max(Comparator.comparingInt(RealizedScheduleScenario::getArrivalSeconds)).orElse(null);
        RealizedScheduleScenario maxRealizedDeparture = realizedScheduleScenarios.stream().
                max(Comparator.comparingInt(RealizedScheduleScenario::getDepartureSeconds)).orElse(null);
        return Math.max(maxRealizedArrival == null ? 0 : maxRealizedArrival.getArrivalSeconds(),
                maxRealizedDeparture == null ? 0 : maxRealizedDeparture.getDepartureSeconds());
    }
}
