package context;

import context.scenario.StationExtendedDwellScenario;
import entity.BaseStationValue;
import entity.PadtllWchapxrTargetFrequency;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

/**
 * Node （站点）
 * 站点
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class Node {
    /**
     * 站点的类型
     */
    public enum Type {
        CONTROL_POINT,
        JUNCTION,
        STATION
    }
    private int index;
    private String name;
    private String code;
    private Type type;
    private List<Track> tracks;
    private Map<String, Track> name2Track = new HashMap<>();
    private List<BaseStationValue> bsvList = new ArrayList<>();
    private double avgBsv;
    private Map<Track.Direction, List<PadtllWchapxrTargetFrequency>> targetFrequency = new HashMap<>();
    private int actualDwellTime; // seconds
    private int plannedDwellTime; // seconds
    private double latitude;
    private double longitude;
    private String stEb; // null: no short turning, 1: relevant track, 2: reverse track, 3: both track
    private String stWb; // same as stEb
    private boolean isDepot;
    private List<Schedule> schedules = new ArrayList<>();
    private List<StationExtendedDwellScenario> stationExtendedDwellScenarios = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Node node = (Node) o;
        return name.equals(node.name) && code.equals(node.code) && type == node.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, code, type);
    }
}
