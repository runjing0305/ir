package solver;

import context.ProblemContext;
import context.Schedule;
import dataprocess.ContextFactory;
import datareader.DataReader;
import graph.Graph;
import gurobi.GRBException;
import model.Model;
import solution.Solution;

import java.util.Comparator;

/**
 * TestMipSolver （功能简述）
 * 功能详细描述
 *
 * @author s00536729
 * @since 2022-06-17
 */
public class TestMipSolver {
    public static void main(String[] args) throws GRBException {
        DataReader reader = new DataReader();
        ContextFactory contextFactory = new ContextFactory();
        ProblemContext problemContext = contextFactory.build(reader);
        contextFactory.update(problemContext);
        contextFactory.filter(problemContext);
        problemContext.getSchedules().sort(Comparator.comparingInt(Schedule::getStartTime));
        problemContext.setSchedules(problemContext.getSchedules().subList(0, 10));
        Graph graph = new Graph(problemContext);
        Model model = new Model(problemContext, graph);
        model.createVars();
        model.createCons();
        MipSolver solver = new MipSolver();
        Solution solution = solver.solve(model);
        Solution.printSolInfo(solution, problemContext);
        System.out.println("MIP solver works!");
    }
}
