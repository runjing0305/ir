package reschedule;

import constant.Constants;
import context.Node;
import context.ProblemContext;
import context.Schedule;
import context.Track;
import dataprocess.ContextFactory;
import datareader.DataReader;
import eventbased.graph.Graph;
import eventbased.graph.NodeGraph;
import eventbased.model.EventBasedModel;
import eventbased.solver.EventBasedMainSolver;
import graph.scgraph.SingleCommodityGraph;
import gurobi.GRBException;
import model.assignmodel.AssignModel;
import model.scmodel.SCModel;
import org.apache.commons.lang3.tuple.Pair;
import reschedule.solver.MipRescheduleSolver;
import reschedule.solver.RescheduleSolver;
import solution.Solution;
import solution.SolutionGenerator;
import solution.modifier.SolutionCourseModifier;
import solutionwriter.SolutionWriter;
import solver.MipSolver;
import util.AmendmentsXmlWriter;

import java.util.*;

public class TestMipRescheduleSolver {
    public static void main(String[] args) throws GRBException {
        long starTime = System.currentTimeMillis();
        DataReader reader = new DataReader();
        ContextFactory contextFactory = new ContextFactory();
        ProblemContext problemContext = contextFactory.build(reader);
        contextFactory.update(problemContext);
        contextFactory.filter(problemContext);
        String problemId = problemContext.getProblemId();
        problemContext.getSchedules().sort(Comparator.comparingInt(Schedule::getStartTime));
        SolutionGenerator generator = new SolutionGenerator(problemContext);
        Solution solution = generator.generate();
        contextFactory.loadCourseSol(reader, solution, problemContext);
        generator.updateSkipStatus(solution);
        Pair<Integer, Integer> courseNum = solution.getCourseNum();
        System.out.println("EE: " + courseNum.getLeft() + "; OO: " + courseNum.getRight());

        SingleCommodityGraph graph = new SingleCommodityGraph(problemContext, solution);
        SCModel model = new SCModel(problemContext, graph, solution);
        model.createVars();
        model.createCons();
        MipSolver mipSolver = new MipSolver();
        Solution newSolution = mipSolver.solve(model);
        Solution.printSolInfo(newSolution, problemContext);

        courseNum = newSolution.getCourseNum();
        System.out.println("EE: " + courseNum.getLeft() + "; OO: " + courseNum.getRight());

        SolutionWriter writer = new SolutionWriter();
        writer.output(problemContext, newSolution, String.join("_", problemId, "init", "sol.xlsx"));


        if (Constants.GENERATE_DUTY_FOR_EACH_ROLLING_STOCK) {
            newSolution.generateDutyList();
        }
        long eventBasedModelStartTime = System.currentTimeMillis();
        Set<String> updatedCourses = new HashSet<>(problemContext.getCourseId2Schedule().keySet());
        double prevIterDuration = 0.0;
        double prevObj = 0.0;
        boolean noImprovement = false;
        for (int i = 0; i < 10; ++i) {
            long currentIterStartTime = System.currentTimeMillis();
            Graph eventBasedModelGraph = new Graph();
            if (i == 1) {
                eventBasedModelGraph.setDeltaTime(100);
            } else if (i == 2) {
                eventBasedModelGraph.setDeltaTime(60);
            } else if (i >= 3) {
                if (noImprovement) {
                    eventBasedModelGraph = new Graph(3, 1, 60);
                }
                eventBasedModelGraph.setDeltaTime(30);
            }
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
            newSolution = eventBasedMainSolver.solve(problemContext, eventBasedModelGraph, nodeGraphList, eventBasedModel, newSolution, updatedCourses);
            updatedCourses = new HashSet<>(problemContext.getCourseId2Schedule().keySet());
            Solution.printSolInfo(newSolution, problemContext);
            double currentObj = newSolution.getObjValue();
            if (Math.abs(currentObj - prevObj) < 1e-6) {
                noImprovement = true;
            } else {
                noImprovement = false;
            }
            prevObj = currentObj;
            courseNum = newSolution.getCourseNum();
            System.out.println("EE: " + courseNum.getLeft() + "; OO: " + courseNum.getRight());

            writer.output(problemContext, newSolution, String.join("_", problemId, "event_based", String.valueOf(i), "sol.xlsx"));
            long currentIterEndTime = System.currentTimeMillis();
            prevIterDuration = (currentIterEndTime - currentIterStartTime) / 1000.0;
        }
        long eventBasedModelEndTime = System.currentTimeMillis();
        System.out.println("Event based model solving time: " + (eventBasedModelEndTime - eventBasedModelStartTime) / 1000.0);

        RescheduleSolver solver = new MipRescheduleSolver();
        Solution newSol = solver.solve(problemContext, newSolution);
        Solution.printSolInfo(newSol, problemContext);
        System.out.println("RescheduleSolver solver success");

        courseNum = newSol.getCourseNum();
        System.out.println("EE: " + courseNum.getLeft() + "; OO: " + courseNum.getRight());

        writer.output(problemContext, newSol, String.join("_", problemId, "rescheduler", "sol.xlsx"));

        long endTime = System.currentTimeMillis();
        System.out.println("Total solving time: " + (endTime - starTime) / 1000.0);

        AmendmentsXmlWriter amendmentsXmlWriter = new AmendmentsXmlWriter();
        amendmentsXmlWriter.writeAmendments(problemContext, newSol, problemId + "_amendments.xml");
    }
}
