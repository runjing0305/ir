package solution;

import constant.Constants;
import context.Node;
import context.ProblemContext;
import context.RollingStock;
import context.Schedule;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

/**
 * Solution （问题解）
 * 功能详细描述
 *
 * @author s00536729
 * @since 2022-06-17
 */
@Getter
@Setter
public class Solution {
    private int resultStatus; // 求解器的返回状态
    private double objValue = 0; // 解的目标函数值
    private double vioDegree = 0; // 解的约束违反值
    private long elapsedTime = 0; // 得到解的算法耗时（单位：秒）

    // Schedule, RollingStock 是否还跟ProblemContext中的相同?
    // 对象相同，但是增加了信息
    private Map<Schedule, RollingStock> schedule2RollingStockMap = new HashMap<>(); // 每个路线分配的列车组
    private Map<Schedule, List<Boolean>> scheduleSkipStationMap = new HashMap<>(); // 代表路线在每个station是否跳站
    private Map<Schedule, Integer> scheduleDestinationDelayMap = new HashMap<>(); // 代表路线到终点的时延
    // 第一个Node的Arrival Time：null
    // 最后一个Node的Departure Time：null
    private Map<Schedule, List<Integer>> scheduleStationArrivalTimeMap = new HashMap<>(); // 代表路线每个station到达时间
    private Map<Schedule, List<Integer>> scheduleStationDepartureTimeMap = new HashMap<>(); // 代表路线每个station离开时间
    private Map<Schedule, List<String>> scheduleStationTrackMap = new HashMap<>(); // 代表路线每个station占用的轨道
    private Map<RollingStock, List<Schedule>> rollingStock2ScheduleListMap = new HashMap<>(); // 代表每个列车组的路线列表

    // 对违反度进行展开
    private Map<String, Integer> runTimeVioMap = new HashMap<>(); // key: course_link
    private Map<String, Integer> headwayVioMap = new HashMap<>(); // key: course1_link1_course2_link1
    private Map<String, Integer> trackVioMap = new HashMap<>(); // key: course1_node1_course2_node1
    private Map<String, Integer> rollingStockVioMap = new HashMap<>(); // key: course1_course2
    private Set<Schedule> vioSchedules = new HashSet<>();

    private Map<RollingStock, List<String>> rollingStock2DutyListMap = new HashMap<>();
    private Map<String, List<Schedule>> duty2ScheduleListMap = new HashMap<>();

    private Map<String, Double> nonZeroValMap = new HashMap<>();

    private Map<Schedule, Integer> schedulePartialCancellationEndIndexMap = new HashMap<>();
    private Map<Schedule, Integer> schedulePartialCancellationStartIndexMap = new HashMap<>();

    /**
     * 空构造器
     */
    public Solution() {

    }

    /**
     * 基于当前解的构造器
     *
     * @param solution 当前解
     */
    public Solution(Solution solution) {
        this.resultStatus = solution.resultStatus;
        this.objValue = solution.objValue;
        this.vioDegree = solution.vioDegree;
        this.elapsedTime = solution.getElapsedTime();
        this.schedule2RollingStockMap = new HashMap<>(solution.schedule2RollingStockMap);
        this.scheduleSkipStationMap = new HashMap<>(solution.scheduleSkipStationMap);
        this.scheduleDestinationDelayMap = new HashMap<>(solution.scheduleDestinationDelayMap);
        this.scheduleStationArrivalTimeMap = new HashMap<>(solution.scheduleStationArrivalTimeMap);
        this.scheduleStationDepartureTimeMap = new HashMap<>(solution.scheduleStationDepartureTimeMap);
        this.scheduleStationTrackMap = new HashMap<>(solution.scheduleStationTrackMap);
        this.rollingStock2ScheduleListMap = new HashMap<>(solution.rollingStock2ScheduleListMap);
        this.rollingStock2DutyListMap = new HashMap<>(solution.getRollingStock2DutyListMap());
        this.duty2ScheduleListMap = new HashMap<>(solution.getDuty2ScheduleListMap());
        this.schedulePartialCancellationEndIndexMap = new HashMap<>(solution.getSchedulePartialCancellationEndIndexMap());
        this.schedulePartialCancellationStartIndexMap = new HashMap<>(solution.getSchedulePartialCancellationStartIndexMap());
    }

    /**
     * 基于问题解评估目标函数和约束违反度，并打印信息
     *
     * @param solution 当前解
     * @param problemContext Problem data
     */
    public static void printSolInfo(Solution solution, ProblemContext problemContext) {
        SolutionEvaluator se = new SolutionEvaluator(problemContext);
        solution.setObjValue(se.calcObj(solution));
        clearVioMaps(solution);
        solution.setVioDegree(se.calcVio(solution));
        System.out.println("Sol status: " + solution.getResultStatus() + ", obj: " + solution.getObjValue() +
                ", vio: " + solution.getVioDegree());
    }

    public static void clearVioMaps(Solution solution) {
        solution.getRunTimeVioMap().clear();
        solution.getHeadwayVioMap().clear();
        solution.getTrackVioMap().clear();
        solution.getRollingStockVioMap().clear();
        solution.getVioSchedules().clear();
    }

    /**
     * 根据问题解的数据刷新目标函数值，仅考虑了跳站和延迟惩罚
     */
    @Deprecated
    public void updateObjectValue() {
        double totalDelayPenalty = 0.0;
        for (Map.Entry<Schedule, Integer> entry : scheduleDestinationDelayMap.entrySet()) {
            if (entry.getKey().getCategory().equals(Schedule.Category.OO)) {
                totalDelayPenalty += entry.getValue() * Constants.DELAY_PENALTY / Constants.SECONDS_IN_MINUTE;
            }
        }
        double totalSkipStationPenalty = 0.0;
        for (Map.Entry<Schedule, List<Boolean>> entry : scheduleSkipStationMap.entrySet()) {
            if (entry.getKey().getCategory().equals(Schedule.Category.EE)) {
                continue;
            }
            List<Node> nodes = entry.getKey().getPlannedNodes();
            List<Boolean> skipStationStatus = entry.getValue();
            for (int i = 0; i < nodes.size(); i++) {
                Boolean b = skipStationStatus.get(i);
                if (Boolean.TRUE.equals(b)) {
                    totalSkipStationPenalty += nodes.get(i).getAvgBsv() * Constants.SECOND_SKIP_STOP_MULTIPLIER +
                            Constants.INITIAL_SKIP_STATION_PENALTY;
                }
            }
        }
        objValue = totalSkipStationPenalty + totalDelayPenalty;
    }

    public void generateDutyList() {
        for (Map.Entry<RollingStock, List<Schedule>> entry : rollingStock2ScheduleListMap.entrySet()) {
            int rollingStockIndex = entry.getKey().getIndex();

            String dutyId = String.valueOf(rollingStockIndex);
            rollingStock2DutyListMap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(dutyId);
            duty2ScheduleListMap.put(dutyId, entry.getValue());
        }
    }

    public Pair<Integer, Integer> getCourseNum() {
        int eeCourseNum = 0;
        int ooCourseNum = 0;
        for (Schedule schedule : schedule2RollingStockMap.keySet()) {
            if (Schedule.Category.EE == schedule.getCategory()) {
                eeCourseNum += 1;
            } else {
                ooCourseNum += 1;
            }
        }

        return Pair.of(eeCourseNum, ooCourseNum);
    }
}
