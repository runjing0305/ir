package reschedule.model;


import context.*;
import reschedule.graph.*;
import gurobi.*;
import graph.Vertex;
import solution.Solution;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class CourseLevelModel {
    public final boolean ENABLE_SOLVER_LOG = true;
    private ProblemContext problemContext;
    private CellGraph graph;
    private GRBEnv grbEnv;
    private GRBModel solver;
    private CourseLevelVariable var;
    private CourseLevelConstraint cons;

    public CourseLevelModel(ProblemContext problemContext, CellGraph graph) {
        this.problemContext = problemContext;
        this.graph = graph;
        try {
            grbEnv = new GRBEnv("CourseLevel.log");
            solver = new GRBModel(grbEnv);
            this.var = new CourseLevelVariable();
            this.cons = new CourseLevelConstraint(var);
        } catch (GRBException e) {
            e.printStackTrace();
        }
    }

    public void build() {
        try {
            createVars();
            createCons();
        } catch (GRBException e) {
            e.printStackTrace();
        }
    }

    public void createVars() throws GRBException {
        if (var == null) {
            return;
        }
        var.createVars(problemContext, graph, solver);
    }

    public void createCons() throws GRBException {
        if (cons == null) {
            return;
        }
        cons.createCons(problemContext, graph, solver);
    }

    public int solve(Solution solution) {
        int status = -1;
        try {
            cons.fixStationStatus(graph, solver, solution);
            solver.set(GRB.DoubleParam.MIPGap, 0.01);
            solver.optimize();
            status = solver.get(GRB.IntAttr.Status);
            if (status == GRB.Status.OPTIMAL) {
                getSolution(solution);
            }
        } catch (GRBException e) {
            e.printStackTrace();
        }
        return status;
    }

    private void getSolution(Solution solution) throws GRBException {
        updateStationSchedule(solution);
        updateDestinationDelay(solution);
        checkSolValid(solution);
    }


    private void updateStationSchedule(Solution solution) throws GRBException {
        for (Schedule schedule : graph.getScheduleList()) {
            RollingStock rollingStock = solution.getSchedule2RollingStockMap().get(schedule);
            for (int seq = 0; seq < CellGraph.getNodeList(schedule).size(); ++seq) {
                String name = CellGraph.getScheduleNodeStr(schedule, seq);
                GRBVar arrivalVar = var.getAVars().get(name);
                int arrivalTime = (int) arrivalVar.get(GRB.DoubleAttr.X);
                GRBVar departureVar = var.getDVars().get(name);
                int departureTime = (int) departureVar.get(GRB.DoubleAttr.X);
                Set<CellVertex> vertices = graph.getScheduleAndNode2Vertices().get(name);
                boolean skip = getSkipStationStatus(vertices, rollingStock);
                solution.getScheduleSkipStationMap().get(schedule).set(seq, skip);
                solution.getScheduleStationArrivalTimeMap().get(schedule).set(seq, arrivalTime);
                solution.getScheduleStationDepartureTimeMap().get(schedule).set(seq, departureTime);
            }
        }
    }

    private boolean getSkipStationStatus(Set<CellVertex> vertices, RollingStock rollingStock) throws GRBException {
        for (CellVertex vertex : vertices) {
            for (CellEdge edge : vertex.getInArcList()) {
                GRBVar xVar = var.getXVar(rollingStock, edge);
                if (xVar == null) {
                    System.out.println("RollingStock" + rollingStock.toString() + "to Edge "
                            + edge.getName() + "var is null ");
                    continue;
                }
                if (xVar.get(GRB.DoubleAttr.X) > 1e-6) {
                    return vertex.getType().equals(Vertex.Type.PASS);
                }
            }
        }
        return false;
    }

    private void updateDestinationDelay(Solution solution) {
        for (Map.Entry<Schedule, List<Integer>> item : solution.getScheduleStationArrivalTimeMap().entrySet()) {
            if (item.getValue().isEmpty()) {
                continue;
            }
            int planTime = item.getKey().getEndTime();
            int reScheduleTime = item.getValue().get(item.getValue().size() - 1);
            int delayTime = Math.max(reScheduleTime - planTime, 0);
            solution.getScheduleDestinationDelayMap().put(item.getKey(), delayTime);
        }
    }

    private void checkSolValid(Solution solution) {
        for (Schedule schedule : solution.getSchedule2RollingStockMap().keySet()) {
            if (!solution.getScheduleSkipStationMap().containsKey(schedule)) {
                System.out.println("solution.getScheduleSkipStationMap do not containsKey " + schedule.getCourseId());
                return;
            }
            if (!solution.getScheduleStationArrivalTimeMap().containsKey(schedule)) {
                System.out.println("solution.getScheduleStationArrivalTimeMap do not containsKey "
                        + schedule.getCourseId());
                return;
            }

            if (!solution.getScheduleStationDepartureTimeMap().containsKey(schedule)) {
                System.out.println("solution.getScheduleStationDepartureTimeMap do not containsKey "
                        + schedule.getCourseId());
                return;
            }
            for (int i = 0; i < CellGraph.getNodeList(schedule).size(); ++i) {
                boolean skip = solution.getScheduleSkipStationMap().get(schedule).get(i);
                if (i == 0 || i == CellGraph.getNodeList(schedule).size() - 1) {
                    continue;
                }
                int arrivalTime = solution.getScheduleStationArrivalTimeMap().get(schedule).get(i);
                int departureTime = solution.getScheduleStationDepartureTimeMap().get(schedule).get(i);
                if (arrivalTime > departureTime) {
                    System.out.println("node " + i + " " + CellGraph.getNodeList(schedule).get(i).getCode() +
                            " of schedule " + schedule.getCourseId() + " arrivalTime is later than departureTime "
                            + arrivalTime + " " + departureTime);
                    return;
                }
            }
        }
    }
}
