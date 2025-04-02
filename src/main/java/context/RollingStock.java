package context;

import context.scenario.Scenario;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * RollingStock （列车组）
 * 列车组
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class RollingStock {
    private int index;
    private Node startPos;
    private List<Duty> plannedDuties = new ArrayList<>();
    private List<Duty> realizedDuties = new ArrayList<>();
    private TreeSet<Schedule> schedules = new TreeSet<>();
    private List<Schedule> realizedSchedules = new ArrayList<>();
    protected Node nextNode = null; // 实际上是“当前任务”
    protected Schedule nextSchedule = null;
    protected int nextNodeTime = 0;
    protected boolean nextNodeArrival=true;
    protected Course nextCourse=null;

    /**
     * 列车组更新状态
     *
     * @param scenario 情景
     */
    public void updateStatus(Scenario scenario) {

    }
}
