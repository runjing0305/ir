package model.scmodel;

import constant.Constants;
import context.ProblemContext;
import graph.rsdgraph.RollingStockDutyEdge;
import graph.rsdgraph.RollingStockDutyVertex;
import graph.scgraph.SingleCommodityGraph;
import gurobi.*;

/**
 * @description:
 * @author: Shengcheng Shao
 * @date: 2022/7/28
 */
public class SCConstraint {
    SCVariable var;
    public SCConstraint(SCVariable var) {
        this.var = var;
    }

    public void createCons(ProblemContext problemContext, SingleCommodityGraph graph, GRBModel solver) throws GRBException {
        RollingStockDutyVertex startVertex = graph.getName2Vertex().get(Constants.VIRTUAL_START_VERTEX_NAME);
        GRBLinExpr expr = new GRBLinExpr();
        for (RollingStockDutyEdge edge : startVertex.getOutArcList()) {
            double coef = 1;
            expr.addTerm(coef, var.getXVars()[edge.getIndex()]);
        }
        solver.addConstr(expr, GRB.EQUAL, problemContext.getRollingStocks().size(), "source cons");

        RollingStockDutyVertex endVertex = graph.getName2Vertex().get(Constants.VIRTUAL_END_VERTEX_NAME);
        GRBLinExpr expr2 = new GRBLinExpr();
        for (RollingStockDutyEdge edge : endVertex.getInArcList()) {
            expr2.addTerm(1, var.getXVars()[edge.getIndex()]);
        }
        solver.addConstr(expr2, GRB.EQUAL,problemContext.getRollingStocks().size(), "sink cons");

        for (RollingStockDutyVertex vertex : graph.getVertexList()) {
            if (vertex.isVirtual()) {
                continue;
            }
            GRBLinExpr expr3 = new GRBLinExpr();
            for (RollingStockDutyEdge edge : vertex.getInArcList()) {
                expr3.addTerm(1, var.getXVars()[edge.getIndex()]);
            }
            for (RollingStockDutyEdge edge : vertex.getOutArcList()) {
                expr3.addTerm(-1 ,var.getXVars()[edge.getIndex()]);
            }
            if (vertex.getInArcList().isEmpty()) {
                System.out.println(vertex.getName() + " has not in arcs");
            }
            if (vertex.getOutArcList().isEmpty()) {
                System.out.println(vertex.getName() + " has not out arcs");
            }
            solver.addConstr(expr3, GRB.EQUAL, 0, vertex.getName() + " flow balance cons");

            GRBLinExpr expr4 = new GRBLinExpr();
            for (RollingStockDutyEdge edge : vertex.getInArcList()) {
                expr4.addTerm(1, var.getXVars()[edge.getIndex()]);
            }
            expr4.addTerm(1, var.getYVars()[vertex.getIndex()]);
            solver.addConstr(expr4, GRB.EQUAL, 1, vertex.getName() + " usage cons");
        }
    }
}
