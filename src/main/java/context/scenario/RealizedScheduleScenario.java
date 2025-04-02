package context.scenario;

import context.Node;
import context.Schedule;
import lombok.Getter;
import lombok.Setter;
/**
 * RealizedScheduleScenario （已发生路线运行情景）
 * 已发生路线运行情景
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class RealizedScheduleScenario {
    private Schedule schedule;
    private int seq;
    private Node node;
    private int arrivalSeconds; // 为0代表当前还未到达该站点
    private int departureSeconds; // 为0代表当前还未离开该站点
    private String track;
}
