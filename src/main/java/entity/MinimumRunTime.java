package entity;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * MinimumRunTime （最小运行时间）
 * 最小运行时间
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Data
public class MinimumRunTime {
    @ExcelProperty("LINK_START_NODE")
    private String linkStartNode;
    @ExcelProperty("LINK_END_NODE")
    private String linkEndNode;
    @ExcelProperty("START_ACTIVITY")
    private String startActivity;
    @ExcelProperty("END_ACTIVITY")
    private String endActivity;
    @ExcelProperty("MINIMUM_RUN_TIME_SECONDS")
    private int minimumRunTimeSeconds;
}
