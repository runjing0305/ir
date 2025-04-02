package reschedule.graph;

import constant.Constants;
import context.*;
import context.scenario.LateDepartureScenario;
import entity.BaseStationValue;
import graph.Vertex;
import lombok.Getter;
import lombok.Setter;
import solution.Solution;
import util.EvaluationUtils;

import java.util.*;
import java.util.logging.LogManager;
import java.util.logging.Logger;

@Getter
@Setter
public class CellGraph {
    private static final Logger LOGGER = LogManager.getLogManager().getLogger(CellGraph.class.getName());
    private static final int MAX_NODE_DELAY_TIME = 60;

    private static final int MAX_EXTEND_RUN_TIME = 300;
    private static final int MAX_NODE_ADVANCE_TIME = 60;
    private static final int MAX_COURSE_DELAY_TIME = 1800;
    int startTime = 0;
    int endTime = Constants.BIG_M;
    private List<Cell> cells = new ArrayList<>();
    private Map<String, Cell> name2Cell = new HashMap<>();
    private Map<Cell, List<CellEdge>> cell2Edges = new HashMap<>();
    private List<CellVertex> vertexList = new ArrayList<>();
    private Map<String, CellVertex> name2Vertex = new HashMap<>();
    private Map<String, Set<CellVertex>> scheduleAndNode2Vertices = new HashMap<>();
    private List<CellEdge> cellEdges = new ArrayList<>();
    private Map<String, CellEdge> name2CellEdge = new HashMap<>();
    private Map<RollingStock, List<CellEdge>> rs2CellEdges = new HashMap<>();
    private Map<Schedule, List<CellVertex>> schedule2SkipPenaltyVertexList = new HashMap<>();
    private List<RollingStock> rollingStocks;
    private Map<RollingStock, List<CellEdge>> rolling2FixedEdge = new HashMap<>();
    private List<CellVertex> stopVertexList = new ArrayList<>();
    private Map<String, Set<Track>> scheduleAndNode2Tracks = new HashMap<>();
    private Map<String, List<Integer>> scheduleNodeSeqList = new HashMap<>();
    private Map<Node, List<TimeItem>> nodeTimeItemList = new HashMap<>();
    private Map<RollingStock, Integer> rollingStockDelay = new HashMap<>(); // 列车fix的前序路线导致的延迟
    private Map<Schedule, Integer> lateDepartureScenarios = new HashMap<>(); // 延迟发车的路线对应的延迟时间
    private Map<Vertex, Cell> vertex2Cell = new HashMap<>();
    private List<Schedule> scheduleList = new ArrayList<>();


    public CellGraph(ProblemContext problemContext, Solution solution) {
        this.rollingStocks = problemContext.getRollingStocks();
        initScheduleList(solution);
        preProcess(problemContext, solution);
        genCells(problemContext);
        genVertexList(solution);
        genEdges(solution, problemContext);
        genIndexes();
        initScheduleNodeAvaTracks(problemContext);
        initScheduleNodeSeqList(problemContext);
        updateScenarios(problemContext);
        getLinkCellProTimeItem(problemContext, solution);
        getNodeProTimeItem(problemContext, solution);
        sortCellTimeItem();
    }

    public static List<Node> getNodeList(Schedule schedule) {
        return schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.getRealizedNodes();
    }

    public String genVertexName(Schedule schedule, Node node, Vertex.Type type, int i) {
        return schedule.getCourseId() + "_" + node.getCode() + "_" + i + "_" + type.toString();
    }

    public String genTrackVertexName(Schedule schedule, Track track, int i) {
        return schedule.getCourseId() + "_" + track.getNode().getCode() + "-" + track.getName() + "-" + i;
    }

    public static String getScheduleNodeStr(Schedule schedule, int i) {
        return schedule.getCourseId() + "_" + i;
    }

    public List<Integer> getNodeSeqList(Schedule schedule, Node node) {
        return scheduleNodeSeqList.getOrDefault(schedule.getCourseId() + "_" + node.getCode(), null);
    }

    public String getTrackCellName(Track track) {
        return track.getNode().getCode() + "_" + track.getName();
    }

    public void saveFixInfo(ProblemContext problemContext, Solution solution) {
        fixLinkCellOccupiedTime(problemContext, solution);
        fixTrackCellOccupiedTime(solution);
    }

    /*
     * get cellEdge link the ith node and (i+1)th node in schedule
     */
    public List<CellEdge> getEdges(Schedule schedule, int i) {
        List<CellEdge> edges = new ArrayList<>();
        if (i < 0 || i >= getNodeList(schedule).size() - 1) {
            return edges;
        }
        Node headNode = getNodeList(schedule).get(i);
        Node tailNode = getNodeList(schedule).get(i + 1);
        Set<CellVertex> headVertices = scheduleAndNode2Vertices.get(getScheduleNodeStr(schedule, i));
        Set<CellVertex> tailVertices = scheduleAndNode2Vertices.get(getScheduleNodeStr(schedule, i + 1));
        for (CellVertex head : headVertices) {
            for (CellVertex tail : tailVertices) {
                if (name2CellEdge.containsKey(head.getName() + "_" + tail.getName())) {
                    edges.add(name2CellEdge.get(head.getName() + "_" + tail.getName()));
                }
            }
        }
        return edges;
    }

    private void updateScenarios(ProblemContext problemContext) {
        updateLateDepartureScenarios(problemContext);
    }

    private void updateLateDepartureScenarios(ProblemContext problemContext) {
        for (LateDepartureScenario lateDepartureScenario : problemContext.getScenario().getLateDepartureScenarios()) {
            lateDepartureScenarios.put(lateDepartureScenario.getSchedule(),
                    lateDepartureScenario.getDepartureDelaySeconds());
        }
    }

    private void initScheduleList(Solution solution) {
        scheduleList.addAll(solution.getSchedule2RollingStockMap().keySet());
    }

    private void fixLinkCellOccupiedTime(ProblemContext problemContext, Solution solution) {
        for (Schedule schedule : solution.getSchedule2RollingStockMap().keySet()) {
            for (int i = 0; i < getNodeList(schedule).size() - 1; ++i) {
                if (!solution.getScheduleStationArrivalTimeMap().containsKey(schedule)
                        || solution.getScheduleStationArrivalTimeMap().get(schedule).get(i) < startTime) {
                    continue;
                }
                Node head = getNodeList(schedule).get(i);
                Node tail = getNodeList(schedule).get(i + 1);
                Link link = problemContext.getName2Link().get(head.getCode() + "_" + tail.getCode());
                CellEdge edge = getCellEdge(solution, schedule, i);
                if (!name2Cell.containsKey(link.getName()) || edge == null) {
                    continue;
                }
                int headDepartureTime = solution.getScheduleStationDepartureTimeMap().get(schedule).get(i);
                int tailArrivalTime = solution.getScheduleStationArrivalTimeMap().get(schedule).get(i + 1);
                Cell cell = name2Cell.get(link.getName());
                cell.getFixOccTimeItems().add(new TimeItem(headDepartureTime + edge.getArrivalTimeInterval(),
                        tailArrivalTime + edge.getDepartureTimeInterval()));
            }
        }
        for (Cell cell : cells) {
            if (!cell.getType().equals(Cell.Type.LINK)) {
                continue;
            }
            cell.setFixOccTimeItems(connectTimeItem(cell.getFixOccTimeItems()));
        }
    }

    private void fixTrackCellOccupiedTime(Solution solution) {
        for (Schedule schedule : solution.getSchedule2RollingStockMap().keySet()) {
            for (int i = 0; i < getNodeList(schedule).size(); ++i) {
                Node node = getNodeList(schedule).get(i);
                if (!solution.getScheduleStationTrackMap().containsKey(schedule)
                        || solution.getScheduleStationArrivalTimeMap().get(schedule).get(i) < startTime ||
                        !name2Cell.containsKey(node.getCode())) {
                    continue;
                }
                String trackStr = solution.getScheduleStationTrackMap().get(schedule).get(i);
                if (!node.getName2Track().containsKey(trackStr)) {
                    continue;
                }
                Cell cell = name2Cell.get(node.getCode());
                Track track = node.getName2Track().get(trackStr);
                int arrivalTime = solution.getScheduleStationArrivalTimeMap().get(schedule).get(i);
                int departureTime = solution.getScheduleStationDepartureTimeMap().get(schedule).get(i);
                TimeItem item = new TimeItem(arrivalTime, departureTime);
                item.setTrack(track.getName());
                cell.getFixOccTimeItems().add(item);
            }
        }
    }

    private CellEdge getCellEdge(Solution solution, Schedule schedule, int headSeq) {
        if (headSeq < 0 || headSeq >= getNodeList(schedule).size() - 1) {
            return null;
        }
        Node head = getNodeList(schedule).get(headSeq);
        Vertex.Type headVType = solution.getScheduleSkipStationMap().get(schedule).get(headSeq) ?
                Vertex.Type.PASS : Vertex.Type.STOP;
        Node tail = getNodeList(schedule).get(headSeq + 1);
        Vertex.Type tailVType = solution.getScheduleSkipStationMap().get(schedule).get(headSeq + 1) ?
                Vertex.Type.PASS : Vertex.Type.STOP;
        if (!name2Vertex.containsKey(genVertexName(schedule, head, headVType, headSeq)) ||
                !name2Vertex.containsKey(genVertexName(schedule, tail, tailVType, headSeq + 1))) {
            return null;
        }
        CellVertex headVertex = name2Vertex.get(genVertexName(schedule, head, headVType, headSeq));
        CellVertex tailVertex = name2Vertex.get(genVertexName(schedule, tail, tailVType, headSeq + 1));
        return name2CellEdge.getOrDefault(headVertex.getName() + "_" + tailVertex.getName(), null);
    }

    private void preProcess(ProblemContext problemContext, Solution solution) {
        updateEndNodeRealizedDeparture(problemContext, solution);
    }

    private void initTimeInterval(ProblemContext problemContext, Solution solution) {
        initRollingStockDelay(problemContext, solution);
        initGraphTimeInterval(problemContext, solution);
        initCellEdgeTimeInterval(problemContext, solution);
        initLinkCellTimeItem();
    }

    private void updateEndNodeRealizedDeparture(ProblemContext problemContext, Solution solution) {
        for (List<Schedule> schedules : solution.getRollingStock2ScheduleListMap().values()) {
            for (int i = 0; i < schedules.size() - 1; ++i) {
                Schedule head = schedules.get(i);
                Schedule tail = schedules.get(i + 1);
                if (head.getRealizedNodes().isEmpty() ||
                        !head.getRealizedEnterTimes().containsKey(head.getRealizedNodes().size())
                        || head.getRealizedEnterTimes().get(head.getRealizedNodes().size()) == 0) {
                    break;
                }
                if (!tail.getRealizedNodes().isEmpty() && tail.getRealizedEnterTimes().containsKey(1)
                        && tail.getRealizedEnterTimes().get(1) != 0) {
                    int leaveTime = tail.getRealizedLeaveTimes().get(1);
                    head.getRealizedLeaveTimes().put(head.getRealizedNodes().size(), leaveTime);
                }
            }
        }
    }

    private void initRollingStockDelay(ProblemContext problemContext, Solution solution) {
        for (Map.Entry<RollingStock, List<Schedule>> item : solution.getRollingStock2ScheduleListMap().entrySet()) {
            if (item.getValue().isEmpty()) {
                continue;
            }
            Schedule headSchedule = item.getValue().get(0);
            int delayTime = 0;
            if (solution.getScheduleStationArrivalTimeMap().containsKey(headSchedule)) {
                int arrivalTime = solution.getScheduleStationArrivalTimeMap().get(headSchedule).get(0);
                delayTime = Math.max(arrivalTime - headSchedule.getStartTime(), 0);
            }
            if (delayTime > 0) {
                delayTime = Math.max(lateDepartureScenarios.getOrDefault(headSchedule, 0), delayTime);
                lateDepartureScenarios.put(headSchedule, delayTime);
            }
            rollingStockDelay.put(item.getKey(), delayTime);
        }
    }

    private void initGraphTimeInterval(ProblemContext problemContext, Solution solution) {
        startTime = Constants.BIG_M;
        endTime = 0;
        for (Schedule schedule : scheduleList) {
            RollingStock rollingStock = solution.getSchedule2RollingStockMap().getOrDefault(schedule, null);
            int delayTime = rollingStockDelay.getOrDefault(rollingStock, 0);
            int delayStartTime = lateDepartureScenarios.getOrDefault(schedule, 0);
            startTime = Math.min(schedule.getStartTime() + delayStartTime, startTime);
            endTime = Math.max(schedule.getEndTime() + MAX_COURSE_DELAY_TIME + delayTime,
                    endTime + delayTime + delayStartTime);
        }
    }

    private int getNodeStartTime(Schedule schedule, int seq, Solution solution) {
        int startTime = seq != 0 ? schedule.getEnterTimes().get(seq + 1)
                : schedule.getStartTime();
        int delayStarTime = (seq == 0) ? lateDepartureScenarios.getOrDefault(schedule, 0) : 0;
        startTime += delayStarTime;
        int reScheduleTime = seq != 0 ? schedule.getEnterTimes().get(seq + 1) : schedule.getLeaveTimes().get(seq + 1);
        startTime = Math.max(startTime, reScheduleTime - MAX_NODE_ADVANCE_TIME);
        return startTime;
    }

    private void initLinkCellTimeItem() {
        for (Map.Entry<Cell, List<CellEdge>> item : cell2Edges.entrySet()) {
            List<TimeItem> timeItems = new ArrayList<>();
            for (CellEdge edge : item.getValue()) {
                timeItems.add(new TimeItem(edge.getStartTime() - edge.getArrivalTimeInterval(),
                        edge.getEndTime() + edge.getDepartureTimeInterval()));
            }
            List<TimeItem> newTimeItems = connectTimeItem(timeItems);
            item.getKey().setProOccTimeItems(newTimeItems);
        }
    }

    private void getLinkCellProTimeItem(ProblemContext problemContext, Solution solution) {
        for (Link link : problemContext.getLinks()) {
            if (!name2Cell.containsKey(link.getName())) {
                continue;
            }
            Cell cell = name2Cell.get(link.getName());
            for (Schedule schedule : scheduleList) {
                if (!schedule.getPlannedLinks().contains(link)) {
                    continue;
                }
                for (int i = 0; i < getNodeList(schedule).size() - 1; ++i) {
                    if (!getNodeList(schedule).get(i).equals(link.getStartNode()) ||
                            !getNodeList(schedule).get(i + 1).equals(link.getEndNode())) {
                        continue;
                    }
                    int startTime = getScheduleSeqStarTime(schedule, i, solution);
                    TimeItem item = new TimeItem(startTime);
                    item.setNodeSeq(i);
                    item.setSchedule(schedule);
                    cell.getProOccTimeItems().add(item);
                }
            }
        }
    }

    private int getScheduleSeqStarTime(Schedule schedule, int seq, Solution solution) {
        if (seq == 0) {
            if (schedule.getRealizedLeaveTimes().containsKey(seq + 1)
                    && schedule.getRealizedLeaveTimes().get(seq + 1) != 0) {
                return schedule.getRealizedLeaveTimes().get(seq + 1);
            } else {
                return solution.getScheduleStationDepartureTimeMap().containsKey(schedule) ?
                        solution.getScheduleStationDepartureTimeMap().get(schedule).get(seq) : schedule.getStartTime();
            }
        } else {
            if (schedule.getRealizedEnterTimes().containsKey(seq + 1)
                    && schedule.getRealizedEnterTimes().get(seq + 1) != 0) {
                return schedule.getRealizedEnterTimes().get(seq + 1);
            } else {
                return solution.getScheduleStationArrivalTimeMap().containsKey(schedule) ?
                        solution.getScheduleStationArrivalTimeMap().get(schedule).get(seq) :
                        schedule.getEnterTimes().get(seq + 1);
            }
        }
    }

    private void getNodeProTimeItem(ProblemContext problemContext, Solution solution) {
        for (Map.Entry<RollingStock, List<Schedule>> listEntry : solution.getRollingStock2ScheduleListMap().entrySet()) {
            for (Schedule schedule : listEntry.getValue()) {
                for (int seq = 0; seq < getNodeList(schedule).size(); ++seq) {
                    Node node = getNodeList(schedule).get(seq);
                    if (!name2Cell.containsKey(node.getCode())) {
                        continue;
                    }
                    Cell cell = name2Cell.get(node.getCode());
                    int startTime = getScheduleSeqStarTime(schedule, seq, solution);
                    String track = solution.getScheduleStationTrackMap().get(schedule).get(seq);
                    TimeItem item = new TimeItem(startTime);
                    item.setNodeSeq(seq);
                    item.setSchedule(schedule);
                    item.setTrack(track);
                    item.setRollingStock(listEntry.getKey());
                    cell.getProOccTimeItems().add(item);
                }
            }
        }
    }

    private void sortCellTimeItem() {
        for (Cell cell : cells) {
            cell.getProOccTimeItems().sort(Comparator.comparingInt(TimeItem::getStartTime));
        }
    }

    private List<TimeItem> connectTimeItem(List<TimeItem> items) {
        if (items.isEmpty()) {
            return items;
        }
        List<TimeItem> newItemList = new ArrayList<>();
        items.sort(Comparator.comparingInt(TimeItem::getStartTime));
        int itemStartTime = items.get(0).getStartTime();
        int itemEndTime = items.get(0).getEndTime();
        for (int i = 1; i < items.size(); ++i) {
            TimeItem curItem = items.get(i);
            if (curItem.getStartTime() > itemEndTime) {
                newItemList.add(new TimeItem(itemStartTime, itemEndTime));
                itemStartTime = curItem.getStartTime();
            }
            itemEndTime = curItem.getEndTime();
        }
        newItemList.add(new TimeItem(itemStartTime, itemEndTime));
        return newItemList;
    }

    private void initCellEdgeTimeInterval(ProblemContext problemContext, Solution solution) {
        for (Schedule schedule : scheduleList) {
            RollingStock rollingStock = solution.getSchedule2RollingStockMap().getOrDefault(schedule, null);
            int delayTime = rollingStockDelay.getOrDefault(rollingStock, 0);
            for (int seq = 0; seq < getNodeList(schedule).size(); ++seq) {
                String name = getScheduleNodeStr(schedule, seq);
                if (!scheduleAndNode2Vertices.containsKey(name)) {
                    continue;
                }
                int startTime = getNodeStartTime(schedule, seq, solution);
                int endTime = startTime + MAX_NODE_DELAY_TIME + delayTime;
                for (CellVertex vertex : scheduleAndNode2Vertices.get(name)) {
                    vertex.setStartTime(startTime);
                    vertex.setEndTime(endTime + Constants.INNER_SCHEDULE_NODE_DWELL_TIME);
                    for (CellEdge edge : vertex.getOutArcList()) {
                        edge.setStartTime(startTime);
                    }

                    for (CellEdge edge : vertex.getInArcList()) {
                        edge.setEndTime(endTime);
                    }
                }
            }
        }

    }

    private void genCells(ProblemContext problemContext) {
        genLinkCells(problemContext);
        genNodeCells(problemContext);
    }

    private void genLinkCells(ProblemContext problemContext) {
        for (Link link : problemContext.getLinks()) {
            Cell cell = new Cell(link);
            cells.add(cell);
            name2Cell.put(cell.getName(), cell);
        }
    }

    private void genNodeCells(ProblemContext problemContext) {
        for (Node node : problemContext.getNodes()) {
            Cell cell = new Cell(node);
            cells.add(cell);
            name2Cell.put(cell.getName(), cell);
        }
    }

    private void genVertexList(Solution solution) {
        genScheduleNodeVertices(solution);
        genVirtualVertex(Constants.VIRTUAL_START_VERTEX_NAME);
        genVirtualVertex(Constants.VIRTUAL_END_VERTEX_NAME);
    }

    private void genScheduleNodeVertices(Solution solution) {
        // generate schedule-node vertices
        for (Schedule schedule : scheduleList) {
            List<CellVertex> skipVertexList = new ArrayList<>();
            for (int i = 0; i < getNodeList(schedule).size(); i++) {
                Node node = getNodeList(schedule).get(i);
                List<CellVertex> vertexList = new ArrayList<>();
                if (i == 0 || i == getNodeList(schedule).size() - 1) {
                    // schedule head or tail vertex
                    CellVertex stopVertex = genScheduleNodeVertex(schedule, node, Vertex.Type.STOP, i,
                            getScheduleSeqStarTime(schedule, i, solution));
                    vertexList.add(stopVertex);
                } else {
                    CellVertex passVertex = genScheduleNodeVertex(schedule, node, Vertex.Type.PASS, i,
                            getScheduleSeqStarTime(schedule, i, solution));
                    vertexList.add(passVertex);
                    if (isNodePlanedToStop(schedule, i)) {
                        CellVertex stopVertex = genScheduleNodeVertex(schedule, node, Vertex.Type.STOP, i,
                                getScheduleSeqStarTime(schedule, i, solution));
                        stopVertex.setMinDwellTime(Constants.INNER_SCHEDULE_NODE_DWELL_TIME);
                        vertexList.add(stopVertex);
                        stopVertexList.add(stopVertex);
                        skipVertexList.add(passVertex);
                    }
                }
                addVertex(schedule, i, vertexList);
            }
            schedule2SkipPenaltyVertexList.put(schedule, skipVertexList);
        }
    }

    private boolean isNodePlanedToStop(Schedule schedule, int seq) {
        if (!schedule.getRealizedNodes().isEmpty() && schedule.getRealizedNodeStatus().containsKey(seq + 1)) {
            if (schedule.getRealizedNodeStatus().get(seq + 1).equals("UNREALIZED")) {
                return (schedule.getNodeStatus().containsKey(seq + 1)
                        && schedule.getNodeStatus().get(seq + 1).equals(Vertex.Type.STOP.toString()));
            } else {
                return (schedule.getRealizedNodeStatus().containsKey(seq + 1)
                        && schedule.getRealizedNodeStatus().get(seq + 1).equals(Vertex.Type.STOP.toString()));
            }
        } else {
            return (schedule.getNodeStatus().containsKey(seq + 1)
                    && schedule.getNodeStatus().get(seq + 1).equals(Vertex.Type.STOP.toString()));
        }
    }

    private void addVertex(Schedule schedule, int seq, List<CellVertex> vertexList) {
        Node node = getNodeList(schedule).get(seq);
        String scheduleNodeStr = getScheduleNodeStr(schedule, seq);
        if (scheduleAndNode2Vertices.containsKey(scheduleNodeStr)) {
            for (CellVertex vertex : vertexList) {
                scheduleAndNode2Vertices.get(scheduleNodeStr).add(vertex);
            }
        } else {
            Set<CellVertex> vertexSet = new HashSet<>(vertexList);
            scheduleAndNode2Vertices.put(scheduleNodeStr, vertexSet);
        }
    }

    private CellVertex genScheduleNodeVertex(Schedule schedule, Node node, Vertex.Type type,
                                             int i, int arrivalTime) {
        String vertexName = genVertexName(schedule, node, type, i);
        CellVertex vertex = new CellVertex(vertexName);
        vertex.setType(type);
        vertex.setBsv(getBsv(schedule, node, arrivalTime));
        vertex.setSchedule(schedule);
        vertex.setSeq(i);
        vertexList.add(vertex);
        name2Vertex.put(vertex.getName(), vertex);
        return vertex;
    }

    private int getBsv(Schedule schedule, Node node, int arrivalTime) {
        arrivalTime = arrivalTime % 86400;
        int corBsv = 0;
        for (BaseStationValue bsv : node.getBsvList()) {
            if (schedule.getDirection().name().equalsIgnoreCase(bsv.getDirection())
                    && bsv.getStartTimeBandSeconds() <= arrivalTime
                    && bsv.getEndTimeBandSeconds() >= arrivalTime) {
                corBsv = bsv.getBsv();
                break;
            }
        }
        return corBsv;
    }

    private void genVirtualVertex(String name) {
        CellVertex vertex = new CellVertex(name);
        vertex.setType(Vertex.Type.STOP);
        vertex.setVirtual(true);
        vertexList.add(vertex);
        name2Vertex.put(vertex.getName(), vertex);
    }

    private void genStationTracVertex(ProblemContext problemContext) {
        for (Schedule schedule : scheduleList) {
            for (int i = 0; i < getNodeList(schedule).size(); i++) {
                Node node = getNodeList(schedule).get(i);
                for (Track track : node.getTracks()) {
                    String vertexName = genTrackVertexName(schedule, track, i);
                    CellVertex vertex = new CellVertex(vertexName);
                    vertexList.add(vertex);
                    name2Vertex.put(vertex.getName(), vertex);
                }
            }
        }
    }

    private void genIndexes() {
        for (int i = 0; i < vertexList.size(); i++) {
            vertexList.get(i).setIndex(i);
        }
        for (int i = 0; i < cellEdges.size(); i++) {
            cellEdges.get(i).setIndex(i);
        }
    }

    private void genEdges(Solution solution, ProblemContext problemContext) {
        for (Map.Entry<RollingStock, List<Schedule>> item : solution.getRollingStock2ScheduleListMap().entrySet()) {
            if (item.getValue().isEmpty()) {
                continue;
            }
            RollingStock rollingStock = item.getKey();
            genInnerScheduleEdges(rollingStock, item.getValue().get(0), problemContext);
            for (int i = 1; i < item.getValue().size(); ++i) {
                Schedule schedule = item.getValue().get(i);
                genInnerScheduleEdges(rollingStock, schedule, problemContext);
                Schedule headSchedule = item.getValue().get(i - 1);
                genInterScheduleEdges(problemContext, rollingStock, headSchedule, schedule);
            }
            genVirtualStart2ScheduleEdges(rollingStock, item.getValue().get(0));
            genSchedule2VirtualEndEdges(rollingStock, item.getValue().get(item.getValue().size() - 1));
        }
    }

    private void genVirtualStart2ScheduleEdges(RollingStock rollingStock, Schedule schedule) {
        CellVertex head = name2Vertex.get(Constants.VIRTUAL_START_VERTEX_NAME);
        CellVertex tail = name2Vertex.get(genVertexName(schedule, getNodeList(schedule).get(0), Vertex.Type.STOP, 0));
        CellEdge cellEdge = new CellEdge(head, tail, 0, 0);
        cellEdge.setStartTime(schedule.getStartTime());
        addFixedEdge(rollingStock, cellEdge);
        addCellEdge(rollingStock, cellEdge);
    }

    private void genSchedule2VirtualEndEdges(RollingStock rollingStock, Schedule schedule) {
        int index = getNodeList(schedule).size() - 1;
        CellVertex head = name2Vertex.get(genVertexName(schedule, getNodeList(schedule).get(index), Vertex.Type.STOP, index));
        CellVertex tail = name2Vertex.get(Constants.VIRTUAL_END_VERTEX_NAME);
        CellEdge cellEdge = new CellEdge(head, tail, 0, 0);
        cellEdge.setStartTime(schedule.getEndTime());
        addCellEdge(rollingStock, cellEdge);
        addFixedEdge(rollingStock, cellEdge);
    }

    private void genInterScheduleEdges(ProblemContext problemContext,
                                       RollingStock rollingStock, Schedule head, Schedule tail) {
        Node headEndNode = CellGraph.getNodeList(head).get(CellGraph.getNodeList(head).size() - 1);
        CellVertex headVertex = name2Vertex.get(genVertexName(head, headEndNode,
                Vertex.Type.STOP, CellGraph.getNodeList(head).size() - 1));
        Node tailStartNode = CellGraph.getNodeList(tail).get(0);
        CellVertex tailVertex = name2Vertex.get(genVertexName(tail, tailStartNode, Vertex.Type.STOP, 0));
        final int restTime = EvaluationUtils.getChangeEndBetweenConsecutiveCourses(problemContext, head, tail);
        headVertex.setMinDwellTime(restTime);
        tailVertex.setMinDwellTime(0);
        CellEdge edge = new CellEdge(headVertex, tailVertex, 0, 0);
        edge.setStartTime(head.getEndTime());
        edge.setType(CellEdge.Type.INTER);
        addCellEdge(rollingStock, edge);
        addFixedEdge(rollingStock, edge);
    }

    private void genInnerScheduleEdges(RollingStock rollingStock, Schedule schedule, ProblemContext problemContext) {
        for (int i = 0; i < getNodeList(schedule).size() - 1; i++) {
            Node headNode = getNodeList(schedule).get(i);
            Node tailNode = getNodeList(schedule).get(i + 1);
            Link link = problemContext.getName2Link().get(headNode.getCode() + "_" + tailNode.getCode());
            List<CellVertex> headVertices = new ArrayList<>(
                    scheduleAndNode2Vertices.get(getScheduleNodeStr(schedule, i)));
            List<CellVertex> tailVertices = new ArrayList<>(
                    scheduleAndNode2Vertices.get(getScheduleNodeStr(schedule, i + 1)));
            genInnerScheduleEdge(rollingStock, headVertices, tailVertices, link);
        }
    }

    private void genInnerScheduleEdge(RollingStock rollingStock, List<CellVertex> headVertices,
                                      List<CellVertex> tailVertices, Link link) {
        Cell cell = name2Cell.get(link.getName());
        if (cell == null) {
            return;
        }
        List<CellEdge> cellEdges = new ArrayList<>();
        for (CellVertex headVertex : headVertices) {
            for (CellVertex tailVertex : tailVertices) {
                CellEdge edge = new CellEdge(headVertex, tailVertex, 0, link.
                        calcMinimumRunTime(headVertex.getType(), tailVertex.getType()));
                cellEdges.add(edge);
                addCellEdge(rollingStock, edge);
            }
        }
        if (cell2Edges.containsKey(cell)) {
            cell2Edges.get(cell).addAll(cellEdges);
        } else {
            cell2Edges.put(cell, cellEdges);
        }
    }

    private CellVertex getTrackVertex(Schedule schedule, Track track, int i) {
        String verTexName = genTrackVertexName(schedule, track, i);
        if (!name2Vertex.containsKey(verTexName)) {
            return null;
        }
        return name2Vertex.get(verTexName);
    }


    private void addCellEdge(RollingStock rollingStock, CellEdge cellEdge) {
        cellEdge.setRollingStock(rollingStock);
        cellEdges.add(cellEdge);
        name2CellEdge.put(cellEdge.getName(), cellEdge);
        if (rs2CellEdges.containsKey(rollingStock)) {
            rs2CellEdges.get(rollingStock).add(cellEdge);
        } else {
            List<CellEdge> cellEdges = new ArrayList<>();
            cellEdges.add(cellEdge);
            rs2CellEdges.put(rollingStock, cellEdges);
        }
    }

    private void addFixedEdge(RollingStock rollingStock, CellEdge cellEdge) {
        if (rolling2FixedEdge.containsKey(rollingStock)) {
            rolling2FixedEdge.get(rollingStock).add(cellEdge);
        } else {
            List<CellEdge> cellEdges = new ArrayList<>();
            cellEdges.add(cellEdge);
            rolling2FixedEdge.put(rollingStock, cellEdges);
        }
    }

    private void initScheduleNodeAvaTracks(ProblemContext problemContext) {
        for (Schedule schedule : scheduleList) {
            List<Node> nodeList = getNodeList(schedule);
            for (int i = 0; i < nodeList.size(); i++) {
                Node node = nodeList.get(i);
                Track.Direction direction;
                if (i == 0) {
                    direction = schedule.getDirection();
                } else {
                    Node lastNode = nodeList.get(i - 1);
                    Link link = problemContext.getName2Link().get(lastNode.getCode() + "_" + node.getCode());
                    direction = link.getDirection();
                }
                String scheduleNodeStr = getScheduleNodeStr(schedule, i);
                Set<Track> tracks = new HashSet<>();
                for (Track track : node.getTracks()) {
                    if (track.getDirection() == direction
                            || track.getDirection().equals(Track.Direction.BOTH)) {
                        tracks.add(track);
                    }
                }
                scheduleAndNode2Tracks.put(scheduleNodeStr, tracks);
            }
        }
    }

    private void initScheduleNodeSeqList(ProblemContext problemContext) {
        for (Schedule schedule : scheduleList) {
            for (int seq = 0; seq < getNodeList(schedule).size(); ++seq) {
                Node node = getNodeList(schedule).get(seq);
                String name = schedule.getCourseId() + "_" + node.getCode();
                if (!scheduleNodeSeqList.containsKey(name)) {
                    scheduleNodeSeqList.put(name, new ArrayList<>());
                }
                scheduleNodeSeqList.get(name).add(seq);
            }
        }
    }
}
