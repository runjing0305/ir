package model.mcmodel;

import constant.Constants;
import context.ProblemContext;
import context.Schedule;
import graph.rsdgraph.RollingStockDutyEdge;
import graph.scgraph.MultiCopyGraph;
import gurobi.*;
import lombok.Getter;
import lombok.Setter;
import solution.Solution;
import solution.SolutionEvaluator;

import java.util.*;

/**
* @description:
* @author: Shengcheng Shao
* @date: 2022/8/5
*/
@Getter
@Setter
public class MCVariable {
    private GRBVar[] edgeVars;
    private Map<Schedule, GRBVar> yVars;

    public void createVars(ProblemContext context, MultiCopyGraph graph, GRBModel solver) throws GRBException {
        GRBLinExpr objExpr = new GRBLinExpr();
        edgeVars = new GRBVar[graph.getEdges().size()];
        for (RollingStockDutyEdge edge : graph.getEdges()) {
            if (edge.getHead().isVirtual() && edge.getTail().isVirtual()) {
                edgeVars[edge.getIndex()] = solver.addVar(0, Double.POSITIVE_INFINITY,0, GRB.INTEGER,
                        edge.getName() + " var");
            } else if (graph.getRealizedEdges().contains(edge)) {
                edgeVars[edge.getIndex()] = solver.addVar(1, 1,0, GRB.CONTINUOUS,
                        edge.getName() + " var");
            } else {
                edgeVars[edge.getIndex()] = solver.addVar(0, 1,0, GRB.BINARY,
                        edge.getName() + " var");
            }
            // 通过调整目标函数系数，我们可以尽量避免让Course Delay太多
            double coef = 0;
            if (!edge.getTail().isVirtual() && !graph.getRealizedEdges().contains(edge)
                    && edge.getTail().getEndTime() - edge.getTail().getOrigCourse().getEndTime() >= 180) {
                coef = (edge.getTail().getEndTime() - edge.getTail().getOrigCourse().getEndTime()) *
                        Constants.DELAY_PENALTY / Constants.SECONDS_IN_MINUTE;
            }
            objExpr.addTerm(coef, edgeVars[edge.getIndex()]);
        }

        yVars = new HashMap<>();
        for (int j = 0; j < context.getSchedules().size(); j++) {
            Schedule schedule = context.getSchedules().get(j);
            GRBVar skipVar;
            if (!schedule.getRealizedNodes().isEmpty()
                    && schedule.getRealizedEnterTimes().get(schedule.getRealizedNodes().size()) != 0) {
                skipVar = solver.addVar(0,0,0, GRB.CONTINUOUS, schedule.getCourseId() + " skip var");
            } else {
                skipVar = solver.addVar(0,1,0, GRB.BINARY, schedule.getCourseId() + " skip var");
            }
            yVars.put(schedule, skipVar);
            SolutionEvaluator se = new SolutionEvaluator(context);
            Solution tempSol = new Solution(graph.getCurSol());
            List<Boolean> skipStations = new ArrayList<>(graph.getCurSol().getScheduleSkipStationMap()
                    .get(schedule));
            Collections.fill(skipStations, Boolean.TRUE);
            tempSol.getScheduleSkipStationMap().put(schedule, skipStations);
            double penalty = se.calcSkipStationPenalty(tempSol, schedule);
            objExpr.addTerm(penalty, skipVar);
        }
        solver.setObjective(objExpr);
    }
}
