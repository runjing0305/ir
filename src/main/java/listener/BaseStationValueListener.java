package listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import entity.BaseStationValue;

import java.util.ArrayList;
import java.util.List;

/**
 * BaseStationValueListener （BaseStationValue 倾听器）
 * BaseStationValue 倾听器
 *
 * @author s00536729
 * @since 2022-07-01
 */
public class BaseStationValueListener extends AnalysisEventListener<BaseStationValue> {
    List<BaseStationValue> bsvList = new ArrayList<>();

    @Override
    public void invoke(BaseStationValue baseStationValue, AnalysisContext analysisContext) {
        bsvList.add(baseStationValue);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

    }

    public List<BaseStationValue> getData() {
        return bsvList;
    }
}
