package listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import entity.TrainExtendedDwell;

import java.util.ArrayList;
import java.util.List;

/**
 * TrainExtendedDwellListener （TrainExtendedDwell 倾听器）
 * TrainExtendedDwell 倾听器
 *
 * @author s00536729
 * @since 2022-07-01
 */
public class TrainExtendedDwellListener extends AnalysisEventListener<TrainExtendedDwell> {
    List<TrainExtendedDwell> tedList = new ArrayList<>();
    @Override
    public void invoke(TrainExtendedDwell trainExtendedDwell, AnalysisContext analysisContext) {
        tedList.add(trainExtendedDwell);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

    }

    public List<TrainExtendedDwell> getData() {
        return tedList;
    }
}
