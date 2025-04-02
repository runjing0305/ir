package util;

import constant.Constants;
import context.Node;
import context.ProblemContext;
import context.Schedule;
import context.Track;
import entity.BaseStationValue;
import entity.PadtllWchapxrTargetFrequency;
import eventbased.model.SkippedStopType;
import org.apache.commons.lang3.tuple.Triple;

import java.util.List;

import static constant.Constants.HEADWAY_THRESHOLD;

/**
 * @author longfei
 */
public class EvaluationUtils {

    public static int getChangeEndBetweenConsecutiveCourses(ProblemContext problemContext, Schedule firstSchedule, Schedule secondSchedule) {
        int expectedChangeEndTime = 420;
        Track.Direction firstScheduleDirection = firstSchedule.getDirection();
        Track.Direction secondScheduleDirection = secondSchedule.getDirection();

        List<Node> nodeList = firstSchedule.getRealizedNodes().isEmpty() ? firstSchedule.getPlannedNodes() : firstSchedule.getRealizedNodes();
        String firstScheduleEndNodeCode = nodeList.get(nodeList.size() - 1).getCode();
        if (firstScheduleDirection == secondScheduleDirection) {
            if (Constants.PADTLL_NODE_CODE.equals(firstScheduleEndNodeCode)) {
                expectedChangeEndTime = 30;
            } else {
                expectedChangeEndTime = 60;
            }
        }

//        if (problemContext.getSpecialChangeEndTimeMap().containsKey(firstSchedule.getCourseId()) && problemContext.getSpecialChangeEndTimeMap().get(firstSchedule.getCourseId()).containsKey(secondSchedule.getCourseId())) {
//            expectedChangeEndTime = problemContext.getSpecialChangeEndTimeMap().get(firstSchedule.getCourseId()).get(secondSchedule.getCourseId());
//        }

        return expectedChangeEndTime;
    }

    public static int getPassageFrequencyThreshold(int time) {
        for (Triple<Integer, Integer, Integer> entry : HEADWAY_THRESHOLD) {
            if (time >= entry.getLeft() && time <= entry.getMiddle()) {
                return entry.getRight();
            }
        }

        return 0;
    }

    public static double getSkippedStopsPenalty(List<Integer> bsvList) {
        bsvList.sort(Integer::compareTo);

        double penalty = 0.0;
        for (int i = 0; i < bsvList.size(); ++i) {
            double factor = SkippedStopType.REMAIN.getFactor();
            if (i == 0) {
                factor = SkippedStopType.FIRST.getFactor();
            } else if (i == 1) {
                factor = SkippedStopType.SECOND.getFactor();
            }

            penalty += factor * bsvList.get(i);
        }

        return penalty;
    }

    public static int getBsv(Node node, int time, Track.Direction direction) {
        int tmpTime = time % 86400;
        BaseStationValue baseStationValue = node.getBsvList().stream().filter(bsv -> direction.name().equals(bsv.getDirection()) && tmpTime >= bsv.getStartTimeBandSeconds() && tmpTime <= bsv.getEndTimeBandSeconds()).findFirst().orElse(null);
        if (baseStationValue == null) {
            return 0;
        }

        return baseStationValue.getBsv();
    }
}
