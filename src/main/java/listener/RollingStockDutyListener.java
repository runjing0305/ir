package listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import entity.RollingStockDuty;

import java.util.ArrayList;
import java.util.List;

/**
 * RollingStockDutyListener （RollingStockDuty 倾听器）
 * RollingStockDuty 倾听器
 *
 * @author s00536729
 * @since 2022-07-01
 */
public class RollingStockDutyListener extends AnalysisEventListener<RollingStockDuty> {
    List<RollingStockDuty> rsdList = new ArrayList<>();
    @Override
    public void invoke(RollingStockDuty rollingStockDuty, AnalysisContext analysisContext) {
        rsdList.add(rollingStockDuty);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

    }

    public List<RollingStockDuty> getData() {
        return rsdList;
    }
}
