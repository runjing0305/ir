package context.scenario;

import context.Node;
import context.Schedule;
import lombok.Getter;
import lombok.Setter;
/**
 * TrainExtendedDwellScenario （列车延长逗留时间情景）
 * 列车延长逗留时间情景
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class TrainExtendedDwellScenario {
    private Schedule schedule;
    private Node node;
    private int extendedRunTimeSeconds;
}
