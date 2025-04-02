package solution.modifier;

import constant.Constants;
import context.Link;
import context.Node;
import context.ProblemContext;
import context.Schedule;
import context.scenario.LinkScenario;
import context.scenario.StationExtendedDwellScenario;
import context.scenario.TrainExtendedDwellScenario;
import solution.Solution;

import java.util.List;

/**
 * SolutionCourseModifier （解调节器）
 * 基于输入的问题情景(Context)和用例(Scenario)，对问题在Course-level(run time, dwell time, e.t.c.)进行解的修缮
 * 这个举动结合Course的不同开始时间的copy，说不定可以解决困扰我们的初始解问题
 *
 * @author s00536729
 * @since 2022-07-18
 */
public class SolutionCourseModifier {
    private ProblemContext problemContext;

    /**
     * 构造器
     *
     * @param problemContext 问题情景
     */
    public SolutionCourseModifier(ProblemContext problemContext) {
        this.problemContext = problemContext;
    }

    public void modify(Solution solution) {
        for (Schedule schedule : problemContext.getSchedules()) {
            if (!schedule.getRealizedNodes().isEmpty()
                && schedule.getRealizedEnterTimes().get(schedule.getRealizedNodes().size()) != 0) {
                // 已经发生的course不再调整
                continue;
            }
            List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() :
                    schedule.getRealizedNodes();
            for (int i = 0; i < nodeList.size() - 1; i++) {
                Node firstNode = nodeList.get(0);
                int delta = 0;
                for (; delta < schedule.getPlannedNodes().size(); delta ++) {
                    if (schedule.getPlannedNodes().get(delta).equals(firstNode)) {
                        break;
                    }
                }
                Link link = schedule.getPlannedLinks().get(i + delta);
                int leaveTime = solution.getScheduleStationDepartureTimeMap().get(schedule).get(i);
                if (schedule.getLateDeparture() != null && i == 0) {
                    leaveTime += schedule.getLateDeparture().getDepartureDelaySeconds();
                    if (!schedule.getRealizedLeaveTimes().containsKey(i + 1)
                        || schedule.getRealizedLeaveTimes().get(i + 1) == 0) {
                        // 如果离站时间尚未实现
                        solution.getScheduleStationDepartureTimeMap().get(schedule).set(i, leaveTime);
                    }
                }
                int arrivalTime = solution.getScheduleStationArrivalTimeMap().get(schedule).get(i + 1);
                int runTime = arrivalTime - leaveTime;
                int[][] minimumRunTime = link.getMinimumRunTime();
                // mark schedule node status, 0 for pass, 1 for stop
                int headStatus = Boolean.TRUE.equals(solution.getScheduleSkipStationMap().get(schedule).
                    get(i)) ? 0 : 1;
                int tailStatus = Boolean.TRUE.equals(solution.getScheduleSkipStationMap().get(schedule).
                    get(i + 1)) ? 0 : 1;
                int actualMinRunTime = minimumRunTime[headStatus][tailStatus];
                for (LinkScenario linkScenario : link.getLinkScenarioList()) {
                    if (linkScenario.getStartTime() <= leaveTime && linkScenario.getEndTime() > leaveTime) {
                        actualMinRunTime = linkScenario.getExtendedRunTime();
                    }
                }
                if (runTime < actualMinRunTime) {
                    arrivalTime = leaveTime + actualMinRunTime;
                    if (!schedule.getRealizedEnterTimes().containsKey(i + 2)
                        || schedule.getRealizedEnterTimes().get(i + 2) == 0) {
                        solution.getScheduleStationArrivalTimeMap().get(schedule).set(i + 1, arrivalTime);
                    }
                }
                if (tailStatus == 0) {
                    if (!schedule.getRealizedLeaveTimes().containsKey(i + 2)
                        || schedule.getRealizedLeaveTimes().get(i + 2) == 0) {
                        solution.getScheduleStationDepartureTimeMap().get(schedule).set(i + 1, arrivalTime);
                    }
                } else {
                    int dwellTime = Constants.INNER_SCHEDULE_NODE_DWELL_TIME;
                    Node tailNode = schedule.getPlannedNodes().get(i + 1);
                    for (StationExtendedDwellScenario sedScenario : tailNode.getStationExtendedDwellScenarios()) {
                        if (sedScenario.getStartTimeSeconds() < arrivalTime
                            && sedScenario.getEndTimeSeconds() > arrivalTime) {
                            // 按照Scenario规定的dwellTime进行延迟
                            dwellTime = sedScenario.getExtendedRunTimeSeconds();

                            // // 直接跳过该站，dwellTime为零
                            // solution.getScheduleSkipStationMap().get(schedule).set(i + 1, Boolean.TRUE);
                            // dwellTime = 0;

                            // 添加scenario到schedule里
                            schedule.getStationExtendedDwellScenarios().add(sedScenario);
                        }
                    }
                    for (TrainExtendedDwellScenario tedScenario : schedule.getTrainExtendedDwellScenarios()) {
                        if (tedScenario.getNode() == tailNode) {
                            // 按照Scenario规定的dwellTime进行延迟
                            dwellTime = tedScenario.getExtendedRunTimeSeconds();

                            // // 直接跳过该站，dwellTime为零
                            // solution.getScheduleSkipStationMap().get(schedule).set(i + 1, Boolean.TRUE);
                            // dwellTime = 0;
                        }
                    }
                    if (!schedule.getRealizedLeaveTimes().containsKey(i + 2)
                        || schedule.getRealizedLeaveTimes().get(i + 2) == 0) {
                        solution.getScheduleStationDepartureTimeMap().get(schedule).set(i + 1, arrivalTime + dwellTime);
                    }
                }
            }
        }
    }
}
