package eventbased.graph;

import context.ProblemContext;
import solution.Solution;

import java.util.*;

/**
 * @author longfei
 */

public enum EdgeType {
    // Train -> Duty start
    TRAIN_TO_DUTY_START(0, "TRAIN_TO_DUTY_START", new HashSet<VertexType>() {{
        add(VertexType.TRAIN);
    }}, new HashSet<VertexType>() {{
        add(VertexType.DUTY_START);
    }}, new ArrayList<>()),

    // Duty end -> Train
    DUTY_END_TO_TRAIN(1, "DUTY_END_TO_TRAIN", new HashSet<VertexType>() {{
        add(VertexType.DUTY_END);
    }}, new HashSet<VertexType>() {{
        add(VertexType.TRAIN);
    }}, new ArrayList<>()),

    // Duty start -> Course start
    DUTY_START_TO_COURSE_START(2, "DUTY_START_TO_COURSE_START", new HashSet<VertexType>() {{
        add(VertexType.DUTY_START);
    }}, new HashSet<VertexType>() {{
        add(VertexType.COURSE_START);
    }}, new ArrayList<EdgeInfeasible>() {{
        add(EdgeInfeasibleFunctions::isHeadTailTimeDifferent);
    }}),

    // Duty end -> Duty start
    DUTY_END_TO_DUTY_START(3, "DUTY_END_TO_DUTY_START", new HashSet<VertexType>() {{
        add(VertexType.DUTY_END);
    }}, new HashSet<VertexType>() {{
        add(VertexType.DUTY_START);
    }}, new ArrayList<EdgeInfeasible>() {{
        add(EdgeInfeasibleFunctions::isConsecutiveRollingStocksInfeasible);
    }}),

    // Course end -> Duty end
    COURSE_END_TO_DUTY_END(4, "COURSE_END_TO_DUTY_END", new HashSet<VertexType>() {{
        add(VertexType.COURSE_END);
    }}, new HashSet<VertexType>() {{
        add(VertexType.DUTY_END);
    }}, new ArrayList<EdgeInfeasible>() {{
        add(EdgeInfeasibleFunctions::isHeadTailTimeDifferent);
    }}),

    // Course end -> Course start
    COURSE_END_TO_COURSE_START(5, "COURSE_END_TO_COURSE_START", new HashSet<VertexType>() {{
        add(VertexType.COURSE_END);
    }}, new HashSet<VertexType>() {{
        add(VertexType.COURSE_START);
    }}, new ArrayList<EdgeInfeasible>() {{
        add(EdgeInfeasibleFunctions::isConsecutiveCoursesInfeasible);
    }}),

    // Course start -> node
    COURSE_START_TO_NODE(6, "COURSE_START_TO_NODE", new HashSet<VertexType>() {{
        add(VertexType.COURSE_START);
    }}, new HashSet<VertexType>() {{
        add(VertexType.NODE_STOP_LEAVE);
    }}, new ArrayList<EdgeInfeasible>() {{
        add(EdgeInfeasibleFunctions::isHeadTailTimeDifferent);
        add(EdgeInfeasibleFunctions::isLateDepartureTimeInfeasible);
    }}),

    // Node -> Course end
    NODE_TO_COURSE_END(7, "NODE_TO_COURSE_END", new HashSet<VertexType>() {{
        add(VertexType.NODE_STOP_LEAVE);
    }}, new HashSet<VertexType>() {{
        add(VertexType.COURSE_END);
    }}, new ArrayList<EdgeInfeasible>() {{
        add(EdgeInfeasibleFunctions::isHeadTailTimeDifferent);
    }}),

    // Node -> Node (cross station)
    CROSS_STATION_NODE_TO_NODE(8, "CROSS_STATION_NODE_TO_NODE", new HashSet<VertexType>() {{
        add(VertexType.NODE_PASS);
        add(VertexType.NODE_LEAVE);
        add(VertexType.NODE_STOP_LEAVE);
    }}, new HashSet<VertexType>() {{
        add(VertexType.NODE_PASS);
        add(VertexType.NODE_STOP);
        add(VertexType.NODE_STOP_LEAVE);
    }}, new ArrayList<EdgeInfeasible>() {{
        add(EdgeInfeasibleFunctions::isMinimumRunTimeNotSatisfied);
    }}),

    // Node -> Node (same station)
    SAME_STATION_NODE_TO_NODE(9, "SAME_STATION_NODE_TO_NODE", new HashSet<VertexType>() {{
        add(VertexType.NODE_STOP);
    }}, new HashSet<VertexType>() {{
        add(VertexType.NODE_LEAVE);
    }}, new ArrayList<EdgeInfeasible>() {{
        add(EdgeInfeasibleFunctions::isDwellTimeNotSatisfied);
    }});

    // Train -> Course start
    // TODO: ferry
//    TRAIN_TO_COURSE_START(10, "TRAIN_TO_COURSE_START", new HashSet<VertexType>() {{
//        add(VertexType.TRAIN);
//    }}, new HashSet<VertexType>() {{
//        add(VertexType.COURSE_START);
//    }}, new ArrayList<>()),


    // Course start -> Course end
    // TODO: Full cancellation
//    COURSE_START_TO_COURSE_END(11, "COURSE_START_TO_COURSE_END", new HashSet<VertexType>() {{
//        add(VertexType.COURSE_START);
//    }}, new HashSet<VertexType>() {{
//        add(VertexType.COURSE_END);
//    }}, new ArrayList<>()),

    private final int index;
    private final String value;
    private final Set<VertexType> headVertexTypeSet;
    private final Set<VertexType> tailVertexTypeSet;
    private final List<EdgeInfeasible> edgeInfeasibleList;

    EdgeType(int index, String value, Set<VertexType> headVertexTypeSet, Set<VertexType> tailVertexTypeSet, List<EdgeInfeasible> edgeInfeasibleList) {
        this.index = index;
        this.value = value;
        this.headVertexTypeSet = headVertexTypeSet;
        this.tailVertexTypeSet = tailVertexTypeSet;
        this.edgeInfeasibleList = edgeInfeasibleList;
    }

    public int getIndex() {
        return index;
    }

    public String getValue() {
        return value;
    }

    public Set<VertexType> getHeadVertexTypeSet() {
        return headVertexTypeSet;
    }

    public Set<VertexType> getTailVertexTypeSet() {
        return tailVertexTypeSet;
    }

    public List<EdgeInfeasible> getEdgeInfeasibleList() {
        return edgeInfeasibleList;
    }

    public boolean isInfeasible(Vertex headVertex, Vertex tailVertex, ProblemContext problemContext, Solution solution) {
        for (EdgeInfeasible edgeInfeasible : edgeInfeasibleList) {
            if (edgeInfeasible.isInfeasible(headVertex, tailVertex, problemContext, solution)) {
                return true;
            }
        }

        return false;
    }
}
