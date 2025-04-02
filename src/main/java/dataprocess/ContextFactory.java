package dataprocess;

import context.ProblemContext;
import context.RollingStock;
import context.Schedule;
import context.scenario.*;
import datareader.LoadData;
import entity.*;
import graph.dijkstra.Dijkstra;
import solution.Solution;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ContextFactory （情景工厂）
 * 生成问题情景
 *
 * @author s00536729
 * @since 2022-06-16
 */
public class ContextFactory implements Process, Update, Filter, LoadSol {
    @Override
    public ProblemContext build(LoadData reader) {
        ProblemContext problemContext = new ProblemContext();
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
        List<StationExtendedDwell> stationExtendedDwellList = reader.loadStationExtendedDwell();
        List<TrainExtendedDwell> trainExtendedDwellList = reader.loadTrainExtendedDwell();
        List<TrainHeader> trainHeaderList = reader.loadTrainHeader();
        List<TrainSchedule> trainScheduleList = reader.loadTrainSchedule();
        problemContext.setProblemId(reader.loadProblemId());
        problemContext.fillNode(nodeList);
        problemContext.fillLinks(linkList, headwayList,minimumRunTimeList);
        problemContext.fillBaseStationValue(baseStationValueList);
        problemContext.fillSchedules(trainHeaderList, trainScheduleList);
        problemContext.fillDuties(allDutyStartEndList);
        problemContext.fillRollingStocks(trainScheduleList,rollingStockDutyList);
        problemContext.fillTargetFrequency(pwtfList);
        problemContext.fillScenario(extendedRunTimeList, lateDepartureList, realizedScheduleList,
                stationExtendedDwellList, trainExtendedDwellList);

//        problemContext.adjustMinimumRunTime();
//        problemContext.checkConsecutiveCourses();
//        problemContext.checkConsecutiveDuties();
//        problemContext.checkFirstLastSchedule();
//        problemContext.checkChangeEndSpareSchedule();
//        problemContext.checkMinimumConnectionTimeBetweenTrains();
//        problemContext.checkMinimumHeadwayBetweenConsecutiveTrains();

        Dijkstra dijkstra = new Dijkstra(problemContext);
        problemContext.setTimeMatrix(dijkstra.calcShortestDistanceMatrix());
        problemContext.setPathMap(dijkstra.getPathMap());
        return problemContext;
    }

    @Override
    public void update(ProblemContext problemContext) {
        for (RealizedScheduleScenario realizedSchedule : problemContext.getScenario().
                getRealizedScheduleScenarios()) {
            Schedule plannedSchedule = realizedSchedule.getSchedule();
            if (plannedSchedule != null) {
                // still there are some unseen schedules coming out of nowhere, and we just skip them
                plannedSchedule.updateStatus(realizedSchedule);
            }
        }

        Set<String> skipFirstNodeCourses = new HashSet<String>() {{
            add("5Y08RL#1");
            add("5Y10RL#1");
            add("5Y12RL#1");
            add("5Y14RL#1");
            add("5Y16RL#1");
            add("5Y56RL#1");
            add("5Y18RL#1");
            add("5Y68RL#1");
        }};
        Set<String> skipLastNodeCourses = new HashSet<String>() {{
            add("5P02RN#1");
        }};
        for (Schedule schedule : problemContext.getSchedules()) {
            if (schedule.getRealizedNodes().isEmpty()) {
                continue;
            }
            if (schedule.getRealizedNodes().size() == schedule.getPlannedNodes().size()) {
                continue;
            }
            if (!skipFirstNodeCourses.contains(schedule.getCourseId()) && !skipLastNodeCourses.contains(schedule.getCourseId())) {
                System.out.printf("Warning: Planned node size %d, realized node size %d\n", schedule.getPlannedNodes().size(), schedule.getRealizedNodes().size());
            }
            if (skipFirstNodeCourses.contains(schedule.getCourseId())) {
                schedule.setStartNode(schedule.getRealizedNodes().get(0));
                schedule.setStartTime(schedule.getLeaveTimes().get(2));
                for (int i = 1; i < schedule.getPlannedNodes().size(); ++i) {
                    schedule.getNodeStatus().put(i, schedule.getNodeStatus().get(i + 1));
                    schedule.getTracks().put(i, schedule.getTracks().get(i + 1));
                }
                schedule.getNodeStatus().remove(schedule.getPlannedNodes().size());
                schedule.getTracks().remove(schedule.getTracks().size());

                for (int i = 2; i < schedule.getPlannedNodes().size(); ++i) {
                    schedule.getEnterTimes().put(i, schedule.getEnterTimes().get(i + 1));
                }
                schedule.getEnterTimes().remove(schedule.getPlannedNodes().size());

                for (int i = 1; i < schedule.getPlannedNodes().size() - 1; ++i) {
                    schedule.getLeaveTimes().put(i, schedule.getLeaveTimes().get(i + 1));
                }
                schedule.getLeaveTimes().remove(schedule.getPlannedNodes().size() - 1);

                schedule.getDwellTimes().remove(schedule.getPlannedNodes().size() - 1);

                schedule.getPlannedNodes().remove(0);
                schedule.getPlannedLinks().remove(0);
            }
//            if (skipLastNodeCourses.contains(schedule.getCourseId())) {
//                schedule.setEndNode(schedule.getRealizedNodes().get(schedule.getRealizedNodes().size() - 1));
//                schedule.setEndTime(schedule.getEnterTimes().get(schedule.getPlannedNodes().size()));
//
//                schedule.getNodeStatus().remove(schedule.getPlannedNodes().size());
//                schedule.getTracks().remove(schedule.getPlannedNodes().size());
//
//                schedule.getEnterTimes().remove(schedule.getPlannedNodes().size());
//                schedule.getLeaveTimes().remove(schedule.getPlannedNodes().size() - 1);
//                schedule.getDwellTimes().remove(schedule.getPlannedNodes().size() - 1);
//
//                schedule.getPlannedNodes().remove(schedule.getPlannedNodes().size() - 1);
//                schedule.getPlannedLinks().remove(schedule.getPlannedLinks().size() - 1);
//            }

            context.Node lastRealizedNode = schedule.getRealizedNodes().get(schedule.getRealizedNodes().size() - 1);
            int lastRealizedNodeIndex = schedule.getPlannedNodes().lastIndexOf(lastRealizedNode);
            if (lastRealizedNodeIndex == schedule.getPlannedNodes().size() - 1) {
                continue;
            }

            for (int i = lastRealizedNodeIndex + 1; i < schedule.getPlannedNodes().size(); ++i) {
                context.Node node = schedule.getPlannedNodes().get(i);
                schedule.getRealizedNodes().add(node);

                int seq = i + 1;
                schedule.getRealizedEnterTimes().put(seq, 0);
                schedule.getRealizedLeaveTimes().put(seq, 0);
                schedule.getRealizedNodeStatus().put(seq, "UNREALIZED");
                schedule.getRealizedTracks().put(seq, schedule.getTracks().get(seq));
            }
        }

        for (LinkScenario linkScenario : problemContext.getScenario().getLinkScenarios()) {
            context.Link link = linkScenario.getLink();
            link.getLinkScenarioList().add(linkScenario);
        }
        for (LateDepartureScenario lateDeparture : problemContext.getScenario().
                getLateDepartureScenarios()) {
            Schedule schedule = lateDeparture.getSchedule();
            schedule.setLateDeparture(lateDeparture);
        }
        for (StationExtendedDwellScenario extendedDwellScenario : problemContext.getScenario().
                getStationExtendedDwellScenarios()) {
            context.Node node = extendedDwellScenario.getNode();
            node.getStationExtendedDwellScenarios().add(extendedDwellScenario);
        }
        for (TrainExtendedDwellScenario trainExtendedDwellScenario : problemContext.getScenario().
                getTrainExtendedDwellScenarios()) {
            Schedule schedule = trainExtendedDwellScenario.getSchedule();
            schedule.getTrainExtendedDwellScenarios().add(trainExtendedDwellScenario);
        }
    }

    @Override
    public void filter(ProblemContext problemContext) {
        List<Schedule> schedules = problemContext.getSchedules();
        List<Schedule> filteredSchedules = schedules.stream().filter(schedule -> schedule.getEventType().
                equals(Schedule.EventType.TRAIN)).collect(Collectors.toList());
        problemContext.setSchedules(filteredSchedules);
        problemContext.getSchedules().sort(Comparator.comparingInt(Schedule::getStartTime));
    }

    @Override
    public void loadCourseSol(LoadData reader, Solution solution, ProblemContext problemContext) {
        List<TrainSchedule> trainSchedules = reader.loadCourseSolFromCsv();
        Map<String, Integer> negativeTimeNumMap = new HashMap<>();
        for (TrainSchedule trainSchedule : trainSchedules) {
            Schedule schedule = problemContext.getCourseId2Schedule().get(trainSchedule.getTrainCourseId());
            if (!schedule.getRealizedNodes().isEmpty()
                    && schedule.getRealizedEnterTimes().get(schedule.getRealizedNodes().size()) != 0) {
                // 已经发生的Course不再刷新
                continue;
            }
            int seq = trainSchedule.getSeq();
            int delta = 0;
            if (!schedule.getRealizedNodes().isEmpty()) {
                context.Node firstNode = schedule.getRealizedNodes().get(0);
                for (; delta < schedule.getPlannedNodes().size(); delta ++) {
                    if (schedule.getPlannedNodes().get(delta).equals(firstNode)) {
                        break;
                    }
                }
            }
            if (seq - 1 - delta < 0) {
                continue;
            }
            if (trainSchedule.getArrivalSeconds() < 0 || trainSchedule.getDepartureSeconds() < 0) {
                if (negativeTimeNumMap.containsKey(schedule.getCourseId())) {
                    negativeTimeNumMap.put(schedule.getCourseId(), negativeTimeNumMap.get(schedule.getCourseId() + 1));
                } else {
                    negativeTimeNumMap.put(schedule.getCourseId(), 1);
                }
                continue;
            }
            int negativeTimeNum = negativeTimeNumMap.getOrDefault(schedule.getCourseId(), 0);
            List<Integer> arrivals = solution.getScheduleStationArrivalTimeMap().get(schedule);
            arrivals.set(seq - 1 - delta - negativeTimeNum, trainSchedule.getArrivalSeconds());
            solution.getScheduleStationArrivalTimeMap().put(schedule, arrivals);
            List<Integer> departures = solution.getScheduleStationDepartureTimeMap().get(schedule);
            departures.set(seq - 1 - delta - negativeTimeNum, trainSchedule.getDepartureSeconds());
            solution.getScheduleStationDepartureTimeMap().put(schedule, departures);
            List<String> tracks = solution.getScheduleStationTrackMap().get(schedule);
            tracks.set(seq - 1 - delta - negativeTimeNum, trainSchedule.getTrack());
            solution.getScheduleStationTrackMap().put(schedule, tracks);
            List<Boolean> skipStations = solution.getScheduleSkipStationMap().get(schedule);
            skipStations.set(seq - 1 - delta - negativeTimeNum, trainSchedule.getActivity().equalsIgnoreCase("PASS") ? Boolean.TRUE :
                    Boolean.FALSE);
            solution.getScheduleSkipStationMap().put(schedule, skipStations);
        }
    }

    public void loadRollingStockSol(LoadData reader, Solution solution, ProblemContext problemContext) {
        List<String> paths = reader.loadRollingStockSol();
        int rsGap = paths.size() - problemContext.getRollingStocks().size();
        final int index = 100;
        while (rsGap > 0) {
            RollingStock rollingStock = new RollingStock();
            rollingStock.setIndex(index + rsGap);
            problemContext.getRollingStocks().add(rollingStock);
            --rsGap;
        }
        solution.getRollingStock2ScheduleListMap().clear();
        for (int rs = 0; rs < paths.size(); ++rs) {
            RollingStock rollingStock = problemContext.getRollingStocks().get(rs);
            List<Schedule> schedules = new ArrayList<>();
            String path = paths.get(rs);
            String[] courseIds = path.split(",");
            for (String courseId : courseIds) {
                if (courseId.equalsIgnoreCase("")) {
                    break;
                }
                if (!problemContext.getCourseId2Schedule().containsKey(courseId)) {
                    continue;
                }
                Schedule schedule = problemContext.getCourseId2Schedule().get(courseId);
                if (!solution.getSchedule2RollingStockMap().containsKey(schedule)) {
                    continue;
                }
                schedules.add(schedule);
                solution.getSchedule2RollingStockMap().put(schedule, rollingStock);
            }
            solution.getRollingStock2ScheduleListMap().put(rollingStock, schedules);
        }
    }
}
