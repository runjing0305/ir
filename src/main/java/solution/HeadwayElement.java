package solution;

import context.Link;
import context.Schedule;
import lombok.Getter;
import lombok.Setter;

import java.util.Comparator;

/**
 * HeadwayElement （途径相同Link的元素）
 * 到达某一个特定Link的Schedule的到达和离开的时间以及头尾站点的状态（STOP or PASS)
 *
 * @author s00536729
 * @since 2022-06-20
 */
@Getter
@Setter
public class HeadwayElement implements Comparable<HeadwayElement>{
    private Link link;
    private int headStatus;
    private int tailStatus;
    private int headArrival;
    private int headDeparture;
    private int tailArrival;
    private int tailDeparture;
    private Schedule schedule;

    private boolean headNodeArrivalRealized;
    private boolean headNodeDepartureRealized;
    private boolean tailNodeArrivalRealized;
    private boolean tailNodeDepartureRealized;

    private int change; // 代表时间上的平移度

    /**
     * 基于问题解的信息构造HeadwayElement
     *
     * @param link 链路
     * @param schedule 路线
     * @param j 路线的第几条边
     * @param solution 问题解
     */
    public HeadwayElement(Link link, Schedule schedule, int j, Solution solution, boolean headNodeArrivalRealized, boolean headNodeDepartureRealized, boolean tailNodeArrivalRealized, boolean tailNodeDepartureRealized) {
        this.link = link;
        this.headStatus = Boolean.TRUE.equals(solution.getScheduleSkipStationMap().get(schedule).get(j)) ?
                0 : 1;
        this.tailStatus = Boolean.TRUE.equals(solution.getScheduleSkipStationMap().get(schedule).get(j + 1)) ?
                0 : 1;
        this.headArrival = solution.getScheduleStationArrivalTimeMap().get(schedule).get(j) == null ?
            solution.getScheduleStationDepartureTimeMap().get(schedule).get(j) :
            solution.getScheduleStationArrivalTimeMap().get(schedule).get(j);
        this.headDeparture = solution.getScheduleStationDepartureTimeMap().get(schedule).get(j);
        if (j == 0 && link.getStartNode().isDepot()) {
            this.headArrival = this.headDeparture;
        }
        this.tailArrival = solution.getScheduleStationArrivalTimeMap().get(schedule).get(j + 1);
        this.tailDeparture = solution.getScheduleStationDepartureTimeMap().get(schedule).get(j + 1) == null ? solution.getScheduleStationArrivalTimeMap().get(schedule).get(j + 1) : solution.getScheduleStationDepartureTimeMap().get(schedule).get(j + 1);
        this.schedule = schedule;

        this.headNodeArrivalRealized = headNodeArrivalRealized;
        this.headNodeDepartureRealized = headNodeDepartureRealized;
        this.tailNodeArrivalRealized = tailNodeArrivalRealized;
        this.tailNodeDepartureRealized = tailNodeDepartureRealized;
    }

    /**
     * 基于初始状态构造HeadwayElement
     *
     * @param link 链路
     * @param schedule 路线
     * @param j 路线的第几条边
     */
    public HeadwayElement(Link link, Schedule schedule, int j) {
        this.link = link;
        this.headStatus = schedule.getNodeStatus().get(j + 1).equalsIgnoreCase("PASS") ? 0 : 1;
        this.tailStatus = schedule.getNodeStatus().get(j + 2).equalsIgnoreCase("PASS") ? 0 : 1;
        this.headArrival = schedule.getEnterTimes().get(j + 1);
        this.headDeparture = schedule.getLeaveTimes().get(j + 1);
//        this.tailArrival = schedule.getEnterTimes().get(j + 2);
//        if (schedule.getLeaveTimes().containsKey(j + 2)) {
//            this.tailDeparture = schedule.getLeaveTimes().get(j + 2);
//        }
        this.schedule = schedule;
    }

    /**
     * 空构造器
     */
    public HeadwayElement() {
    }

    @Override
    public int compareTo(HeadwayElement o) {
        if (headArrival == o.headArrival) {
            return Integer.compare(headDeparture, o.headDeparture);
        } else {
            return Integer.compare(headArrival, o.headArrival);
        }
    }
}
