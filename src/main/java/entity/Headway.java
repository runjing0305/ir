package entity;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * Headway （车头间隔）
 * 车头间隔
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Data
public class Headway {
    @ExcelProperty("LINK_START_NODE")
    private String linkStartNode;
    @ExcelProperty("LINK_END_NODE")
    private String linkEndNode;
    @ExcelProperty("START_ACTIVITY_TRAIN_FRONT")
    private String startActivityTrainFront;
    @ExcelProperty("END_ACTIVITY_TRAIN_FRONT")
    private String endActivityTrainFront;
    @ExcelProperty("START_ACTIVITY_TRAIN_BEHIND")
    private String startActivityTrainBehind;
    @ExcelProperty("END_ACTIVITY_TRAIN_BEHIND")
    private String endActivityTrainBehind;
    @ExcelProperty("MINIMUM_HEADWAY_SECONDS")
    private int minimumHeadwaySeconds;
}
