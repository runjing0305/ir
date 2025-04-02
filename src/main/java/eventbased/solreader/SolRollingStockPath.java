package eventbased.solreader;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * @author longfei
 */
@Data
public class SolRollingStockPath {
    @ExcelProperty("ROLLING_STOCK_ID")
    private int rollingStockId;
    @ExcelProperty("PATH")
    private String path;
}
