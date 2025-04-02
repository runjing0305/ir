package model.mcmodel;

import constant.Constants;
import context.*;
import graph.rsdgraph.RollingStockDutyEdge;
import graph.rsdgraph.RollingStockDutyVertex;
import graph.scgraph.MultiCopyGraph;
import gurobi.*;
import solution.Solution;

import java.util.*;

/**
 * @description:
 * @author: Shengcheng Shao
 * @date: 2022/8/5
 */
public class MCConstraint {
    MCVariable var;

    public MCConstraint(MCVariable var) {
        this.var = var;
    }
    public void createCons(ProblemContext problemContext, MultiCopyGraph graph, GRBModel solver) throws GRBException {
        RollingStockDutyVertex startVertex = graph.getName2Vertex().get(Constants.VIRTUAL_START_VERTEX_NAME);
        GRBLinExpr expr = new GRBLinExpr();
        for (RollingStockDutyEdge edge : startVertex.getOutArcList()) {
            double coef = 1;
            expr.addTerm(coef, var.getEdgeVars()[edge.getIndex()]);
        }
        solver.addConstr(expr, GRB.EQUAL, problemContext.getRollingStocks().size(), "source cons");

        RollingStockDutyVertex endVertex = graph.getName2Vertex().get(Constants.VIRTUAL_END_VERTEX_NAME);
        GRBLinExpr expr2 = new GRBLinExpr();
        for (RollingStockDutyEdge edge : endVertex.getInArcList()) {
            expr2.addTerm(1, var.getEdgeVars()[edge.getIndex()]);
        }
        solver.addConstr(expr2, GRB.EQUAL,problemContext.getRollingStocks().size(), "sink cons");

        for (RollingStockDutyVertex vertex : graph.getVertexList()) {
            if (vertex.isVirtual()) {
                continue;
            }
            GRBLinExpr expr3 = new GRBLinExpr();
            for (RollingStockDutyEdge edge : vertex.getInArcList()) {
                expr3.addTerm(1, var.getEdgeVars()[edge.getIndex()]);
            }
            for (RollingStockDutyEdge edge : vertex.getOutArcList()) {
                expr3.addTerm(-1 ,var.getEdgeVars()[edge.getIndex()]);
            }
            if (vertex.getInArcList().isEmpty()) {
                System.out.println(vertex.getName() + " has not in arcs");
            }
            if (vertex.getOutArcList().isEmpty()) {
                System.out.println(vertex.getName() + " has not out arcs");
            }
            solver.addConstr(expr3, GRB.EQUAL, 0, vertex.getName() + " flow balance cons");
        }

        for (int j = 0; j < problemContext.getSchedules().size(); j++) {
            Schedule schedule = problemContext.getSchedules().get(j);
            GRBLinExpr expr4 = new GRBLinExpr();
            for (int change : Constants.COURSE_START_TIME_CHANGE) {
                RollingStockDutyVertex vertex = graph.getName2Vertex().get(schedule.getCourseId() + "_" + change);
                if (vertex == null) {
                    continue;
                }
                for (RollingStockDutyEdge edge : vertex.getInArcList()) {
                    expr4.addTerm(1, var.getEdgeVars()[edge.getIndex()]);
                }
            }
            expr4.addTerm(1, var.getYVars().get(schedule));
            solver.addConstr(expr4, GRB.EQUAL, 1, schedule.getCourseId() + " usage cons");
        }

    }

    public static void changeSol(Solution tempSol, Schedule schedule, int change) {
        List<Integer> arrivals = new ArrayList<>(tempSol.getScheduleStationArrivalTimeMap().get(schedule));
        List<Integer> departures = new ArrayList<>(tempSol.getScheduleStationDepartureTimeMap().get(schedule));
        for (int i = 0; i < arrivals.size(); i++) {
            if (arrivals.get(i) != null) {
                arrivals.set(i, arrivals.get(i) + change);
            }
        }
        for (int i = 0; i < departures.size(); i++) {
            if (departures.get(i) != null) {
                departures.set(i, departures.get(i) + change);
            }
        }
        tempSol.getScheduleStationArrivalTimeMap().put(schedule, arrivals);
        tempSol.getScheduleStationDepartureTimeMap().put(schedule, departures);
    }
}
