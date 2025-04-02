package model;

import context.ProblemContext;
import context.Schedule;
import dataprocess.ContextFactory;
import datareader.DataReader;
import graph.scgraph.MultiCopyGraph;
import graph.scgraph.SingleCommodityGraph;
import gurobi.GRBException;
import model.mcmodel.MCModel;
import model.scmodel.SCModel;
import solution.Solution;
import solution.SolutionGenerator;
import solution.modifier.SolutionCourseModifier;
import solver.MipSolver;

import java.util.Comparator;

/**
 * @description:
 * @author: Shengcheng Shao
 * @date: 2022/8/8
 */
public class TestMCModel {
    public static void main(String[] args) throws GRBException {
        DataReader reader = new DataReader();
        ContextFactory contextFactory = new ContextFactory();
        ProblemContext problemContext = contextFactory.build(reader);
        contextFactory.update(problemContext);
        contextFactory.filter(problemContext);
        problemContext.getSchedules().sort(Comparator.comparingInt(Schedule::getStartTime));
        SolutionGenerator generator = new SolutionGenerator(problemContext);
        Solution solution = generator.generate();
        SolutionCourseModifier modifier = new SolutionCourseModifier(problemContext);
        modifier.modify(solution);
        MultiCopyGraph graph = new MultiCopyGraph(problemContext, solution);
        MCModel model = new MCModel(problemContext, graph, solution);
        model.createVars();
        model.createCons();
        MipSolver solver = new MipSolver();
        Solution newSolution = solver.solve(model);
        Solution.printSolInfo(newSolution, problemContext);
        System.out.println("Multi copy model works");
    }
}
