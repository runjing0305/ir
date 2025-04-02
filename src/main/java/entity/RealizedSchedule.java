package entity;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * RealizedSchedule （已发生的路线运行情况）
 * 已发生的路线运行情况
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Data
public class RealizedSchedule {
    @ExcelProperty("TRAIN_COURSE_ID")
    private String trainCourseId;
    @ExcelProperty("SEQ")
    private int seq;
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
    @ExcelProperty("TRACK")
    private String track;
}
