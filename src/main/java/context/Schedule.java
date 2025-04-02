package context;

import context.scenario.LateDepartureScenario;
import context.scenario.RealizedScheduleScenario;
import context.scenario.StationExtendedDwellScenario;
import context.scenario.TrainExtendedDwellScenario;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Schedule （路线）
 * 列车路线
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class Schedule implements Comparable<Schedule> {
    @Override
    public int compareTo(Schedule o) {
        if (this.getStartTime() == o.getStartTime()) {
            return Integer.compare(this.getEndTime(), o.getEndTime());
        } else {
            return Integer.compare(this.getStartTime(), o.getStartTime());
        }
    }

    /**
     * 路线类别，区分乘客路线和空车路线
     */
    public enum Category {
        OO, // passenger ride
        EE  // empty ride
    }

    /**
     * 事件类别
     */
    public enum EventType {
        RESERVE, // 预留
        CHANGE_END, // 掉头
        SPARE, // 空闲
        TRAIN // 列车运行
    }
    private String courseId;
    private Track.Direction direction;
    private Category category;
    private EventType eventType;
    private Node startNode;
    private Node endNode;
    private int startTime;
    private int endTime;
    private List<Node> plannedNodes = new ArrayList<>();
    private List<Link> plannedLinks = new ArrayList<>();
    private Map<Integer, String> nodeStatus = new HashMap<>();
    private Map<Integer, Integer> enterTimes = new HashMap<>();
    private Map<Integer, Integer> leaveTimes = new HashMap<>();
    private Map<Integer, Integer> dwellTimes = new HashMap<>();
    private Map<Integer, String> tracks = new HashMap<>();

    private List<Node> realizedNodes = new ArrayList<>();
    // STOP，PASS，UNREALIZED
    private Map<Integer, String> realizedNodeStatus = new HashMap<>();
    private Map<Integer, Integer> realizedEnterTimes = new HashMap<>();
    private Map<Integer, Integer> realizedLeaveTimes = new HashMap<>();
    private Map<Integer, String> realizedTracks = new HashMap<>();
    private int desDelay;
    private LateDepartureScenario lateDeparture;
    private List<StationExtendedDwellScenario> stationExtendedDwellScenarios = new ArrayList<>();
    private List<TrainExtendedDwellScenario> trainExtendedDwellScenarios = new ArrayList<>();

    /**
     * 更新列车路线状态
     *
     * @param realizedScheduleScenario 已发生的路线情景
     */
    public void updateStatus(RealizedScheduleScenario realizedScheduleScenario) {
//        realizedNodes.addAll(plannedNodes);
        if (realizedScheduleScenario.getSchedule() == this) {
            Node node = realizedScheduleScenario.getNode();
            realizedNodes.add(node);
            int index = realizedScheduleScenario.getSeq();
            realizedEnterTimes.put(index, realizedScheduleScenario.getArrivalSeconds());
            realizedLeaveTimes.put(index, realizedScheduleScenario.getDepartureSeconds());
            if ((realizedScheduleScenario.getDepartureSeconds() == 0 && realizedScheduleScenario.
                getArrivalSeconds() == 0) || (realizedScheduleScenario.getDepartureSeconds() == 0
                && realizedScheduleScenario.getSeq() == 1 )) {
                realizedNodeStatus.put(index, "UNREALIZED");
            } else if (Math.abs(realizedScheduleScenario.getDepartureSeconds() - realizedScheduleScenario.
                    getArrivalSeconds()) <= 1e-6) {
                realizedNodeStatus.put(index, ActivityType.PASS.getValue());
            } else {
                realizedNodeStatus.put(index, ActivityType.STOP.getValue());
            }
            if (StringUtils.isNotEmpty(realizedScheduleScenario.getTrack())) {
                realizedTracks.put(index, realizedScheduleScenario.getTrack());
                if (!node.getName2Track().containsKey(realizedScheduleScenario.getTrack())) {
                    Track track = new Track();
                    track.setNode(node);
                    track.setDirection(direction);
                    track.setName(realizedScheduleScenario.getTrack());
                    node.getTracks().add(track);
                    node.getName2Track().put(track.getName(), track);
                }
            } else {
                int newIndex = realizedScheduleScenario.getSchedule().getPlannedNodes().indexOf(node) + 1;
                realizedTracks.put(index, tracks.get(newIndex));
            }
        }
    }
}
