package model;

import constant.Constants;
import context.ProblemContext;
import context.Schedule;
import dataprocess.ContextFactory;
import datareader.DataReader;
import graph.Graph;
import gurobi.GRB;
import gurobi.GRBException;

import java.util.Comparator;

/**
 * TestModel （功能简述）
 * 功能详细描述
 *
 * @author s00536729
 * @since 2022-06-16
 */
public class TestModel {
    public static void main(String[] args) throws GRBException {
        DataReader reader = new DataReader();
        ContextFactory contextFactory = new ContextFactory();
        ProblemContext problemContext = contextFactory.build(reader);
        contextFactory.update(problemContext);
        contextFactory.filter(problemContext);
        problemContext.getSchedules().sort(Comparator.comparingInt(Schedule::getStartTime));
        problemContext.setSchedules(problemContext.getSchedules().subList(0, 10));

//        problemContext.setRollingStocks(problemContext.getRollingStocks().subList(0, 1));
        Graph graph = new Graph(problemContext);
        Model model = new Model(problemContext, graph);
        model.createVars();
        model.createCons();
        model.getSolver().set(GRB.DoubleParam.TimeLimit, Constants.SOLVER_TIME_LIMIT);
        model.getSolver().set(GRB.DoubleParam.MIPGap, Constants.SOLVER_MIP_GAP);
        model.getSolver().set(GRB.IntParam.OutputFlag, Constants.SOLVER_VERBOSITY_LEVEL);
        model.getSolver().optimize();
        System.out.println("Build and solve model successfully!");
    }
}
