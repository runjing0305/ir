package context.scenario;

import context.Link;
import lombok.Getter;
import lombok.Setter;
/**
 * LinkScenario （链路延长运行时间情景）
 * 链路延长运行时间情景
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class LinkScenario {
    private Link link;
    private int startTime;
    private int endTime;
    private int extendedRunTime;
}
