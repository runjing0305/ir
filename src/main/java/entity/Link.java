package entity;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * Link （链路）
 * 链路
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Data
public class Link {
    @ExcelProperty("START_NODE")
    private String startNode;
    @ExcelProperty("END_NODE")
    private String endNode;
    @ExcelProperty("DIRECTION")
    private String direction;
    @ExcelProperty("DISTANCE_METERS")
    private int distanceMeters;
}
