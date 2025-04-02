package context;

import com.alibaba.excel.util.StringUtils;
import context.scenario.*;
import entity.*;
import graph.Vertex;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;
import java.util.logging.Logger;

/**
 * ProblemContext （问题情景）
 * 问题情景
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class ProblemContext {
    private static final Logger LOGGER = Logger.getLogger(ProblemContext.class.getName());

    private String problemId;

    private List<Node> nodes = new ArrayList<>();
    private Map<String, Node> code2Node = new HashMap<>();
    private List<Link> links = new ArrayList<>();
    private Map<String, Link> name2Link = new HashMap<>();
    private List<RollingStock> rollingStocks = new ArrayList<>();
    private List<Duty> duties = new ArrayList<>();
    private Map<String, Duty> id2Duty = new HashMap<>();
    private Map<Schedule, RollingStock> schedule2RollingStock = new HashMap<>();
    private List<Schedule> schedules = new ArrayList<>();
    private Map<String, Schedule> courseId2Schedule = new HashMap<>();
    private Scenario scenario = new Scenario();

    private int maxHeadway;

    @Deprecated
    private Map<String, Course> realizedCourseMap = new HashMap<>();
    private Set<String> fixedCourseSet = new HashSet<>();
    private Set<Schedule> eeSchedules = new HashSet<>(); // 代表新增的EE的course
    private double[][] timeMatrix = new double[nodes.size()][nodes.size()]; // time from one node index to another
    private Map<Integer, Map<Integer, List<Vertex>>> pathMap = new HashMap<>(); // path from one node index to another

    private Map<String, Map<String, Integer>> specialChangeEndTimeMap = new HashMap<>();

    /**
     * 空构造器
     */
    public ProblemContext() {

    }

    /**
     * 基于现有问题情景构造新情景
     *
     * @param problemContext 问题情景
     */
    public ProblemContext(ProblemContext problemContext) {
        nodes = new ArrayList<>(problemContext.getNodes());
        code2Node = new HashMap<>(problemContext.code2Node);
        links = new ArrayList<>(problemContext.getLinks());
        name2Link = new HashMap<>(problemContext.getName2Link());
        rollingStocks = new ArrayList<>(problemContext.getRollingStocks());
        duties = new ArrayList<>(problemContext.getDuties());
        id2Duty = new HashMap<>(problemContext.getId2Duty());
        schedules = new ArrayList<>(problemContext.getSchedules());
        courseId2Schedule = new HashMap<>(problemContext.getCourseId2Schedule());
        scenario = problemContext.getScenario();
        maxHeadway = problemContext.getMaxHeadway();
    }

    /**
     * 基于[startIndex, endIndex)的列车路线index区间，构造子问题情景
     *
     * @param startIndex 区间起始index
     * @param endIndex 区间终止index
     * @return ProblemContext 子问题情景
     */
    public ProblemContext genScheduleHorizon(int startIndex, int endIndex) {
        ProblemContext subContext = new ProblemContext(this);
        subContext.setSchedules(this.schedules.subList(Math.max(startIndex, 0), Math.min(endIndex, this.schedules.
                size())));
        Map<String, Schedule> courseId2Schedule = new HashMap<>();
        for (Schedule schedule : subContext.getSchedules()) {
            courseId2Schedule.put(schedule.getCourseId(), schedule);
        }
        subContext.setCourseId2Schedule(courseId2Schedule);
//        List<Link> links = new ArrayList<>();
//        for (Link link : subContext.getLinks()) {
//            List<Schedule> schedules = new ArrayList<>();
//            for (Schedule schedule : link.getSchedules()) {
//                if (subContext.getCourseId2Schedule().containsKey(schedule.getCourseId())) {
//                    schedules.add(schedule);
//                }
//            }
//            if (schedules.size() > 0) {
//                link.setSchedules(schedules);
//                links.add(link);
//            }
//        }
//        subContext.setLinks(links);
        return subContext;
    }

    /**
     * 记录从depot出发的rolling stock
     */
    @Getter
    @Setter
    public static class NodeRollingStock {
        private String dutyId;
        private int time = 0;
        private boolean isStart;

    }

    /**
     * 分别填充PADTLL和WCHAPXR的WB和EB目标频次
     *
     * @param pwtfList 不同时段的目标频次
     */
    public void fillTargetFrequency(List<PadtllWchapxrTargetFrequency> pwtfList) {
        Node padtllNode = code2Node.get("PADTLL");
        List<PadtllWchapxrTargetFrequency> wbTF = new ArrayList<>(pwtfList);
        padtllNode.getTargetFrequency().put(Track.Direction.WB, wbTF);
        Node wchapxrNode = code2Node.get("WCHAPXR");
        List<PadtllWchapxrTargetFrequency> ebTF = new ArrayList<>(pwtfList);
        wchapxrNode.getTargetFrequency().put(Track.Direction.EB, ebTF);
    }

    /**
     * 填充Rolling Stock Duty
     *
     * @param allDutyStartEndList 所有Duty的起点终点和开始与结束运营时间
     */
    public void fillDuties(List<AllDutyStartEnd> allDutyStartEndList) {
        for (AllDutyStartEnd allDutyStartEnd : allDutyStartEndList) {
            Duty duty = new Duty();
            duty.setDutyId(allDutyStartEnd.getDutyId());
            duty.setStartNode(code2Node.get(allDutyStartEnd.getStartNode()));
            duty.setEndNode(code2Node.get(allDutyStartEnd.getEndNode()));
            duty.setStartTime(allDutyStartEnd.getStartTimeSeconds());
            duty.setEndTime(allDutyStartEnd.getEndTimeSeconds());
            duties.add(duty);
            id2Duty.put(duty.getDutyId(), duty);
        }
    }

    /**
     * 填充Rolling Stock，并且填充planned duties & schedules
     *
     * @param trainSchedules 列车路线
     * @param rollingStockDuties 列车职责
     */
    public void fillRollingStocks(List<entity.TrainSchedule> trainSchedules,
                                  List<entity.RollingStockDuty> rollingStockDuties) {
        Map<String, PriorityQueue<entity.TrainSchedule>> scheduleListMap = new HashMap<>();
        for (entity.TrainSchedule trainSchedule : trainSchedules) {
            if (scheduleListMap.containsKey(trainSchedule.getTrainCourseId())) {
                scheduleListMap.get(trainSchedule.getTrainCourseId()).offer(trainSchedule);
            } else {
                PriorityQueue<entity.TrainSchedule> queue = new PriorityQueue<>(Comparator.comparingInt(TrainSchedule::getSeq));
                queue.offer(trainSchedule);
                scheduleListMap.put(trainSchedule.getTrainCourseId(), queue);
            }
        }

        Map<String, PriorityQueue<entity.RollingStockDuty>> rollingStockMap = new HashMap<>();//dutyid,rollingstock

        for (entity.RollingStockDuty rollingStockDuty : rollingStockDuties) {
            String dutyId = rollingStockDuty.getDutyId();
            if (!rollingStockMap.containsKey(dutyId)) {
                PriorityQueue<entity.RollingStockDuty> queue = new PriorityQueue<>(Comparator.comparingInt(RollingStockDuty::getStartTimeSeconds));
                queue.offer(rollingStockDuty);
                rollingStockMap.put(dutyId, queue);
            } else {
                rollingStockMap.get(dutyId).offer(rollingStockDuty);
            }
        }

        for (entity.RollingStockDuty rollingStockDuty : rollingStockDuties) {
            String dutyId = rollingStockDuty.getDutyId();
            Duty duty;
            if (id2Duty.containsKey(dutyId)) {
                duty = id2Duty.get(dutyId);
            } else {
                duty = new Duty();
                duty.setDutyId(dutyId);
                duty.setStartTime(rollingStockDuty.getStartTimeSeconds());
                duty.setEndTime(rollingStockDuty.getEndTimeSeconds());
                duty.setStartNode(code2Node.get(rollingStockDuty.getStartNode()));
                duty.setEndNode(code2Node.get(rollingStockDuty.getEndNode()));
                duties.add(duty);
                id2Duty.put(dutyId, duty);
            }

            if (rollingStockDuty.getTrainCourseId() != null && !"".equals(rollingStockDuty.getTrainCourseId())) {
                duty.getPlannedSchedules().add(courseId2Schedule.get(rollingStockDuty.getTrainCourseId()));
            } else {
                Schedule schedule = new Schedule();
                if (rollingStockDuty.getStartTimeSeconds() == -24660) {
                    schedule.setStartTime(37740);
                } else {
                    schedule.setStartTime(rollingStockDuty.getStartTimeSeconds());
                }
                if (rollingStockDuty.getEndTimeSeconds() == -24660) {
                    schedule.setEndTime(37740);
                } else {
                    schedule.setEndTime(rollingStockDuty.getEndTimeSeconds());
                }
                schedule.setStartNode(code2Node.get(rollingStockDuty.getStartNode()));
                schedule.setEndNode(code2Node.get(rollingStockDuty.getEndNode()));
                schedule.setEventType(Schedule.EventType.valueOf(rollingStockDuty.getEventType()));
                schedules.add(schedule);
                duty.getPlannedSchedules().add(schedule);
            }
        }
        Map<String, entity.RollingStockDuty> dutyStart = new HashMap<>();
        Map<String, entity.RollingStockDuty> dutyEnd = new HashMap<>();
        for (Map.Entry<String, PriorityQueue<entity.RollingStockDuty>> entry : rollingStockMap.entrySet()) {
            String dutyId = entry.getKey();
            entity.RollingStockDuty rollingStockDuty = entry.getValue().poll();
            assert rollingStockDuty != null;
            dutyStart.put(dutyId, rollingStockDuty);
//            int startTime = rollingStockDuty.getStartTimeSeconds();
//            int endTime = rollingStockDuty.getEndTimeSeconds();
            while (!entry.getValue().isEmpty()) {
                rollingStockDuty = entry.getValue().poll();
            }
//            endTime = rollingStockDuty.getEndTimeSeconds();
            dutyEnd.put(dutyId, rollingStockDuty);
        }

        // 统计每个depot的出入情况
        Map<String, PriorityQueue<NodeRollingStock>> nodeRollingStockMap = new HashMap<>();
        for (Map.Entry<String, entity.RollingStockDuty> entry : dutyStart.entrySet()) {
            int startTime = entry.getValue().getStartTimeSeconds();
            String startNode = entry.getValue().getStartNode();
            NodeRollingStock nodeRollingStock = new NodeRollingStock();
            nodeRollingStock.setDutyId(entry.getKey());
            nodeRollingStock.setTime(startTime);
            nodeRollingStock.setStart(true);
            if (nodeRollingStockMap.containsKey(startNode)) {
                nodeRollingStockMap.get(startNode).offer(nodeRollingStock);
            } else {
                PriorityQueue<NodeRollingStock> queue = new PriorityQueue<>(Comparator.comparingInt(NodeRollingStock::getTime));
                queue.offer(nodeRollingStock);
                nodeRollingStockMap.put(startNode, queue);
            }
        }
        for (Map.Entry<String, entity.RollingStockDuty> entry : dutyEnd.entrySet()) {
            int endTime = entry.getValue().getEndTimeSeconds();
            String endNode = entry.getValue().getEndNode();
            NodeRollingStock nodeRollingStock = new NodeRollingStock();
            nodeRollingStock.setDutyId(entry.getKey());
            nodeRollingStock.setTime(endTime);
            nodeRollingStock.setStart(false);
            if (nodeRollingStockMap.containsKey(endNode)) {
                nodeRollingStockMap.get(endNode).offer(nodeRollingStock);
            } else {
                PriorityQueue<NodeRollingStock> queue = new PriorityQueue<>(Comparator.comparingInt(NodeRollingStock::getTime));
                queue.offer(nodeRollingStock);
                nodeRollingStockMap.put(endNode, queue);
            }
        }

        // 判断depot
        for (String nodeId : nodeRollingStockMap.keySet()) {
            this.code2Node.get(nodeId).setDepot(true);
        }

        Map<String, Integer> dutyGroup = new HashMap<>(); // duty分组，int就是组号
//        Set<Set<String>> dutyGroups = new HashSet<>();
        int i = 0;
        for (Map.Entry<String, PriorityQueue<NodeRollingStock>> entry : nodeRollingStockMap.entrySet()) {
            LinkedList<String> dwellDuties = new LinkedList<>();
            while (!entry.getValue().isEmpty()) {
                NodeRollingStock nodeRollingStock = entry.getValue().poll();
                if (nodeRollingStock.isStart) {
                    if (dwellDuties.isEmpty()) {
                        if (!dutyGroup.containsKey(nodeRollingStock.getDutyId())) {
                            dutyGroup.put(nodeRollingStock.getDutyId(), i);
                            i++;
                        }
                    } else {
                        if (!dutyGroup.containsKey(nodeRollingStock.getDutyId())) {
                            dutyGroup.put(nodeRollingStock.getDutyId(), dutyGroup.get(dwellDuties.pop()));
                        } else {
                            int groupId = dutyGroup.get(nodeRollingStock.getDutyId());
                            int newGroupId = dutyGroup.get(dwellDuties.pop());
                            for (Map.Entry<String, Integer> entry1 : dutyGroup.entrySet()) {
                                if (entry1.getValue() == groupId) {
                                    entry1.setValue(newGroupId);
                                }
                            }
                        }
                    }
                } else {
                    dwellDuties.add(nodeRollingStock.getDutyId());
                    if (!dutyGroup.containsKey(nodeRollingStock.getDutyId())) {
                        dutyGroup.put(nodeRollingStock.getDutyId(), i);
                        i++;
                    }
                }
            }
        }

        Map<Integer, Set<String>> groups = new HashMap<>(); // duty分组，int就是组号
        for (Map.Entry<String, Integer> entry : dutyGroup.entrySet()) {
            if (groups.containsKey(entry.getValue())) {
                groups.get(entry.getValue()).add(entry.getKey());
            } else {
                Set<String> tmp = new HashSet<>();
                tmp.add(entry.getKey());
                groups.put(entry.getValue(), tmp);
            }
        }


        //Todo 重复了，临时用一下
        for (entity.RollingStockDuty rollingStockDuty : rollingStockDuties) {
            String dutyId = rollingStockDuty.getDutyId();
            if (!rollingStockMap.containsKey(dutyId)) {
                PriorityQueue<entity.RollingStockDuty> queue = new PriorityQueue<>(Comparator.comparingInt(RollingStockDuty::getStartTimeSeconds));
                queue.offer(rollingStockDuty);
                rollingStockMap.put(dutyId, queue);
            } else {
                rollingStockMap.get(dutyId).offer(rollingStockDuty);
            }
        }

        for (Map.Entry<Integer, Set<String>> entry : groups.entrySet()) {
            RollingStock rollingStock = new RollingStock();
            rollingStock.setIndex(entry.getKey());
//            PriorityQueue<entity.RollingStockDuty> dutyPriorityQueue = new PriorityQueue<>((o1, o2) -> o1.getStartTimeSeconds() - o2.getStartTimeSeconds());

            int startTime = Integer.MAX_VALUE;
            Node startNode = null;
            for (String duty : entry.getValue()) {
//                dutyPriorityQueue.offer(dutyStart.get(duty));
                if (startTime > dutyStart.get(duty).getStartTimeSeconds()) {
                    startTime = dutyStart.get(duty).getStartTimeSeconds();
                    startNode = this.code2Node.get(dutyStart.get(duty).getStartNode());
                }
            }
            rollingStock.setStartPos(startNode);

            List<Duty> plannedDuties = new ArrayList<>();
            for (String dutyId : entry.getValue()) {
                Duty duty = new Duty();
                duty.setDutyId(dutyId);
                duty.setStartTime(Integer.MAX_VALUE);
                duty.setEndTime(Integer.MIN_VALUE);
                List<Schedule> plannedSchedules = new ArrayList<>();
                while (!rollingStockMap.get(dutyId).isEmpty()) {
                    entity.RollingStockDuty rollingStockDuty = rollingStockMap.get(dutyId).poll();
                    if (rollingStockDuty.getStartTimeSeconds() < duty.getStartTime()) {
                        duty.setStartTime(rollingStockDuty.getStartTimeSeconds());
                        duty.setStartNode(code2Node.get(rollingStockDuty.getStartNode()));
                    }
                    if (rollingStockDuty.getEndTimeSeconds() > duty.getEndTime()) {
                        duty.setEndTime(rollingStockDuty.getEndTimeSeconds());
                        duty.setEndNode(code2Node.get(rollingStockDuty.getEndNode()));
                    }
                    Schedule schedule = courseId2Schedule.get(rollingStockDuty.getTrainCourseId());
                    if (schedule != null) {
                        plannedSchedules.add(schedule);
                        rollingStock.getSchedules().add(schedule);
                    }
                }
                if (plannedSchedules.size() > 0) {
                    duty.setPlannedSchedules(plannedSchedules);
                }
                plannedDuties.add(duty);
            }
            rollingStock.setPlannedDuties(plannedDuties);
            this.rollingStocks.add(rollingStock);
        }
        for (RollingStock rollingStock : rollingStocks) {
            for (Schedule schedule : rollingStock.getSchedules()) {
                schedule2RollingStock.put(schedule, rollingStock);
            }
        }
    }

    /**
     * 填充列车路线相关信息
     *
     * @param trainHeaders 列车路线简要信息
     * @param trainSchedules 列车路线详细信息
     */
    public void fillSchedules(List<TrainHeader> trainHeaders, List<TrainSchedule> trainSchedules) {
        for (TrainHeader trainHeader : trainHeaders) {
            Schedule schedule = new Schedule();
            schedule.setCourseId(trainHeader.getTrainCourseId());
            schedule.setDirection(Track.Direction.valueOf(trainHeader.getDirection()));
            schedule.setCategory(Schedule.Category.valueOf(trainHeader.getCategory()));
            schedule.setStartTime(trainHeader.getStartSeconds());
            schedule.setStartNode(code2Node.get(trainHeader.getStartNode()));
            schedule.setEndTime(trainHeader.getEndSeconds());
            schedule.setEndNode(code2Node.get(trainHeader.getEndNode()));
            schedule.setEventType(Schedule.EventType.TRAIN);
            schedules.add(schedule);
            courseId2Schedule.put(schedule.getCourseId(), schedule);
        }

        Node lastNode = null;
        for (TrainSchedule trainSchedule : trainSchedules) {
            if (trainSchedule.getSeq() == 1) {
               lastNode = null;
            }
            Schedule schedule = courseId2Schedule.get(trainSchedule.getTrainCourseId());
            Node node = code2Node.get(trainSchedule.getNode());
            int index = trainSchedule.getSeq();
            schedule.getPlannedNodes().add(node);
            node.getSchedules().add(schedule);
            if (lastNode != null) {
                String linkName = Link.generateLinkName(lastNode, node);
                Link link = name2Link.get(linkName);
                schedule.getPlannedLinks().add(link);
                link.getSchedules().add(schedule);
            }
            if (trainSchedule.getArrivalHhmmss() != null && !"".equals(trainSchedule.getArrivalHhmmss())) {
                schedule.getEnterTimes().put(index, trainSchedule.getArrivalSeconds());
            }
            if (trainSchedule.getDepartureHhmmss() != null && !"".equals(trainSchedule.getDepartureHhmmss())) {
                schedule.getLeaveTimes().put(index, trainSchedule.getDepartureSeconds());
            }
            if (schedule.getLeaveTimes().containsKey(index) && schedule.getEnterTimes().containsKey(index)) {
                schedule.getDwellTimes().put(index, schedule.getLeaveTimes().get(index) - schedule.getEnterTimes().get(index));
            }

            schedule.getTracks().put(index, trainSchedule.getTrack());
            Track.Direction direction;
            if (lastNode != null) {
                String linkName = Link.generateLinkName(lastNode, node);
                Link link = name2Link.get(linkName);
                direction = link.getDirection();
            } else {
                direction = schedule.getDirection();
            }
            if (!node.getName2Track().containsKey(trainSchedule.getTrack())) {
                Track track = new Track();
                track.setName(trainSchedule.getTrack());
                track.setNode(node);
                track.setDirection(direction);
                node.getTracks().add(track);
                node.getName2Track().put(track.getName(), track);
            } else {
                Track track = node.getName2Track().get(trainSchedule.getTrack());
                if (!track.getDirection().equals(Track.Direction.BOTH) && !track.getDirection().equals(direction)) {
                    track.setDirection(Track.Direction.BOTH);
                }
            }
            schedule.getNodeStatus().put(index, trainSchedule.getActivity());
            lastNode = node;
        }
    }

    /**
     * 填充铁路链路信息
     *
     * @param linkList 铁路链路表
     * @param headways 链路车头间隔
     * @param minimumRunTimes 链路最小运行时间
     */
    public void fillLinks(List<entity.Link> linkList, List<Headway> headways, List<MinimumRunTime> minimumRunTimes) {
        for (entity.Link link : linkList) {
            Link linkTmp = new Link();
            String name = Link.generateLinkName(link.getStartNode(), link.getEndNode());
            linkTmp.setName(name);
            linkTmp.setStartNode(this.code2Node.get(link.getStartNode()));
            linkTmp.setEndNode(this.code2Node.get(link.getEndNode()));
            linkTmp.setDirection(Track.Direction.valueOf(link.getDirection()));
            linkTmp.setDistanceMeter(link.getDistanceMeters());
            links.add(linkTmp);
            name2Link.put(linkTmp.getName(), linkTmp);
        }

        for (entity.Headway headway : headways) {
            Link linkTmp;
            String name = Link.generateLinkName(headway.getLinkStartNode(), headway.getLinkEndNode());
            if (this.name2Link.containsKey(name)) {
                linkTmp = this.name2Link.get(name);
            } else {
                linkTmp = new Link();
                linkTmp.setName(name);
                linkTmp.setStartNode(this.code2Node.get(headway.getLinkStartNode()));
                linkTmp.setEndNode(this.code2Node.get(headway.getLinkEndNode()));
            }
            int a = 0;
            int b = 0;
            int c = 0;
            int d = 0;
            if (!ActivityType.PASS.getValue().equals(headway.getStartActivityTrainFront())) {
                a = 1;
            }
            if (!ActivityType.PASS.getValue().equals(headway.getStartActivityTrainBehind())) {
                c = 1;
            }
            if (!ActivityType.PASS.getValue().equals(headway.getEndActivityTrainFront())) {
                b = 1;
            }
            if (!ActivityType.PASS.getValue().equals(headway.getEndActivityTrainBehind())) {
                d = 1;
            }
            int[][][][] minimumHeadway = linkTmp.getMinimumHeadway();
            minimumHeadway[a][b][c][d] = headway.getMinimumHeadwaySeconds();
            linkTmp.setMinimumHeadway(minimumHeadway);
            //todo
//            linkTmp.setLinkScenarioList();

            if (!name2Link.containsKey(name)) {
                links.add(linkTmp);
                name2Link.put(name, linkTmp);
            }

            // updateMaxHeadway
            maxHeadway = Math.max(maxHeadway, headway.getMinimumHeadwaySeconds());
        }

        for (entity.MinimumRunTime minimumRunTime : minimumRunTimes) {
            Link linkTmp;
            String linkName = Link.generateLinkName(minimumRunTime.getLinkStartNode(), minimumRunTime.getLinkEndNode());
            if (this.name2Link.containsKey(linkName)) {
                linkTmp = this.name2Link.get(linkName);
            } else {
                linkTmp = new Link();
                linkTmp.setName(linkName);
                linkTmp.setStartNode(this.code2Node.get(minimumRunTime.getLinkStartNode()));
                linkTmp.setEndNode(this.code2Node.get(minimumRunTime.getLinkEndNode()));
            }
            int a = 0;
            int b = 0;
            if (!ActivityType.PASS.getValue().equals(minimumRunTime.getStartActivity())) {
                a = 1;
            }
            if (!ActivityType.PASS.getValue().equals(minimumRunTime.getEndActivity())) {
                b = 1;
            }
            int[][] minRunTime = linkTmp.getMinimumRunTime();
            minRunTime[a][b] = minimumRunTime.getMinimumRunTimeSeconds();
            linkTmp.setMinimumRunTime(minRunTime);
            //todo
//            linkTmp.setLinkScenarioList();


            if (!name2Link.containsKey(linkName)) {
                links.add(linkTmp);
                name2Link.put(linkName, linkTmp);
            }
        }
    }

    /**
     * 填充站点信息
     *
     * @param nodes 站点列表
     */
    public void fillNode(List<entity.Node> nodes) {
        for (int index = 0; index < nodes.size(); index++) {
            entity.Node node = nodes.get(index);
            Node nodeTmp = new Node();
            nodeTmp.setIndex(index);
            nodeTmp.setName(node.getName());
            nodeTmp.setCode(node.getCode());
            switch (node.getCategory()) {
                case "STATION":
                    nodeTmp.setType(Node.Type.STATION);
                    break;
                case "CONTROL_POINT":
                    nodeTmp.setType(Node.Type.CONTROL_POINT);
                    break;
                case "JUNCTION":
                    nodeTmp.setType(Node.Type.JUNCTION);
                    break;
                default:
                    System.out.println("No match node category!");
            }
            nodeTmp.setLatitude(node.getLatitude());
            nodeTmp.setLongitude(node.getLongitude());
            fillTracks(node, nodeTmp);
            nodeTmp.setDepot(false); // 会在后面判断，默认false
            nodeTmp.setStEb(node.getStEb());
            nodeTmp.setStWb(node.getStWb());
            this.nodes.add(nodeTmp);
            this.code2Node.put(node.getCode(), nodeTmp);
        }
    }

    /**
     * 填充基站价值
     *
     * @param baseStationValues 基站价值列表
     */
    public void fillBaseStationValue(List<BaseStationValue> baseStationValues) {
        for (BaseStationValue baseStationValue : baseStationValues) {
            Node node = code2Node.get(baseStationValue.getNode());
            node.getBsvList().add(baseStationValue);
        }
        for (Node node : nodes) {
            double cumBsv = 0;
            if (!node.getBsvList().isEmpty()) {
                for (BaseStationValue bsv : node.getBsvList()) {
                    cumBsv += bsv.getBsv();
                }
                cumBsv /= node.getBsvList().size();
            }
            node.setAvgBsv(cumBsv);
        }
    }

    /**
     * 填充轨道信息
     *
     * @param node 站点数据
     * @param nodeTmp 站点
     */
    private void fillTracks(entity.Node node, Node nodeTmp) {
        Map<String, Track> name2Track = new HashMap<>(12);
        if (!StringUtils.isEmpty(node.getEbTracks())) {
            for (String name : node.getEbTracks().split(",")) {
                Track track = new Track();
                track.setNode(nodeTmp);
                track.setName(name);
                track.setDirection(Track.Direction.EB);
                name2Track.put(name, track);
            }
        }
        if (!StringUtils.isEmpty(node.getWbTracks())) {
            for (String name : node.getWbTracks().split(",")) {
                if (name2Track.containsKey(name)) {
                    name2Track.get(name).setDirection(Track.Direction.BOTH);
                } else {
                    Track track = new Track();
                    track.setNode(nodeTmp);
                    track.setName(name);
                    track.setDirection(Track.Direction.WB);
                    name2Track.put(name, track);
                }
            }
        }
        List<Track> tracks = new ArrayList<>(name2Track.values());
        nodeTmp.setTracks(tracks);
        nodeTmp.setName2Track(name2Track);
    }

    /**
     * 填充问题情景信息
     *
     * @param ertList 延长运行时间
     * @param ldList 延迟出发
     * @param rsList 已发生的路线运行情况
     * @param sedList 车展延长逗留
     * @param tedList 列车延长逗留
     */
    public void fillScenario(List<ExtendedRunTime> ertList, List<LateDeparture> ldList, List<RealizedSchedule> rsList,
                             List<StationExtendedDwell> sedList, List<TrainExtendedDwell> tedList) {
        for (ExtendedRunTime extendedRunTime : ertList) {
            LinkScenario linkScenario = new LinkScenario();
            String linkName = Link.generateLinkName(extendedRunTime.getLinkStartNode(), extendedRunTime.getLinkEndNode());
            linkScenario.setLink(name2Link.get(linkName));
            linkScenario.setStartTime(extendedRunTime.getStartTimeSeconds());
            linkScenario.setEndTime(extendedRunTime.getEndTimeSeconds());
            linkScenario.setExtendedRunTime(extendedRunTime.getExtendedRunTimeSeconds());
            scenario.getLinkScenarios().add(linkScenario);
        }

        for (LateDeparture lateDeparture : ldList) {
            LateDepartureScenario lps = new LateDepartureScenario();
            lps.setSchedule(courseId2Schedule.get(lateDeparture.getTrainCourseId()));
            lps.setDepartureDelaySeconds(lateDeparture.getDepartureDelaySeconds());
            scenario.getLateDepartureScenarios().add(lps);
        }

        for (RealizedSchedule rs : rsList) {
            RealizedScheduleScenario rsScenario = new RealizedScheduleScenario();
            rsScenario.setSchedule(courseId2Schedule.get(rs.getTrainCourseId()));
            rsScenario.setSeq(rs.getSeq());
            rsScenario.setNode(code2Node.get(rs.getNode()));
            rsScenario.setArrivalSeconds(rs.getArrivalSeconds());
            rsScenario.setDepartureSeconds(rs.getDepartureSeconds());
            rsScenario.setTrack(rs.getTrack());
            scenario.getRealizedScheduleScenarios().add(rsScenario);
        }

        for (StationExtendedDwell sed : sedList) {
            StationExtendedDwellScenario sedScenario = new StationExtendedDwellScenario();
            sedScenario.setNode(code2Node.get(sed.getNode()));
            sedScenario.setStartTimeSeconds(sed.getStartTimeSeconds());
            sedScenario.setEndTimeSeconds(sed.getEndTimeSeconds());
            sedScenario.setExtendedRunTimeSeconds(sed.getExtendedRunTimeSeconds());
            scenario.getStationExtendedDwellScenarios().add(sedScenario);
        }

        for (TrainExtendedDwell ted : tedList) {
            TrainExtendedDwellScenario tedScenario = new TrainExtendedDwellScenario();
            tedScenario.setSchedule(courseId2Schedule.get(ted.getTrainCourseId()));
            tedScenario.setNode(code2Node.get(ted.getNode()));
            tedScenario.setExtendedRunTimeSeconds(ted.getExtendedRunTimeSeconds());
            scenario.getTrainExtendedDwellScenarios().add(tedScenario);
        }
    }

    public void adjustMinimumRunTime() {
        boolean allowAdjustMinimumRunTime = true;
        if (!allowAdjustMinimumRunTime) {
            return;
        }

        for (Schedule schedule : schedules) {
            if (Schedule.EventType.TRAIN != schedule.getEventType()) {
                continue;
            }

            List<Node> plannedNodeList = schedule.getPlannedNodes();
            Map<Integer, Integer> leaveTimeMap = schedule.getLeaveTimes();
            Map<Integer, Integer> enterTimeMap = schedule.getEnterTimes();
            Map<Integer, String> nodeStatusMap = schedule.getNodeStatus();

            for (int i = 2; i <= plannedNodeList.size(); ++i) {
                Node prevNode = plannedNodeList.get(i - 2);
                Node currentNode = plannedNodeList.get(i - 1);

                String prevStatus = nodeStatusMap.get(i - 1);
                String currentStatus = nodeStatusMap.get(i);
                Vertex.Type prevType = ActivityType.PASS.getValue().equals(prevStatus) ? Vertex.Type.PASS : Vertex.Type.STOP;
                Vertex.Type currentType = ActivityType.PASS.getValue().equals(currentStatus) ? Vertex.Type.PASS : Vertex.Type.STOP;

                int enterTime = enterTimeMap.get(i);
                int prevNodeLeaveTime = leaveTimeMap.get(i - 1);
                int actualRunTime = enterTime - prevNodeLeaveTime;

                String linkName = Link.generateLinkName(prevNode, currentNode);
                Link link = name2Link.get(linkName);
                int minimumRunTime = link.calcMinimumRunTime(prevType, currentType);

                if (actualRunTime < minimumRunTime) {
                    link.getMinimumRunTime()[link.vertexType2Index(prevType)][link.vertexType2Index(currentType)] = actualRunTime;
                }
            }
        }
    }

    public void checkConsecutiveCourses() {
        for (Duty duty : this.duties) {
            String prevTrainEndNodeCode = null;
            String prevCourseId = null;
            for (Schedule schedule : duty.getPlannedSchedules()) {
                if (prevTrainEndNodeCode != null) {
                    String currentTrainStartNodeCode = schedule.getStartNode().getCode();
                    String currentCourseId = schedule.getCourseId();
                    if (!prevTrainEndNodeCode.equals(currentTrainStartNodeCode)) {
                        System.out.printf("%s and %s are consecutive courses, but the end and start node are different.%n", prevCourseId, currentCourseId);
                    }
                }

                prevTrainEndNodeCode = schedule.getEndNode().getCode();
                prevCourseId = schedule.getCourseId();
            }
        }
    }

    public void checkConsecutiveDuties() {
        for (RollingStock rollingStock : this.getRollingStocks()) {
            List<Duty> dutyList = rollingStock.getPlannedDuties();
            if (dutyList.size() == 1) {
                continue;
            }

            dutyList.sort(Comparator.comparingInt(Duty::getStartTime));

            String prevDutyEndNodeCode = null;
            String prevDutyId = null;
            int prevDutyEndTime = 0;
            for (Duty duty : rollingStock.getPlannedDuties()) {
                String currentDutyStartNodeCode = duty.getStartNode().getCode();
                String currentDutyId = duty.getDutyId();
                int currentDutyStartTime = duty.getStartTime();

                if (prevDutyEndNodeCode != null) {
                    if (!prevDutyEndNodeCode.equals(currentDutyStartNodeCode)) {
                        LOGGER.warning(String.format("%s and %s are consecutive duties, but the end and start node are different.%n", prevDutyId, currentDutyId));
                    }

                    int timeDiff = currentDutyStartTime - prevDutyEndTime;
                    if (timeDiff < 420) {
                        LOGGER.warning(String.format("The separation time between two duties %s and %s : %d > 420", prevDutyId, currentDutyId, timeDiff));
                    }
                }

                prevDutyEndNodeCode = duty.getEndNode().getCode();
                prevDutyId = currentDutyId;
                prevDutyEndTime = duty.getEndTime();
            }
        }
    }

    public void checkFirstLastSchedule() {
        for (Duty duty : duties) {
            if (duty.getPlannedSchedules().isEmpty()) {
                LOGGER.warning(String.format("Empty duty: %s", duty.getDutyId()));
                continue;
            }

            if (duty.getPlannedSchedules().size() == 1 && Schedule.EventType.RESERVE == duty.getPlannedSchedules().get(0).getEventType()) {
                continue;
            }

            Schedule firstSchedule = duty.getPlannedSchedules().get(0);
            if (Schedule.EventType.TRAIN != firstSchedule.getEventType()) {
                LOGGER.warning(String.format("Event type of the first schedule is not TRAIN: %s %s", duty.getDutyId(), firstSchedule.getEventType().name()));
            }

            Schedule lastSchedule = duty.getPlannedSchedules().get(duty.getPlannedSchedules().size() - 1);
            if (Schedule.EventType.TRAIN != lastSchedule.getEventType()) {
                LOGGER.warning(String.format("Event type of the last schedule is not TRAIN: %s %s", duty.getDutyId(), lastSchedule.getEventType().name()));
            }
        }
    }

    public void checkChangeEndSpareSchedule() {
        for (Duty duty : duties) {
            if (duty.getPlannedSchedules().isEmpty()) {
                continue;
            }

            if (duty.getPlannedSchedules().size() == 1 && Schedule.EventType.RESERVE == duty.getPlannedSchedules().get(0).getEventType()) {
                continue;
            }

            Schedule prevCourseSchedule = null;
            for (int i = 0; i < duty.getPlannedSchedules().size(); ++i) {
                Schedule schedule = duty.getPlannedSchedules().get(i);
                Schedule.EventType eventType = schedule.getEventType();

                if (Schedule.EventType.TRAIN == eventType) {
                    prevCourseSchedule = schedule;
                    continue;
                }

                if (Schedule.EventType.CHANGE_END != eventType && Schedule.EventType.SPARE != eventType) {
                    continue;
                }

                String startNodeCode = schedule.getStartNode().getCode();
                String endNodeCode = schedule.getEndNode().getCode();

                if (!startNodeCode.equals(endNodeCode)) {
                    LOGGER.warning(String.format("Schedule has different start and end node: %s %s %s", schedule.getEventType().name(), startNodeCode, endNodeCode));
                }

                if (Schedule.EventType.SPARE == eventType) {
                    if (schedule.getEndTime() - schedule.getStartTime() >= 0) {
                        continue;
                    } else {
                        LOGGER.warning(String.format("Spare time: start: %d, end: %d", schedule.getStartTime(), schedule.getEndTime()));
                    }
                }

                Schedule nextCourseSchedule = null;
                for (int j = i + 1; j < duty.getPlannedSchedules().size(); ++j) {
                    if (Schedule.EventType.TRAIN == duty.getPlannedSchedules().get(j).getEventType()) {
                        nextCourseSchedule = duty.getPlannedSchedules().get(j);
                        break;
                    }
                }

                int expectedChangeEndTime = 420;
                if (prevCourseSchedule.getDirection() == nextCourseSchedule.getDirection()) {
                    if ("PADTLL".equals(schedule.getEndNode().getCode())) {
                        expectedChangeEndTime = 30;
                    } else {
                        expectedChangeEndTime = 60;
                    }
                }

                int actualChangeEndTime = schedule.getEndTime() - schedule.getStartTime();
                if (actualChangeEndTime < expectedChangeEndTime) {
                    LOGGER.warning(String.format("ChangeEnd time %d < %d, start time: %d, end time: %d, previous course: %s, next course: %s, node code: %s", actualChangeEndTime, expectedChangeEndTime, schedule.getStartTime(), schedule.getEndTime(), prevCourseSchedule.getCourseId(), nextCourseSchedule.getCourseId(), schedule.getStartNode().getCode()));
                    specialChangeEndTimeMap.computeIfAbsent(prevCourseSchedule.getCourseId(), k -> new HashMap<>()).put(nextCourseSchedule.getCourseId(), actualChangeEndTime);
                }
            }
        }
    }

    public void checkMinimumConnectionTimeBetweenTrains() {
        Map<String, List<Pair<Integer, String>>> nodeTrainStartTimeMap = new HashMap<>();

        for (RollingStock rollingStock : rollingStocks) {
            if (Schedule.EventType.TRAIN != rollingStock.getPlannedDuties().get(0).getPlannedSchedules().get(0).getEventType()) {
                continue;
            }

            List<Duty> dutyList = rollingStock.getPlannedDuties();
            dutyList.sort(Comparator.comparingInt(Duty::getStartTime));

            Duty firstDuty = dutyList.get(0);
            Schedule firstSchedule = firstDuty.getPlannedSchedules().get(0);

            String startNodeCode = firstSchedule.getStartNode().getCode();
            int startTime = firstDuty.getStartTime();

            nodeTrainStartTimeMap.computeIfAbsent(startNodeCode, k -> new ArrayList<>()).add(Pair.of(startTime, firstDuty.getDutyId()));
        }

        for (Map.Entry<String, List<Pair<Integer, String>>> nodeTrainStartTime : nodeTrainStartTimeMap.entrySet()) {
            nodeTrainStartTime.getValue().sort(Comparator.comparingInt(Pair::getLeft));

            for (int i = 0; i < nodeTrainStartTime.getValue().size() - 1; ++i) {
                Pair<Integer, String> firstPair = nodeTrainStartTime.getValue().get(i);
                Pair<Integer, String> secondPair = nodeTrainStartTime.getValue().get(i + 1);

                int timeDiff = secondPair.getKey() - firstPair.getKey();
                if (timeDiff < 420) {
                    LOGGER.warning(String.format("Connection time between two train %d < 420: %s, %s, %s", timeDiff, nodeTrainStartTime.getKey(), firstPair.getValue(), secondPair.getValue()));
                }
            }
        }
    }

    public void checkMinimumHeadwayBetweenConsecutiveTrains() {
        Map<String, List<Triple<String, Integer, Integer>>> nodeEventListMap = new HashMap<>();

        for (Schedule schedule : schedules) {
            if (Schedule.EventType.TRAIN != schedule.getEventType()) {
                continue;
            }

            List<Node> plannedNodeList = schedule.getPlannedNodes();
            Map<Integer, Integer> leaveTimeMap = schedule.getLeaveTimes();
            Map<Integer, Integer> enterTimeMap = schedule.getEnterTimes();

            for (int i = 0; i < plannedNodeList.size(); ++i) {
                Node node = plannedNodeList.get(i);
                int arrivalTime, leaveTime;
                if (i == 0) {
                    leaveTime = leaveTimeMap.get(1);
                    arrivalTime = leaveTime;
                } else if (i == plannedNodeList.size() - 1) {
                    arrivalTime = enterTimeMap.get(i + 1);
                    leaveTime = arrivalTime;
                } else {
                    arrivalTime = enterTimeMap.get(i + 1);
                    leaveTime = leaveTimeMap.get(i + 1);
                }

                String track = schedule.getTracks().get(i + 1);
                String id = String.join("_", node.getCode(), track);
                nodeEventListMap.computeIfAbsent(id, k -> new ArrayList<>()).add(Triple.of(schedule.getCourseId(), arrivalTime, leaveTime));
            }
        }

        for (Map.Entry<String, List<Triple<String, Integer, Integer>>> entry : nodeEventListMap.entrySet()) {
            entry.getValue().sort(Comparator.comparingInt(Triple::getMiddle));

            List<Triple<String, Integer, Integer>> eventList = entry.getValue();
            eventList.sort(Comparator.comparingInt(Triple::getMiddle));

            int prevLeave = -1;
            String prevCourseId = "";
            for (Triple<String, Integer, Integer> event : eventList) {
                int currentArrival = event.getMiddle();
                int timeDiff = currentArrival - prevLeave;
                if (prevLeave >= 0 && currentArrival - prevLeave < 30) {
                    LOGGER.warning(String.format("Consecutive two trains at %s %s: %d < 30, %d, %d", entry.getKey(), event.getLeft(), timeDiff, currentArrival, prevLeave));

                    Schedule schedule = courseId2Schedule.get(prevCourseId);
                    int tmpSeq = -1;
                    for (Map.Entry<Integer, Integer> leaveEntry : schedule.getLeaveTimes().entrySet()) {
                        if (leaveEntry.getValue() == prevLeave) {
                            tmpSeq = leaveEntry.getKey();
                            break;
                        }
                    }

                    schedule.getLeaveTimes().put(tmpSeq, prevLeave - 60);
                    schedule.getDwellTimes().put(tmpSeq, schedule.getDwellTimes().get(tmpSeq) - 60);
                }

                prevLeave = event.getRight();
                prevCourseId = event.getLeft();
            }
        }
    }

    public static String getCourseName(Course course) {
        return course.id + "_" + course.seq + "_" + course.node.getCode();
    }
}
