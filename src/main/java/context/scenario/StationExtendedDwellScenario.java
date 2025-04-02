package context.scenario;

import context.Node;
import lombok.Getter;
import lombok.Setter;
/**
 * StationExtendedDwellScenario （车站延长逗留时间情景）
 * 车站延长逗留时间情景
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class StationExtendedDwellScenario {
    private Node node;
    private int startTimeSeconds;
    private int endTimeSeconds;
    private int extendedRunTimeSeconds;
}
