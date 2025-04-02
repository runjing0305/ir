package solutionchecker;

import context.*;
import dataprocess.ContextFactory;
import datareader.DataReader;
import graph.scgraph.SingleCommodityGraph;
import gurobi.GRBException;
import model.scmodel.SCModel;
import org.apache.commons.lang3.tuple.Pair;
import solution.Solution;
import solution.SolutionGenerator;
import solver.MipSolver;

import java.io.BufferedReader;
import java.util.*;
import java.util.stream.Collectors;

public class FinalSolutionFileChecker {

    private void readAndSetSolutionData(Solution solution) {
        DataReader dataReader = new DataReader();
        Map<String, Schedule> courseId2ScheduleMap = solution.getSchedule2RollingStockMap().keySet().stream().collect(Collectors.toMap(Schedule::getCourseId, k -> k));
        try (BufferedReader bufferedReader = dataReader.loadFinalSolutionSchedulingFile()) {
            String line;
            boolean firstLine = true;
            while ((line = bufferedReader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                String[] data = line.split(",");

                String courseId = data[0];
                int seq = Integer.parseInt(data[1]);
                String nodeCode = data[3];
                int arrivalSeconds = data[4].isEmpty() ? 0 : Integer.parseInt(data[4]);
                int departureSeconds = data[6].isEmpty() ? 0 : Integer.parseInt(data[6]);
                String trackId = data[8];
                ActivityType activityType = ActivityType.valueOf(data[9]);

                Schedule schedule = courseId2ScheduleMap.get(courseId);
                List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.getRealizedNodes();
                List<Integer> arrivalTimeMap = solution.getScheduleStationArrivalTimeMap().get(schedule);
                if (arrivalSeconds > 0) {
                    arrivalTimeMap.set(seq - 1, arrivalSeconds);
                }
                if (seq == 1) {
                    arrivalTimeMap.set(0, departureSeconds);
                }

                List<Integer> departureTimeMap = solution.getScheduleStationDepartureTimeMap().get(schedule);
                if (departureSeconds > 0) {
                    departureTimeMap.set(seq - 1, departureSeconds);
                }
                if (seq == nodeList.size()) {
                    departureTimeMap.set(seq - 1, arrivalSeconds);
                }

                List<String> trackMap = solution.getScheduleStationTrackMap().get(schedule);
                trackMap.set(seq - 1, trackId);

                if (!nodeCode.equals(nodeList.get(seq - 1).getCode())) {
                    System.out.println("Error: Inconsistent node code");
                }

                List<Boolean> skipStationMap = solution.getScheduleSkipStationMap().get(schedule);
                if (ActivityType.PASS == activityType) {
                    skipStationMap.set(seq - 1, true);
                } else {
                    skipStationMap.set(seq - 1, false);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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


        FinalSolutionFileChecker finalSolutionFileChecker = new FinalSolutionFileChecker();
        finalSolutionFileChecker.readAndSetSolutionData(newSolution);
        Solution.printSolInfo(newSolution, problemContext);
    }
}
