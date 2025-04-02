package entity;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * RollingStockDuty （列车组计划职责）
 * 描述列车组计划执行的路线（Train Course）
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Data
public class RollingStockDuty {
    @ExcelProperty("DUTY_ID")
    private String dutyId;
    @ExcelProperty("SEQ")
    private int seq;
    @ExcelProperty("REV_SEQ")
    private int revSeq;
    @ExcelProperty("Comp idx")
    private int compIdx;
    @ExcelProperty("START_TIME_SECONDS")
    private int startTimeSeconds;
    @ExcelProperty("START_TIME_HHMMSS")
    private String startTimeHhmmss;
    @ExcelProperty("END_TIME_SECONDS")
    private int endTimeSeconds;
    @ExcelProperty("END_TIME_HHMMSS")
    private String endTimeHhmmss;
    @ExcelProperty("START_NODE")
    private String startNode;
    @ExcelProperty("END_NODE")
    private String endNode;
    @ExcelProperty("EVENT_TYPE")
    private String eventType;
    @ExcelProperty("TRAIN_COURSE_ID")
    private String trainCourseId;
}
