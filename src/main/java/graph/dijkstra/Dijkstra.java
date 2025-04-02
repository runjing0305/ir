package graph.dijkstra;

import context.Link;
import context.Node;
import context.ProblemContext;
import context.Track;
import graph.Edge;
import graph.Vertex;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Dijkstra （功能简述）
 * 功能详细描述
 *
 * @author s00536729
 * @since 2022-07-22
 */
@Getter
public class Dijkstra {
    protected ProblemContext problemContext;
    protected List<Node> nodeList;
    protected List<Link> linkList;
    protected List<Vertex> vertexList;
    protected Map<String, Vertex> vertexMap;
    protected List<Edge> edgeList;
    protected Map<Integer, Map<Integer, List<Vertex>>> pathMap;

    public Dijkstra(ProblemContext problemContext) {
        this.problemContext = problemContext;
        this.nodeList = problemContext.getNodes();
        this.linkList = problemContext.getLinks();
        this.edgeList = new ArrayList<>();
        this.vertexList = new ArrayList<>();
        this.vertexMap = new HashMap<>();
        this.pathMap = new HashMap<>();
    }

    protected static void computePaths(Vertex source) {
        source.setMinDistance(0);
        PriorityQueue<Vertex> vertexQueue = new PriorityQueue<>();
        vertexQueue.add(source);

        while (!vertexQueue.isEmpty()) {
            Vertex u = vertexQueue.poll();

            // Visit each edge exiting from u
            for (Edge e : u.getOutEdges()) {
                Vertex v = e.getTail();
                double weight = e.getWeight();
                double distanceThroughU = u.getMinDistance() + weight;
                if (distanceThroughU < v.getMinDistance()) {
                    vertexQueue.remove(v);
                    v.setMinDistance(distanceThroughU);
                    v.setPrevious(u);
                    vertexQueue.add(v);
                }
            }
        }
    }

    protected static List<Vertex> getShortestPathTo(Vertex target) {
        List<Vertex> path = new ArrayList<>();
        for (Vertex vertex = target; vertex != null; vertex = vertex.getPrevious()) {
            path.add(vertex);
        }
        Collections.reverse(path);
        return path;
    }

    public double[][] calcShortestDistanceMatrix() {
        double[][] shortestDistanceMatrix = new double[nodeList.size()][nodeList.size()];

        // for each given head, generate the shortest time distance to any tail in the graph
        for (int headNodeID = 0; headNodeID < nodeList.size(); headNodeID++) {
            Node head = nodeList.get(headNodeID);
            vertexList = new ArrayList<>();
            vertexMap = new HashMap<>();
            for (Node node : nodeList) {
                Vertex vertex = new Vertex(node.getCode());
                vertexList.add(vertex);
                vertexMap.put(node.getCode(), vertex);
            }

            // mark all adjacency, i.e., all the edges exiting from each node
            for (Link link : linkList) {
                if (link.getStartNode()
                    .getTracks()
                    .stream()
                    .noneMatch(track -> track.getDirection().equals(Track.Direction.BOTH)
                            || track.getDirection().equals(link.getDirection()))
                    || link.getEndNode()
                    .getTracks()
                    .stream()
                    .noneMatch(track -> track.getDirection().equals(Track.Direction.BOTH)
                            || track.getDirection().equals(link.getDirection()))) {
                    // if no compatible track exists, then skip the edge
                    continue;
                }
                int minimumRunTime = link.getMinimumRunTime()[0][0];
                Edge edge = new Edge(vertexMap.get(link.getStartNode().getCode()), vertexMap.get(link.getEndNode().
                    getCode()), minimumRunTime, minimumRunTime);
                edgeList.add(edge);
            }

            Vertex source = vertexMap.get(head.getCode());
            computePaths(source); // run Dijkstra
            HashMap<Integer, List<Vertex>> tailMap = new HashMap<>();
            for (int tailNodeID = 0; tailNodeID < nodeList.size(); tailNodeID++) {
                if (headNodeID == tailNodeID) {
                    continue;
                }
                Node tail = nodeList.get(tailNodeID);
                Vertex sink = vertexMap.get(tail.getCode());
                shortestDistanceMatrix[head.getIndex()][tail.getIndex()] = sink.getMinDistance();
                List<Vertex> path = getShortestPathTo(sink);
                int pathLength = path.size();
                if (pathLength <= 1/*||pathLength>7*/) {
                    shortestDistanceMatrix[head.getIndex()][tail.getIndex()] = Double.MAX_VALUE;
                } else {
                    tailMap.put(tailNodeID, path);
                }
            }
            pathMap.put(headNodeID, tailMap);
        }
        return shortestDistanceMatrix;
    }
}
