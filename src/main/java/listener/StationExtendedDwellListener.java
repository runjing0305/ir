package listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import entity.StationExtendedDwell;

import java.util.ArrayList;
import java.util.List;

/**
 * StationExtendedDwellListener （StationExtendedDwell 倾听器）
 * StationExtendedDwell 倾听器
 *
 * @author s00536729
 * @since 2022-07-01
 */
public class StationExtendedDwellListener extends AnalysisEventListener<StationExtendedDwell> {
    List<StationExtendedDwell> sedList = new ArrayList<>();
    @Override
    public void invoke(StationExtendedDwell stationExtendedDwell, AnalysisContext analysisContext) {
        sedList.add(stationExtendedDwell);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

    }

    public List<StationExtendedDwell> getData() {
        return sedList;
    }
}
