package listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import entity.TrainHeader;

import java.util.ArrayList;
import java.util.List;

/**
 * TrainHeaderListener （TrainHeader 倾听器）
 * TrainHeader 倾听器
 *
 * @author s00536729
 * @since 2022-07-01
 */
public class TrainHeaderListener extends AnalysisEventListener<TrainHeader> {
    List<TrainHeader> trainHeaderList = new ArrayList<>();
    @Override
    public void invoke(TrainHeader trainHeader, AnalysisContext analysisContext) {
        trainHeaderList.add(trainHeader);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

    }

    public List<TrainHeader> getData() {
        return trainHeaderList;
    }
}
