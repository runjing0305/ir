package eventbased.solreader;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * @author longfei
 */
public class SolRollingStockPathListener extends AnalysisEventListener<SolRollingStockPath> {
    List<SolRollingStockPath> solRollingStockPathList = new ArrayList<>();

    @Override
    public void invoke(SolRollingStockPath solRollingStockPath, AnalysisContext analysisContext) {
        solRollingStockPathList.add(solRollingStockPath);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
    }

    public List<SolRollingStockPath> getData() {
        return solRollingStockPathList;
    }
}
