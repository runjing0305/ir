package solution;

import constant.Constants;
import context.Link;
import context.Node;
import context.ProblemContext;
import context.RollingStock;
import context.Schedule;
import context.Track;
import context.scenario.LinkScenario;
import context.scenario.StationExtendedDwellScenario;
import context.scenario.TrainExtendedDwellScenario;
import entity.BaseStationValue;
import entity.PadtllWchapxrTargetFrequency;
import util.EvaluationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SolutionEvaluator （问题解评估器）
 * 评估解的目标函数和约束违反度
 *
 * @author s00536729
 * @since 2022-06-20
 */
public class SolutionEvaluator {
    private final ProblemContext problemContext;

    public SolutionEvaluator(ProblemContext problemContext) {
        this.problemContext = problemContext;
    }

    private Map<String, Double> courseDelayPenaltyMap = new HashMap<>();

    /**
     * 基于输入解计算目标函数值
     *
     * @param solution 当前解
     * @return double 目标函数值
     */
    public double calcObj(Solution solution) {
        double skipStationPenalty = 0;
        double destinationDelayPenalty = 0;
        double targetFrequencyPenalty = 0;
        List<Integer> padtllWbArrivalTimes = new ArrayList<>();
        List<Integer> wchapxrEbArrivalTimes = new ArrayList<>();
        for (Schedule schedule : problemContext.getSchedules()) {
            // If solution.getScheduleStationArrivalTimeMap() does not contain schedule: Change End Schedule
            if (schedule.getCategory().equals(Schedule.Category.EE)
                || !solution.getScheduleStationArrivalTimeMap().containsKey(schedule)) {
                continue;
            }
            skipStationPenalty += calcSkipStationPenalty(solution, schedule);
            double scheduleDelayPenalty = calcDestinationDelayPenalty(solution, schedule);
            destinationDelayPenalty += scheduleDelayPenalty;
            if (scheduleDelayPenalty > 0.0) {
                courseDelayPenaltyMap.put(schedule.getCourseId(), scheduleDelayPenalty);
            }
            updateConsecutiveCentralArrivals(solution, padtllWbArrivalTimes, wchapxrEbArrivalTimes, schedule);
        }
        Collections.sort(padtllWbArrivalTimes);
        Collections.sort(wchapxrEbArrivalTimes);
        targetFrequencyPenalty += calcTargetFrequencyPenalty(padtllWbArrivalTimes);
        targetFrequencyPenalty += calcTargetFrequencyPenalty(wchapxrEbArrivalTimes);
        System.out.println("skipStationPenalty: " + skipStationPenalty + ", destinationDelayPenalty: " +
            destinationDelayPenalty + ", targetFrequencyPenalty: " + targetFrequencyPenalty);
        return skipStationPenalty + destinationDelayPenalty + targetFrequencyPenalty;
    }

    /**
     * 基于输入解计算约束违反度
     *
     * @param solution 当前解
     * @return double 约束违反度
     */
    public double calcVio(Solution solution) {
        double runTimeViolation = calcRunTimeViolation(solution);
        double headwayViolation = calcHeadwayViolation(solution);
        double trackCapacityViolation = calcTrackCapacityViolation(solution);
        double rsViolation = calcRollingStockViolation(solution);
        double activityViolation = calcActivityViolation(solution);
        double lateDepartureViolation = calculateLateDepartureViolation(solution);
        double stationExtendedDwellViolation = calculateStationExtendedDwellViolation(solution);
        double trainExtendedDwellViolation = calculateTrainExtendedDwellViolation(solution);
        boolean checkFirstInFirstOutViolation = false;
        if (checkFirstInFirstOutViolation) {
            calculateFirstInFirstOutViolation(solution);
        }
        boolean checkDwellTimeViolation = false;
        if (checkDwellTimeViolation) {
            calculateDwellTimeViolation(solution);
        }
        boolean checkCurrentTimeViolation = true;
        if (checkCurrentTimeViolation) {
            calculateBeforeCurrentTimeViolation(solution);
        }
        System.out.println("runTimeViolation: " + runTimeViolation + ", headwayViolation: " + headwayViolation +
                ", trackCapacityViolation: " + trackCapacityViolation + ", rollingStockViolation: " + rsViolation +
                ", lateDepartureViolation: " + lateDepartureViolation + ", stationExtendedDwellViolation: " +
                stationExtendedDwellViolation + ", trainExtendedDwellViolation: " + trainExtendedDwellViolation +
                ", activityViolation: " + activityViolation);
        return runTimeViolation + headwayViolation + trackCapacityViolation + rsViolation + lateDepartureViolation + stationExtendedDwellViolation + trainExtendedDwellViolation;
    }

    private double calcActivityViolation(Solution solution) {
        double activityViolation = 0;
        for (Schedule schedule : problemContext.getSchedules()) {
            if (!solution.getScheduleStationArrivalTimeMap().containsKey(schedule)) {
                continue;
            }

            // 整个Schedule都已经Realized
            if (!schedule.getRealizedNodes().isEmpty() && schedule.getRealizedEnterTimes().get(schedule.getRealizedNodes().size()) != 0) {
                continue;
            }
            List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.
                    getRealizedNodes();
            for (int j = 1; j < nodeList.size() - 1; j++) {
                // If activities on the link has been realized
                if (!schedule.getRealizedLeaveTimes().isEmpty()
                        && !schedule.getRealizedEnterTimes().isEmpty()
                        && schedule.getRealizedLeaveTimes().get(j + 1) != 0) {
                    continue;
                }

                Node node = nodeList.get(j);
                List<Boolean> skipStations = solution.getScheduleSkipStationMap().get(schedule);
                Boolean status = skipStations.get(j);
                int arrivalTime = solution.getScheduleStationArrivalTimeMap().get(schedule).get(j);
                int departureTime = solution.getScheduleStationDepartureTimeMap().get(schedule).get(j);
                if (status) {
                    if (departureTime > arrivalTime) {
                        activityViolation += departureTime - arrivalTime;
                        if (Constants.OUTPUT_FLAG) {
                            System.out.println("Course: " + schedule.getCourseId() + " at node: " +
                                    node.getCode() + ", arrival: " + arrivalTime + ", departure time: " +
                                    departureTime + ", skip status: " + true);
                        }
                    }
                } else {
                    if (departureTime - arrivalTime < Constants.INNER_SCHEDULE_NODE_DWELL_TIME) {
                        activityViolation += Constants.INNER_SCHEDULE_NODE_DWELL_TIME - (departureTime - arrivalTime);
                        if (Constants.OUTPUT_FLAG) {
                            System.out.println("Course: " + schedule.getCourseId() + " at node: " +
                                    node.getCode() + ", arrival: " + arrivalTime + ", departure time: " +
                                    departureTime + ", skip status: " + false);
                        }
                    }

                }
            }
        }
        return activityViolation;
    }

    private double calcRollingStockViolation(Solution solution) {
        double rollingStockViolation = 0;
        for (Map.Entry<RollingStock, List<Schedule>> entry : solution.getRollingStock2ScheduleListMap().entrySet()) {
            List<Schedule> schedules = new ArrayList<>(entry.getValue());
            // TODO: 这里修改了start time, end time？
            for (int scheduleIndex = 0; scheduleIndex < schedules.size(); scheduleIndex++) {
                Schedule schedule = schedules.get(scheduleIndex);
                schedule.setStartTime(solution.getScheduleStationDepartureTimeMap().get(schedule).get(0));
                List<Integer> frontArrivals = solution.getScheduleStationArrivalTimeMap().get(schedule);
                schedule.setEndTime(frontArrivals.get(frontArrivals.size() - 1));
            }
            Collections.sort(schedules);
            for (int scheduleIndex = 0; scheduleIndex < schedules.size() - 1; scheduleIndex++) {
                Schedule frontSchedule = schedules.get(scheduleIndex);
                List<Integer> frontArrivals = solution.getScheduleStationArrivalTimeMap().get(frontSchedule);
                int frontEndTime = frontArrivals.get(frontArrivals.size() - 1);
                int partialCancellationEndIndex = solution.getSchedulePartialCancellationEndIndexMap().getOrDefault(frontSchedule, -1);
                if (partialCancellationEndIndex >= 0) {
                    frontEndTime = frontArrivals.get(partialCancellationEndIndex);
                }
                Schedule behindSchedule = schedules.get(scheduleIndex + 1);

                boolean frontScheduleRealized = !frontSchedule.getRealizedEnterTimes().isEmpty()
                        && frontSchedule.getRealizedEnterTimes().get(frontSchedule.getRealizedNodes().size()) != 0;
                boolean behindScheduleRealized = !behindSchedule.getRealizedLeaveTimes().isEmpty()
                        && behindSchedule.getRealizedLeaveTimes().get(1) != 0;
                if (frontScheduleRealized && behindScheduleRealized) {
                    continue;
                }
                List<Integer> behindDepartures = solution.getScheduleStationDepartureTimeMap().get(behindSchedule);
                int behindStartTime = behindDepartures.get(0);
                int partialCancellationStartIndex = solution.getSchedulePartialCancellationStartIndexMap().getOrDefault(behindSchedule, -1);
                if (partialCancellationStartIndex >= 0) {
                    behindStartTime = behindDepartures.get(partialCancellationStartIndex);
                }
                int timeDiff = behindStartTime - frontEndTime;
                int changeEndTime = EvaluationUtils.getChangeEndBetweenConsecutiveCourses(problemContext, frontSchedule, behindSchedule);
                if (timeDiff < changeEndTime) {
                    rollingStockViolation += changeEndTime - timeDiff;
                    if (Constants.OUTPUT_FLAG) {
                        System.out.println("Head: " + frontSchedule.getCourseId() + ", end time: " + frontEndTime);
                        System.out.println("Tail: " + behindSchedule.getCourseId() + ", start time: " + behindStartTime);
                        System.out.println("Expected Change End Time: " + changeEndTime + "; Actual Change End Time: " + timeDiff);
                        System.out.println("Rolling Stock Violation: " + rollingStockViolation);
                    }
                    String key = frontSchedule.getCourseId() + "_" + behindSchedule.getCourseId();
                    solution.getRollingStockVioMap().put(key, frontEndTime - behindStartTime);
                    solution.getVioSchedules().add(frontSchedule);
                    solution.getVioSchedules().add(behindSchedule);
                }
            }
        }
        return rollingStockViolation;
    }


    private double calculateLateDepartureViolation(Solution solution) {
        double lateDepartureViolation = 0.0;
        for (Schedule schedule : problemContext.getSchedules()) {
            if (!solution.getScheduleStationArrivalTimeMap().containsKey(schedule)) {
                continue;
            }

            if (!schedule.getRealizedNodes().isEmpty() && schedule.getRealizedEnterTimes().get(schedule.getRealizedNodes().size()) != 0) {
                continue;
            }

            if (schedule.getLateDeparture() != null) {
                int actualDepartureTime = solution.getScheduleStationDepartureTimeMap().get(schedule).get(0);
                int partialCancellationStartIndex = solution.getSchedulePartialCancellationStartIndexMap().getOrDefault(schedule, -1);
                if (partialCancellationStartIndex >= 0) {
                    actualDepartureTime = solution.getScheduleStationDepartureTimeMap().get(schedule).get(partialCancellationStartIndex);
                }
                int expectedDepartureTime = schedule.getLeaveTimes().get(1) + schedule.getLateDeparture().getDepartureDelaySeconds();

                if (actualDepartureTime < expectedDepartureTime) {
                    lateDepartureViolation += (expectedDepartureTime - actualDepartureTime);
                    if (Constants.OUTPUT_FLAG) {
                        System.out.println("Course: " + schedule.getCourseId() +
                                ", actual departure time: " + actualDepartureTime +
                                ", expected departure time: " +  expectedDepartureTime);
                    }
                }
            }
        }

        return lateDepartureViolation;
    }


    private double calculateStationExtendedDwellViolation(Solution solution) {
        double stationExtendedDwellViolation = 0.0;
        for (Schedule schedule : problemContext.getSchedules()) {
            if (!solution.getScheduleStationArrivalTimeMap().containsKey(schedule)) {
                continue;
            }

            if (!schedule.getRealizedNodes().isEmpty() && schedule.getRealizedEnterTimes().get(schedule.getRealizedNodes().size()) != 0) {
                continue;
            }

            List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.
                    getRealizedNodes();
            int nodeStartIndex = 0;
            int nodeEndIndex = nodeList.size() - 1;
            int partialCancellationEndIndex = solution.getSchedulePartialCancellationEndIndexMap().getOrDefault(schedule, -1);
            int partialCancellationStartIndex = solution.getSchedulePartialCancellationStartIndexMap().getOrDefault(schedule, -1);

            if (partialCancellationEndIndex >= 0) {
                nodeEndIndex = Math.min(nodeEndIndex, partialCancellationEndIndex);
            }
            if (partialCancellationStartIndex >= 0) {
                nodeStartIndex = Math.max(nodeStartIndex, partialCancellationStartIndex);
            }

            for (int j = nodeStartIndex; j <= nodeEndIndex; j++) {
                Node node = nodeList.get(j);
                if (node.getStationExtendedDwellScenarios().isEmpty()) {
                    continue;
                }

                if (solution.getScheduleSkipStationMap().get(schedule).get(j)) {
                    continue;
                }

                for (StationExtendedDwellScenario stationExtendedDwellScenario : node.getStationExtendedDwellScenarios()) {
                    int startTime = stationExtendedDwellScenario.getStartTimeSeconds();
                    int endTime = stationExtendedDwellScenario.getEndTimeSeconds();

                    int arrivalTime = solution.getScheduleStationArrivalTimeMap().get(schedule).get(j);
                    int departureTime = solution.getScheduleStationDepartureTimeMap().get(schedule).get(j);
                    if (arrivalTime < startTime || arrivalTime > endTime) {
                        continue;
                    }
                    if (!schedule.getRealizedEnterTimes().isEmpty()
                            && !schedule.getRealizedLeaveTimes().isEmpty()
                            && arrivalTime == schedule.getRealizedEnterTimes().get(j + 1)
                            && departureTime == schedule.getRealizedLeaveTimes().get(j + 1)) {
                        continue;
                    }

                    int expectedDwellTime = stationExtendedDwellScenario.getExtendedRunTimeSeconds();
                    Boolean status = solution.getScheduleSkipStationMap().get(schedule).get(j);
                    int actualDwellTime = departureTime - arrivalTime;
                    if (actualDwellTime < expectedDwellTime) {
                        stationExtendedDwellViolation += expectedDwellTime - actualDwellTime;
                        if (Constants.OUTPUT_FLAG) {
                            System.out.println("Course: " + schedule.getCourseId() + " at node: " +
                                    node.getCode() + ", actual dwell time: " + actualDwellTime +
                                    ", expected dwell time: " +  expectedDwellTime + ", skip status: " + status);
                        }
                    }
                }
            }
        }

        return stationExtendedDwellViolation;
    }

    private double calculateTrainExtendedDwellViolation(Solution solution) {
        double trainExtendedDwellViolation = 0.0;
        for (Schedule schedule : problemContext.getSchedules()) {
            if (!solution.getScheduleStationArrivalTimeMap().containsKey(schedule)) {
                continue;
            }

            if (!schedule.getRealizedNodes().isEmpty() && schedule.getRealizedEnterTimes().get(schedule.getRealizedNodes().size()) != 0) {
                continue;
            }

            if (schedule.getTrainExtendedDwellScenarios() == null || schedule.getTrainExtendedDwellScenarios().isEmpty()) {
                continue;
            }

            Map<Node, Integer> nodeExtendedDwellTimeMap = schedule.getTrainExtendedDwellScenarios().stream().collect(Collectors.toMap(TrainExtendedDwellScenario::getNode, TrainExtendedDwellScenario::getExtendedRunTimeSeconds));

            List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.
                    getRealizedNodes();
            int nodeStartIndex = 0;
            int nodeEndIndex = nodeList.size() - 1;
            int partialCancellationEndIndex = solution.getSchedulePartialCancellationEndIndexMap().getOrDefault(schedule, -1);
            int partialCancellationStartIndex = solution.getSchedulePartialCancellationStartIndexMap().getOrDefault(schedule, -1);

            if (partialCancellationEndIndex >= 0) {
                nodeEndIndex = Math.min(nodeEndIndex, partialCancellationEndIndex);
            }
            if (partialCancellationStartIndex >= 0) {
                nodeStartIndex = Math.max(nodeStartIndex, partialCancellationStartIndex);
            }

            for (int j = nodeStartIndex; j <= nodeEndIndex; j++) {
                Node node = nodeList.get(j);
                if (!nodeExtendedDwellTimeMap.containsKey(node)) {
                    continue;
                }

                int extendedDwellTime = nodeExtendedDwellTimeMap.get(node);
                Boolean status = solution.getScheduleSkipStationMap().get(schedule).get(j);
                int arrivalTime = solution.getScheduleStationArrivalTimeMap().get(schedule).get(j);
                int departureTime = solution.getScheduleStationDepartureTimeMap().get(schedule).get(j);
                int actualDwellTime = departureTime - arrivalTime;

                if (actualDwellTime < extendedDwellTime) {
                    trainExtendedDwellViolation += extendedDwellTime - actualDwellTime;
                    if (Constants.OUTPUT_FLAG) {
                        System.out.println("Course: " + schedule.getCourseId() + " at node: " + node.getCode() + ", actual dwell time: " + actualDwellTime + ", expected dwell time: " + extendedDwellTime + ", arrival time: " + arrivalTime + ", departure time: " + departureTime + ", skip status: " + status);
                    }
                }
            }
        }

        return trainExtendedDwellViolation;
    }

    private double calculateFirstInFirstOutViolation(Solution solution) {
        double firstInFirstOutViolation = 0;
        Map<Link, List<HeadwayElement>> link2HeadwayElements = new HashMap<>();
        for (Schedule schedule : problemContext.getSchedules()) {
            if (!solution.getSchedule2RollingStockMap().containsKey(schedule)) {
                continue;
            }

            List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.
                    getRealizedNodes();
            int nodeStartIndex = 0;
            int nodeEndIndex = nodeList.size() - 1;
            int partialCancellationEndIndex = solution.getSchedulePartialCancellationEndIndexMap().getOrDefault(schedule, -1);
            int partialCancellationStartIndex = solution.getSchedulePartialCancellationStartIndexMap().getOrDefault(schedule, -1);
            if (partialCancellationStartIndex >= 0) {
                nodeStartIndex = partialCancellationStartIndex;
            }
            if (partialCancellationEndIndex >= 0) {
                nodeEndIndex = partialCancellationEndIndex;
            }
            for (int j = nodeStartIndex; j < nodeEndIndex; j++) {
                Node headNode = nodeList.get(j);
                Node tailNode = nodeList.get(j + 1);
                String linkName = Link.generateLinkName(headNode.getCode(), tailNode.getCode());
                Link link = problemContext.getName2Link().get(linkName);

                boolean headNodeArrivalRealized = !schedule.getRealizedEnterTimes().isEmpty() && schedule.getRealizedEnterTimes().get(j + 1) != 0;
                boolean headNodeDepartureRealized = !schedule.getRealizedLeaveTimes().isEmpty() && schedule.getRealizedLeaveTimes().get(j + 1) != 0;
                boolean tailNodeArrivalRealized = !schedule.getRealizedEnterTimes().isEmpty() && schedule.getRealizedEnterTimes().get(j + 2) != 0;
                boolean tailNodeDepartureRealized = !schedule.getRealizedLeaveTimes().isEmpty() && schedule.getRealizedLeaveTimes().get(j + 2) != 0;

                HeadwayElement headwayElement = new HeadwayElement(link, schedule, j, solution, headNodeArrivalRealized, headNodeDepartureRealized, tailNodeArrivalRealized, tailNodeDepartureRealized);
                headwayElement.setSchedule(schedule);
                List<HeadwayElement> headwayElements = link2HeadwayElements.getOrDefault(link, new ArrayList<>());
                headwayElements.add(headwayElement);
                link2HeadwayElements.put(link, headwayElements);
            }
        }
        for (Map.Entry<Link, List<HeadwayElement>> entry : link2HeadwayElements.entrySet()) {
            Link link = entry.getKey();
            List<HeadwayElement> headwayElements = entry.getValue();
            headwayElements.sort(Comparator.comparingInt(HeadwayElement::getHeadDeparture));
            for (int i = 0; i < headwayElements.size() - 1; i++) {
                HeadwayElement frontTrain = headwayElements.get(i);
                HeadwayElement behindTrain = headwayElements.get(i + 1);
                if (frontTrain.isTailNodeArrivalRealized() && behindTrain.isTailNodeArrivalRealized()) {
                    continue;
                }

                if (frontTrain.getTailArrival() > behindTrain.getTailArrival()) {
                    firstInFirstOutViolation += frontTrain.getTailArrival() - behindTrain.getTailArrival();
                    if (Constants.OUTPUT_FLAG) {
                        System.out.println("Front train: " + frontTrain.getSchedule().getCourseId() + " at link: " + frontTrain.getLink().getName() + " with arrival: " + frontTrain.getHeadArrival() + ", " + frontTrain.getTailArrival());
                        System.out.println("Behind train: " + behindTrain.getSchedule().getCourseId() + " at link: " + behindTrain.getLink().getName() + " with arrival: " + behindTrain.getHeadArrival() + ", " + behindTrain.getTailArrival());
                    }
                }
            }
        }
        return firstInFirstOutViolation;
    }

    private int calculateDwellTimeViolation(Solution solution) {
        int dwellTimeViolation = 0;
        for (Schedule schedule : problemContext.getSchedules()) {
            if (!solution.getSchedule2RollingStockMap().containsKey(schedule)) {
                continue;
            }

            List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.
                    getRealizedNodes();
            int nodeStartIndex = 0;
            int nodeEndIndex = nodeList.size() - 1;
            int partialCancellationEndIndex = solution.getSchedulePartialCancellationEndIndexMap().getOrDefault(schedule, -1);
            int partialCancellationStartIndex = solution.getSchedulePartialCancellationStartIndexMap().getOrDefault(schedule, -1);
            if (partialCancellationStartIndex >= 0) {
                nodeStartIndex = partialCancellationStartIndex;
            }
            if (partialCancellationEndIndex >= 0) {
                nodeEndIndex = partialCancellationEndIndex;
            }
            List<Boolean> skipStationList = solution.getScheduleSkipStationMap().get(schedule);
            List<Integer> arrivalTimeList = solution.getScheduleStationArrivalTimeMap().get(schedule);
            List<Integer> departureTimeList = solution.getScheduleStationDepartureTimeMap().get(schedule);
            Map<Integer, Integer> dwellTimeMap = schedule.getDwellTimes();
            for (int j = nodeStartIndex + 1; j < nodeEndIndex; j++) {
                if (skipStationList.get(j)) {
                    continue;
                }

                boolean arrivalRealized = !schedule.getRealizedEnterTimes().isEmpty() && schedule.getRealizedEnterTimes().get(j + 1) != 0;
                boolean leaveRealized = !schedule.getRealizedLeaveTimes().isEmpty() && schedule.getRealizedLeaveTimes().get(j + 1) != 0;
                if (arrivalRealized && leaveRealized) {
                    continue;
                }

                int arrivalTime = arrivalTimeList.get(j);
                int departureTime = departureTimeList.get(j);
                double dwellTime = departureTime - arrivalTime;
                if (dwellTime < dwellTimeMap.get(j + 1)) {
                    dwellTimeViolation += dwellTimeMap.get(j + 1) - dwellTime;
                    System.out.println("Dwell time is violated: " + schedule.getCourseId() + ", " + nodeList.get(j).getCode() + ", " + dwellTime + " < " + dwellTimeMap.get(j + 1));
                }
            }
        }

        return dwellTimeViolation;
    }

    private int calculateBeforeCurrentTimeViolation(Solution solution) {
        int currentTime = 0;
        for (Schedule schedule : problemContext.getSchedules()) {
            List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.getRealizedNodes();
            if (!schedule.getRealizedEnterTimes().isEmpty()) {
                for (int i = 0; i < nodeList.size(); ++i) {
                    int realizedEnterTime = schedule.getRealizedEnterTimes().get(i + 1);
                    if (realizedEnterTime != 0) {
                        currentTime = Math.max(currentTime, realizedEnterTime);
                    }
                }
            }
            if (!schedule.getRealizedLeaveTimes().isEmpty()) {
                for (int i = 0; i < nodeList.size(); ++i) {
                    int realizedLeaveTime = schedule.getRealizedLeaveTimes().get(i + 1);
                    if (realizedLeaveTime != 0) {
                        currentTime = Math.max(currentTime, realizedLeaveTime);
                    }
                }
            }
        }

        int violation = 0;
        for (Schedule schedule : problemContext.getSchedules()) {
            if (!solution.getSchedule2RollingStockMap().containsKey(schedule)) {
                continue;
            }

            List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.
                    getRealizedNodes();
            int nodeStartIndex = 0;
            int nodeEndIndex = nodeList.size() - 1;
            int partialCancellationEndIndex = solution.getSchedulePartialCancellationEndIndexMap().getOrDefault(schedule, -1);
            int partialCancellationStartIndex = solution.getSchedulePartialCancellationStartIndexMap().getOrDefault(schedule, -1);
            if (partialCancellationStartIndex >= 0) {
                nodeStartIndex = partialCancellationStartIndex;
            }
            if (partialCancellationEndIndex >= 0) {
                nodeEndIndex = partialCancellationEndIndex;
            }
            List<Integer> arrivalTimeList = solution.getScheduleStationArrivalTimeMap().get(schedule);
            List<Integer> departureTimeList = solution.getScheduleStationDepartureTimeMap().get(schedule);
            for (int j = nodeStartIndex; j <= nodeEndIndex; j++) {

                boolean arrivalRealized = !schedule.getRealizedEnterTimes().isEmpty() && schedule.getRealizedEnterTimes().get(j + 1) != 0;
                boolean leaveRealized = !schedule.getRealizedLeaveTimes().isEmpty() && schedule.getRealizedLeaveTimes().get(j + 1) != 0;
                if (j > nodeStartIndex && !arrivalRealized) {
                    int arrivalTime = arrivalTimeList.get(j);
                    if (arrivalTime > 0 && arrivalTime < currentTime) {
                        violation += currentTime - arrivalTime;
                        System.out.println("Current time is violated: " + schedule.getCourseId() + ", " + nodeList.get(j).getCode() + ", arrival time: " + arrivalTime + " < " + currentTime);
                    }
                }
                if (j < nodeEndIndex && !leaveRealized) {
                    int departureTime = departureTimeList.get(j);
                    if (departureTime > 0 && departureTime < currentTime) {
                        violation += currentTime - departureTime;
                        System.out.println("Current time is violated: " + schedule.getCourseId() + ", " + nodeList.get(j).getCode() + ", departure time: " + departureTime + " < " + currentTime);
                    }
                }
            }
        }

        return violation;
    }

    public double calcTrackCapacityViolation(Solution solution) {
        double trackCapacityViolation = 0;
        Map<Track, List<TrackElement>> trackListMap = new HashMap<>();
        for (Schedule schedule : problemContext.getSchedules()) {
            if (!solution.getSchedule2RollingStockMap().containsKey(schedule)) {
                continue;
            }
            List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.
                getRealizedNodes();
            int nodeStartIndex = 0;
            int nodeEndIndex = nodeList.size() - 1;
            int partialCancellationEndIndex = solution.getSchedulePartialCancellationEndIndexMap().getOrDefault(schedule, -1);
            int partialCancellationStartIndex = solution.getSchedulePartialCancellationStartIndexMap().getOrDefault(schedule, -1);
            if (partialCancellationStartIndex >= 0) {
                nodeStartIndex = partialCancellationStartIndex;
            }
            if (partialCancellationEndIndex >= 0) {
                nodeEndIndex = partialCancellationEndIndex;
            }
            for (int j = nodeStartIndex; j <= nodeEndIndex; j++) {
                Node node = nodeList.get(j);
                String trackStr;
                boolean nodeRealized = !schedule.getRealizedEnterTimes().isEmpty() && schedule.getRealizedEnterTimes().get(j + 1) != 0;
                if (solution.getScheduleStationTrackMap().containsKey(schedule)) {
                    trackStr = solution.getScheduleStationTrackMap().get(schedule).get(j);
                } else {
                    trackStr = schedule.getTracks().get(j + 1);
                }
                Track track;
                if (node.getName2Track().containsKey(trackStr)) {
                    track = node.getName2Track().get(trackStr);
                } else {
                    throw new NullPointerException();
                }
                int arrivalTime = solution.getScheduleStationArrivalTimeMap().get(schedule).get(j) == null ?
                        solution.getScheduleStationDepartureTimeMap().get(schedule).get(j) :
                        solution.getScheduleStationArrivalTimeMap().get(schedule).get(j);
                int departureTime = solution.getScheduleStationDepartureTimeMap().get(schedule).get(j) == null ?
                        solution.getScheduleStationArrivalTimeMap().get(schedule).get(j) :
                        solution.getScheduleStationDepartureTimeMap().get(schedule).get(j);

                // 对于最后一个Node
                // 如果是Depot，进入停留区，不再占Track
                // 如果不是Depot，直到下一个Course开始之后才离开，释放Track
                if (j == nodeEndIndex) {
                    if (partialCancellationEndIndex >= 0) {
                        if (Constants.GIDEAPK_NODE_CODE.equals(node.getCode()) || Constants.CHDWLHT_NODE_CODE.equals(node.getCode())) {
                            continue;
                        }
                    }
                    if (node.isDepot()) {
                        departureTime = arrivalTime;
                    } else {
                        // 寻找下一个Schedule
                        RollingStock rollingStock = solution.getSchedule2RollingStockMap().get(schedule);
                        List<Schedule> schedules = solution.getRollingStock2ScheduleListMap().get(rollingStock);

                        List<Schedule> sortedSchedules = new ArrayList<>(schedules);
                        sortedSchedules.sort(Comparator.comparing(s -> solution.getScheduleStationDepartureTimeMap().get(s).get(0)));

                        int index = 0;
                        for (; index < sortedSchedules.size(); ++index) {
                            if (schedule == sortedSchedules.get(index)) {
                                break;
                            }
                        }

                        if (index == sortedSchedules.size() - 1) {
                            departureTime = Integer.MAX_VALUE;
                        } else {
                            Schedule nextSchedule = sortedSchedules.get(index + 1);
                            departureTime = solution.getScheduleStationDepartureTimeMap().get(nextSchedule).get(0);
                        }
                    }
                }
                TrackElement te = new TrackElement(track, node, schedule, j, arrivalTime, departureTime, nodeRealized);
                List<TrackElement> trackElements = trackListMap.getOrDefault(track, new ArrayList<>());
                trackElements.add(te);
                trackListMap.put(track, trackElements);
            }
        }
        for (Map.Entry<Track, List<TrackElement>> entry : trackListMap.entrySet()) {
            List<TrackElement> trackElements = entry.getValue();
            Collections.sort(trackElements);
            for (int j = 0; j < trackElements.size() - 1; j++) {
                TrackElement te1 = trackElements.get(j);
                TrackElement te2 = trackElements.get(j + 1);
                if (te1.isNodeRealized() && te2.isNodeRealized()) {
                    continue;
                }

                if (solution.getSchedule2RollingStockMap().get(te1.getSchedule()) == solution.getSchedule2RollingStockMap().get(te2.getSchedule())) {
                    continue;
                }
                int minimumSeparationTime = Constants.MINIMUM_SEPARATION_TIME;
                if (te1.getDeparture() > te2.getArrival() - minimumSeparationTime) {
                    trackCapacityViolation += te1.getDeparture() - te2.getArrival() + minimumSeparationTime;
                    if (Constants.OUTPUT_FLAG) {
                        System.out.println("Head: " + te1.getSchedule().getCourseId() + " at node: " + te1.getNode().
                            getCode() + " at track: " + te1.getTrack().getName() + " with arrival: " + te1.
                                getArrival() + " and departure: " + te1.getDeparture());
                        System.out.println("Tail: " + te2.getSchedule().getCourseId() + " at node: " + te2.getNode().
                            getCode() + " at track: " + te2.getTrack().getName() + " with arrival: " + te2.
                                getArrival() + " and departure: " + te2.getDeparture());
                    }
                    String key = te1.getSchedule().getCourseId() + "_" + te1.getNode().getCode() + "_" +
                        te2.getSchedule().getCourseId() + "_" + te2.getNode().getCode();
                    solution.getTrackVioMap().put(key, te1.getDeparture() - te2.getArrival() + minimumSeparationTime);
                    solution.getVioSchedules().add(te1.getSchedule());
                    solution.getVioSchedules().add(te2.getSchedule());
                }
            }
        }
        return trackCapacityViolation;
    }

    public double calcHeadwayViolation(Solution solution) {
        double headwayViolation = 0;
        Map<Link, List<HeadwayElement>> link2HeadwayElements = new HashMap<>();
        for (Schedule schedule : problemContext.getSchedules()) {
            if (!solution.getSchedule2RollingStockMap().containsKey(schedule)) {
                continue;
            }

            List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.
                getRealizedNodes();
            int nodeStartIndex = 0;
            int nodeEndIndex = nodeList.size() - 1;
            int partialCancellationEndIndex = solution.getSchedulePartialCancellationEndIndexMap().getOrDefault(schedule, -1);
            int partialCancellationStartIndex = solution.getSchedulePartialCancellationStartIndexMap().getOrDefault(schedule, -1);
            if (partialCancellationStartIndex >= 0) {
                nodeStartIndex = partialCancellationStartIndex;
            }
            if (partialCancellationEndIndex >= 0) {
                nodeEndIndex = partialCancellationEndIndex;
            }
            for (int j = nodeStartIndex; j < nodeEndIndex; j++) {
                Node headNode = nodeList.get(j);
                Node tailNode = nodeList.get(j + 1);
                String linkName = Link.generateLinkName(headNode.getCode(), tailNode.getCode());
                Link link = problemContext.getName2Link().get(linkName);

                // TODO: Head/Tail Node Enter/Leave Realized
                boolean headNodeArrivalRealized = !schedule.getRealizedEnterTimes().isEmpty() && schedule.getRealizedEnterTimes().get(j + 1) != 0;
                boolean headNodeDepartureRealized = !schedule.getRealizedLeaveTimes().isEmpty() && schedule.getRealizedLeaveTimes().get(j + 1) != 0;
                boolean tailNodeArrivalRealized = !schedule.getRealizedEnterTimes().isEmpty() && schedule.getRealizedEnterTimes().get(j + 2) != 0;
                boolean tailNodeDepartureRealized = !schedule.getRealizedLeaveTimes().isEmpty() && schedule.getRealizedLeaveTimes().get(j + 2) != 0;

                HeadwayElement headwayElement = new HeadwayElement(link, schedule, j, solution, headNodeArrivalRealized, headNodeDepartureRealized, tailNodeArrivalRealized, tailNodeDepartureRealized);
                headwayElement.setSchedule(schedule);
                List<HeadwayElement> headwayElements = link2HeadwayElements.getOrDefault(link, new ArrayList<>());
                headwayElements.add(headwayElement);
                link2HeadwayElements.put(link, headwayElements);
            }
        }
        for (Map.Entry<Link, List<HeadwayElement>> entry : link2HeadwayElements.entrySet()) {
            Link link = entry.getKey();
            List<HeadwayElement> headwayElements = entry.getValue();
            Collections.sort(headwayElements);
            for (int i = 0; i < headwayElements.size() - 1; i++) {
                HeadwayElement frontTrain = headwayElements.get(i);
                HeadwayElement behindTrain = headwayElements.get(i + 1);
                // 前面一辆车已经离开, 后面一辆车也已经到达
                if (frontTrain.isHeadNodeDepartureRealized() && behindTrain.isHeadNodeArrivalRealized()) {
                    continue;
                }
                int minimumHeadWay = link.getMinimumHeadway()[frontTrain.getHeadStatus()][frontTrain.getTailStatus()]
                        [behindTrain.getHeadStatus()][behindTrain.getTailStatus()];
                if (frontTrain.isHeadNodeArrivalRealized() && behindTrain.isHeadNodeArrivalRealized()) {
                    // 前面一辆车已经到达,后面一辆车已经到达,到达时间差小于headway,不管前面车什么时候离开,都无法满足headway约束
                    if (behindTrain.getHeadArrival() - frontTrain.getHeadArrival() < minimumHeadWay) {
                        continue;
                    }
                    // 前面一辆车已经到达,但是没有离开,后面一辆车已经到达,无法满足headway约束
                    if (!frontTrain.isHeadNodeDepartureRealized()) {
                        continue;
                    }
                }
                int actHeadway = behindTrain.getHeadArrival() - frontTrain.getHeadDeparture();
                if (actHeadway < minimumHeadWay) {
                    headwayViolation += minimumHeadWay - actHeadway;
                    String key = frontTrain.getSchedule().getCourseId() + "_" + frontTrain.getLink().getName() + "_" +
                        behindTrain.getSchedule().getCourseId() + "_" + behindTrain.getLink().getName();
                    if (Constants.OUTPUT_FLAG) {
                        System.out.println("Front train: " + frontTrain.getSchedule().getCourseId() + " at link: " +
                            frontTrain.getLink().getName() + " with departure: " + frontTrain.getHeadDeparture() +
                                ", " + frontTrain.getHeadStatus() + ", " + frontTrain.getTailStatus());
                        System.out.println("Behind train: " + behindTrain.getSchedule().getCourseId() + " at link: " +
                            behindTrain.getLink().getName() + " with arrival: " + behindTrain.getHeadArrival() +
                                ", " + behindTrain.getHeadStatus() + ", " + behindTrain.getTailStatus());
                        System.out.println("MinimumHeadway: " + minimumHeadWay + ", " + "actualHeadway: " + actHeadway);
                    }
                    solution.getHeadwayVioMap().put(key, minimumHeadWay - actHeadway);
                    solution.getVioSchedules().add(frontTrain.getSchedule());
                    solution.getVioSchedules().add(behindTrain.getSchedule());
                }
            }
        }
        return headwayViolation;
    }

    private double calcRunTimeViolation(Solution solution) {
        double runTimeViolation = 0;
        for (Schedule schedule : problemContext.getSchedules()) {
            if (!solution.getScheduleStationArrivalTimeMap().containsKey(schedule)) {
                continue;
            }

            // 整个Schedule都已经Realized
            if (!schedule.getRealizedNodes().isEmpty() && schedule.getRealizedEnterTimes().get(schedule.getRealizedNodes().size()) != 0) {
                continue;
            }
            List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.
                getRealizedNodes();
            int linkNodeStartIndex = 0;
            int linkNodeEndIndex = nodeList.size() - 2;
            int partialCancellationStartIndex = solution.getSchedulePartialCancellationStartIndexMap().getOrDefault(schedule, -1);
            int partialCancellationEndIndex = solution.getSchedulePartialCancellationEndIndexMap().getOrDefault(schedule, -1);
            if (partialCancellationStartIndex >= 0) {
                linkNodeStartIndex = partialCancellationStartIndex;
            }
            if (partialCancellationEndIndex >= 0) {
                linkNodeEndIndex = partialCancellationEndIndex - 1;
            }
            for (int j = linkNodeStartIndex; j <= linkNodeEndIndex; j++) {
                // If activities on the link has been realized
                if (!schedule.getRealizedLeaveTimes().isEmpty()
                        && !schedule.getRealizedEnterTimes().isEmpty()
                        && schedule.getRealizedLeaveTimes().get(j + 1) != 0
                        && schedule.getRealizedEnterTimes().get(j + 2) != 0) {
                    continue;
                }

                Node headNode = nodeList.get(j);
                Node tailNode = nodeList.get(j + 1);
                String linkName = Link.generateLinkName(headNode.getCode(), tailNode.getCode());
                Link link = problemContext.getName2Link().get(linkName);
                int headStatus = Boolean.TRUE.equals(solution.getScheduleSkipStationMap().get(schedule).get(j)) ?
                        0 : 1;
                int tailStatus = Boolean.TRUE.equals(solution.getScheduleSkipStationMap().get(schedule).get(j + 1)) ?
                        0 : 1;
                int minimumRunTime = link.getMinimumRunTime()[headStatus][tailStatus];

                int actualArrivalTime = solution.getScheduleStationArrivalTimeMap().get(schedule).get(j + 1);
                int actualDepartureTime = solution.getScheduleStationDepartureTimeMap().get(schedule).get(j);

                for (LinkScenario linkScenario : link.getLinkScenarioList()) {
                    if (linkScenario.getStartTime() <= actualDepartureTime && linkScenario.getEndTime() > actualDepartureTime) {
                        minimumRunTime = linkScenario.getExtendedRunTime();
                        break;
                    }
                }

                int actualRunTime = actualArrivalTime - actualDepartureTime;
                if (actualRunTime < minimumRunTime) {
                    runTimeViolation += (minimumRunTime - actualRunTime);
                    if (Constants.OUTPUT_FLAG) {
                        System.out.println("Course: " + schedule.getCourseId() + " at link: " +
                                linkName + ", actual run time: " + actualRunTime + ", minimum run time: " + minimumRunTime + ", head status: " + headStatus + ", tail status: " + tailStatus);
                    }
                    solution.getRunTimeVioMap().put(schedule.getCourseId() + "_" + linkName,
                        minimumRunTime - actualRunTime);
                    solution.getVioSchedules().add(schedule);
                }
            }
        }
        return runTimeViolation;
    }

    private double calcTargetFrequencyPenalty(List<Integer> padtllWbArrivalTimes) {
        double targetFrequencyPenalty = 0;
        for (int i = 0; i < padtllWbArrivalTimes.size() - 1; i++) {
            int first = padtllWbArrivalTimes.get(i);
            int second = padtllWbArrivalTimes.get(i + 1);
            int tfFirst = EvaluationUtils.getPassageFrequencyThreshold(first);
            int tfSecond = EvaluationUtils.getPassageFrequencyThreshold(second);
            int targetFrequency = Math.max(tfFirst, tfSecond);
            int actualHeadway = second - first;
            if (actualHeadway > targetFrequency) {
                targetFrequencyPenalty += (actualHeadway - targetFrequency) * Constants.FREQUENCY_PENALTY /
                        Constants.SECONDS_IN_MINUTE;
            }
        }
        return targetFrequencyPenalty;
    }

    private int thresholdHeadway(int arrivalTime) {
        int ret = 0;
        for (PadtllWchapxrTargetFrequency targetFrequency : problemContext.getCode2Node().get("PADTLL").
                getTargetFrequency().get(Track.Direction.WB)) {
            if (targetFrequency.getStartTimeSeconds() <= arrivalTime
                    && targetFrequency.getEndTimeSeconds() >= arrivalTime) {
                ret = targetFrequency.getThresholdHeadwaySeconds();
                break;
            }
        }
        return ret;
    }

    private void updateConsecutiveCentralArrivals(Solution solution, List<Integer> padtllWbArrivalTimes,
                                                  List<Integer> wchapxrEbArrivalTimes, Schedule schedule) {
        List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.
            getRealizedNodes();
        Track.Direction direction = schedule.getDirection();
        int partialCancellationEndIndex = solution.getSchedulePartialCancellationEndIndexMap().getOrDefault(schedule, -1);
        int partialCancellationStartIndex = solution.getSchedulePartialCancellationStartIndexMap().getOrDefault(schedule, -1);
        int nodeStartIndex = 0;
        int nodeEndIndex = nodeList.size() - 1;
        if (partialCancellationEndIndex >= 0) {
            nodeEndIndex = partialCancellationEndIndex;
        }
        if (partialCancellationStartIndex >= 0) {
            nodeStartIndex = partialCancellationStartIndex;
        }
        for (int j = nodeStartIndex; j <= nodeEndIndex; j++) {
            Node node = nodeList.get(j);
            Boolean b = solution.getScheduleSkipStationMap().get(schedule).get(j);
            if (Boolean.FALSE.equals(b) && node.getCode().equalsIgnoreCase(Constants.WEST_BOUND_REFERENCE_STATION) &&
                    direction.equals(Track.Direction.WB)) {
                padtllWbArrivalTimes.add(solution.getScheduleStationArrivalTimeMap().get(schedule).get(j));
            }
            if (Boolean.FALSE.equals(b) && node.getCode().equalsIgnoreCase(Constants.EAST_BOUND_REFERENCE_STATION) &&
                    direction.equals(Track.Direction.EB)) {
                wchapxrEbArrivalTimes.add(solution.getScheduleStationArrivalTimeMap().get(schedule).get(j));
            }
        }
    }

    private double calcDestinationDelayPenalty(Solution solution, Schedule schedule) {
        if (!schedule.getRealizedNodes().isEmpty()
            && schedule.getRealizedEnterTimes().get(schedule.getRealizedNodes().size()) != 0) {
            // 已经完全发生的course不记目的地延迟到达惩罚
            return 0;
        }

        double destinationDelayPenalty = 0;
        List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.
            getRealizedNodes();
        int arrivalIndex = schedule.getPlannedNodes().lastIndexOf(nodeList.get(nodeList.size() - 1));
        // schedule.getEndTime() 可能已经被修改，需要使用schedule中的EnterTimes
        int plannedArrival = schedule.getEnterTimes().get(arrivalIndex + 1);
        List<Integer> arrivalTimes = solution.getScheduleStationArrivalTimeMap().get(schedule);
        int actEndTime = arrivalTimes.get(arrivalTimes.size() - 1);
        int partialCancellationEndIndex = solution.getSchedulePartialCancellationEndIndexMap().getOrDefault(schedule, -1);
        if (partialCancellationEndIndex >= 0) {
            plannedArrival = schedule.getEnterTimes().get(partialCancellationEndIndex + 1);
            actEndTime = arrivalTimes.get(partialCancellationEndIndex);
        }

        int actualDelay = actEndTime - plannedArrival;
        if (actualDelay >= 3 * Constants.SECONDS_IN_MINUTE) {
            destinationDelayPenalty += actualDelay * Constants.DELAY_PENALTY / Constants.SECONDS_IN_MINUTE;
//            if (Constants.OUTPUT_FLAG) {
//                System.out.println(schedule.getCourseId() + " has delay time: " + actualDelay);
//            }
        }
        solution.getScheduleDestinationDelayMap().put(schedule, actualDelay);
        return destinationDelayPenalty;
    }

    public double calcSkipStationPenalty(Solution solution, Schedule schedule) {
        if (!schedule.getRealizedNodes().isEmpty()
            && schedule.getRealizedEnterTimes().get(schedule.getRealizedNodes().size()) != 0) {
            // 已经完全发生的course不记跳站惩罚
            return 0;
        }
        double obj = 0;
        List<Integer> scheduleSkipBsvList = new ArrayList<>();
        fillScheduleSkipBsvList(solution, schedule, scheduleSkipBsvList);
        Collections.sort(scheduleSkipBsvList);
        for (int i = 0; i < scheduleSkipBsvList.size(); i++) {
            int multiplier = 1;
            if (i == 0) {
                multiplier = Constants.FIRST_SKIP_STOP_MULTIPLIER;
            } else if (i == 1) {
                multiplier = Constants.SECOND_SKIP_STOP_MULTIPLIER;
            }
            obj += multiplier * scheduleSkipBsvList.get(i);
        }
//        if (Constants.OUTPUT_FLAG && obj > 0) {
//            System.out.println(schedule.getCourseId() + " has skip station penalty: " + obj);
//        }
        return obj;
    }

    private void fillScheduleSkipBsvList(Solution solution, Schedule schedule, List<Integer> scheduleBsvList) {
        List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.
            getRealizedNodes();
        fillNodeListBsvList(solution, schedule, scheduleBsvList, nodeList, problemContext);
    }

    public static void fillNodeListBsvList(Solution solution, Schedule schedule, List<Integer> scheduleBsvList,
                                           List<Node> nodeList, ProblemContext problemContext) {
        Track.Direction direction = schedule.getDirection();
        int nodeStartIndex = 1;
        int nodeEndIndex = nodeList.size() - 1;
        int partialCancellationEndIndex = solution.getSchedulePartialCancellationEndIndexMap().getOrDefault(schedule, -1);
        int partialCancellationStartIndex = solution.getSchedulePartialCancellationStartIndexMap().getOrDefault(schedule, -1);

        if (partialCancellationEndIndex >= 0) {
            nodeEndIndex = Math.min(nodeEndIndex, partialCancellationEndIndex);
        }
        if (partialCancellationStartIndex >= 0) {
            nodeStartIndex = Math.max(nodeStartIndex, partialCancellationStartIndex);
        }

        for (int j = nodeStartIndex; j <= nodeEndIndex; j++) {
            Node node = nodeList.get(j);
            Boolean b = solution.getScheduleSkipStationMap().get(schedule).get(j);
            boolean arrivalRealized = !schedule.getRealizedEnterTimes().isEmpty() && schedule.getRealizedEnterTimes().get(j + 1) != 0;
            boolean departureRealized = !schedule.getRealizedLeaveTimes().isEmpty() && schedule.getRealizedLeaveTimes().get(j + 1) != 0;
            if (arrivalRealized && departureRealized) {
                if (Boolean.TRUE.equals(b) && schedule.getNodeStatus().get(j + 1).
                        equalsIgnoreCase("STOP")) {
                    System.out.println("Warning: Skipped a realized node.");
                }
                continue;
            }

            if (Boolean.TRUE.equals(b) && schedule.getNodeStatus().get(j + 1).
                    equalsIgnoreCase("STOP")) {
                // 说明跳站了，并且跳过的站计划的状态是STOP
                List<BaseStationValue> baseStationValues = node.getBsvList();
                int arrivalTime = solution.getScheduleStationArrivalTimeMap().get(schedule).get(j);
                arrivalTime = arrivalTime % 86400;
                int corBsv = 0;
                for (BaseStationValue bsv : baseStationValues) {
                    if (direction.name().equalsIgnoreCase(bsv.getDirection())
                            && bsv.getStartTimeBandSeconds() <= arrivalTime
                            && bsv.getEndTimeBandSeconds() >= arrivalTime) {
                        corBsv = bsv.getBsv();
                        break;
                    }
                }
                scheduleBsvList.add(corBsv);
            }
        }

        int cancelledNodeStartIndex = -1;
        int cancelledNodeEndIndex = -1;
        if (partialCancellationEndIndex >= 0) {
            cancelledNodeStartIndex = partialCancellationEndIndex + 1;
            cancelledNodeEndIndex = nodeList.size() - 1;
        }
        if (partialCancellationStartIndex >= 0) {
            cancelledNodeStartIndex = 0;
            cancelledNodeEndIndex = partialCancellationStartIndex - 1;
        }

        if (cancelledNodeStartIndex < 0 || cancelledNodeEndIndex < 0) {
            return;
        }

        for (int i = cancelledNodeStartIndex; i <= cancelledNodeEndIndex; ++i) {
            Node node = nodeList.get(i);
            if (schedule.getNodeStatus().get(i + 1).equalsIgnoreCase("STOP")) {
                List<BaseStationValue> baseStationValues = node.getBsvList();
                int arrivalTime = solution.getScheduleStationArrivalTimeMap().get(schedule).get(i);
                arrivalTime = arrivalTime % 86400;
                int corBsv = 0;
                for (BaseStationValue bsv : baseStationValues) {
                    if (direction.name().equalsIgnoreCase(bsv.getDirection())
                            && bsv.getStartTimeBandSeconds() <= arrivalTime
                            && bsv.getEndTimeBandSeconds() >= arrivalTime) {
                        corBsv = bsv.getBsv();
                        break;
                    }
                }
                scheduleBsvList.add(corBsv);
            }
        }
    }

    public Map<String, Double> getCourseDelayPenaltyMap() {
        return courseDelayPenaltyMap;
    }
}
