package entity;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * TrainSchedule （列车路线）
 * 列车路线
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Data
public class TrainSchedule {
    @ExcelProperty("TRAIN_COURSE_ID")
    private String trainCourseId;
    @ExcelProperty("SEQ")
    private int seq;
    @ExcelProperty("REV_SEQ")
    private int revSeq;
    @ExcelProperty("NODE")
    private String node;
    @ExcelProperty("ARRIVAL_SECONDS")
    private int arrivalSeconds;
    @ExcelProperty("ARRIVAL_HHMMSS")
    private String arrivalHhmmss;
    @ExcelProperty("DEPARTURE_SECONDS")
    private int departureSeconds;
    @ExcelProperty("DEPARTURE_HHMMSS")
    private String departureHhmmss;
    @ExcelProperty("Track")
    private String track;
    @ExcelProperty("Activity")
    private String activity;
}
