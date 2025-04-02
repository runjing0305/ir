package entity;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * TrainExtendedDwell （列车路线延长停留时间）
 * 列车路线延长停留时间
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Data
public class TrainExtendedDwell {
    @ExcelProperty("TRAIN_COURSE_ID")
    private String trainCourseId;
    @ExcelProperty("NODE")
    private String node;
    @ExcelProperty("EXTENDED_RUN_TIME_SECONDS")
    private int extendedRunTimeSeconds;
}
