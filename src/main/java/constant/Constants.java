package constant;

import org.apache.commons.lang3.tuple.Triple;

import java.util.ArrayList;
import java.util.List;

/**
 * Constants （常数项）
 * 常数项
 *
 * @author s00536729
 * @since 2022-07-01
 */
public class Constants {
    public static final int CONSTRAINT_VIOLATION_PENALTY = 1000000;
    public static final int INITIAL_SKIP_STATION_PENALTY = 10000;
    public static final int INTER_SCHEDULE_REST_TIME = 60;
    public static final int INNER_SCHEDULE_NODE_DWELL_TIME = 30;
    public static final int CHANGE_END_TIME = 60;
    public static final int TIME_DIFF_TOL = CHANGE_END_TIME; // 预估图中可以之间连接点的时间间隔，和掉头时间一致
    public static final int MINIMUM_SEPARATION_TIME = 30;
    public static final int FIRST_SKIP_STOP_MULTIPLIER = 35;
    public static final int SECOND_SKIP_STOP_MULTIPLIER = 15;
    public static final int SOLVER_TIME_LIMIT = 300;
    public static final double SOLVER_MIP_GAP = 0.01;
    public static final boolean PARTIAL_CANCELLATION_ALLOWED = false; // 是否允许局部取消
    public static final boolean OUTPUT_FLAG = true; // 是否打印算法明细
    public static final int SOLVER_VERBOSITY_LEVEL = 1;
    public static final int BIG_M = 86400;
    public static final int DELAY_PENALTY = 125; // 125 pound per minute
    public static final int FREQUENCY_PENALTY = 150; // 150 pound per minute
    public static final double SECONDS_IN_MINUTE = 60;
    public static final String VIRTUAL_START_VERTEX_NAME = "VirtualStart";
    public static final String VIRTUAL_END_VERTEX_NAME = "VirtualEnd";

    public static final int NONE_DEPOT_TRAIN_WAIT_TIME = 960;

    public static final int[] COURSE_START_TIME_CHANGE = new int[] {0, 30, 60, 90, 120, 180, 240, 360, 420, 540, 660,
            780, 900}; // 代表几种Course起始时间变动的可能性

    public static final List<Triple<Integer, Integer, Integer>> HEADWAY_THRESHOLD = new ArrayList<Triple<Integer, Integer, Integer>>() {{
        add(Triple.of(4800, 22500, 420));
        add(Triple.of(22500, 27900, 252));
        add(Triple.of(27900, 33300, 210));
        add(Triple.of(33300, 60300, 252));
        add(Triple.of(60300, 65700, 210));
        add(Triple.of(65700, 82800, 252));
        add(Triple.of(82800, 86340, 420));
        add(Triple.of(86340, 93600, 630));
    }};

    public static final String WEST_BOUND_REFERENCE_STATION = "PADTLL";
    public static final String EAST_BOUND_REFERENCE_STATION = "WCHAPXR";

    public static final String PADTLL_NODE_CODE = "PADTLL";

    public static final Boolean GENERATE_DUTY_FOR_EACH_ROLLING_STOCK = true;
    public static final Boolean ALLOW_TO_SKIP_STOPS_IN_EVENT_BASED_MODEL = false;
    public static final Boolean ALLOW_PARTIAL_CANCELLATION_IN_EVENT_BASED_MODEL = true;








    // GIDEAPK (GIDEA PARK), use the track in GIDEPKM (GIDEA PARK MIDDLE SIDING)
    public static final String GIDEAPK_NODE_CODE = "GIDEAPK";

    // CHDWLHT (CHADWELL HEATH), use the track in CHDWHTT (CHADWELL HEATH TURNBACK)
    public static final String CHDWLHT_NODE_CODE = "CHDWLHT";
}
