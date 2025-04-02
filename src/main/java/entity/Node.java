package entity;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * Node （站点）
 * 站点
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Data
public class Node {
    @ExcelProperty("NAME")
    private String name;
    @ExcelProperty("CODE")
    private String code;
    @ExcelProperty("NODE_CATEGORY")
    private String category;
    @ExcelProperty("EB_TRACKS")
    private String ebTracks;
    @ExcelProperty("WB_TRACKS")
    private String wbTracks;
    @ExcelProperty("LATITUDE")
    private double latitude;
    @ExcelProperty("LONGITUDE")
    private double longitude;
    @ExcelProperty("ST_EB")
    private String stEb;
    @ExcelProperty("ST_WB")
    private String stWb;
}
