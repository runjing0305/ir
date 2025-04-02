package datareader;

import context.ProblemContext;
import dataprocess.ContextFactory;
import entity.*;

import java.util.List;

public class TestDataReader {
    public static void main(String[] arg) {
        DataReader reader = new DataReader();
        List<AllDutyStartEnd> allDutyStartEndList = reader.loadAllDutyStartAndEnd();
        List<BaseStationValue> baseStationValueList = reader.loadBaseStationValue();
        List<ExtendedRunTime> extendedRunTimeList = reader.loadExtendedRunTime();
        List<Headway> headwayList = reader.loadHeadway();
        List<LateDeparture> lateDepartureList = reader.loadLateDeparture();
        List<Link> linkList = reader.loadLink();
        List<MinimumRunTime> minimumRunTimeList = reader.loadMinimumRunTime();
        List<Node> nodeList = reader.loadNode();
        List<PadtllWchapxrTargetFrequency> pwtfList = reader.loadPadtllWchapxrTargetFrequency();
        List<RealizedSchedule> realizedScheduleList = reader.loadRealizedSchedule();
        List<RollingStockDuty> rollingStockDutyList = reader.loadRollingStockDuty();
        List<StationExtendedDwell> StationExtendedDwellList = reader.loadStationExtendedDwell();
        List<TrainExtendedDwell> trainExtendedDwellList = reader.loadTrainExtendedDwell();
        List<TrainHeader> trainHeaderList = reader.loadTrainHeader();
        List<TrainSchedule> trainScheduleList = reader.loadTrainSchedule();
        System.out.println("Data reader works!");
    }
}
