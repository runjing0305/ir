package eventbased.graph;

import context.Track;
import lombok.Data;

import java.util.Objects;

/**
 * @author longfei
 */
@Data
public class Vertex {
    private final int index;
    /**
     * For train, train index
     * For rolling stock, rolling stock (duty) id
     * For course, course id
     * For node, node id (code)
     */
    private final String id;
    private final String trackName;
    private final VertexType vertexType;
    private final int time;
    private final Track.Direction direction;

    private final String courseId;

    /**
     * The sequence of node in the course
     */
    private final int nodeSeq;

    private final boolean realized;

    private int bsv = 0;

    private double destinationDelayPenalty = 0.0;

    private String uniqueKey;
    private int stopLeaveArrivalTime = -1;
    private int stopLeaveDepartureTime = -1;
    private boolean stopLeaveArrivalRealized = false;
    private boolean stopLeaveDepartureRealized = false;
    private boolean partialCancellationCourseStartEnd = false;
    private double partialCancellationSkippedStopsPenalty = 0.0;
    private String partialCancellationTrack;

    public Vertex(int index, String id, String trackName, VertexType vertexType, int time, Track.Direction direction, String courseId, int nodeSeq, boolean realized) {
        this.index = index;
        this.id = id;
        this.trackName = trackName;
        this.vertexType = vertexType;
        this.time = time;
        this.direction = direction;
        this.courseId = courseId;
        this.nodeSeq = nodeSeq;
        this.realized = realized;
        this.uniqueKey = generateUniqueKey();
    }

    private String generateUniqueKey() {
        return String.join("_", id, trackName, vertexType.name(), String.valueOf(time), direction == null ? null : direction.name(), courseId, String.valueOf(nodeSeq), String.valueOf(realized));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Vertex vertex = (Vertex) o;
        return time == vertex.time && Objects.equals(id, vertex.id) && Objects.equals(trackName, vertex.trackName) && vertexType == vertex.vertexType && direction == vertex.direction && Objects.equals(courseId, vertex.getCourseId()) && nodeSeq == vertex.nodeSeq && realized == vertex.realized;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, trackName, vertexType, time, direction, courseId, nodeSeq, realized);
    }
}
