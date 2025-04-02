package model;

import context.ProblemContext;
import context.Schedule;
import dataprocess.ContextFactory;
import datareader.DataReader;
import graph.scgraph.MultiCopyGraph;
import gurobi.GRBException;
import model.assignmodel.AssignModel;
import model.mcmodel.MCModel;
import solution.Solution;
import solution.SolutionGenerator;
import solution.modifier.SolutionCourseModifier;
import solver.MipSolver;

import java.util.Comparator;

/**
 * @description:
 * @author: Shengcheng Shao
 * @date: 2022/8/10
 */
public class TestMCPlusAssignModel {
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
        MCModel mcModel = new MCModel(problemContext, graph, solution);
        mcModel.createVars();
        mcModel.createCons();
        MipSolver solver = new MipSolver();
        Solution mcSolution = solver.solve(mcModel);
        Solution.printSolInfo(mcSolution, problemContext);
        AssignModel model = new AssignModel(problemContext, mcSolution);
        model.createVars();
        model.createCons();
        Solution assSolution = solver.solve(model);
        Solution.printSolInfo(assSolution, problemContext);
        System.out.println("Assign model works");
    }
}
