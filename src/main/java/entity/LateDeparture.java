package entity;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * LateDeparture （晚出发）
 * 晚出发
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Data
public class LateDeparture {
    @ExcelProperty("TRAIN_COURSE_ID")
    private String trainCourseId;
    @ExcelProperty("DEPARTURE_DELAY_SECONDS")
    private int departureDelaySeconds;
}
