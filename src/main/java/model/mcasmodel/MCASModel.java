package model.mcasmodel;

import constant.Constants;
import context.ProblemContext;
import context.Schedule;
import graph.rsdgraph.RollingStockDutyEdge;
import graph.rsdgraph.RollingStockDutyVertex;
import graph.scgraph.MultiCopyGraph;
import gurobi.GRB;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBModel;
import model.Build;
import model.Model;
import model.mcmodel.MCConstraint;
import model.mcmodel.MCVariable;
import solution.Solution;

import java.util.*;

/**
 * @description:
 * @author: Shengcheng Shao
 * @date: 2022/8/9
 */
public class MCASModel extends Model implements Build {
    private ProblemContext context;
    private MultiCopyGraph graph;
    private MCASVariable var;
    private MCASConstraint cons;
    private int resultStatus;
    private long elapsedTime;
    private Solution oldSol;

    public MCASModel(ProblemContext context, MultiCopyGraph graph, Solution oldSol) throws GRBException {
        super();
        this.context = context;
        this.graph = graph;
        this.env = new GRBEnv("RAS.log");
        this.solver = new GRBModel(env);
        this.oldSol = oldSol;
    }
    @Override
    public void createVars() throws GRBException {
        var = new MCASVariable();
        var.createVars(context, graph, solver);
    }

    @Override
    public void createCons() throws GRBException {
        cons = new MCASConstraint(var);
        cons.createCons(context, graph, solver);
    }

    @Override
    public Solution genSol() throws GRBException {
        Solution solution = new Solution(oldSol);
        solution.setSchedule2RollingStockMap(new HashMap<>());
        solution.setRollingStock2ScheduleListMap(new HashMap<>());
        solution.setResultStatus(resultStatus);
        solution.setElapsedTime(elapsedTime);

        Map<Schedule, Schedule> nextMap = new HashMap<>();
        Set<Schedule> startSchedules = new HashSet<>();
        Map<Schedule, Integer> changeMap = new HashMap<>();
        for (RollingStockDutyEdge edge : graph.getEdges()) {
            if (var.getEdgeVars()[edge.getIndex()].get(GRB.DoubleAttr.X) > 1e-6) {
                solution.getNonZeroValMap().put(var.getEdgeVars()[edge.getIndex()].get(GRB.StringAttr.VarName),
                        var.getEdgeVars()[edge.getIndex()].get(GRB.DoubleAttr.X));
                RollingStockDutyVertex headVertex = edge.getHead();
                Schedule headSchedule = headVertex.getOrigCourse();
                RollingStockDutyVertex tailVertex = edge.getTail();
                Schedule tailSchedule = tailVertex.getOrigCourse();
                if (headSchedule != null && tailSchedule != null) {
                    nextMap.put(headSchedule, tailSchedule);
                    changeMap.put(tailSchedule, tailVertex.getChange());
                } else if (tailSchedule != null) {
                    startSchedules.add(tailSchedule);
                    changeMap.put(tailSchedule, tailVertex.getChange());
                } else if (headSchedule == null) {
                    if (Constants.OUTPUT_FLAG) {
                        System.out.println("Virtual edge train num: " +
                                var.getEdgeVars()[edge.getIndex()].get(GRB.DoubleAttr.X));
                    }
                }
            }
        }

        for (Map.Entry<Schedule, Integer> entry : changeMap.entrySet()) {
            Schedule schedule = entry.getKey();
            int change = entry.getValue();
            int xIndex = 0;
            for (; xIndex < Constants.COURSE_START_TIME_CHANGE.length; xIndex++) {
                if (Constants.COURSE_START_TIME_CHANGE[xIndex] == change) {
                    break;
                }
            }
            if (Constants.OUTPUT_FLAG && var.getXVars().get(entry.getKey()).get(xIndex).get(GRB.DoubleAttr.X) <= 1e-6) {
                System.out.println(schedule.getCourseId() + "_" + change + " is not allowed!");
            }
            MCConstraint.changeSol(solution, schedule, change);
        }

        Map<Schedule, List<Schedule>> pathMap = new HashMap<>();
        for (Schedule schedule : startSchedules) {
            List<Schedule> path = new ArrayList<>();
            path.add(schedule);
            pathMap.put(schedule, path);
            Schedule cur = schedule;
            while (nextMap.containsKey(cur)) {
                cur = nextMap.get(cur);
                path.add(cur);
            }
            pathMap.put(schedule, path);
        }
        if (Constants.OUTPUT_FLAG) {
            for (int j = 0; j < context.getSchedules().size(); j++) {
                Schedule schedule = context.getSchedules().get(j);
                if (var.getYVars().get(schedule).get(GRB.DoubleAttr.X) > 1e-6) {
                    System.out.println(schedule.getCourseId() + " with category: " +
                            schedule.getCategory() + " is not finished");
                    solution.getSchedule2RollingStockMap().remove(schedule);
                }
            }
        }
        int rsIndex = 0;
        for (Map.Entry<Schedule, List<Schedule>> pathEntry : pathMap.entrySet()) {
            solution.getRollingStock2ScheduleListMap().put(context.getRollingStocks().get(rsIndex),
                    pathEntry.getValue());
            if (Constants.OUTPUT_FLAG) {
                System.out.print(rsIndex + ",");
                for (int i = 0; i < pathEntry.getValue().size(); i++) {
                    System.out.print(pathEntry.getValue().get(i).getCourseId());
                    if (i < pathEntry.getValue().size() - 1) {
                        System.out.print("->");
                    } else {
                        System.out.println();
                    }
                }
            }
            for (Schedule subSchedule : pathEntry.getValue()) {
                solution.getSchedule2RollingStockMap().put(subSchedule, context.getRollingStocks().get(rsIndex));
            }
            rsIndex++;
        }
        return solution;
    }
}
