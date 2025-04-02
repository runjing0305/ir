package context;

import lombok.Getter;
import lombok.Setter;

/**
 * Track （轨道）
 * 车站轨道
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class Track {
    /**
     * 轨道的方向类
     */
    public enum Direction {
        WB,
        EB,
        BOTH,
    }
    private String name;
    private Direction direction;
    private Node node;
}
