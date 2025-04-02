package context.scenario;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
/**
 * Scenario （情景）
 * 问题用例的所有情景
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class Scenario {
    List<LinkScenario> linkScenarios = new ArrayList<>();
    List<LateDepartureScenario> lateDepartureScenarios = new ArrayList<>();
    List<RealizedScheduleScenario> realizedScheduleScenarios = new ArrayList<>();
    List<StationExtendedDwellScenario> stationExtendedDwellScenarios = new ArrayList<>();
    List<TrainExtendedDwellScenario> trainExtendedDwellScenarios = new ArrayList<>();
}
