package entity;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * TrainHeader （列车路线的简述）
 * 列车路线的简述
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Data
public class TrainHeader {
    @ExcelProperty("TRAIN_COURSE_ID")
    private String trainCourseId;
    @ExcelProperty("DIRECTION")
    private String direction;
    @ExcelProperty("CATEGORY")
    private String category;
    @ExcelProperty("START_SECONDS")
    private int startSeconds;
    @ExcelProperty("END_SECONDS")
    private int endSeconds;
    @ExcelProperty("START_NODE")
    private String startNode;
    @ExcelProperty("END_NODE")
    private String endNode;
}
