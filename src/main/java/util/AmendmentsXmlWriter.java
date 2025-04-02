package util;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import context.*;
import entity.BaseStationValue;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import solution.Solution;

/**
 * @author longfei
 */
public class AmendmentsXmlWriter {

    public List<Pair<String, Integer>> getStoppedStopList(ProblemContext problemContext, Solution solution) {
        // Skipped Stops in EE should be also processed
        List<Pair<String, Integer>> stoppedStopList = new ArrayList<>();
        for (Schedule schedule : problemContext.getSchedules()) {
            if (!solution.getScheduleStationArrivalTimeMap().containsKey(schedule)) {
                continue;
            }

            if (!schedule.getRealizedNodes().isEmpty()
                    && schedule.getRealizedEnterTimes().get(schedule.getRealizedNodes().size()) != 0) {
                continue;
            }

            String courseId = schedule.getCourseId();
            List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.
                    getRealizedNodes();
            int nodeStartIndex = 1;
            int nodeEndIndex = nodeList.size() - 1;
            int partialCancellationEndIndex = solution.getSchedulePartialCancellationEndIndexMap().getOrDefault(schedule, -1);
            int partialCancellationStartIndex = solution.getSchedulePartialCancellationStartIndexMap().getOrDefault(schedule, -1);

            if (partialCancellationEndIndex >= 0) {
                nodeEndIndex = Math.min(nodeEndIndex, partialCancellationEndIndex);
            }
            if (partialCancellationStartIndex >= 0) {
                nodeStartIndex = Math.max(nodeStartIndex, partialCancellationStartIndex);
            }

            for (int j = nodeStartIndex; j <= nodeEndIndex; j++) {
                boolean arrivalRealized = !schedule.getRealizedEnterTimes().isEmpty() && schedule.getRealizedEnterTimes().get(j + 1) != 0;
                boolean departureRealized = !schedule.getRealizedLeaveTimes().isEmpty() && schedule.getRealizedLeaveTimes().get(j + 1) != 0;
                if (arrivalRealized && departureRealized) {
                    continue;
                }
                Boolean b = solution.getScheduleSkipStationMap().get(schedule).get(j);
                if (Boolean.TRUE.equals(b) && "STOP".
                        equalsIgnoreCase(schedule.getNodeStatus().get(j + 1))) {
                    stoppedStopList.add(Pair.of(courseId, nodeStartIndex > 1 ? j - nodeStartIndex + 1 : j + 1));
                }
            }
        }

        return stoppedStopList;
    }

    public List<Triple<String, Integer, Integer>> getPartialCancellationList(ProblemContext problemContext, Solution solution) {
        List<Triple<String, Integer, Integer>> partialCancellationList = new ArrayList<>();

        for (Schedule schedule : problemContext.getSchedules()) {
            if (!solution.getScheduleStationArrivalTimeMap().containsKey(schedule)) {
                continue;
            }

            if (!schedule.getRealizedNodes().isEmpty()
                    && schedule.getRealizedEnterTimes().get(schedule.getRealizedNodes().size()) != 0) {
                continue;
            }

            String courseId = schedule.getCourseId();
            List<Node> nodeList = schedule.getRealizedNodes().isEmpty() ? schedule.getPlannedNodes() : schedule.
                    getRealizedNodes();
            int nodeStartIndex = 0;
            int nodeEndIndex = nodeList.size() - 1;
            int partialCancellationEndIndex = solution.getSchedulePartialCancellationEndIndexMap().getOrDefault(schedule, -1);
            int partialCancellationStartIndex = solution.getSchedulePartialCancellationStartIndexMap().getOrDefault(schedule, -1);

            if (partialCancellationEndIndex >= 0) {
                nodeEndIndex = Math.min(nodeEndIndex, partialCancellationEndIndex);
            }
            if (partialCancellationStartIndex >= 0) {
                nodeStartIndex = Math.max(nodeStartIndex, partialCancellationStartIndex);
            }

            if (partialCancellationEndIndex >= 0 || partialCancellationStartIndex >= 0) {
                partialCancellationList.add(Triple.of(courseId, nodeStartIndex, nodeEndIndex));
            }
        }

        return partialCancellationList;
    }

    public List<String> getTotalCancellationList(ProblemContext problemContext, Solution solution) {
        List<String> totalCancellationList = new ArrayList<>();

        for (Schedule schedule : problemContext.getSchedules()) {
            if (!solution.getSchedule2RollingStockMap().containsKey(schedule)) {
                totalCancellationList.add(schedule.getCourseId());
            }
        }

        return totalCancellationList;
    }

    public List<Pair<String, String>> getNewTrainList(ProblemContext problemContext, Solution solution) {
        List<Pair<String, String>> newTrainList = new ArrayList<>();

        List<Schedule> plannedScheduleList = new ArrayList<>();
        for (Duty duty : problemContext.getDuties()) {
            plannedScheduleList.addAll(duty.getPlannedSchedules());
        }

        for (Schedule schedule : solution.getSchedule2RollingStockMap().keySet()) {
            if (!plannedScheduleList.contains(schedule)) {
                newTrainList.add(Pair.of(schedule.getCourseId(), schedule.getCategory().name()));
            }
        }
        return newTrainList;
    }

    public List<Triple<String, String, Integer>> getNewConnectionList(ProblemContext problemContext, Solution solution) {
        List<Triple<String, String, Integer>> newConnectionList = new ArrayList<>();

        List<String> plannedConnectionList = new ArrayList<>();
        for (RollingStock rollingStock : problemContext.getRollingStocks()) {
            for (Duty duty : rollingStock.getPlannedDuties()) {
                String prevSchedule = null;
                for (Schedule schedule : duty.getPlannedSchedules()) {
                    if (prevSchedule != null) {
                        plannedConnectionList.add(String.join("_", prevSchedule, schedule.getCourseId()));
                    }

                    prevSchedule = schedule.getCourseId();
                }
            }
        }
        boolean considerConnectionBetweenDuty = false;
        if (considerConnectionBetweenDuty) {
            for (RollingStock rollingStock : problemContext.getRollingStocks()) {
                String prevSchedule = null;
                for (Duty duty : rollingStock.getPlannedDuties()) {
                    if (prevSchedule != null) {
                        plannedConnectionList.add(String.join("_", prevSchedule, duty.getPlannedSchedules().get(0).getCourseId()));
                    }

                    prevSchedule = duty.getPlannedSchedules().get(duty.getPlannedSchedules().size() - 1).getCourseId();
                }
            }
        }

        for (RollingStock rollingStock : solution.getRollingStock2ScheduleListMap().keySet()) {
            List<Schedule> scheduleList = solution.getRollingStock2ScheduleListMap().get(rollingStock);
            Schedule prevSchedule = null;
            for (Schedule schedule : scheduleList) {
                if (prevSchedule != null) {
                    String connection = String.join("_", prevSchedule.getCourseId(), schedule.getCourseId());
                    if (!plannedConnectionList.contains(connection)) {
                        int minConnectionTime = EvaluationUtils.getChangeEndBetweenConsecutiveCourses(problemContext, prevSchedule, schedule);

                        newConnectionList.add(Triple.of(prevSchedule.getCourseId(), schedule.getCourseId(), minConnectionTime));
                    }
                }
                prevSchedule = schedule;
            }
        }

        return newConnectionList;
    }

    public static void writeXmlFile(Document document, Writer writer) {
        try {
            Source source = new DOMSource(document);
            Result result = new StreamResult(writer);

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(source, result);
        } catch (TransformerException e) {
            e.printStackTrace();
        }
    }

    public void writeAmendments(ProblemContext problemContext, Solution solution, String fileName) {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = documentBuilderFactory.newDocumentBuilder();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        Document doc = builder.newDocument();

        Element root = doc.createElement("Amendments");
        doc.appendChild(root);
        int id = 1;

        Element skippedStops = doc.createElement("SkippedStops");
        root.appendChild(skippedStops);

        List<Pair<String, Integer>> skippedStopList = getStoppedStopList(problemContext, solution);
        for (Pair<String, Integer> stringIntegerPair : skippedStopList) {
            Element skippedStop = doc.createElement("SkippedStop");
            skippedStops.appendChild(skippedStop);

            Element tmpId = doc.createElement("ID");
            Text tmpText = doc.createTextNode(String.valueOf(id));
            tmpId.appendChild(tmpText);

            skippedStop.appendChild(tmpId);

            Element tmpTrainId = doc.createElement("TrainID");
            tmpText = doc.createTextNode(stringIntegerPair.getKey());
            tmpTrainId.appendChild(tmpText);

            skippedStop.appendChild(tmpTrainId);

            Element tmpPassageIndex = doc.createElement("PassageIndex");
            tmpText = doc.createTextNode(String.valueOf(stringIntegerPair.getValue()));
            tmpPassageIndex.appendChild(tmpText);

            skippedStop.appendChild(tmpPassageIndex);

            ++id;
        }

        Element partialCancellations = doc.createElement("PartialCancellations");
        root.appendChild(partialCancellations);

        List<Triple<String, Integer, Integer>> partialCancellationList = getPartialCancellationList(problemContext, solution);
        for (Triple<String, Integer, Integer> partialCancellation : partialCancellationList) {
            Element partialCancellationEle = doc.createElement("PartialCancellation");
            partialCancellations.appendChild(partialCancellationEle);

            Element tmpId = doc.createElement("ID");
            Text tmpText = doc.createTextNode(String.valueOf(id));
            tmpId.appendChild(tmpText);

            partialCancellationEle.appendChild(tmpId);

            Element tmpTrainId = doc.createElement("TrainID");
            tmpText = doc.createTextNode(partialCancellation.getLeft());
            tmpTrainId.appendChild(tmpText);

            partialCancellationEle.appendChild(tmpTrainId);

            Element tmpFromPassageIndex = doc.createElement("FromPassageIdx");
            tmpText = doc.createTextNode(String.valueOf(partialCancellation.getMiddle()));
            tmpFromPassageIndex.appendChild(tmpText);

            partialCancellationEle.appendChild(tmpFromPassageIndex);

            Element tmpToPassageIndex = doc.createElement("ToPassageIdx");
            tmpText = doc.createTextNode(String.valueOf(partialCancellation.getRight()));
            tmpToPassageIndex.appendChild(tmpText);

            partialCancellationEle.appendChild(tmpToPassageIndex);

            ++id;
        }

        Element totalCancellations = doc.createElement("TotalCancellations");
        root.appendChild(totalCancellations);

        List<String> totalCancellationList = getTotalCancellationList(problemContext, solution);
        for (String totalCancellation : totalCancellationList) {
            Element totalCancellationEle = doc.createElement("TotalCancellation");
            totalCancellations.appendChild(totalCancellationEle);

            Element tmpId = doc.createElement("ID");
            Text tmpText = doc.createTextNode(String.valueOf(id));
            tmpId.appendChild(tmpText);

            totalCancellationEle.appendChild(tmpId);

            Element tmpTrainId = doc.createElement("TrainID");
            tmpText = doc.createTextNode(totalCancellation);
            tmpTrainId.appendChild(tmpText);

            totalCancellationEle.appendChild(tmpTrainId);
            ++id;
        }

        Element newTrains = doc.createElement("NewTrains");
        root.appendChild(newTrains);

        List<Pair<String, String>> newTrainList = getNewTrainList(problemContext, solution);
        for (Pair<String, String> newTrain : newTrainList) {
            Element newTrainEle = doc.createElement("NewTrain");
            newTrains.appendChild(newTrainEle);

            Element tmpId = doc.createElement("ID");
            Text tmpText = doc.createTextNode(String.valueOf(id));
            tmpId.appendChild(tmpText);

            newTrainEle.appendChild(tmpId);

            Element tmpTrainId = doc.createElement("TrainID");
            tmpText = doc.createTextNode(newTrain.getKey());
            tmpTrainId.appendChild(tmpText);

            newTrainEle.appendChild(tmpTrainId);

            Element tmpCategory = doc.createElement("TrainCategory");
            tmpText = doc.createTextNode(newTrain.getValue());
            tmpCategory.appendChild(tmpText);

            newTrainEle.appendChild(tmpCategory);

            ++id;
        }

        Element newConnections = doc.createElement("NewConnections");
        root.appendChild(newConnections);

        List<Triple<String, String, Integer>> newConnectionList = getNewConnectionList(problemContext, solution);
        for (Triple<String, String, Integer> newConnection : newConnectionList) {
            Element newConnectionEle = doc.createElement("NewConnection");
            newConnections.appendChild(newConnectionEle);

            Element tmpId = doc.createElement("ID");
            Text tmpText = doc.createTextNode(String.valueOf(id));
            tmpId.appendChild(tmpText);

            newConnectionEle.appendChild(tmpId);

            Element tmpTrainId = doc.createElement("TrainID1");
            tmpText = doc.createTextNode(newConnection.getLeft());
            tmpTrainId.appendChild(tmpText);

            newConnectionEle.appendChild(tmpTrainId);

            tmpTrainId = doc.createElement("TrainID2");
            tmpText = doc.createTextNode(newConnection.getMiddle());
            tmpTrainId.appendChild(tmpText);

            newConnectionEle.appendChild(tmpTrainId);

            Element tmpMinConnectionTime = doc.createElement("MinConnectionTime");
            tmpText = doc.createTextNode(String.valueOf(newConnection.getRight()));
            tmpMinConnectionTime.appendChild(tmpText);

            newConnectionEle.appendChild(tmpMinConnectionTime);

            ++id;
        }

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(fileName);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream);
            writeXmlFile(doc, outputStreamWriter);
            outputStreamWriter.close();
            fileOutputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
