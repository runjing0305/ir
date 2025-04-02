package solutionwriter;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class ScheduleSolution {
    @ExcelProperty("TRAIN_COURSE_ID")
    private String trainCourseId;
    @ExcelProperty("DUTY_ID")
    private int rollingStock;
    @ExcelProperty("SEQ")
    private int seq;
    @ExcelProperty("NODE")
    private String node;
    @ExcelProperty("ARRIVAL_SECONDS")
    private String arrivalSeconds;
    @ExcelProperty("DEPARTURE_SECONDS")
    private String departureSeconds;
    @ExcelProperty("Activity")
    private String status;
    @ExcelProperty("Track")
    private String track;
}
