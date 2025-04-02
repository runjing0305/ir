package eventbased.solreader;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * @author longfei
 */
@Data
public class SolTrainSchedule {
    @ExcelProperty("TRAIN_COURSE_ID")
    private String trainCourseId;

    @ExcelProperty("SEQ")
    private int seq;

    @ExcelProperty("NODE")
    private String node;

    @ExcelProperty("ARRIVAL_SECONDS")
    private int arrivalSeconds;

    @ExcelProperty("DEPARTURE_SECONDS")
    private int departureSeconds;

    @ExcelProperty("Track")
    private String track;

    @ExcelProperty("Activity")
    private String activity;

    @ExcelProperty("DUTY_ID")
    private String dutyId;

    @ExcelProperty("ARRIVAL_SECONDS_R")
    private int arrivalSecondsR;

    @ExcelProperty("DEPARTURE_SECONDS_R")
    private int departureSecondsR;

    @ExcelProperty("UNREALIZED")
    private String unrealized;
}
