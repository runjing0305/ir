package reschedule.graph;

import context.RollingStock;
import context.Schedule;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TimeItem {
    private final int startTime;
    private int endTime = 0;

    private Schedule schedule = null;

    private RollingStock rollingStock = null;

    private int nodeSeq = 0;

    String track = null;

    public TimeItem(int startTime, int endTime) {
        this.endTime = endTime;
        this.startTime = startTime;
    }

    public TimeItem(int startTime) {
        this.startTime = startTime;
    }
}
