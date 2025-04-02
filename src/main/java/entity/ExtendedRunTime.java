package entity;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * ExtendedRunTime （延长运行时间）
 * 边的延长运行时间
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Data
public class ExtendedRunTime {
    @ExcelProperty("LINK_START_NODE")
    private String linkStartNode;
    @ExcelProperty("LINK_END_NODE")
    private String linkEndNode;
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
