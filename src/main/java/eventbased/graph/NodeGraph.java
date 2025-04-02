package eventbased.graph;

import constant.Constants;
import context.ProblemContext;
import context.Schedule;
import context.Track;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;
import util.EvaluationUtils;

import java.util.*;

@Data
public class NodeGraph {
    private final String nodeCode;
    private final String trackName;
    private final Track.Direction direction;
    private Vertex dummyNodeStartVertex;
    private Vertex dummyNodeEndVertex;

    private List<Vertex> vertexList = new ArrayList<>();
    private Map<Integer, Vertex> vertexMap = new HashMap<>();
    private int vertexIndex = 0;
    private List<Edge> edgeList = new ArrayList<>();
    private Map<Vertex, List<Edge>> headEdgeListMap = new HashMap<>();
    private Map<Vertex, List<Edge>> tailEdgeListMap = new HashMap<>();
    private Map<Vertex, List<Vertex>> vertexVertexMap = new HashMap<>();

    public NodeGraph(String nodeCode, String trackName, Track.Direction direction) {
        this.nodeCode = nodeCode;
        this.trackName = trackName;
        this.direction = direction;
    }

    public void build(ProblemContext problemContext, Graph graph) {
        this.buildVertexList(problemContext, graph);
        this.buildEdgeList(problemContext, graph);
        this.calculatePassageFrequencyPenalty();
    }

    public String generateNodeGraphId() {
        return String.join("_", nodeCode, trackName, direction.name());
    }

    public void buildVertexList(ProblemContext problemContext, Graph graph) {
        this.dummyNodeStartVertex = new Vertex(vertexIndex, nodeCode, trackName, VertexType.NODE_GRAPH_VERTEX, -1, direction, null, -1, false);
        ++vertexIndex;
        this.dummyNodeEndVertex = new Vertex(vertexIndex, nodeCode, trackName, VertexType.NODE_GRAPH_VERTEX, -1, direction, null, -1, false);
        ++vertexIndex;
        Set<Integer> timePointList = new HashSet<>();
        Map<Integer, List<Vertex>> graphVertexList = new HashMap<>();
        for (Vertex vertex : graph.getVertexMap().values()) {
            if (!nodeCode.equals(vertex.getId())) {
                continue;
            }

            if (direction != vertex.getDirection()) {
                continue;
            }

            if (!trackName.equals(vertex.getTrackName())) {
                continue;
            }

            if (VertexType.NODE_LEAVE == vertex.getVertexType()) {
                continue;
            }

            String courseId = vertex.getCourseId();
            if (Schedule.Category.EE == problemContext.getCourseId2Schedule().get(courseId).getCategory()) {
                continue;
            }

            timePointList.add(vertex.getTime());
            graphVertexList.computeIfAbsent(vertex.getTime(), k -> new ArrayList<>()).add(vertex);
        }

        for (Integer timePoint : timePointList) {
            Vertex vertex = new Vertex(vertexIndex, nodeCode, trackName, VertexType.NODE_GRAPH_VERTEX, timePoint, direction, null, -1, false);
            ++vertexIndex;
            vertexList.add(vertex);
            vertexVertexMap.put(vertex, graphVertexList.get(timePoint));
            vertexMap.put(timePoint, vertex);
        }
    }

    public void buildEdgeList(ProblemContext problemContext, Graph graph) {
        vertexList.sort(Comparator.comparingInt(Vertex::getTime));

        List<Pair<Integer, Integer>> timeBandList = Track.Direction.EB == direction ? graph.getEbReferenceStationArrivalTimeBandList() : graph.getWbReferenceStationArrivalTimeBandList();
        int dummyStartConnectTimeLimit = timeBandList.get(0).getRight();
        int dummyEndConnectTimeLimit = timeBandList.get(timeBandList.size() - 1).getLeft();

        Edge edge;
        for (int i = 0; i < vertexList.size(); ++i) {
            Vertex vertex1 = vertexList.get(i);

            if (vertex1.getTime() <= dummyStartConnectTimeLimit) {
                edge = new Edge();
                edge.setHead(this.dummyNodeStartVertex);
                edge.setTail(vertex1);
                edgeList.add(edge);
                headEdgeListMap.computeIfAbsent(this.dummyNodeStartVertex, k -> new ArrayList<>()).add(edge);
                tailEdgeListMap.computeIfAbsent(vertex1, k -> new ArrayList<>()).add(edge);
            }

            if (vertex1.getTime() >= dummyEndConnectTimeLimit) {
                edge = new Edge();
                edge.setHead(vertex1);
                edge.setTail(this.dummyNodeEndVertex);
                edgeList.add(edge);
                headEdgeListMap.computeIfAbsent(vertex1, k -> new ArrayList<>()).add(edge);
                tailEdgeListMap.computeIfAbsent(this.dummyNodeEndVertex, k -> new ArrayList<>()).add(edge);
            }

            int time1 = vertex1.getTime();
            int firstVertexConnectTimeLimit = Integer.MAX_VALUE;
            for (int j = 0; j < timeBandList.size(); ++j) {
                if (timeBandList.get(j).getLeft() > time1) {
                    firstVertexConnectTimeLimit = timeBandList.get(j).getRight();
                    break;
                }
            }

            for (int j = i + 1; j < vertexList.size(); ++j) {
                Vertex vertex2 = vertexList.get(j);
                int time2 = vertex2.getTime();

                if (time2 > firstVertexConnectTimeLimit) {
                    continue;
                }

                edge = new Edge();
                edge.setHead(vertex1);
                edge.setTail(vertex2);
                edgeList.add(edge);

                headEdgeListMap.computeIfAbsent(vertex1, k -> new ArrayList<>()).add(edge);
                tailEdgeListMap.computeIfAbsent(vertex2, k -> new ArrayList<>()).add(edge);
            }
        }
    }

    public void calculatePassageFrequencyPenalty() {
        for (Edge edge : edgeList) {
            Vertex headVertex = edge.getHead();
            Vertex tailVertex = edge.getTail();

            if (headVertex == dummyNodeStartVertex || tailVertex == dummyNodeEndVertex) {
                continue;
            }

            int firstTime = headVertex.getTime();
            int secondTime = tailVertex.getTime();

            int firstThreshold = EvaluationUtils.getPassageFrequencyThreshold(firstTime);
            int secondThreshold = EvaluationUtils.getPassageFrequencyThreshold(secondTime);
            int threshold = Math.max(firstThreshold, secondThreshold);
            int timeDiff = tailVertex.getTime() - headVertex.getTime();
            if (timeDiff > threshold) {
                double penalty = (timeDiff - threshold) / Constants.SECONDS_IN_MINUTE * Constants.FREQUENCY_PENALTY;
                edge.setPassageFrequencyPenalty(penalty);
            }
        }
    }
}
