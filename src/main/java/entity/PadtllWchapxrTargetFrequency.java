package entity;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * PadtllWchapxrTargetFrequency （Padtll和Wchapxr目标列车运行频率）
 * Padtll和Wchapxr目标列车运行频率
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Data
public class PadtllWchapxrTargetFrequency {
    @ExcelProperty("START_TIME_SECONDS")
    private int startTimeSeconds;
    @ExcelProperty("END_TIME_SECONDS")
    private int endTimeSeconds;
    @ExcelProperty("START_TIME_HHMM")
    private String startTimeHhmm;
    @ExcelProperty("END_TIME_HHMM")
    private String endTimeHhmm;
    @ExcelProperty("THRESHOLD_HEADWAY_SECONDS")
    private int thresholdHeadwaySeconds;
}
