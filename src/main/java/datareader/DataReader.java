package datareader;


import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.exception.ExcelAnalysisException;
import com.alibaba.excel.read.metadata.ReadSheet;
import entity.*;
import listener.*;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;

/**
 * DataReader （数据读取器）
 * 读取数据
 *
 * @author s00536729
 * @since 2022-07-01
 */
public class DataReader implements LoadData {
    private static final Logger LOGGER = Logger.getLogger(DataReader.class.getName());

    private static final List<Triple<String, String, String>> PROBLEM_SOL_FILES_LIST = new ArrayList<Triple<String, String, String>>() {{
        add(Triple.of("INSIDE_COS", "data/ELIZABETH_LINE_DATA_INCIDENT_INSIDE_COS.xlsx", "data/sol_final_COS_20220823.csv"));
        add(Triple.of("ABWDXR", "data/ELIZABETH_LINE_DATA_INCIDENT_ABWDXR.xlsx", "data/sol_final_ABWDXR.csv"));
        add(Triple.of("TO_HEATHROW", "data/ELIZABETH_LINE_DATA_INCIDENT_TO_HEATHROW.xlsx", "data/sol_final_HEATHROW.csv"));
        add(Triple.of("ILL_PASSENGER", "data/ELIZABETH_LINE_DATA_ILL_PASSENGER.xlsx", "data/sol_final_ILL_PASSENGER_20220922_2.csv"));
        add(Triple.of("ORIGINAL_INSTANCE", "data/ELIZABETH_LINE_DATA_V2.xlsx", "data/sol_final_case_org.csv"));
    }};

    public static final List<Pair<String, String>> FINAL_SOLUTION_FILES_LIST = new ArrayList<Pair<String, String>>() {{
        add(Pair.of("data/final_sol_files/20220926/Incident_inside_COS_ROLLING_STOCK_DUTY.csv", "data/final_sol_files/20220926/Incident_inside_COS_SCHEDULE.csv"));
        add(Pair.of("data/final_sol_files/20220926/Incident_ABWDXR_ROLLING_STOCK_DUTY.csv", "data/final_sol_files/20220926/Incident_ABWDXR_SCHEDULE.csv"));
        add(Pair.of("data/final_sol_files/20220926/Incident_to_Heathrow_ROLLING_STOCK_DUTY.csv", "data/final_sol_files/20220926/Incident_to_Heathrow_SCHEDULE.csv"));
        add(Pair.of("data/final_sol_files/Incident_ILL_PASSENGER_ROLLING_STOCK_DUTY.csv", "data/final_sol_files/Incident_ILL_PASSENGER_SCHEDULE.csv"));
    }};

    public static final List<Pair<String, String>> EVENT_BASED_SOLUTION_FILES_LIST = new ArrayList<Pair<String, String>>() {{
        add(Pair.of("", ""));
        add(Pair.of("data/final_sol_files/Incident_ABWDXR_event_based_ROLLING_STOCK_DUTY.csv", "data/final_sol_files/Incident_ABWDXR_event_based_SCHEDULE.csv"));
        add(Pair.of("", ""));
    }};

    public final static int INSTANCE_INDEX = 0;
    private final String filePath = PROBLEM_SOL_FILES_LIST.get(INSTANCE_INDEX).getMiddle();
    private final String solPath = PROBLEM_SOL_FILES_LIST.get(INSTANCE_INDEX).getRight();

    private static final String REV_MINIMUM_RUN_TIME_FILE_PATH = "data/MINIMUM_RUN_TIME_rev2.csv";


    private final String rsSolPath = "data/trains_final_output.csv";
    public List<AllDutyStartEnd> loadAllDutyStartAndEnd() {
        AllDutyStartEndListener allDutyStartEndListener = new AllDutyStartEndListener();
        try (ExcelReader excelReader = EasyExcel.read(filePath, AllDutyStartEnd.class, allDutyStartEndListener).build()) {
            ReadSheet readSheet = EasyExcel.readSheet(2).build();
            excelReader.read(readSheet);
        } catch (ExcelAnalysisException e) {
            LOGGER.info("No All Duty Start And End");
        }
        return allDutyStartEndListener.getData();
    }

    public List<BaseStationValue> loadBaseStationValue() {
        BaseStationValueListener baseStationValueListener = new BaseStationValueListener();
        try (ExcelReader excelReader = EasyExcel.read(filePath, BaseStationValue.class, baseStationValueListener).build()) {
            ReadSheet readSheet = EasyExcel.readSheet(3).build();
            excelReader.read(readSheet);
        } catch (ExcelAnalysisException e) {
            LOGGER.info("No Base Station Value");
        }
        return baseStationValueListener.getData();
    }

    public List<Headway> loadHeadway() {
        HeadwayListener headwayListener = new HeadwayListener();
        try (ExcelReader excelReader = EasyExcel.read(filePath, Headway.class, headwayListener).build()) {
            ReadSheet readSheet = EasyExcel.readSheet(4).build();
            excelReader.read(readSheet);
        } catch (ExcelAnalysisException e) {
            LOGGER.info("No Headway");
        }
        return headwayListener.getData();
    }

    public List<Link> loadLink() {
        LinkListener linkListener = new LinkListener();
        try (ExcelReader excelReader = EasyExcel.read(filePath, Link.class, linkListener).build()) {
            ReadSheet readSheet = EasyExcel.readSheet(5).build();
            excelReader.read(readSheet);
        } catch (ExcelAnalysisException e) {
            LOGGER.info("No Link");
        }
        return linkListener.getData();
    }

    public List<MinimumRunTime> loadMinimumRunTime() {
        boolean useRevMinimumRunTime = false;
        if (!useRevMinimumRunTime) {
            MinimumRunTimeListener minimumRunTimeListener = new MinimumRunTimeListener();
            try (ExcelReader excelReader = EasyExcel.read(filePath, MinimumRunTime.class, minimumRunTimeListener).build()) {
                ReadSheet readSheet = EasyExcel.readSheet(6).build();
                excelReader.read(readSheet);
            } catch (ExcelAnalysisException e) {
                LOGGER.info("No Minimum Run Time");
            }
            return minimumRunTimeListener.getData();
        }

        List<MinimumRunTime> minimumRunTimeList = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(Paths.get(REV_MINIMUM_RUN_TIME_FILE_PATH));
            boolean headerLine = true;
            for (String line : lines) {
                if (headerLine) {
                    headerLine = false;
                    continue;
                }
                String[] data = line.split(";");

                MinimumRunTime minimumRunTime = new MinimumRunTime();
                minimumRunTime.setLinkStartNode(data[0]);
                minimumRunTime.setLinkEndNode(data[1]);
                minimumRunTime.setStartActivity(data[2]);
                minimumRunTime.setEndActivity(data[3]);
                minimumRunTime.setMinimumRunTimeSeconds(Integer.parseInt(data[4]));
                minimumRunTimeList.add(minimumRunTime);
            }
        } catch (IOException e) {
            LOGGER.info(e.getMessage());
        }

        return minimumRunTimeList;
    }

    public List<Node> loadNode() {
        NodeListener nodeListener = new NodeListener();
        try (ExcelReader excelReader = EasyExcel.read(filePath, Node.class, nodeListener).build()) {
            ReadSheet readSheet = EasyExcel.readSheet(7).build();
            excelReader.read(readSheet);
        } catch (ExcelAnalysisException e) {
            LOGGER.info("No Node");
        }
        return nodeListener.getData();
    }

    public List<PadtllWchapxrTargetFrequency> loadPadtllWchapxrTargetFrequency() {
        PadtllWchapxrTargetFrequencyListener pwtfListener = new PadtllWchapxrTargetFrequencyListener();
        try (ExcelReader excelReader = EasyExcel.read(filePath, PadtllWchapxrTargetFrequency.class,
                pwtfListener).build()) {
            ReadSheet readSheet = EasyExcel.readSheet(8).build();
            excelReader.read(readSheet);
        } catch (ExcelAnalysisException e) {
            LOGGER.info("No Padtll Wchapxr Target Frequency");
        }
        return pwtfListener.getData();
    }

    public List<RollingStockDuty> loadRollingStockDuty() {
        RollingStockDutyListener rsdListener = new RollingStockDutyListener();
        try (ExcelReader excelReader = EasyExcel.read(filePath, RollingStockDuty.class,
                rsdListener).build()) {
            ReadSheet readSheet = EasyExcel.readSheet(9).build();
            excelReader.read(readSheet);
        } catch (ExcelAnalysisException e) {
            LOGGER.info("No Rolling Stock Duty");
        }
        return rsdListener.getData();
    }

    public List<TrainHeader> loadTrainHeader() {
        TrainHeaderListener trainHeaderListener = new TrainHeaderListener();
        try (ExcelReader excelReader = EasyExcel.read(filePath, TrainHeader.class,
                trainHeaderListener).build()) {
            ReadSheet readSheet = EasyExcel.readSheet(10).build();
            excelReader.read(readSheet);
        } catch (ExcelAnalysisException e) {
            LOGGER.info("No Train Header");
        }
        return trainHeaderListener.getData();
    }

    public List<TrainSchedule> loadTrainSchedule() {
        TrainScheduleListener trainScheduleListener = new TrainScheduleListener();
        try (ExcelReader excelReader = EasyExcel.read(filePath, TrainSchedule.class,
                trainScheduleListener).build()) {
            ReadSheet readSheet = EasyExcel.readSheet(11).build();
            excelReader.read(readSheet);
        } catch (ExcelAnalysisException e) {
            LOGGER.info("No Train Schedule");
        }
        return trainScheduleListener.getData();
    }

    public List<ExtendedRunTime> loadExtendedRunTime() {
        ExtendedRunTimeListener ertListener = new ExtendedRunTimeListener();
        try (ExcelReader excelReader = EasyExcel.read(filePath, ExtendedRunTime.class,
                ertListener).build()) {
            ReadSheet readSheet = EasyExcel.readSheet(13).build();
            excelReader.read(readSheet);
        } catch (ExcelAnalysisException e) {
            LOGGER.info("No Extended Run Time");
        }
        return ertListener.getData();
    }

    public List<LateDeparture> loadLateDeparture() {
        LateDepartureListener lateDepartureListener = new LateDepartureListener();
        try (ExcelReader excelReader = EasyExcel.read(filePath, LateDeparture.class,
                lateDepartureListener).build()) {
            ReadSheet readSheet = EasyExcel.readSheet(14).build();
            excelReader.read(readSheet);
        } catch (ExcelAnalysisException e) {
            LOGGER.info("No Late Departure");
        }
        return lateDepartureListener.getData();
    }

    public List<RealizedSchedule> loadRealizedSchedule() {
        RealizedScheduleListener realizedScheduleListener = new RealizedScheduleListener();
        try (ExcelReader excelReader = EasyExcel.read(filePath, RealizedSchedule.class,
                realizedScheduleListener).build()) {
            ReadSheet readSheet = EasyExcel.readSheet(15).build();
            excelReader.read(readSheet);
        } catch (ExcelAnalysisException e) {
            LOGGER.info("No Realized Schedule");
        }
        return realizedScheduleListener.getData();
    }

    public List<StationExtendedDwell> loadStationExtendedDwell() {
        StationExtendedDwellListener stationExtendedDwellListener = new StationExtendedDwellListener();
        try (ExcelReader excelReader = EasyExcel.read(filePath, StationExtendedDwell.class,
                stationExtendedDwellListener).build()) {
            ReadSheet readSheet = EasyExcel.readSheet(16).build();
            excelReader.read(readSheet);
        } catch (ExcelAnalysisException e) {
            LOGGER.info("No Station Extended Dwell");
        }
        return stationExtendedDwellListener.getData();
    }

    public List<TrainExtendedDwell> loadTrainExtendedDwell() {
        TrainExtendedDwellListener trainExtendedDwellListener = new TrainExtendedDwellListener();
        try (ExcelReader excelReader = EasyExcel.read(filePath, TrainExtendedDwell.class,
                trainExtendedDwellListener).build()) {
            ReadSheet readSheet = EasyExcel.readSheet(17).build();
            excelReader.read(readSheet);
        } catch (ExcelAnalysisException e) {
            LOGGER.info("No Train Extended Dwell");
        }
        return trainExtendedDwellListener.getData();
    }

    @Override
    public List<TrainSchedule> loadCourseSolFromCsv() {
        List<TrainSchedule> trainSchedules = new ArrayList<>();
        try (Scanner scanner = new Scanner(new File(solPath))) {
            scanner.nextLine();
            while (scanner.hasNext()) {
                String nextLine = scanner.nextLine();
                if ("".equalsIgnoreCase(nextLine)) {
                    break;
                }
                String[] data = nextLine.split(",");
                TrainSchedule trainSchedule = new TrainSchedule();
                String courseId = data[0];
                int seq = Integer.parseInt(data[1]);
                String node = data[2];
                int arrivalSeconds = 0;
                if (!"".equalsIgnoreCase(data[3])) {
                    arrivalSeconds = Integer.parseInt(data[3].split("\\.")[0]);
                }
                int departureSeconds = 0;
                if (!"".equalsIgnoreCase(data[4])) {
                    departureSeconds = Integer.parseInt(data[4].split("\\.")[0]);
                }
                String track = data[5];
                String activity = data[6];
                trainSchedule.setTrainCourseId(courseId);
                trainSchedule.setSeq(seq);
                trainSchedule.setNode(node);
                trainSchedule.setArrivalSeconds(arrivalSeconds);
                trainSchedule.setDepartureSeconds(departureSeconds);
                trainSchedule.setTrack(track);
                trainSchedule.setActivity(activity);
                trainSchedules.add(trainSchedule);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return trainSchedules;
    }

    @Override
    public List<String> loadRollingStockSol() {
        List<String> paths = new ArrayList<>();
        try (Scanner scanner = new Scanner(new File(rsSolPath))) {
            scanner.nextLine();
            while (scanner.hasNext()) {
                String line = scanner.nextLine();
                if (line.equalsIgnoreCase("")) {
                    break;
                }
                paths.add(line);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return paths;
    }

    @Override
    public String loadProblemId() {
        return PROBLEM_SOL_FILES_LIST.get(INSTANCE_INDEX).getLeft();
    }

    public BufferedReader loadFinalSolutionSchedulingFile() {
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(Files.newInputStream(new File(FINAL_SOLUTION_FILES_LIST.get(INSTANCE_INDEX).getValue()).toPath())));
            return bufferedReader;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public BufferedReader loadFinalSolutionRollingStockDutyFile() {
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(Files.newInputStream(new File(FINAL_SOLUTION_FILES_LIST.get(INSTANCE_INDEX).getKey()).toPath())));
            return bufferedReader;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public BufferedReader loadEventBasedSolutionScheduleFile() {
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(Files.newInputStream(new File(EVENT_BASED_SOLUTION_FILES_LIST.get(INSTANCE_INDEX).getValue()).toPath())));
            return bufferedReader;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
