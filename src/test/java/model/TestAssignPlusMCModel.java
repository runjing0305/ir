package model;

import context.ProblemContext;
import context.Schedule;
import dataprocess.ContextFactory;
import datareader.DataReader;
import graph.scgraph.MultiCopyGraph;
import graph.scgraph.SingleCommodityGraph;
import gurobi.GRBException;
import model.assignmodel.AssignModel;
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
 * @date: 2022/8/10
 */
public class TestAssignPlusMCModel {
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
        AssignModel model = new AssignModel(problemContext, solution);
        model.createVars();
        model.createCons();
        MipSolver solver = new MipSolver();
        Solution assSolution = solver.solve(model);
        Solution.printSolInfo(assSolution, problemContext);
        MultiCopyGraph graph = new MultiCopyGraph(problemContext, assSolution);
        MCModel mcModel = new MCModel(problemContext, graph, assSolution);
        mcModel.createVars();
        mcModel.createCons();
        Solution mcSolution = solver.solve(mcModel);
        Solution.printSolInfo(mcSolution, problemContext);
        System.out.println("Assign model works");
    }
}
