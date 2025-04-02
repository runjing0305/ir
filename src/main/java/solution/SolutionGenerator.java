package solution;

import context.Node;
import context.ProblemContext;
import context.RollingStock;
import context.Schedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SolutionGenerator （解生成器）
 * 基于问题情景，生成解
 *
 * @author s00536729
 * @since 2022-07-18
 */
public class SolutionGenerator {
    private ProblemContext problemContext;

    /**
     * 构造器
     *
     * @param problemContext 问题情景
     */
    public SolutionGenerator(ProblemContext problemContext) {
        this.problemContext = problemContext;
    }

    /**
     * 基于问题情景生成初始解，注意此时它大概率不可行
     *
     * @return Solution 生成的初始解
     */
    public Solution generate() {
        Solution ret = new Solution();
        for (Schedule schedule : problemContext.getSchedules()) {
            RollingStock rs = problemContext.getSchedule2RollingStock().get(schedule);
            if (rs == null) {
                System.out.println(schedule.getCourseId() + " is not assigned");
                continue;
            }
            ret.getSchedule2RollingStockMap().put(schedule, rs);
            if (ret.getRollingStock2ScheduleListMap().containsKey(rs)) {
                ret.getRollingStock2ScheduleListMap().get(rs).add(schedule);
            } else {
                List<Schedule> tempList = new ArrayList<>();
                tempList.add(schedule);
                ret.getRollingStock2ScheduleListMap().put(rs,tempList);
            }
            List<Boolean> skippedStops = new ArrayList<>();
            List<Integer> arrivals = new ArrayList<>();
            List<Integer> departures = new ArrayList<>();
            List<String> tracks = new ArrayList<>();
            if (schedule.getRealizedNodes().isEmpty()) {
                // Unrealized scenario
                for (int n = 0; n < schedule.getPlannedNodes().size(); n++) {
                    skippedStops.add("PASS".equalsIgnoreCase(schedule.getNodeStatus().get(n + 1)) ? Boolean.
                        TRUE : Boolean.FALSE);
                    arrivals.add(schedule.getEnterTimes().get(n + 1));
                    departures.add(schedule.getLeaveTimes().get(n + 1));
                    tracks.add(schedule.getTracks().get(n + 1));
                }
            } else {
                Node firstNode = schedule.getRealizedNodes().get(0);
                int delta = 0;
                for (; delta < schedule.getPlannedNodes().size(); delta ++) {
                    if (schedule.getPlannedNodes().get(delta).equals(firstNode)) {
                        break;
                    }
                }
                for (int n = 0; n < schedule.getRealizedNodes().size(); n++) {
                    if ("UNREALIZED".equalsIgnoreCase(schedule.getRealizedNodeStatus().get(n + 1))) {
                        // Half realized scenario
                        skippedStops.add("PASS".equalsIgnoreCase(schedule.getNodeStatus().get(n + 1)) ? Boolean.
                            TRUE : Boolean.FALSE);
                        arrivals.add(schedule.getEnterTimes().get(n + 1 + delta));
                        departures.add(schedule.getLeaveTimes().get(n + 1 + delta));
                        tracks.add(schedule.getTracks().get(n + 1 + delta));
                    } else {
                        // Realized Scenario
                        skippedStops.add("PASS".equalsIgnoreCase(schedule.getRealizedNodeStatus().get(n + 1)) ? Boolean.
                            TRUE : Boolean.FALSE);
                        arrivals.add(schedule.getRealizedEnterTimes().get(n + 1));
                        departures.add(schedule.getRealizedLeaveTimes().get(n + 1));
                        tracks.add(schedule.getRealizedTracks().get(n + 1));
                    }
                }
            }
            ret.getScheduleSkipStationMap().put(schedule, skippedStops);
            ret.getScheduleStationArrivalTimeMap().put(schedule, arrivals);
            ret.getScheduleStationDepartureTimeMap().put(schedule, departures);
            ret.getScheduleStationTrackMap().put(schedule, tracks);
            // ret.getScheduleDestinationDelayMap().put(schedule, arrivals.get(schedule.getRealizedNodes().size() - 1) - schedule.get);
        }
        return ret;
    }

    /**
     * 将离开时间大于到达时间的站点的状态改为STOP
     * @param solution
     */
    public void updateSkipStatus(Solution solution) {
        for (Map.Entry<Schedule, List<Boolean>> entry : solution.getScheduleSkipStationMap().entrySet()) {
            if (!solution.getScheduleStationArrivalTimeMap().containsKey(entry.getKey()) ||
            !solution.getScheduleStationDepartureTimeMap().containsKey(entry.getKey())) {
                continue;
            }
            List<Integer> arrivals = solution.getScheduleStationArrivalTimeMap().get(entry.getKey());
            List<Integer> departures = solution.getScheduleStationDepartureTimeMap().get(entry.getKey());
            for (int i = 1; i < entry.getValue().size() - 1; ++i) {
                if (!Objects.equals(arrivals.get(i), departures.get(i))) {
                    entry.getValue().set(i, false);
                }
            }
        }
    }
}
