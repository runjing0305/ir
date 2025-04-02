package context;

import context.scenario.LinkScenario;
import graph.Vertex;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Link （链路）
 * 铁路链路
 *
 * @author s00536729
 * @since 2022-07-01
 */
@Getter
@Setter
public class Link {
    private String name;
    private int[][] minimumRunTime = new int[2][2]; // seconds, 0 stands for pass, 1 stands for stop
    private int[][][][] minimumHeadway = new int[2][2][2][2]; // seconds, 0 stands for pass, 1 stands for stop
    private List<LinkScenario> linkScenarioList = new ArrayList<>();
    private Node startNode = new Node();
    private Node endNode = new Node();
    private Track.Direction direction;
    private int distanceMeter;
    private List<Schedule> schedules = new ArrayList<>();

    public int calcMinimumRunTime(Vertex.Type startStatus, Vertex.Type endStatus) {
        return minimumRunTime[vertexType2Index(startStatus)][vertexType2Index(endStatus)];
    }

    public double calcAverageHeadway() {
        double averageHeadway = 0;
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                for (int c = 0; c < 2; c++) {
                    for (int d = 0; d < 2; d++) {
                        averageHeadway += minimumHeadway[a][b][c][d];
                    }
                }
            }
        }
        averageHeadway  = averageHeadway / 16.0;
        return averageHeadway;
    }

    public int vertexType2Index(Vertex.Type type) {
        if (type.equals(Vertex.Type.PASS)) {
            return 0;
        } else {
            return 1;
        }
    }

    public static String generateLinkName(Node startNode, Node endNode) {
        return generateLinkName(startNode.getCode(), endNode.getCode());
    }

    public static String generateLinkName(String startNodeCode, String endNodeCode) {
        return String.join("_", startNodeCode, endNodeCode);
    }
}
