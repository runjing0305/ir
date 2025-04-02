package listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import entity.AllDutyStartEnd;

import java.util.ArrayList;
import java.util.List;
/**
 * AllDutyStartEndListener （AllDutyStartAndEnd 倾听器）
 * AllDutyStartAndEnd 倾听器
 *
 * @author s00536729
 * @since 2022-07-01
 */
public class AllDutyStartEndListener extends AnalysisEventListener<AllDutyStartEnd> {
    List<AllDutyStartEnd> dutyList = new ArrayList<>();

    @Override
    public void invoke(AllDutyStartEnd allDutyStartEnd, AnalysisContext analysisContext) {
        dutyList.add(allDutyStartEnd);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

    }

    public List<AllDutyStartEnd> getData() {
        return dutyList;
    }
}
