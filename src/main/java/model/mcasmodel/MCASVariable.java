package model.mcasmodel;

import constant.Constants;
import context.ProblemContext;
import context.Schedule;
import graph.rsdgraph.RollingStockDutyEdge;
import graph.scgraph.MultiCopyGraph;
import gurobi.*;
import lombok.Getter;
import solution.Solution;
import solution.SolutionEvaluator;

import java.util.*;

/**
 * @description:
 * @author: Shengcheng Shao
 * @date: 2022/8/9
 */
@Getter
public class MCASVariable {
    private Map<Schedule, List<GRBVar>> xVars = new HashMap<>();
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

        for (int i = 0; i < context.getSchedules().size(); i++) {
            Schedule schedule = context.getSchedules().get(i);
            List<GRBVar> changeVars = new ArrayList<>();
            if (!schedule.getRealizedNodes().isEmpty()
                    && schedule.getRealizedEnterTimes().get(schedule.getRealizedNodes().size()) != 0) {
                changeVars.add(solver.addVar(1,1,0, GRB.CONTINUOUS, schedule.getCourseId() +
                        "_0 var"));
            } else {
                for (int change : Constants.COURSE_START_TIME_CHANGE) {
                    changeVars.add(solver.addVar(0,1,0, GRB.BINARY, schedule.getCourseId() +
                            "_" + change + " var"));
                }
            }
            xVars.put(schedule, changeVars);
        }
        solver.setObjective(objExpr);
    }
}
