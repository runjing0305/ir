package context.scenario;

import context.Schedule;
import lombok.Getter;
import lombok.Setter;
/**
 * LateDepartureScenario （延迟出发情景）
 * 延迟出发情景
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class LateDepartureScenario {
    private Schedule schedule;
    private int departureDelaySeconds;
}
