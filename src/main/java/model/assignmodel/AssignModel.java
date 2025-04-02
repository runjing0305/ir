package model.assignmodel;

import constant.Constants;
import context.ProblemContext;
import context.Schedule;
import graph.rsdgraph.RollingStockDutyEdge;
import graph.rsdgraph.RollingStockDutyVertex;
import graph.scgraph.MultiCopyGraph;
import gurobi.*;
import model.Build;
import model.Model;
import model.mcmodel.MCConstraint;
import model.mcmodel.MCVariable;
import solution.Solution;

import java.util.*;

/**
 * @description:
 * @author: Shengcheng Shao
 * @date: 2022/8/8
 */
public class AssignModel extends Model implements Build {
    private ProblemContext context;
    private AssignVariable var;
    private AssignConstraint cons;
    private int resultStatus;
    private long elapsedTime;
    private Solution oldSol;

    public AssignModel(ProblemContext context, Solution oldSol) throws GRBException {
        super();
        this.context = context;
        this.env = new GRBEnv("RAS.log");
        this.solver = new GRBModel(env);
        this.oldSol = oldSol;
    }


    @Override
    public void createVars() throws GRBException {
        var = new AssignVariable();
        var.createVars(context, solver, oldSol);
    }

    @Override
    public void createCons() throws GRBException {
        cons = new AssignConstraint(var);
        cons.createCons(context, solver, oldSol);
    }

    @Override
    public Solution genSol() throws GRBException {
        Solution solution = new Solution(oldSol);
        solution.setResultStatus(resultStatus);
        solution.setElapsedTime(elapsedTime);

        for (Schedule schedule : context.getSchedules()) {
            List<GRBVar> xVars = var.getXVars().get(schedule);
            for (int xIndex = 0; xIndex < xVars.size(); xIndex++) {
                GRBVar xVar = xVars.get(xIndex);
                if (xVar.get(GRB.DoubleAttr.X) > 1e-6) {
                    MCConstraint.changeSol(solution, schedule, Constants.COURSE_START_TIME_CHANGE[xIndex]);
                }
            }
        }

        if (Constants.OUTPUT_FLAG) {
            for (int j = 0; j < context.getSchedules().size(); j++) {
                Schedule schedule = context.getSchedules().get(j);
                if (var.getYVars().get(schedule).get(GRB.DoubleAttr.X) > 1e-6) {
                    System.out.println(schedule.getCourseId() + " with category: " +
                            schedule.getCategory() + " is not finished");
                    solution.getSchedule2RollingStockMap().remove(schedule);
                    List<Boolean> skipStations = new ArrayList<>(solution.getScheduleSkipStationMap().get(schedule));
                    Collections.fill(skipStations, Boolean.TRUE);
                    solution.getScheduleSkipStationMap().put(schedule, skipStations);
                }
            }
        }
        return solution;
    }
}
