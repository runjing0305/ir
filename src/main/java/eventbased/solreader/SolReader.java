package eventbased.solreader;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.exception.ExcelAnalysisException;
import com.alibaba.excel.read.metadata.ReadSheet;

import java.util.List;
import java.util.logging.Logger;

/**
 * @author longfei
 */
public class SolReader {
    private static final Logger LOGGER = Logger.getLogger(SolReader.class.getName());

    private final String SOL_FILE_Path = "data/ELIZABETH_LINE_SOL.xlsx";

    public List<SolRollingStockPath> loadSolRollingStockPath() {
        SolRollingStockPathListener solRollingStockPathListener = new SolRollingStockPathListener();
        try (ExcelReader excelReader = EasyExcel.read(SOL_FILE_Path, SolRollingStockPath.class, solRollingStockPathListener).build()) {
            ReadSheet readSheet = EasyExcel.readSheet(1).build();
            excelReader.read(readSheet);
        } catch (ExcelAnalysisException e) {
            LOGGER.info("No Sol Rolling Stock Path");
        }
        return solRollingStockPathListener.getData();
    }

    public List<SolTrainSchedule> loadSolTrainSchedule() {
        SolTrainScheduleListener solTrainScheduleListener = new SolTrainScheduleListener();
        try (ExcelReader excelReader = EasyExcel.read(SOL_FILE_Path, SolTrainSchedule.class, solTrainScheduleListener).build()) {
            ReadSheet readSheet = EasyExcel.readSheet(2).build();
            excelReader.read(readSheet);
        } catch (ExcelAnalysisException e) {
            LOGGER.info("No Sol Train Schedule");
        }
        return solTrainScheduleListener.getData();
    }
}
