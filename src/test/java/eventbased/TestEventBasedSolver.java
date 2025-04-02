package eventbased;

import constant.Constants;
import context.Node;
import context.ProblemContext;
import context.Track;
import dataprocess.ContextFactory;
import datareader.DataReader;
import eventbased.graph.Graph;
import eventbased.graph.NodeGraph;
import eventbased.model.EventBasedModel;
import eventbased.solprocess.SolProcessor;
import eventbased.solreader.SolReader;
import eventbased.solreader.SolRollingStockPath;
import eventbased.solreader.SolTrainSchedule;
import eventbased.solver.EventBasedMainSolver;
import solution.Solution;

import java.util.*;
import java.util.logging.Logger;

public class TestEventBasedSolver {

    private static final Logger LOGGER = Logger.getLogger(TestEventBasedSolver.class.getName());

    public static void main(String[] args) {
        LOGGER.info("Event based solver started.\n");
        DataReader reader = new DataReader();
        ContextFactory contextFactory = new ContextFactory();
        ProblemContext problemContext = contextFactory.build(reader);
        contextFactory.update(problemContext);
        contextFactory.filter(problemContext);
        LOGGER.info("Build problem context complete.\n");

        LOGGER.info("Sol processor started.\n");
        SolReader solReader = new SolReader();
        List<SolRollingStockPath> solRollingStockPathList = solReader.loadSolRollingStockPath();
        List<SolTrainSchedule> solTrainScheduleList = solReader.loadSolTrainSchedule();
        SolProcessor solProcessor = new SolProcessor();
        solProcessor.processSol(problemContext, solRollingStockPathList, solTrainScheduleList);

        LOGGER.info("Sol processor complete.\n");
        Set<String> updateCourses = new HashSet<>(problemContext.getCourseId2Schedule().keySet());

        Solution solution = new Solution();

        Graph graph = new Graph();
        graph.build(problemContext, solution, updateCourses);
        LOGGER.info("Build graph complete.\n");

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
                nodeGraph.build(problemContext, graph);

                nodeGraphList.add(nodeGraph);
            }
        }
        LOGGER.info("Build node graph complete.\n");

        EventBasedModel eventBasedModel = new EventBasedModel();
        eventBasedModel.buildModel(problemContext, graph, nodeGraphList, solution);
        LOGGER.info("Build event based model complete.\n");
        eventBasedModel.writeModel();

        EventBasedMainSolver eventBasedMainSolver = new EventBasedMainSolver();
        updateCourses.clear();
        eventBasedMainSolver.solve(problemContext, graph, nodeGraphList, eventBasedModel, solution, updateCourses);
    }
}
