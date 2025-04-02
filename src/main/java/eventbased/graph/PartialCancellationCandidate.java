package eventbased.graph;

import context.Node;
import context.Schedule;
import lombok.Data;

@Data
public class PartialCancellationCandidate {

    private String dutyId;
    private Schedule schedule;
    private Schedule nextSchedule;
    private Node node;
    private int nodeIndex;
    private int indexInNextSchedule;

    private int shiftTime;
    private int dutyIndex;
    private int scheduleIndex;
}
