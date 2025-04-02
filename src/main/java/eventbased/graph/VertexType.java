package eventbased.graph;

/**
 * @author longfei
 */

public enum VertexType {
    // Train
    TRAIN,
    // Rolling stock (Duty) start
    DUTY_START,
    // Rolling stock (Duty) end
    DUTY_END,
    // Course start
    COURSE_START,
    // Course end
    COURSE_END,
    // Stop at the node
    NODE_STOP,
    // Leave the node
    NODE_LEAVE,
    // Pass the node
    NODE_PASS,
    // Stop and leave at the first/last node of course
    NODE_STOP_LEAVE,

    // Vertex that is generated in Node Graph
    NODE_GRAPH_VERTEX,
}
