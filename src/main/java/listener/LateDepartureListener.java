package listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import entity.LateDeparture;

import java.util.ArrayList;
import java.util.List;

/**
 * LateDepartureListener （LateDeparture 倾听器）
 * LateDeparture 倾听器
 *
 * @author s00536729
 * @since 2022-07-01
 */
public class LateDepartureListener extends AnalysisEventListener<LateDeparture> {
    List<LateDeparture> lateDepartureList = new ArrayList<>();
    @Override
    public void invoke(LateDeparture lateDeparture, AnalysisContext analysisContext) {
        lateDepartureList.add(lateDeparture);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

    }

    public List<LateDeparture> getData() {
        return lateDepartureList;
    }
}
