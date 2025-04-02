package eventbased.solprocess;

import constant.Constants;
import context.*;
import entity.BaseStationValue;
import eventbased.solreader.SolRollingStockPath;
import eventbased.solreader.SolTrainSchedule;
import graph.Vertex;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author longfei
 */
public class SolProcessor {
    private static final Logger LOGGER = Logger.getLogger(SolProcessor.class.getName());

    private Map<Integer, List<String>> trainPathMap = new HashMap<>();
    private Map<String, String> courseDutyIdMap = new HashMap<>();
    private Map<String, List<SolTrainSchedule>> courseActivityListMap = new HashMap<>();

    private double skippedStopPenalty = 0.0;
    private double destinationDelayPenalty = 0.0;
    private double passageFrequencyPenalty = 0.0;


    public void processSol(ProblemContext problemContext, List<SolRollingStockPath> solRollingStockPathList, List<SolTrainSchedule> solTrainScheduleList) {
        trainPathMap = new HashMap<>(solRollingStockPathList.size());
        for (SolRollingStockPath solRollingStockPath : solRollingStockPathList) {
            List<String> courseIdList = Arrays.asList(solRollingStockPath.getPath().split("->"));
            trainPathMap.put(solRollingStockPath.getRollingStockId(), courseIdList);
        }

        solTrainScheduleList = solTrainScheduleList.stream().filter(ele -> ele.getTrainCourseId().equals(ele.getUnrealized())).collect(Collectors.toList());

        for (SolTrainSchedule solTrainSchedule : solTrainScheduleList) {
            String courseId = solTrainSchedule.getTrainCourseId();
            String dutyId = solTrainSchedule.getDutyId();

            courseDutyIdMap.put(courseId, dutyId);
            courseActivityListMap.computeIfAbsent(courseId, k -> new ArrayList<>()).add(solTrainSchedule);
        }

        courseActivityListMap.forEach((key, value) -> value.sort(Comparator.comparingInt(SolTrainSchedule::getSeq)));

        checkViolation(problemContext);
        calculateObj(problemContext);
    }

    public void checkViolation(ProblemContext problemContext) {
        LOGGER.info("Check Violation Started");

        checkMinimumRunTimeViolation(problemContext);
        checkMinimumHeadway(problemContext);

        LOGGER.info("Check Violation Complete");
    }

    public void checkMinimumRunTimeViolation(ProblemContext problemContext) {
        for (Map.Entry<String, List<SolTrainSchedule>> entry : courseActivityListMap.entrySet()) {
            List<SolTrainSchedule> solTrainScheduleList = entry.getValue();
            int prevLeaveTime = solTrainScheduleList.get(0).getDepartureSeconds();
            boolean prevLeaveRealized = false;
            if (solTrainScheduleList.get(0).getDepartureSecondsR() > 0) {
                prevLeaveTime = solTrainScheduleList.get(0).getDepartureSecondsR();
                prevLeaveRealized = true;
            }
            Vertex.Type prevNodeType = Vertex.Type.valueOf(solTrainScheduleList.get(0).getActivity());
            String prevNodeCode = solTrainScheduleList.get(0).getNode();

            for (int i = 1; i < solTrainScheduleList.size(); ++i) {
                SolTrainSchedule solTrainSchedule = solTrainScheduleList.get(i);
                boolean currentArrivalRealized = false;
                int currentArrivalTime = solTrainSchedule.getArrivalSeconds();
                if (solTrainSchedule.getArrivalSecondsR() > 0) {
                    currentArrivalRealized = true;
                    currentArrivalTime = solTrainSchedule.getArrivalSecondsR();
                }
                Vertex.Type currentNodeType = Vertex.Type.valueOf(solTrainSchedule.getActivity());
                String currentNodeCode = solTrainSchedule.getNode();

                String linkName = Link.generateLinkName(prevNodeCode, currentNodeCode);
                Link link = problemContext.getName2Link().get(linkName);

                int minimumRunTime = link.calcMinimumRunTime(prevNodeType, currentNodeType);
                int actualRunTime = currentArrivalTime - prevLeaveTime;

                if (actualRunTime < minimumRunTime) {
                    LOGGER.warning(String.format("Minimum Run Time is violated: %d %d %s %s %s\n", minimumRunTime, actualRunTime, entry.getKey(), prevNodeCode, currentNodeCode));
                }

                prevLeaveTime = solTrainSchedule.getDepartureSeconds();
                prevLeaveRealized = false;
                if (solTrainSchedule.getDepartureSecondsR() > 0) {
                    prevLeaveTime = solTrainSchedule.getDepartureSecondsR();
                    prevLeaveRealized = true;
                }
                prevNodeType = currentNodeType;
                prevNodeCode = currentNodeCode;
            }
        }
    }

    public void checkMinimumHeadway(ProblemContext problemContext) {
        Map<String, List<Triple<String, Integer, Integer>>> nodeEventListMap = new HashMap<>();

        for (Map.Entry<String, List<SolTrainSchedule>> entry : courseActivityListMap.entrySet()) {
            String courseId = entry.getKey();
            List<SolTrainSchedule> solTrainScheduleList = entry.getValue();
            for (int i = 0; i < solTrainScheduleList.size(); ++i) {
                if (i == solTrainScheduleList.size() - 1) {
                    continue;
                }
                SolTrainSchedule solTrainSchedule = solTrainScheduleList.get(i);
                String nodeCode = solTrainSchedule.getNode();
                int leaveTime = solTrainSchedule.getDepartureSeconds();
                int realizedLeaveTime = solTrainSchedule.getDepartureSecondsR();
                if (realizedLeaveTime > 0) {
                    leaveTime = realizedLeaveTime;
                }
                int arrivalTime = solTrainSchedule.getArrivalSeconds();
                int realizedArrivalTime = solTrainSchedule.getArrivalSecondsR();
                if (realizedArrivalTime > 0) {
                    arrivalTime = realizedArrivalTime;
                }

                if (i == 0) {
                    arrivalTime = leaveTime;
                } else if (i == solTrainScheduleList.size() - 1) {
                    leaveTime = arrivalTime;
                }

                String track = solTrainSchedule.getTrack();
                String id = String.join("_", nodeCode, track);
                nodeEventListMap.computeIfAbsent(id, k -> new ArrayList<>()).add(Triple.of(courseId, arrivalTime, leaveTime));
            }
        }

        for (Map.Entry<String, List<Triple<String, Integer, Integer>>> entry : nodeEventListMap.entrySet()) {
            entry.getValue().sort(Comparator.comparingInt(Triple::getMiddle));

            List<Triple<String, Integer, Integer>> eventList = entry.getValue();
            eventList.sort(Comparator.comparingInt(Triple::getMiddle));

            int prevLeave = -1;
            String prevCourseId = "";
            for (Triple<String, Integer, Integer> event : eventList) {
                int currentArrival = event.getMiddle();
                int timeDiff = currentArrival - prevLeave;
                if (prevLeave >= 0 && currentArrival - prevLeave < 30) {
                    LOGGER.warning(String.format("Consecutive two trains at %s %s %s: %d < 30, %d, %d", entry.getKey(), event.getLeft(), prevCourseId, timeDiff, currentArrival, prevLeave));

//                    Schedule schedule = courseId2Schedule.get(prevCourseId);
//                    int tmpSeq = -1;
//                    for (Map.Entry<Integer, Integer> leaveEntry : schedule.getLeaveTimes().entrySet()) {
//                        if (leaveEntry.getValue() == prevLeave) {
//                            tmpSeq = leaveEntry.getKey();
//                            break;
//                        }
//                    }
//
//                    schedule.getLeaveTimes().put(tmpSeq, prevLeave - 60);
//                    schedule.getDwellTimes().put(tmpSeq, schedule.getDwellTimes().get(tmpSeq) - 60);

                }

                prevLeave = event.getRight();
                prevCourseId = event.getLeft();
            }
        }
    }

    public void calculateObj(ProblemContext problemContext) {
        LOGGER.info("Calculate Objective Started");
        this.calculateSkippedStopPenalty(problemContext);
        this.calculateDestinationDelayPenalty(problemContext);
        this.calculatePassageFrequencyPenalty(problemContext);
        LOGGER.info("Calculate Objective Complete");
    }

    public void calculateSkippedStopPenalty(ProblemContext problemContext) {
        skippedStopPenalty = 0.0;
        for (Map.Entry<String, List<SolTrainSchedule>> entry : courseActivityListMap.entrySet()) {
            String courseId = entry.getKey();
            List<SolTrainSchedule> solTrainScheduleList = entry.getValue();

            Schedule plannedSchedule = problemContext.getCourseId2Schedule().get(courseId);
            List<Integer> penaltyList = new ArrayList<>();
            for (int i = 0; i < solTrainScheduleList.size(); ++i) {
                SolTrainSchedule solTrainSchedule = solTrainScheduleList.get(i);
                Node node = plannedSchedule.getPlannedNodes().get(i);
                String nodeCode = node.getCode();

                if (!nodeCode.equals(solTrainSchedule.getNode())) {
                    LOGGER.warning(String.format("Inconsistent node: %s %d %s\n", courseId, i, nodeCode));
                }

                if (Vertex.Type.PASS.name().equals(solTrainSchedule.getActivity())) {
                    int seq = solTrainSchedule.getSeq();
                    if (Vertex.Type.STOP.name().equals(plannedSchedule.getNodeStatus().get(seq))) {
                        final int plannedArrivalTime = i == 0 ? plannedSchedule.getLeaveTimes().get(1) : plannedSchedule.getEnterTimes().get(i + 1);
                        BaseStationValue baseStationValue = node.getBsvList().stream().filter(ele -> plannedSchedule.getDirection().name().equals(ele.getDirection()) && plannedArrivalTime >= ele.getStartTimeBandSeconds() && plannedArrivalTime <= ele.getEndTimeBandSeconds()).findFirst().orElse(null);
                        if (baseStationValue != null) {
                            penaltyList.add(baseStationValue.getBsv());
                        }
                    }
                }
            }

            penaltyList.sort(Integer::compareTo);

            if (penaltyList.size() >= 1) {
                skippedStopPenalty += 35.0 * penaltyList.get(0);

                if (penaltyList.size() >= 2) {
                    skippedStopPenalty += 15.0 * penaltyList.get(1);

                    if (penaltyList.size() >= 3) {
                        for (int k = 2; k < penaltyList.size(); ++k) {
                            skippedStopPenalty += penaltyList.get(k);
                        }
                    }
                }
            }
        }

        LOGGER.info(String.format("The total skipped stop penalty is: %f", skippedStopPenalty));
    }

    public void calculateDestinationDelayPenalty(ProblemContext problemContext) {
        destinationDelayPenalty = 0.0;
        for (Map.Entry<String, List<SolTrainSchedule>> entry : courseActivityListMap.entrySet()) {
            String courseId = entry.getKey();
            List<SolTrainSchedule> solTrainScheduleList = entry.getValue();

            Schedule plannedSchedule = problemContext.getCourseId2Schedule().get(courseId);
            if (Schedule.Category.EE == plannedSchedule.getCategory()) {
                continue;
            }

            SolTrainSchedule solTrainSchedule = solTrainScheduleList.get(solTrainScheduleList.size() - 1);
            int destinationArrival = solTrainSchedule.getArrivalSeconds();
            if (solTrainSchedule.getArrivalSecondsR() > 0) {
                destinationArrival = solTrainSchedule.getArrivalSecondsR();
            }

            int plannedArrivalTime = plannedSchedule.getEndTime();

            int timeDiff = destinationArrival - plannedArrivalTime;
            if (timeDiff > 180) {
                destinationDelayPenalty += timeDiff / 60.0 * 125;
            }
        }

        LOGGER.info(String.format("The total destination delay penalty is: %f", destinationDelayPenalty));
    }

    public void calculatePassageFrequencyPenalty(ProblemContext problemContext) {
        passageFrequencyPenalty = 0.0;
        List<Integer> wbArrivalEventTimeList = new ArrayList<>();
        List<Integer> ebArrivalEventTimeList = new ArrayList<>();

        for (Map.Entry<String, List<SolTrainSchedule>> entry : courseActivityListMap.entrySet()) {
            String courseId = entry.getKey();
            List<SolTrainSchedule> solTrainScheduleList = entry.getValue();

            Schedule plannedSchedule = problemContext.getCourseId2Schedule().get(courseId);

            if (Schedule.Category.EE == plannedSchedule.getCategory()) {
                continue;
            }

            for (int i = 0; i < solTrainScheduleList.size(); ++i) {
                SolTrainSchedule solTrainSchedule = solTrainScheduleList.get(i);

                String nodeCode = solTrainSchedule.getNode();
                boolean wbArrival = false;
                boolean ebArrival = false;
                if (Constants.WEST_BOUND_REFERENCE_STATION.equals(nodeCode) && plannedSchedule.getDirection() == Track.Direction.WB) {
                    wbArrival = true;
                } else if (Constants.EAST_BOUND_REFERENCE_STATION.equals(nodeCode) && plannedSchedule.getDirection() == Track.Direction.EB) {
                    ebArrival = true;
                }

                if (wbArrival || ebArrival) {
                    int arrivalTime = solTrainSchedule.getArrivalSeconds();
                    int realizedArrivalTime = solTrainSchedule.getArrivalSecondsR();
                    if (realizedArrivalTime > 0) {
                        arrivalTime = realizedArrivalTime;
                    }

                    if (wbArrival) {
                        wbArrivalEventTimeList.add(arrivalTime);
                    } else {
                        ebArrivalEventTimeList.add(arrivalTime);
                    }
                }
            }
        }

        wbArrivalEventTimeList.sort(Integer::compareTo);
        calculateFrequencyPenalty(wbArrivalEventTimeList);

        ebArrivalEventTimeList.sort(Integer::compareTo);
        calculateFrequencyPenalty(ebArrivalEventTimeList);

        LOGGER.info(String.format("The total passage frequency penalty is: %f", passageFrequencyPenalty));
    }

    public void calculateFrequencyPenalty(List<Integer> arrivalTimeList) {
        int prevArrivalTime = arrivalTimeList.get(0);
        for (int i = 1; i < arrivalTimeList.size(); ++i) {
            int currentArrivalTime = arrivalTimeList.get(i);
            int firstThreshold = -1;
            int secondThreshold = -1;
            for (Triple<Integer, Integer, Integer> entry : Constants.HEADWAY_THRESHOLD) {
                if (firstThreshold < 0 && prevArrivalTime >= entry.getLeft() && prevArrivalTime <= entry.getMiddle()) {
                    firstThreshold = entry.getRight();
                }

                if (secondThreshold < 0 && currentArrivalTime >= entry.getLeft() && currentArrivalTime <= entry.getMiddle()) {
                    secondThreshold = entry.getRight();
                }
            }

            int threshold = Math.max(firstThreshold, secondThreshold);
            int timeDiff = currentArrivalTime - prevArrivalTime;
            if (timeDiff > threshold) {
                passageFrequencyPenalty += (timeDiff - threshold) / Constants.SECONDS_IN_MINUTE * Constants.FREQUENCY_PENALTY;
            }

            prevArrivalTime = currentArrivalTime;
        }
    }
}
