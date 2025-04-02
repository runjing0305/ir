package model.scmodel;

import context.ProblemContext;
import graph.rsdgraph.RollingStockDutyEdge;
import graph.rsdgraph.RollingStockDutyVertex;
import graph.scgraph.SingleCommodityGraph;
import gurobi.*;
import lombok.Getter;
import solution.Solution;
import solution.SolutionEvaluator;

import java.util.ArrayList;
import java.util.List;

/**
 * @description:
 * @author: Shengcheng Shao
 * @date: 2022/7/28
 */
@Getter
public class SCVariable {
    private GRBVar[] xVars;
    private GRBVar[] yVars;
    public void createVars(ProblemContext problemContext, SingleCommodityGraph graph, GRBModel solver) throws GRBException {
        GRBLinExpr objExpr = new GRBLinExpr();
        xVars = new GRBVar[graph.getEdges().size()];
        for (RollingStockDutyEdge edge : graph.getEdges()) {
            if (edge.getHead().isVirtual() && edge.getTail().isVirtual()) {
                xVars[edge.getIndex()] = solver.addVar(0, Double.POSITIVE_INFINITY,0, GRB.INTEGER,
                        edge.getName() + " var");
            } else if (graph.getRealizedEdges().contains(edge)) {
                xVars[edge.getIndex()] = solver.addVar(1, 1,0, GRB.CONTINUOUS,
                        edge.getName() + " var");
            } else {
                xVars[edge.getIndex()] = solver.addVar(0, 1,0, GRB.BINARY,
                        edge.getName() + " var");
            }
            objExpr.addTerm(edge.getWeight(), xVars[edge.getIndex()]);
        }

        yVars = new GRBVar[graph.getVertexList().size()];
        for (RollingStockDutyVertex vertex : graph.getVertexList()) {
            if (vertex.isVirtual()) {
                continue;
            }
            if (!vertex.getOrigCourse().getRealizedNodes().isEmpty() && vertex.getOrigCourse().
                    getRealizedEnterTimes().get(vertex.getOrigCourse().getRealizedNodes().size()) != 0) {
                yVars[vertex.getIndex()] = solver.addVar(0,0,0, GRB.CONTINUOUS,
                        vertex.getName() + " skip var");
            } else {
                yVars[vertex.getIndex()] = solver.addVar(0,1,0, GRB.BINARY,
                        vertex.getName() + " skip var");
            }
            SolutionEvaluator se = new SolutionEvaluator(problemContext);
            Solution tempSol = new Solution(graph.getCurSol());
            List<Boolean> skipStations = new ArrayList<>(graph.getCurSol().getScheduleSkipStationMap()
                    .get(vertex.getOrigCourse()));
            for (int i = 0; i < skipStations.size(); i++) {
                skipStations.set(i, Boolean.TRUE);
            }
            tempSol.getScheduleSkipStationMap().put(vertex.getOrigCourse(), skipStations);
            double penalty = se.calcSkipStationPenalty(tempSol, vertex.getOrigCourse());
            objExpr.addTerm(penalty, yVars[vertex.getIndex()]);
        }
        solver.setObjective(objExpr);
    }
}
