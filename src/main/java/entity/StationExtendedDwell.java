package entity;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * StationExtendedDwell （车站延长停留时间）
 * 车站延长停留时间
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Data
public class StationExtendedDwell {
    @ExcelProperty("NODE")
    private String node;
    @ExcelProperty("START_TIME_SECONDS")
    private int startTimeSeconds;
    @ExcelProperty("END_TIME_SECONDS")
    private int endTimeSeconds;
    @ExcelProperty("START_TIME_HHMMSS")
    private String startTimeHhmmss;
    @ExcelProperty("END_TIME_HHMMSS")
    private String endTimeHhmmss;
    @ExcelProperty("EXTENDED_RUN_TIME_SECONDS")
    private int extendedRunTimeSeconds;
}
