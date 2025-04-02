package solution;

import context.Node;
import context.Schedule;
import context.Track;
import lombok.Getter;
import lombok.Setter;

/**
 * TrackElement （途径轨道的元素）
 * 到达某一个特定站点轨道的Schedule的到达和离开的时间
 *
 * @author s00536729
 * @since 2022-06-20
 */
@Getter
@Setter
public class TrackElement implements Comparable<TrackElement> {
    private Track track;
    private Node node;
    private Schedule schedule;
    private int nodeIndex;
    private int arrival;
    private int departure;

    private int timeChange; // 记录schedule平移了多少

    private boolean nodeRealized;

    public TrackElement(Track track, Node node, Schedule schedule, int nodeIndex, int arrival, int departure, boolean nodeRealized) {
        this.track = track;
        this.node = node;
        this.schedule = schedule;
        this.nodeIndex = nodeIndex;
        this.arrival = arrival;
        this.departure = departure;
        this.nodeRealized = nodeRealized;
    }

    @Override
    public int compareTo(TrackElement o) {
        if (arrival == o.getArrival()) {
            return Integer.compare(departure, o.getDeparture());
        } else {
            return Integer.compare(arrival, o.arrival);
        }
    }
}
