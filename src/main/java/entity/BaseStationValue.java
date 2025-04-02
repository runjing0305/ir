package entity;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * BaseStationValue （基站价值）
 * 基站价值
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Data
public class BaseStationValue {
    @ExcelProperty("NODE")
    private String node;
    @ExcelProperty("DIRECTION")
    private String direction;
    @ExcelProperty("TIMEBAND")
    private String timeBand;
    @ExcelProperty("START_TIMEBAND_SECONDS")
    private int startTimeBandSeconds;
    @ExcelProperty("END_TIMEBAND_SECONDS")
    private int endTimeBandSeconds;
    @ExcelProperty("BSV")
    private int bsv;
}
