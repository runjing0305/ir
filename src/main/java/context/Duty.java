package context;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
/**
 * Duty （列车职责）
 * 列车职责
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class Duty {
    private String dutyId;
    private int startTime;
    private int endTime;
    private Node startNode;
    private Node endNode;
    private List<Schedule> plannedSchedules = new ArrayList<>();
    private List<Schedule> realizedSchedules = new ArrayList<>();
}
