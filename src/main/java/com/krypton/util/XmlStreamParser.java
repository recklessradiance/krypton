package com.krypton.util;

import com.krypton.model.DailyMetrics;
import com.krypton.model.Exercise;
import com.krypton.model.Health;
import com.krypton.model.WorkoutLog;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

@Component
public class XmlStreamParser {

    private static final DateTimeFormatter FORMATTER_WITH_OFFSET = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");
    private static final DateTimeFormatter FORMATTER_WITH_ZONE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public List<DailyMetrics> parseXml(Path filePath, Consumer<Integer> progressCallback) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        // Secure XML parser configuration but allow DOCTYPE parsing
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, true);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setXMLResolver((publicID, systemID, baseURI, namespace) -> 
            new java.io.ByteArrayInputStream(new byte[0])
        );

        Map<String, DailyAggregator> dailyMap = new HashMap<>();

        try (InputStream fileStream = Files.newInputStream(filePath);
             ProgressTrackingInputStream trackingStream = new ProgressTrackingInputStream(
                     fileStream, Files.size(filePath), progressCallback)) {

            XMLStreamReader reader = factory.createXMLStreamReader(trackingStream);

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String elementName = reader.getLocalName();
                    if ("Record".equals(elementName)) {
                        String type = reader.getAttributeValue(null, "type");
                        if (type != null) {
                            processRecord(type, reader, dailyMap);
                        }
                    } else if ("Workout".equals(elementName)) {
                        processWorkout(reader, dailyMap);
                    }
                }
            }
        }

        return convertToDailyMetrics(dailyMap);
    }

    private void processRecord(String type, XMLStreamReader reader, Map<String, DailyAggregator> dailyMap) {
        if ("HKCategoryTypeIdentifierSleepAnalysis".equals(type)) {
            String value = reader.getAttributeValue(null, "value");
            if (value != null && value.toLowerCase().contains("asleep")) {
                String startDateStr = reader.getAttributeValue(null, "startDate");
                String endDateStr = reader.getAttributeValue(null, "endDate");
                if (startDateStr != null && endDateStr != null) {
                    try {
                        ZonedDateTime start = parseDateTime(startDateStr);
                        ZonedDateTime end = parseDateTime(endDateStr);
                        if (start != null && end != null) {
                            double durationHours = Duration.between(start, end).toMillis() / (1000.0 * 60.0 * 60.0);
                            if (durationHours > 0) {
                                // Sleep duration is attributed to the waking day (endDate)
                                String dateKey = end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                                DailyAggregator agg = dailyMap.computeIfAbsent(dateKey, k -> new DailyAggregator());
                                agg.sleepHoursSum += durationHours;
                            }
                        }
                    } catch (Exception e) {
                        // Ignore individual record failures to keep parsing resilient
                    }
                }
            }
        } else if ("HKQuantityTypeIdentifierBodyMass".equals(type)) {
            String valueStr = reader.getAttributeValue(null, "value");
            String startDateStr = reader.getAttributeValue(null, "startDate");
            if (valueStr != null && startDateStr != null) {
                try {
                    double val = Double.parseDouble(valueStr);
                    ZonedDateTime start = parseDateTime(startDateStr);
                    if (start != null) {
                        String dateKey = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        DailyAggregator agg = dailyMap.computeIfAbsent(dateKey, k -> new DailyAggregator());
                        Instant recordTime = start.toInstant();
                        if (agg.latestWeightTime == null || recordTime.isAfter(agg.latestWeightTime)) {
                            agg.latestWeightTime = recordTime;
                            agg.latestWeightValue = val;
                        }
                    }
                } catch (Exception e) {
                    // Ignore individual record failures
                }
            }
        } else if ("HKQuantityTypeIdentifierBloodGlucose".equals(type)) {
            String valueStr = reader.getAttributeValue(null, "value");
            String startDateStr = reader.getAttributeValue(null, "startDate");
            if (valueStr != null && startDateStr != null) {
                try {
                    double val = Double.parseDouble(valueStr);
                    ZonedDateTime start = parseDateTime(startDateStr);
                    if (start != null) {
                        String dateKey = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        DailyAggregator agg = dailyMap.computeIfAbsent(dateKey, k -> new DailyAggregator());
                        int hour = start.getHour();
                        if (hour < 10) {
                            agg.bloodGlucoseFastingSum += val;
                            agg.bloodGlucoseFastingCount++;
                        } else {
                            agg.bloodGlucosePPSum += val;
                            agg.bloodGlucosePPCount++;
                        }
                    }
                } catch (Exception e) {
                    // Ignore individual record failures
                }
            }
        } else if ("HKQuantityTypeIdentifierRestingHeartRate".equals(type)) {
            String valueStr = reader.getAttributeValue(null, "value");
            String startDateStr = reader.getAttributeValue(null, "startDate");
            if (valueStr != null && startDateStr != null) {
                try {
                    double val = Double.parseDouble(valueStr);
                    ZonedDateTime start = parseDateTime(startDateStr);
                    if (start != null) {
                        String dateKey = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        DailyAggregator agg = dailyMap.computeIfAbsent(dateKey, k -> new DailyAggregator());
                        agg.restingHeartRateSum += val;
                        agg.restingHeartRateCount++;
                    }
                } catch (Exception e) {
                    // Ignore individual record failures
                }
            }
        }
    }

    private void processWorkout(XMLStreamReader reader, Map<String, DailyAggregator> dailyMap) {
        String activityType = reader.getAttributeValue(null, "workoutActivityType");
        String durationStr = reader.getAttributeValue(null, "duration");
        String durationUnit = reader.getAttributeValue(null, "durationUnit");
        String startDateStr = reader.getAttributeValue(null, "startDate");
        String sourceName = reader.getAttributeValue(null, "sourceName");

        if (activityType != null && startDateStr != null) {
            try {
                ZonedDateTime start = parseDateTime(startDateStr);
                if (start != null) {
                    String dateKey = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    DailyAggregator agg = dailyMap.computeIfAbsent(dateKey, k -> new DailyAggregator());

                    String exerciseName = activityType.replace("HKWorkoutActivityType", "");
                    String durationVal = "0";
                    if (durationStr != null) {
                        try {
                            double d = Double.parseDouble(durationStr);
                            durationVal = String.format(Locale.US, "%.1f", d);
                        } catch (NumberFormatException e) {
                            durationVal = durationStr;
                        }
                    }

                    Exercise exercise = Exercise.builder()
                            .name(exerciseName)
                            .duration(durationVal)
                            .unit(durationUnit != null ? durationUnit : "min")
                            .note(sourceName != null ? "Source: " + sourceName : null)
                            .build();
                    agg.exercises.add(exercise);
                }
            } catch (Exception e) {
                // Ignore individual record failures
            }
        }
    }

    private List<DailyMetrics> convertToDailyMetrics(Map<String, DailyAggregator> dailyMap) {
        List<DailyMetrics> results = new ArrayList<>();
        for (Map.Entry<String, DailyAggregator> entry : dailyMap.entrySet()) {
            String dateStr = entry.getKey();
            DailyAggregator agg = entry.getValue();

            Health.HealthBuilder healthBuilder = Health.builder();
            boolean hasHealth = false;

            if (agg.sleepHoursSum > 0) {
                healthBuilder.sleepHours(Math.round(agg.sleepHoursSum * 10.0) / 10.0);
                hasHealth = true;
            }
            if (agg.restingHeartRateCount > 0) {
                healthBuilder.restingHeartRate(Math.round((agg.restingHeartRateSum / agg.restingHeartRateCount) * 10.0) / 10.0);
                hasHealth = true;
            }
            if (agg.bloodGlucoseFastingCount > 0) {
                healthBuilder.bloodGlucoseFasting(Math.round((agg.bloodGlucoseFastingSum / agg.bloodGlucoseFastingCount) * 10.0) / 10.0);
                hasHealth = true;
            }
            if (agg.bloodGlucosePPCount > 0) {
                healthBuilder.bloodGlucosePP(Math.round((agg.bloodGlucosePPSum / agg.bloodGlucosePPCount) * 10.0) / 10.0);
                hasHealth = true;
            }
            if (agg.latestWeightTime != null) {
                healthBuilder.weight(Math.round(agg.latestWeightValue * 10.0) / 10.0);
                hasHealth = true;
            }

            WorkoutLog workoutLog = null;
            if (!agg.exercises.isEmpty()) {
                workoutLog = WorkoutLog.builder()
                        .programName("Apple Health")
                        .title("Apple Health Workouts")
                        .exercises(agg.exercises)
                        .build();
            }

            if (hasHealth || workoutLog != null) {
                DailyMetrics metrics = DailyMetrics.builder()
                        .date(dateStr)
                        .health(hasHealth ? healthBuilder.build() : null)
                        .workout(workoutLog)
                        .source("xml_import")
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
                results.add(metrics);
            }
        }
        return results;
    }

    private ZonedDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        dateStr = dateStr.trim();
        try {
            return ZonedDateTime.parse(dateStr, FORMATTER_WITH_OFFSET);
        } catch (Exception e1) {
            try {
                return ZonedDateTime.parse(dateStr, FORMATTER_WITH_ZONE);
            } catch (Exception e2) {
                try {
                    return ZonedDateTime.parse(dateStr, ISO_FORMATTER);
                } catch (Exception e3) {
                    try {
                        return LocalDateTime.parse(dateStr).atZone(ZoneId.systemDefault());
                    } catch (Exception e4) {
                        try {
                            if (dateStr.length() >= 19) {
                                DateTimeFormatter simple = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                                return LocalDateTime.parse(dateStr.substring(0, 19), simple).atZone(ZoneId.systemDefault());
                            }
                        } catch (Exception e5) {
                            // fall through
                        }
                        throw new IllegalArgumentException("Cannot parse date: " + dateStr, e4);
                    }
                }
            }
        }
    }

    private static class DailyAggregator {
        double sleepHoursSum = 0.0;
        double latestWeightValue = 0.0;
        Instant latestWeightTime = null;
        double restingHeartRateSum = 0.0;
        int restingHeartRateCount = 0;
        double bloodGlucoseFastingSum = 0.0;
        int bloodGlucoseFastingCount = 0;
        double bloodGlucosePPSum = 0.0;
        int bloodGlucosePPCount = 0;
        List<Exercise> exercises = new ArrayList<>();
    }

    private static class ProgressTrackingInputStream extends FilterInputStream {
        private final long totalBytes;
        private long bytesRead = 0;
        private final Consumer<Integer> progressCallback;
        private int lastProgress = -1;

        protected ProgressTrackingInputStream(InputStream in, long totalBytes, Consumer<Integer> progressCallback) {
            super(in);
            this.totalBytes = totalBytes;
            this.progressCallback = progressCallback;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) {
                bytesRead++;
                updateProgress();
            }
            return b;
        }

        @Override
        public int read(byte[] b) throws IOException {
            int n = super.read(b);
            if (n != -1) {
                bytesRead += n;
                updateProgress();
            }
            return n;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n != -1) {
                bytesRead += n;
                updateProgress();
            }
            return n;
        }

        private void updateProgress() {
            if (totalBytes > 0) {
                int progress = (int) ((bytesRead * 100) / totalBytes);
                if (progress != lastProgress) {
                    lastProgress = progress;
                    progressCallback.accept(progress);
                }
            }
        }
    }
}