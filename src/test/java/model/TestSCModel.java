package model;

import constant.Constants;
import context.*;
import dataprocess.ContextFactory;
import datareader.DataReader;
import eventbased.graph.Graph;
import eventbased.graph.NodeGraph;
import eventbased.model.EventBasedModel;
import eventbased.solver.EventBasedMainSolver;
import graph.scgraph.SingleCommodityGraph;
import gurobi.GRBException;
import model.scmodel.SCModel;
import solution.Solution;
import solution.SolutionEvaluator;
import solution.SolutionGenerator;
import solver.MipSolver;

import java.util.*;

/**
 * @description:
 * @author: Shengcheng Shao
 * @date: 2022/8/2
 */
public class TestSCModel {
    public static void main(String[] args) throws GRBException {
        DataReader reader = new DataReader();
        ContextFactory contextFactory = new ContextFactory();
        ProblemContext problemContext = contextFactory.build(reader);
        contextFactory.update(problemContext);
        contextFactory.filter(problemContext);
        problemContext.getSchedules().sort(Comparator.comparingInt(Schedule::getStartTime));
        SolutionGenerator generator = new SolutionGenerator(problemContext);
        Solution solution = generator.generate();
        contextFactory.loadCourseSol(reader, solution, problemContext);
        SingleCommodityGraph graph = new SingleCommodityGraph(problemContext, solution);
        SCModel model = new SCModel(problemContext, graph, solution);
        model.createVars();
        model.createCons();
        MipSolver solver = new MipSolver();
        Solution newSolution = solver.solve(model);
        Solution.printSolInfo(newSolution, problemContext);
        System.out.println("Single commodity model works");

        if (Constants.GENERATE_DUTY_FOR_EACH_ROLLING_STOCK) {
            newSolution.generateDutyList();
        }
        Set<String> updatedCourses = new HashSet<>(problemContext.getCourseId2Schedule().keySet());
        Graph eventBasedModelGraph = new Graph();
        eventBasedModelGraph.build(problemContext, newSolution, updatedCourses);

        List<NodeGraph> nodeGraphList = new ArrayList<>();
        for (Node node : problemContext.getNodes()) {
            if (!Constants.WEST_BOUND_REFERENCE_STATION.equals(node.getCode()) && !Constants.EAST_BOUND_REFERENCE_STATION.equals(node.getCode())) {
                continue;
            }

            for (Track track : node.getTracks()) {
                Track.Direction direction = track.getDirection();
                if (Constants.WEST_BOUND_REFERENCE_STATION.equals(node.getCode()) && direction == Track.Direction.EB) {
                    continue;
                }

                if (Constants.EAST_BOUND_REFERENCE_STATION.equals(node.getCode()) && direction == Track.Direction.WB) {
                    continue;
                }

                NodeGraph nodeGraph = new NodeGraph(node.getCode(), track.getName(), direction);
                nodeGraph.build(problemContext, eventBasedModelGraph);

                nodeGraphList.add(nodeGraph);
            }
        }

        EventBasedModel eventBasedModel = new EventBasedModel();
        eventBasedModel.buildModel(problemContext, eventBasedModelGraph, nodeGraphList, newSolution);

        EventBasedMainSolver eventBasedMainSolver = new EventBasedMainSolver();
        updatedCourses.clear();
        Solution eventBasedModelSolution = eventBasedMainSolver.solve(problemContext, eventBasedModelGraph, nodeGraphList, eventBasedModel, newSolution, updatedCourses);
        Solution.printSolInfo(eventBasedModelSolution, problemContext);
    }
}
