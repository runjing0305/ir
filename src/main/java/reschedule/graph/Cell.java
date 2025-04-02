package reschedule.graph;

import context.Link;
import context.Node;
import context.Track;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Setter
@Getter
public class Cell {
    public enum Type {
        LINK,
        NODE
    }

    private Type type;
    private String name;
    private List<TimeItem> fixOccTimeItems = new ArrayList<>();
    private List<TimeItem> proOccTimeItems = new ArrayList<>();

    public Cell(Link link) {
        this.name = link.getName();
        this.type = Type.LINK;
    }

    public Cell(Node node) {
        this.name = node.getCode();
        this.type = Type.NODE;
    }

    public List<TimeItem> getProOccTimeItems(Track.Direction direction) {
        return proOccTimeItems.stream().filter(timeItem ->
                timeItem.getSchedule().getDirection().equals(direction)).collect(Collectors.toList());
    }

    public int getMinOccInterval() {
        int minInterval = Integer.MAX_VALUE;
        for (int i = 1; i < proOccTimeItems.size(); ++i) {
            int headArrival = proOccTimeItems.get(i - 1).getStartTime();
            int tailArrival = proOccTimeItems.get(i).getStartTime();
            if (tailArrival - headArrival <= 0) {
                continue;
            }
            minInterval = Math.min(minInterval, tailArrival - headArrival);
        }
        return minInterval;
    }
}
