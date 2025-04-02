package entity;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * AllDutyStartEnd （Duty起点终点情况）
 * Duty起点终点情况
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Data
public class AllDutyStartEnd {
    @ExcelProperty("DUTY_ID")
    private String dutyId;
    @ExcelProperty("START_TIME_SECONDS")
    private int startTimeSeconds;
    @ExcelProperty("START_NODE")
    private String startNode;
    @ExcelProperty("END_TIME_SECONDS")
    private int endTimeSeconds;
    @ExcelProperty("END_NODE")
    private String endNode;
}
