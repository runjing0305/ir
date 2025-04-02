package eventbased.graph;

import constant.Constants;
import context.Link;
import context.Node;
import context.ProblemContext;
import context.Schedule;
import context.scenario.LinkScenario;
import context.scenario.StationExtendedDwellScenario;
import context.scenario.TrainExtendedDwellScenario;
import solution.Solution;
import util.EvaluationUtils;

import java.util.List;

/**
 * @author longfei
 */
public class EdgeInfeasibleFunctions {

    public static boolean isHeadTailTimeDifferent(Vertex headVertex, Vertex tailVertex, ProblemContext problemContext, Solution solution) {
        return headVertex.getTime() != tailVertex.getTime();
    }

    public static boolean isMinimumRunTimeNotSatisfied(Vertex headNodeVertex, Vertex tailNodeVertex, ProblemContext problemContext, Solution solution) {
        if (headNodeVertex.isRealized() && tailNodeVertex.isRealized()) {
            return false;
        }
        int headSeq = headNodeVertex.getNodeSeq();
        int tailSeq = tailNodeVertex.getNodeSeq();
        Schedule schedule = problemContext.getCourseId2Schedule().get(headNodeVertex.getCourseId());

        if (!schedule.getRealizedLeaveTimes().isEmpty()
                && !schedule.getRealizedEnterTimes().isEmpty()
                && schedule.getRealizedLeaveTimes().get(headSeq) != 0
                && schedule.getRealizedEnterTimes().get(tailSeq) != 0) {
            return false;
        }

        String headId = headNodeVertex.getId();
        String tailId = tailNodeVertex.getId();

        String linkName = Link.generateLinkName(headId, tailId);
        Link link = problemContext.getName2Link().get(linkName);
        graph.Vertex.Type headType = graph.Vertex.Type.PASS;
        if (VertexType.NODE_STOP == headNodeVertex.getVertexType() || VertexType.NODE_LEAVE == headNodeVertex.getVertexType() || VertexType.NODE_STOP_LEAVE == headNodeVertex.getVertexType()) {
            headType = graph.Vertex.Type.STOP;
        }

        graph.Vertex.Type tailType = graph.Vertex.Type.PASS;
        if (VertexType.NODE_STOP == tailNodeVertex.getVertexType() || VertexType.NODE_LEAVE == tailNodeVertex.getVertexType() || VertexType.NODE_STOP_LEAVE == tailNodeVertex.getVertexType()) {
            tailType = graph.Vertex.Type.STOP;
        }

        int minimumRunTime = link.calcMinimumRunTime(headType, tailType);

        int actualDepartureTime = headNodeVertex.getTime();
        int actualArrivalTime = tailNodeVertex.getTime();
        // Consider the minimum run time scenario
        for (LinkScenario linkScenario : link.getLinkScenarioList()) {
            if (linkScenario.getStartTime() <= actualDepartureTime && linkScenario.getEndTime() > actualDepartureTime) {
                minimumRunTime = linkScenario.getExtendedRunTime();
                break;
            }
        }

        int actualRunTime = actualArrivalTime - actualDepartureTime;

        return minimumRunTime > actualRunTime;
    }

    public static boolean isDwellTimeNotSatisfied(Vertex headNodeVertex, Vertex tailNodeVertex, ProblemContext problemContext, Solution solution) {
        if (headNodeVertex.isRealized() && tailNodeVertex.isRealized()) {
            return false;
        }
        String headNodeCode = headNodeVertex.getId();
        String tailNodeCode = tailNodeVertex.getId();

        if (!headNodeCode.equals(tailNodeCode)) {
            return true;
        }

        String headCourseId = headNodeVertex.getCourseId();
        String tailCourseId = tailNodeVertex.getCourseId();
        if (!headCourseId.equals(tailCourseId)) {
            return true;
        }

        int headNodeSeq = headNodeVertex.getNodeSeq();
        int tailNodeSeq = tailNodeVertex.getNodeSeq();
        if (headNodeSeq != tailNodeSeq) {
            return true;
        }

        Schedule schedule = problemContext.getCourseId2Schedule().get(headCourseId);
//        int plannedDwellTime = solution.getScheduleStationDepartureTimeMap().get(schedule).get(tailNodeSeq - 1) - solution.getScheduleStationArrivalTimeMap().get(schedule).get(headNodeSeq - 1);
        int arrivalTime = headNodeVertex.getTime();
        int departureTime = tailNodeVertex.getTime();
        int actualDwellTime = departureTime - arrivalTime;

        Node node = problemContext.getCode2Node().get(headNodeCode);
        for (StationExtendedDwellScenario stationExtendedDwellScenario : node.getStationExtendedDwellScenarios()) {
            int startTime = stationExtendedDwellScenario.getStartTimeSeconds();
            int endTime = stationExtendedDwellScenario.getEndTimeSeconds();

            if (arrivalTime < startTime || arrivalTime > endTime) {
                continue;
            }
            if (!schedule.getRealizedEnterTimes().isEmpty()
                    && !schedule.getRealizedLeaveTimes().isEmpty()
                    && arrivalTime == schedule.getRealizedEnterTimes().get(headNodeSeq)
                    && departureTime == schedule.getRealizedLeaveTimes().get(headNodeSeq)) {
                continue;
            }

            int expectedDwellTime = stationExtendedDwellScenario.getExtendedRunTimeSeconds();
            if (actualDwellTime < expectedDwellTime) {
                return true;
            }
        }

        for (TrainExtendedDwellScenario trainExtendedDwellScenario : schedule.getTrainExtendedDwellScenarios()) {
            if (trainExtendedDwellScenario.getNode().getCode().equals(headNodeCode)) {
                int expectedDwellTime = trainExtendedDwellScenario.getExtendedRunTimeSeconds();
                if (actualDwellTime < expectedDwellTime) {
                    return true;
                }
            }
        }

        if (actualDwellTime < 30) {
            return true;
        }

        return false;
    }

    public static boolean isConsecutiveCoursesInfeasible(Vertex headVertex, Vertex tailVertex, ProblemContext problemContext, Solution solution) {
        if (headVertex.isRealized() && tailVertex.isRealized()) {
            return false;
        }
        String firstCourseId = headVertex.getCourseId();
        String secondCourseId = tailVertex.getCourseId();

        Schedule firstSchedule = problemContext.getCourseId2Schedule().get(firstCourseId);
        Schedule secondSchedule = problemContext.getCourseId2Schedule().get(secondCourseId);

        List<Node> firstNodeList = firstSchedule.getRealizedNodes().isEmpty() ? firstSchedule.getPlannedNodes() : firstSchedule.getRealizedNodes();
        List<Node> secondNodeList = secondSchedule.getRealizedNodes().isEmpty() ? secondSchedule.getPlannedNodes() : secondSchedule.getRealizedNodes();

        String firstScheduleEndNodeCode = firstNodeList.get(firstNodeList.size() - 1).getCode();
        String secondScheduleStartNodeCode = secondNodeList.get(0).getCode();

        if (!firstScheduleEndNodeCode.equals(secondScheduleStartNodeCode)) {
            return true;
        }

        int expectedChangeEndTime = EvaluationUtils.getChangeEndBetweenConsecutiveCourses(problemContext, firstSchedule, secondSchedule);

        int actualChangeEndTime = tailVertex.getTime() - headVertex.getTime();
        return actualChangeEndTime < expectedChangeEndTime;
    }

    public static boolean isConsecutiveRollingStocksInfeasible(Vertex headVertex, Vertex tailVertex, ProblemContext problemContext, Solution solution) {
        if (headVertex.isRealized() && tailVertex.isRealized()) {
            return false;
        }
        return tailVertex.getTime() - headVertex.getTime() < 420;
    }

    public static boolean isLateDepartureTimeInfeasible(Vertex headVertex, Vertex tailVertex, ProblemContext problemContext, Solution solution) {
        String courseId = headVertex.getCourseId();

        Schedule schedule = problemContext.getCourseId2Schedule().get(courseId);

        if (schedule.getLateDeparture() != null) {
            int actualDepartureTime = headVertex.getTime();
            int expectedDepartureTime = schedule.getLeaveTimes().get(1) + schedule.getLateDeparture().getDepartureDelaySeconds();

            return actualDepartureTime < expectedDepartureTime;
        }

        return false;
    }
}
