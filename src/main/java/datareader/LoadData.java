package datareader;

import entity.*;

import java.util.List;

/**
 * LoadData （载入数据）
 * 载入数据
 *
 * @author s00536729
 * @since 2022-07-01
 */
public interface LoadData {
    /**
     * 载入AllDutyStartEnd数据
     *
     * @return List<AllDutyStartEnd>
     */
    public List<AllDutyStartEnd> loadAllDutyStartAndEnd();

    /**
     * 载入基站价值数据
     *
     * @return List<BaseStationValue>
     */
    public List<BaseStationValue> loadBaseStationValue();

    /**
     * 载入车头间隔数据
     *
     * @return List<Headway>
     */
    public List<Headway> loadHeadway();

    /**
     * 载入链路数据
     *
     * @return List<Link>
     */
    public List<Link> loadLink();

    /**
     * 载入最小运行时间数据
     *
     * @return List<MinimumRunTime>
     */
    public List<MinimumRunTime> loadMinimumRunTime();

    /**
     * 载入顶点数据
     *
     * @return List<Node>
     */
    public List<Node> loadNode();

    /**
     * 载入目标频率数据
     *
     * @return List<PadtllWchapxrTargetFrequency>
     */
    public List<PadtllWchapxrTargetFrequency> loadPadtllWchapxrTargetFrequency();

    /**
     * 载入列车职责数据
     *
     * @return List<RollingStockDuty>
     */
    public List<RollingStockDuty> loadRollingStockDuty();

    /**
     * 载入列车路线简述数据
     *
     * @return List<TrainHeader>
     */
    public List<TrainHeader> loadTrainHeader();

    /**
     * 载入列车路线数据
     *
     * @return List<TrainSchedule>
     */
    public List<TrainSchedule> loadTrainSchedule();

    /**
     * 载入延长运行时间数据
     *
     * @return List<ExtendedRunTime>
     */
    public List<ExtendedRunTime> loadExtendedRunTime();

    /**
     * 载入延迟出发数据
     *
     * @return List<LateDeparture>
     */
    public List<LateDeparture> loadLateDeparture();

    /**
     * 载入已发生路线数据
     *
     * @return List<RealizedSchedule>
     */
    public List<RealizedSchedule> loadRealizedSchedule();

    /**
     * 载入车站延迟逗留数据
     *
     * @return List<StationExtendedDwell>
     */
    public List<StationExtendedDwell> loadStationExtendedDwell();

    /**
     * 载入列车路线延迟逗留数据
     *
     * @return List<TrainExtendedDwell>
     */
    public List<TrainExtendedDwell> loadTrainExtendedDwell();

    /**
     * 从Python生成的csv文件中读取Course-level的解
     *
     * @return List<TrainExtendedDwell>
     */
    public List<TrainSchedule> loadCourseSolFromCsv();

    /**
     * 读取python生成的rollingStock路径
     * @return
     */
    public List<String> loadRollingStockSol();

    String loadProblemId();
}
