package listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import entity.PadtllWchapxrTargetFrequency;

import java.util.ArrayList;
import java.util.List;

/**
 * PadtllWchapxrTargetFrequencyListener （PadtllWchapxrTargetFrequency 倾听器）
 * PadtllWchapxrTargetFrequency 倾听器
 *
 * @author s00536729
 * @since 2022-07-01
 */
public class PadtllWchapxrTargetFrequencyListener extends AnalysisEventListener<PadtllWchapxrTargetFrequency> {
    List<PadtllWchapxrTargetFrequency> pwtfList = new ArrayList<>();
    @Override
    public void invoke(PadtllWchapxrTargetFrequency pwtf, AnalysisContext analysisContext) {
        pwtfList.add(pwtf);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

    }

    public List<PadtllWchapxrTargetFrequency> getData() {
        return pwtfList;
    }
}
