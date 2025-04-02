package solutionanalyzer;

import context.ActivityType;
import context.Node;
import context.ProblemContext;
import context.Schedule;
import dataprocess.ContextFactory;
import datareader.DataReader;
import graph.scgraph.SingleCommodityGraph;
import gurobi.GRBException;
import model.scmodel.SCModel;
import org.apache.commons.lang3.tuple.Pair;
import solution.Solution;
import solution.SolutionEvaluator;
import solution.SolutionGenerator;
import solver.MipSolver;

import java.io.BufferedReader;
import java.util.*;
import java.util.stream.Collectors;

public class SolutionAnalyzer {
    private void readAndSetSolutionData(Solution solution, BufferedReader bufferedReader) throws Exception {
        Map<String, Schedule> courseId2ScheduleMap = solution.getSchedule2RollingStockMap().keySet().stream().collect(Collectors.toMap(Schedule::getCourseId, k -> k));
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
    }

    public Pair<ProblemContext, Solution> getAnInitialSolution() throws GRBException {
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

        return Pair.of(problemContext, newSolution);
    }

    public static void main(String[] args) throws GRBException {
        SolutionAnalyzer solutionAnalyzer = new SolutionAnalyzer();
        Pair<ProblemContext, Solution> solution1 = solutionAnalyzer.getAnInitialSolution();

        DataReader dataReader = new DataReader();
        try (BufferedReader bufferedReader = dataReader.loadFinalSolutionSchedulingFile()) {
            solutionAnalyzer.readAndSetSolutionData(solution1.getRight(), bufferedReader);
        } catch (Exception e) {
            e.printStackTrace();
        }
        SolutionEvaluator solutionEvaluator1 = new SolutionEvaluator(solution1.getLeft());
        solution1.getRight().setObjValue(solutionEvaluator1.calcObj(solution1.getRight()));
        Solution.clearVioMaps(solution1.getRight());
        solution1.getRight().setVioDegree(solutionEvaluator1.calcVio(solution1.getRight()));
        System.out.println("Sol status: " + solution1.getRight().getResultStatus() + ", obj: " + solution1.getRight().getObjValue() +
                ", vio: " + solution1.getRight().getVioDegree());

        Pair<ProblemContext, Solution> solution2 = solutionAnalyzer.getAnInitialSolution();
        try (BufferedReader bufferedReader = dataReader.loadEventBasedSolutionScheduleFile()) {
            solutionAnalyzer.readAndSetSolutionData(solution2.getRight(), bufferedReader);
        } catch (Exception e) {
            e.printStackTrace();
        }
        SolutionEvaluator solutionEvaluator2 = new SolutionEvaluator(solution2.getLeft());
        solution2.getRight().setObjValue(solutionEvaluator2.calcObj(solution2.getRight()));
        Solution.clearVioMaps(solution2.getRight());
        solution2.getRight().setVioDegree(solutionEvaluator2.calcVio(solution2.getRight()));
        System.out.println("Sol status: " + solution2.getRight().getResultStatus() + ", obj: " + solution2.getRight().getObjValue() +
                ", vio: " + solution2.getRight().getVioDegree());

        Schedule schedule = solution2.getLeft().getCourseId2Schedule().get("9U77RO");
        List<Integer> arrivalTimeList = solution2.getRight().getScheduleStationArrivalTimeMap().get(schedule);
        List<Integer> departureTimeList = solution2.getRight().getScheduleStationDepartureTimeMap().get(schedule);

        int[] newDepartureTimes = {
                84322,
                84592,
                84929,
                85498,
                85875,
                85958,
                86031,
                86049,
                86095,
                86182,
                86195,
                86228,
                86270,
                86390,
                86450,
                86570,
                86642,
                86669,
                86695,
                86725,
                87220,
                87370,
                87430,
                87444,
                87490,
                87670,
                87760,
                87833,
                87839,
                87940,
                88060,
                88159,
                88210,
                88270,
                88420,
                88579,
                88630,
                88645,
                88870,
                88990,
        };

        int[] newArrivalTimes = {
                84562,
                84929,
                85498,
                85845,
                85958,
                86001,
                86049,
                86095,
                86152,
                86195,
                86228,
                86240,
                86360,
                86450,
                86540,
                86642,
                86669,
                86695,
                86725,
                87220,
                87370,
                87430,
                87444,
                87460,
                87640,
                87730,
                87833,
                87839,
                87910,
                88030,
                88159,
                88180,
                88270,
                88390,
                88579,
                88600,
                88645,
                88840,
                88990,
                89159,
        };

        for (int i = 0; i < newArrivalTimes.length; ++i) {
//            arrivalTimeList.set(i + 1, newArrivalTimes[i]);
            arrivalTimeList.set(i + 1, arrivalTimeList.get(i + 1) - 60);
        }

        for (int i = 0; i < newDepartureTimes.length; ++i) {
//            departureTimeList.set(i, newDepartureTimes[i]);
            departureTimeList.set(i + 1, departureTimeList.get(i + 1) - 60);
        }

        Solution.printSolInfo(solution2.getRight(), solution2.getLeft());
//        Map<String, Double> courseDelayPenaltyMap1 = solutionEvaluator1.getCourseDelayPenaltyMap();
//        Map<String, Double> courseDelayPenaltyMap2 = solutionEvaluator2.getCourseDelayPenaltyMap();
//
//        Set<String> courseIdSet = new HashSet<>(courseDelayPenaltyMap1.keySet());
//        courseIdSet.addAll(courseDelayPenaltyMap2.keySet());
//
//        System.out.println("Delay course number: " + courseIdSet.size() + ", " + courseDelayPenaltyMap1.size() + ", " + courseDelayPenaltyMap2.size());
//
//        for (String courseId : courseIdSet) {
//            System.out.println(courseId + ", " + courseDelayPenaltyMap1.getOrDefault(courseId, 0.0) + ", " + courseDelayPenaltyMap2.getOrDefault(courseId, 0.0));
//        }
    }
}
