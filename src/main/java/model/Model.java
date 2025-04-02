package model;

import constant.Constants;
import context.ProblemContext;
import context.RollingStock;
import context.Schedule;
import graph.Edge;
import graph.Graph;
import gurobi.GRB;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBModel;
import lombok.Getter;
import lombok.Setter;
import solution.Solution;

import java.util.ArrayList;
import java.util.List;

/**
 * Model （模型）
 * 创建模型
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class Model implements Build {
    private ProblemContext problemContext;
    private Graph graph;

    protected GRBEnv env;
    protected GRBModel solver;
    private Variable var;
    private Constraint cons;
    protected int resultStatus;
    protected long elapsedTime;

    /**
     * 基于问题情景和图构造模型
     *
     * @param problemContext 问题情景
     * @param graph 图
     * @throws GRBException GUROBI异常
     */
    public Model(ProblemContext problemContext, Graph graph) throws GRBException {
        this.problemContext = problemContext;
        this.graph = graph;
        this.env = new GRBEnv("RAS.log");
        this.solver = new GRBModel(env);
    }

    public Model() {

    }

    /**
     * 创建变量
     *
     * @throws GRBException GUROBI异常
     */
    @Override
    public void createVars() throws GRBException {
        var = new Variable();
        var.createVars(problemContext, graph, solver);
    }

    /**
     * 创建约束
     *
     * @throws GRBException GUROBI异常
     */
    @Override
    public void createCons() throws GRBException {
        cons = new Constraint(var);
        cons.createCons(problemContext, graph, solver);
    }

    /**
     * 基于模型求解状态和求解时间生成 解
     *
     * @return Solution 问题解
     * @throws GRBException GUROBI异常
     */
    public Solution genSol() throws GRBException {
        Solution solution = new Solution();
        solution.setResultStatus(resultStatus);
        solution.setElapsedTime(elapsedTime);
        if (resultStatus == GRB.INFEASIBLE) {
            return solution;
        }
        for (int i = 0; i < problemContext.getRollingStocks().size(); i++) {
            RollingStock rollingStock = problemContext.getRollingStocks().get(i);
            for (Edge edge : graph.getEdgeList()) {
                if (var.getXVars()[i][edge.getIndex()].get(GRB.DoubleAttr.X) > 1e-6 && !edge.getTail().isVirtual()) {
                    String courseId = edge.getTail().getName().split("_")[0];
                    Schedule schedule = problemContext.getCourseId2Schedule().get(courseId);
                    solution.getSchedule2RollingStockMap().put(schedule, rollingStock);
                }
            }
        }
        for (int i = 0; i < problemContext.getSchedules().size(); i++) {
            Schedule schedule = problemContext.getSchedules().get(i);
            List<Boolean> skipStations = new ArrayList<>();
            List<Integer> arrivalTimes = new ArrayList<>();
            List<Integer> departureTimes = new ArrayList<>();
            for (int j = 0; j < schedule.getPlannedNodes().size(); j++) {
                if (var.getYVars().get(schedule).get(j).get(GRB.DoubleAttr.X) <= 1e-5) {
                    skipStations.add(Boolean.FALSE);
                } else if (var.getYVars().get(schedule).get(j).get(GRB.DoubleAttr.X) >= 1 - 1e-5) {
                    skipStations.add(Boolean.TRUE);
                } else {
                    System.out.println(var.getYVars().get(schedule).get(j).get(GRB.DoubleAttr.X));
                }
                arrivalTimes.add((int) Math.round(var.getAVars().get(schedule).get(j).get(GRB.DoubleAttr.X)));
                departureTimes.add((int) Math.round(var.getDVars().get(schedule).get(j).get(GRB.DoubleAttr.X)));
            }
            solution.getScheduleSkipStationMap().put(schedule, skipStations);
            solution.getScheduleStationArrivalTimeMap().put(schedule, arrivalTimes);
            solution.getScheduleStationDepartureTimeMap().put(schedule, departureTimes);
            solution.getScheduleDestinationDelayMap().put(schedule, (int) Math.round(var.getZVars()[i].
                    get(GRB.DoubleAttr.X)));
        }
        solution.updateObjectValue();
        return solution;
    }
}
