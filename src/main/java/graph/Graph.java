package graph;

import constant.Constants;
import context.*;
import context.scenario.RealizedScheduleScenario;
import lombok.Getter;
import lombok.Setter;
import rollinghorizon.TimeHorizonRoller;

import java.util.*;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Graph （图）
 * 图包含点、边和多个商品
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class Graph {
    protected static final Logger LOGGER = LogManager.getLogManager().getLogger(Graph.class.getName());
    private List<Vertex> vertexList = new ArrayList<>();
    private Map<String, Vertex> name2Vertex = new HashMap<>();
    private Map<String, Set<Vertex>> scheduleAndNode2Vertices = new HashMap<>();
    private List<Edge> edgeList = new ArrayList<>();
    private Map<String, Edge> name2Edge = new HashMap<>();
    private List<Commodity> commodityList = new ArrayList<>();
    private Map<String, Commodity> name2Commodity = new HashMap<>();

    /**
     *
     * Build graph based on problem context
     *
     * @param problemContext Problem data
     */
    public Graph(ProblemContext problemContext) {
        // generate vertices
        genScheduleNodeVertices(problemContext);
        genVirtualStartAndEndVertices();

        // generate edges
        genInnerScheduleEdges(problemContext);
        genInterScheduleEdges(problemContext);
        genVirtualStartAndEndWithScheduleEdges(problemContext);


        // generate commodities
        genCommodities(problemContext);

        // generate indexes
        genIndexes();
    }

    public Graph(ProblemContext problemContext, int index) {
        TimeHorizonRoller timeHorizonRoller = new TimeHorizonRoller();
        int maxTime = timeHorizonRoller.getMaxTime();
        int minTime = timeHorizonRoller.getMinTime();
        double timeStepSize = timeHorizonRoller.getTimeStepSize();
        int startTime = (int) Math.round(timeHorizonRoller.getMinTime() + index * timeStepSize);
        int endTime = (int) Math.round(timeHorizonRoller.getMinTime() + (index + 1) * timeStepSize);
        startTime = Math.max(minTime, Math.min(maxTime, startTime));
        endTime = Math.max(minTime, Math.min(maxTime, endTime));

        // generate vertices
        genTimeNodeVertices(problemContext, startTime, endTime, minTime, index);
        genVirtualStartAndEndVertices();

        // generate edges
        genInnerTimeEdges(problemContext);
        genInterTimeEdges(problemContext);
        genVirtualStartAndEndWithTimeEdges(problemContext);


        // generate commodities
        genCommodities(problemContext);

        // generate indexes
        genIndexes();
    }


    private void genCommodities(ProblemContext problemContext) {
        for (RollingStock rs : problemContext.getRollingStocks()) {
            Commodity commodity = new Commodity(rs);
            commodityList.add(commodity);
            name2Commodity.put(commodity.getName(), commodity);
        }
    }

    private void genIndexes() {
        for (int i = 0; i < vertexList.size(); i++) {
            vertexList.get(i).setIndex(i);
        }
        for (int i = 0; i < edgeList.size(); i++) {
            edgeList.get(i).setIndex(i);
        }
        for (int i = 0; i < commodityList.size(); i++) {
            commodityList.get(i).setIndex(i);
        }
    }

    private void genVirtualStartAndEndWithScheduleEdges(ProblemContext problemContext) {
        // generate one edge between virtual start and schedule head, and another edge between schedule
        // tail and virtual end
        for (Schedule schedule : problemContext.getSchedules()) {
            Vertex scheduleHeadVertex = name2Vertex.get(genVertexName(schedule, schedule.getStartNode(),
                    Vertex.Type.STOP, 0));
            Vertex scheduleTailVertex = name2Vertex.get(genVertexName(schedule, schedule.getEndNode(),
                    Vertex.Type.STOP, schedule.getPlannedNodes().size() - 1));
            genStartAndEndEdge(name2Vertex.get(Constants.VIRTUAL_START_VERTEX_NAME), scheduleHeadVertex);
            genStartAndEndEdge(scheduleTailVertex, name2Vertex.get(Constants.VIRTUAL_END_VERTEX_NAME));
        }
        genStartAndEndEdge(name2Vertex.get(Constants.VIRTUAL_START_VERTEX_NAME),
                name2Vertex.get(Constants.VIRTUAL_END_VERTEX_NAME));
    }

    private void genVirtualStartAndEndWithTimeEdges(ProblemContext problemContext) {
        // generate one edge between virtual start and schedule head, and another edge between schedule
        // tail and virtual end
        for (Schedule schedule : problemContext.getSchedules()) {
            Vertex scheduleHeadVertex = name2Vertex.get(genVertexName(schedule, schedule.getStartNode(),
                    Vertex.Type.STOP, 0));
            if (scheduleHeadVertex == null) {
                continue;
            }
            Vertex scheduleTailVertex = name2Vertex.get(genVertexName(schedule, schedule.getEndNode(),
                    Vertex.Type.STOP, schedule.getPlannedNodes().size() - 1));
            if (scheduleTailVertex == null) {
                continue;
            }
            genStartAndEndEdge(name2Vertex.get(Constants.VIRTUAL_START_VERTEX_NAME), scheduleHeadVertex);
            genStartAndEndEdge(scheduleTailVertex, name2Vertex.get(Constants.VIRTUAL_END_VERTEX_NAME));
        }
        for (RollingStock rollingStock : problemContext.getRollingStocks()) {
            Schedule schedule = rollingStock.getNextSchedule();
            if (schedule == null) {
                continue;
            }
            Node node = rollingStock.getNextNode();
            int i = 0;
            for (; i < schedule.getPlannedNodes().size(); i++) {
                if (node == schedule.getPlannedNodes().get(i)) {
                    break;
                }
            }
            Iterator<Vertex> it = scheduleAndNode2Vertices.get(getScheduleNodeStr(schedule, rollingStock.getNextNode(), i)).iterator();
            Vertex scheduleHeadVertex = null;
            if (it.hasNext()) {
                scheduleHeadVertex = it.next();
            }
//            Vertex scheduleHeadVertex = name2Vertex.get(genVertexName(schedule, rollingStock.getNextNode(),
//                    Vertex.Type.STOP, 0));
            genStartAndEndEdge(name2Vertex.get(Constants.VIRTUAL_START_VERTEX_NAME), scheduleHeadVertex);
        }
        genStartAndEndEdge(name2Vertex.get(Constants.VIRTUAL_START_VERTEX_NAME),
                name2Vertex.get(Constants.VIRTUAL_END_VERTEX_NAME));
    }

    private void genInterScheduleEdges(ProblemContext problemContext) {
        // generate inter-schedule edge
        for (int i = 0; i < problemContext.getSchedules().size() - 2; i++) {
            for (int j = i + 1; j < problemContext.getSchedules().size() - 1; j++) {
                Schedule schedule1 = problemContext.getSchedules().get(i);
                Schedule schedule2 = problemContext.getSchedules().get(j);
                if (schedule1.getEndNode() == schedule2.getStartNode()) {
                    genInterScheduleEdge(schedule1, schedule2);
                }
                if (schedule2.getEndNode() == schedule1.getStartNode()) {
                    genInterScheduleEdge(schedule2, schedule1);
                }
            }
        }
    }

    private void genInterTimeEdges(ProblemContext problemContext) {
        // generate inter-time edge
        for (int i = 0; i < problemContext.getSchedules().size() - 2; i++) {
            Schedule schedule1 = problemContext.getSchedules().get(i);
            if (!scheduleAndNode2Vertices.containsKey(getScheduleNodeStr(schedule1, schedule1.getEndNode(), schedule1.getPlannedNodes().size() - 1))) {
                continue;
            }
            for (int j = i + 1; j < problemContext.getSchedules().size() - 1; j++) {
                Schedule schedule2 = problemContext.getSchedules().get(j);
                if (schedule1.getEndNode() == schedule2.getStartNode()) {
                    if (!scheduleAndNode2Vertices.containsKey(getScheduleNodeStr(schedule2, schedule2.getStartNode(), 0))) {
                        continue;
                    }
                    genInterScheduleEdge(schedule1, schedule2);
                }
            }
        }
        for (int i = 0; i < problemContext.getSchedules().size() - 2; i++) {
            Schedule schedule1 = problemContext.getSchedules().get(i);
            if (!scheduleAndNode2Vertices.containsKey(getScheduleNodeStr(schedule1, schedule1.getStartNode(), 0))) {
                continue;
            }
            for (int j = i + 1; j < problemContext.getSchedules().size() - 1; j++) {
                Schedule schedule2 = problemContext.getSchedules().get(j);
                if (schedule2.getEndNode() == schedule1.getStartNode()) {
                    if (!scheduleAndNode2Vertices.containsKey(getScheduleNodeStr(schedule2, schedule2.getEndNode(), schedule2.getPlannedNodes().size() - 1))) {
                        continue;
                    }
                    genInterScheduleEdge(schedule2, schedule1);
                }
            }
        }
    }


    private void genInnerScheduleEdges(ProblemContext problemContext) {
        // generate inner-schedule edge
        for (Schedule schedule : problemContext.getSchedules()) {
            for (int i = 0; i < schedule.getPlannedNodes().size() - 1; i++) {
                Node headNode = schedule.getPlannedNodes().get(i);
                Node tailNode = schedule.getPlannedNodes().get(i + 1);
                Link link = problemContext.getName2Link().get(headNode.getCode() + "_" + tailNode.getCode());
                List<Vertex> headVertices = new ArrayList<>(scheduleAndNode2Vertices.get(getScheduleNodeStr(schedule,
                        headNode, i)));
                List<Vertex> tailVertices = new ArrayList<>(scheduleAndNode2Vertices.get(getScheduleNodeStr(schedule,
                        tailNode, i + 1)));
                genInnerScheduleEdge(headVertices, tailVertices, link);
            }
        }
    }

    private void genInnerTimeEdges(ProblemContext problemContext) {
        // generate inner-schedule edge
        for (Schedule schedule : problemContext.getSchedules()) {
            for (int i = 0; i < schedule.getPlannedNodes().size() - 1; i++) {
                Node headNode = schedule.getPlannedNodes().get(i);
                if (!scheduleAndNode2Vertices.containsKey(getScheduleNodeStr(schedule, headNode, i))) {
                    continue;
                }
                Node tailNode = schedule.getPlannedNodes().get(i + 1);
                if (!scheduleAndNode2Vertices.containsKey(getScheduleNodeStr(schedule, tailNode, i + 1))) {
                    continue;
                }
                Link link = problemContext.getName2Link().get(headNode.getCode() + "_" + tailNode.getCode());
                List<Vertex> headVertices = new ArrayList<>(scheduleAndNode2Vertices.get(getScheduleNodeStr(schedule,
                        headNode, i)));
                List<Vertex> tailVertices = new ArrayList<>(scheduleAndNode2Vertices.get(getScheduleNodeStr(schedule,
                        tailNode, i + 1)));
                genInnerScheduleEdge(headVertices, tailVertices, link);
            }
        }
    }

    private void genVirtualStartAndEndVertices() {
        // generate virtual start & end node
        Vertex virtualStartVertex = new Vertex(Constants.VIRTUAL_START_VERTEX_NAME);
        virtualStartVertex.setType(Vertex.Type.STOP);
        virtualStartVertex.setVirtual(true);
        vertexList.add(virtualStartVertex);
        name2Vertex.put(virtualStartVertex.getName(), virtualStartVertex);
        Vertex virtualEndVertex = new Vertex(Constants.VIRTUAL_END_VERTEX_NAME);
        virtualEndVertex.setType(Vertex.Type.STOP);
        virtualEndVertex.setVirtual(true);
        vertexList.add(virtualEndVertex);
        name2Vertex.put(virtualEndVertex.getName(), virtualEndVertex);
    }

    private void genScheduleNodeVertices(ProblemContext problemContext) {
        // generate schedule-node vertices
        for (Schedule schedule : problemContext.getSchedules()) {
            for (int i = 0; i < schedule.getPlannedNodes().size(); i++) {
                Node node = schedule.getPlannedNodes().get(i);
                String scheduleNodeStr = getScheduleNodeStr(schedule, node, i);
                Set<Vertex> vertexSet;
                if (scheduleAndNode2Vertices.containsKey(scheduleNodeStr)) {
                    vertexSet = scheduleAndNode2Vertices.get(scheduleNodeStr);
                } else {
                    vertexSet = new HashSet<>();
                }
                if (i == 0 || i == schedule.getPlannedNodes().size() - 1) {
                    // schedule head or tail vertex
                    Vertex stopVertex = genVertex(schedule, node, Vertex.Type.STOP, i);
                    vertexSet.add(stopVertex);
                } else {
                    if (!schedule.getCategory().equals(Schedule.Category.EE)) {
                        Vertex stopVertex = genVertex(schedule, node, Vertex.Type.STOP, i);
                        vertexSet.add(stopVertex);
                    }
                    Vertex passVertex = genVertex(schedule, node, Vertex.Type.PASS, i);
                    vertexSet.add(passVertex);
                }
                scheduleAndNode2Vertices.put(scheduleNodeStr, vertexSet);
            }
        }
    }

    private void genTimeNodeVertices(ProblemContext problemContext, int startTime, int endTime, int minTime, int index) {
        if (index == 0) {
            for (RealizedScheduleScenario realizedScheduleScenario : problemContext.getScenario().getRealizedScheduleScenarios()) {
                Course course1 = new Course();
                //todo 临时规避
                if (realizedScheduleScenario.getSchedule() == null) {
                    continue;
                }
                course1.setId(realizedScheduleScenario.getSchedule().getCourseId());
                course1.setNode(realizedScheduleScenario.getNode());
                course1.setSeq(realizedScheduleScenario.getSeq());
                problemContext.getFixedCourseSet().add(ProblemContext.getCourseName(course1));
                RollingStock rollingStock = problemContext.getSchedule2RollingStock().get(realizedScheduleScenario.getSchedule());
                //todo 5U77JS#1在realized里有但是plan没有，目前先规避
                if (rollingStock == null) {
                    continue;
                }
                int arrivalSeconds = realizedScheduleScenario.getArrivalSeconds();
                int departureSeconds = realizedScheduleScenario.getDepartureSeconds();
                if (arrivalSeconds > 0 && rollingStock.getNextNodeTime() < arrivalSeconds) {
                    rollingStock.setNextNodeTime(arrivalSeconds);
                    rollingStock.setNextNode(realizedScheduleScenario.getNode());
                    rollingStock.setNextSchedule(realizedScheduleScenario.getSchedule());
                    rollingStock.setNextNodeArrival(true);
                    Course course = new Course();
                    course.setId(realizedScheduleScenario.getSchedule().getCourseId());
                    course.setNode(realizedScheduleScenario.getNode());
                    course.setSeq(realizedScheduleScenario.getSeq());
                    rollingStock.setNextCourse(course);
                }
                if (departureSeconds > 0 && rollingStock.getNextNodeTime() < departureSeconds) {
                    rollingStock.setNextNodeTime(departureSeconds);
                    rollingStock.setNextNode(realizedScheduleScenario.getNode());
                    rollingStock.setNextSchedule(realizedScheduleScenario.getSchedule());
                    rollingStock.setNextNodeArrival(false);
                    Course course = new Course();
                    course.setId(realizedScheduleScenario.getSchedule().getCourseId());
                    course.setNode(realizedScheduleScenario.getNode());
                    course.setSeq(realizedScheduleScenario.getSeq());
                    rollingStock.setNextCourse(course);
                }
            }
        }

        for (RollingStock rollingStock : problemContext.getRollingStocks()) {
            Schedule schedule = rollingStock.getNextSchedule();
            if (schedule == null) {
                continue;
            }
            Node node = rollingStock.getNextNode();
            int i = 0;
            for (; i < schedule.getPlannedNodes().size(); i++) {
                if (node == schedule.getPlannedNodes().get(i)) {
                    break;
                }
            }
            String scheduleNodeStr = getScheduleNodeStr(schedule, node, i);
            Set<Vertex> vertexSet;
            if (scheduleAndNode2Vertices.containsKey(scheduleNodeStr)) {
                vertexSet = scheduleAndNode2Vertices.get(scheduleNodeStr);
            } else {
                vertexSet = new HashSet<>();
            }
            if (i == 0 || i == schedule.getPlannedNodes().size() - 1) {
                // schedule head or tail vertex
                Vertex stopVertex = genVertex(schedule, node, Vertex.Type.STOP, i);
                vertexSet.add(stopVertex);
            } else {
                if (!schedule.getCategory().equals(Schedule.Category.EE)) {
                    Vertex stopVertex = genVertex(schedule, node, Vertex.Type.STOP, i);
                    vertexSet.add(stopVertex);
                }
                Vertex passVertex = genVertex(schedule, node, Vertex.Type.PASS, i);
                vertexSet.add(passVertex);
            }
            scheduleAndNode2Vertices.put(scheduleNodeStr, vertexSet);
        }

        for (Schedule schedule : problemContext.getSchedules()) {
            for (int i = 0; i < schedule.getPlannedNodes().size(); i++) {
                boolean flag;
//                if(index==0){
                // 主要因为realized可能走完entertime非常晚的的计划。
                flag = schedule.getLeaveTimes().containsKey(i) && schedule.getLeaveTimes().get(i) > startTime;
//                }
//                else{
//                    flag=schedule.getEnterTimes().containsKey(i) && schedule.getEnterTimes().get(i) < endTime && schedule.getLeaveTimes().containsKey(i) && schedule.getLeaveTimes().get(i) > startTime;
//                }
                if (flag) {
                    Node node = schedule.getPlannedNodes().get(i);
                    String scheduleNodeStr = getScheduleNodeStr(schedule, node, i);
                    Course course = new Course();
                    course.setSeq(i);
                    course.setNode(node);
                    course.setId(schedule.getCourseId());
                    if (problemContext.getFixedCourseSet().contains(ProblemContext.getCourseName(course))) {
                        continue;
                    }
                    Set<Vertex> vertexSet;
                    if (scheduleAndNode2Vertices.containsKey(scheduleNodeStr)) {
                        vertexSet = scheduleAndNode2Vertices.get(scheduleNodeStr);
                    } else {
                        vertexSet = new HashSet<>();
                    }
                    if (i == 0 || i == schedule.getPlannedNodes().size() - 1) {
                        // schedule head or tail vertex
                        Vertex stopVertex = genVertex(schedule, node, Vertex.Type.STOP, i);
                        vertexSet.add(stopVertex);
                    } else {
                        if (!schedule.getCategory().equals(Schedule.Category.EE)) {
                            Vertex stopVertex = genVertex(schedule, node, Vertex.Type.STOP, i);
                            vertexSet.add(stopVertex);
                        }
                        Vertex passVertex = genVertex(schedule, node, Vertex.Type.PASS, i);
                        vertexSet.add(passVertex);
                    }
                    scheduleAndNode2Vertices.put(scheduleNodeStr, vertexSet);
                }
            }
        }

    }

    private String getScheduleNodeStr(Schedule schedule, Node node, int i) {
        return schedule.getCourseId() + "_" + node.getCode() + "_" + i;
    }

    public String genVertexName(Schedule schedule, Node node, Vertex.Type type, int i) {
        return schedule.getCourseId() + "_" + node.getCode() + "_" + i + "_" + type.toString();
    }

    private Vertex genVertex(Schedule schedule, Node node, Vertex.Type type, int i) {
        String vertexName = genVertexName(schedule, node, type, i);
        Vertex vertex = new Vertex(vertexName);
        vertex.setType(type);
        vertexList.add(vertex);
        name2Vertex.put(vertex.getName(), vertex);
        return vertex;
    }

    private void genInnerScheduleEdge(List<Vertex> headVertices, List<Vertex> tailVertices, Link link) {
        for (Vertex headVertex : headVertices) {
            for (Vertex tailVertex : tailVertices) {
                Edge edge = new Edge(headVertex, tailVertex, 0, link.
                        calcMinimumRunTime(headVertex.getType(), tailVertex.getType()));
                edgeList.add(edge);
                name2Edge.put(edge.getName(), edge);
            }
        }
    }

    private void genStartAndEndEdge(Vertex headVertex, Vertex tailVertex) {
        Edge edge = new Edge(headVertex, tailVertex, 0, 0);
        edgeList.add(edge);
        name2Edge.put(edge.getName(), edge);
    }

    private void genInterScheduleEdge(Schedule headSchedule, Schedule tailSchedule) {
        Vertex headVertex = name2Vertex.get(genVertexName(headSchedule, headSchedule.getEndNode(),
                Vertex.Type.STOP, headSchedule.getPlannedNodes().size() - 1));
        Vertex tailVertex = name2Vertex.get(genVertexName(tailSchedule, tailSchedule.getStartNode(),
                Vertex.Type.STOP, 0));
        Edge edge = new Edge(headVertex, tailVertex, 0, Constants.INTER_SCHEDULE_REST_TIME);
        edgeList.add(edge);
        name2Edge.put(edge.getName(), edge);
    }
}
