package com.krypton.service;

import com.krypton.dto.response.PredictionResponse;
import com.krypton.model.DailyMetrics;
import com.krypton.repository.DailyMetricsRepository;
import com.krypton.util.BenchmarkProvider;
import com.krypton.util.TrendCalculator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
public class PredictionService {

    private final DailyMetricsRepository repository;
    private final BenchmarkProvider benchmarkProvider;
    private final TrendCalculator trendCalculator;

    public PredictionService(DailyMetricsRepository repository, BenchmarkProvider benchmarkProvider, TrendCalculator trendCalculator) {
        this.repository = repository;
        this.benchmarkProvider = benchmarkProvider;
        this.trendCalculator = trendCalculator;
    }

    public PredictionResponse getPredictions(List<String> metrics, List<String> horizons) throws ExecutionException, InterruptedException {
        List<DailyMetrics> allMetrics = repository.findAll();
        allMetrics.sort(Comparator.comparing(DailyMetrics::getDate));

        if (allMetrics.isEmpty()) {
            return PredictionResponse.builder()
                    .generatedAt(LocalDateTime.now())
                    .dataPointsUsed(0)
                    .predictions(new HashMap<>())
                    .build();
        }

        LocalDate earliestDate = LocalDate.parse(allMetrics.get(0).getDate());
        LocalDate latestDate = LocalDate.parse(allMetrics.get(allMetrics.size() - 1).getDate());
        long historyDays = ChronoUnit.DAYS.between(earliestDate, latestDate) + 1;
        double xLatest = ChronoUnit.DAYS.between(earliestDate, latestDate);

        Map<String, Object> predictionsMap = new LinkedHashMap<>();

        for (String metric : metrics) {
            String normalizedMetric = metric;
            if ("restingHR".equals(metric)) {
                normalizedMetric = "restingHeartRate";
            }

            if (isNumericMetric(normalizedMetric)) {
                List<TrendCalculator.DataPoint> points = extractNumericPoints(allMetrics, normalizedMetric, earliestDate);
                if (points.size() < 2) {
                    continue;
                }

                TrendCalculator.RegressionResult result = trendCalculator.fitLinearRegression(points);
                Double currentAvg = calculateCurrentAvg(points, xLatest);
                BenchmarkProvider.IdealRange idealRange = getIdealRange(normalizedMetric);

                Map<String, Object> metricData = new LinkedHashMap<>();
                metricData.put("currentAvg", currentAvg);
                if (idealRange != null) {
                    Map<String, Object> idealMap = new LinkedHashMap<>();
                    idealMap.put("min", idealRange.min());
                    idealMap.put("max", idealRange.max());
                    idealMap.put("source", idealRange.source());
                    metricData.put("ideal", idealMap);
                }

                Map<String, Object> horizonsMap = new LinkedHashMap<>();
                for (String horizon : horizons) {
                    long horizonDays = getHorizonDays(horizon);
                    double xStar = xLatest + horizonDays;

                    double predicted = result.predict(xStar);
                    double se = result.calculateStandardError(xStar);
                    double low = predicted - 1.96 * se;
                    double high = predicted + 1.96 * se;

                    Map<String, Object> horizonData = new LinkedHashMap<>();
                    horizonData.put("predicted", round(predicted));
                    horizonData.put("low", round(low));
                    horizonData.put("high", round(high));
                    horizonData.put("vsIdeal", getPredictionVsIdeal(predicted, normalizedMetric, idealRange));

                    if (!isHistorySufficient(historyDays, horizon)) {
                        horizonData.put("confidence", "low");
                    }

                    horizonsMap.put(horizon, horizonData);
                }

                metricData.put("horizons", horizonsMap);
                metricData.put("trendDescription", generateTrendDescription(normalizedMetric, result.getSlope()));

                predictionsMap.put(metric, metricData);

            } else if (isChecklistHabit(metric)) {
                String habitName = getHabitName(metric);
                List<TrendCalculator.DataPoint> points = trendCalculator.calculateRollingConsistency(allMetrics, habitName);
                if (points.isEmpty()) {
                    continue;
                }

                TrendCalculator.RegressionResult result = trendCalculator.fitLinearRegression(points);
                double latestConsistency = points.get(points.size() - 1).y();

                Map<String, Object> metricData = new LinkedHashMap<>();
                metricData.put("currentAvg", round(latestConsistency));
                metricData.put("current30d", (int) Math.round(latestConsistency));

                Map<String, Object> horizonsMap = new LinkedHashMap<>();
                for (String horizon : horizons) {
                    long horizonDays = getHorizonDays(horizon);
                    double xStar = xLatest + horizonDays;

                    double predicted = result.predict(xStar);
                    if (predicted < 0.0) predicted = 0.0;
                    if (predicted > 100.0) predicted = 100.0;

                    Map<String, Object> horizonData = new LinkedHashMap<>();
                    horizonData.put("predicted", roundToInt(predicted));

                    if (!isHistorySufficient(historyDays, horizon)) {
                        horizonData.put("confidence", "low");
                    }

                    horizonsMap.put(horizon, horizonData);
                }

                metricData.put("horizons", horizonsMap);
                metricData.put("trendDescription", generateTrendDescription(metric, result.getSlope()));

                predictionsMap.put(metric, metricData);
            }
        }

        return PredictionResponse.builder()
                .generatedAt(LocalDateTime.now())
                .dataPointsUsed(allMetrics.size())
                .predictions(predictionsMap)
                .build();
    }

    private boolean isNumericMetric(String metric) {
        return List.of("weight", "restingHeartRate", "sleepHours", "bloodGlucoseFasting", "bloodGlucosePP").contains(metric);
    }

    private boolean isChecklistHabit(String metric) {
        String name = getHabitName(metric);
        return List.of("workout", "eggs", "quinoa", "vegCurry", "soya", "water").contains(name);
    }

    private String getHabitName(String metric) {
        if (metric.endsWith("Consistency")) {
            return metric.substring(0, metric.length() - "Consistency".length());
        }
        return metric;
    }

    private long getHorizonDays(String horizon) {
        switch (horizon) {
            case "2w": return 14;
            case "1m": return 30;
            case "3m": return 90;
            case "6m": return 180;
            case "1y": return 365;
            default: return 0;
        }
    }

    private boolean isHistorySufficient(long historyDays, String horizon) {
        switch (horizon) {
            case "2w": return historyDays >= 30;
            case "1m": return historyDays >= 60;
            case "3m": return historyDays >= 90;
            case "6m": return historyDays >= 180;
            case "1y": return historyDays >= 365;
            default: return false;
        }
    }

    private List<TrendCalculator.DataPoint> extractNumericPoints(List<DailyMetrics> metrics, String metricName, LocalDate earliestDate) {
        List<TrendCalculator.DataPoint> points = new ArrayList<>();
        for (DailyMetrics m : metrics) {
            if (m.getHealth() == null) continue;
            Double val = null;
            switch (metricName) {
                case "weight":
                    val = m.getHealth().getWeight();
                    break;
                case "restingHeartRate":
                    val = m.getHealth().getRestingHeartRate();
                    break;
                case "sleepHours":
                    val = m.getHealth().getSleepHours();
                    break;
                case "bloodGlucoseFasting":
                    val = m.getHealth().getBloodGlucoseFasting();
                    break;
                case "bloodGlucosePP":
                    val = m.getHealth().getBloodGlucosePP();
                    break;
            }
            if (val != null) {
                double x = ChronoUnit.DAYS.between(earliestDate, LocalDate.parse(m.getDate()));
                points.add(new TrendCalculator.DataPoint(x, val));
            }
        }
        return points;
    }

    private Double calculateCurrentAvg(List<TrendCalculator.DataPoint> points, double xLatest) {
        if (points.isEmpty()) return 0.0;
        double sum = 0.0;
        int count = 0;
        for (TrendCalculator.DataPoint p : points) {
            if (xLatest - p.x() <= 30.0) {
                sum += p.y();
                count++;
            }
        }
        if (count > 0) {
            return Math.round((sum / count) * 10.0) / 10.0;
        }
        return Math.round(points.get(points.size() - 1).y() * 10.0) / 10.0;
    }

    private BenchmarkProvider.IdealRange getIdealRange(String metric) {
        switch (metric) {
            case "weight": return benchmarkProvider.getWeightRange();
            case "restingHeartRate": return benchmarkProvider.getRestingHeartRateRange();
            case "sleepHours": return benchmarkProvider.getSleepRange();
            case "bloodGlucoseFasting": return benchmarkProvider.getFastingBloodGlucoseRange();
            case "bloodGlucosePP": return benchmarkProvider.getPostPrandialBloodGlucoseRange();
            default: return null;
        }
    }

    private String getPredictionVsIdeal(Double val, String metric, BenchmarkProvider.IdealRange range) {
        if (val == null) return "no_data";
        if (range == null) return "in_range";

        if (range.min() != null && val < range.min()) return "below_range";
        if (range.max() != null && val > range.max()) return "above_range";

        switch (metric) {
            case "weight":
                if (val >= 64.0 && val <= 74.0) return "optimal";
                break;
            case "restingHeartRate":
                if (val >= 60.0 && val <= 64.0) return "optimal";
                break;
            case "sleepHours":
                if (val >= 7.5 && val <= 8.5) return "optimal";
                break;
            case "bloodGlucoseFasting":
                if (val >= 70.0 && val <= 85.0) return "optimal";
                break;
            case "bloodGlucosePP":
                if (val < 120.0) return "optimal";
                break;
        }

        return "in_range";
    }

    private String generateTrendDescription(String metric, double slope) {
        double ratePerMonth = Math.round(slope * 30.0 * 10.0) / 10.0;
        boolean stable = Math.abs(ratePerMonth) < 0.1;

        switch (metric) {
            case "weight":
                if (stable) return "Weight is stable.";
                if (ratePerMonth < 0) {
                    return String.format("Dropping ~%.1f kg/month. On track to reach optimal range.", Math.abs(ratePerMonth));
                } else {
                    return String.format("Gaining ~%.1f kg/month.", ratePerMonth);
                }
            case "restingHeartRate":
                if (stable) return "Resting heart rate is stable.";
                if (ratePerMonth < 0) {
                    return String.format("Resting heart rate improving by ~%.1f bpm/month.", Math.abs(ratePerMonth));
                } else {
                    return String.format("Resting heart rate increasing by ~%.1f bpm/month.", ratePerMonth);
                }
            case "sleepHours":
                if (stable) return "Sleep duration is stable.";
                if (ratePerMonth > 0) {
                    return String.format("Sleep duration improving by ~%.1f hours/month.", ratePerMonth);
                } else {
                    return String.format("Sleep duration decreasing by ~%.1f hours/month.", Math.abs(ratePerMonth));
                }
            case "bloodGlucoseFasting":
                if (stable) return "Fasting blood glucose is stable.";
                if (ratePerMonth < 0) {
                    return String.format("Fasting blood glucose decreasing by ~%.1f mg/dL/month.", Math.abs(ratePerMonth));
                } else {
                    return String.format("Fasting blood glucose increasing by ~%.1f mg/dL/month.", ratePerMonth);
                }
            case "bloodGlucosePP":
                if (stable) return "Post-meal blood glucose is stable.";
                if (ratePerMonth < 0) {
                    return String.format("Post-meal blood glucose decreasing by ~%.1f mg/dL/month.", Math.abs(ratePerMonth));
                } else {
                    return String.format("Post-meal blood glucose increasing by ~%.1f mg/dL/month.", ratePerMonth);
                }
            default:
                String name = getHabitName(metric);
                if (stable) return String.format("%s consistency is stable.", capitalize(name));
                if (ratePerMonth > 0) {
                    return String.format("%s consistency improving ~%.1f%% per month.", capitalize(name), ratePerMonth);
                } else {
                    return String.format("%s consistency declining ~%.1f%% per month.", capitalize(name), Math.abs(ratePerMonth));
                }
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private int roundToInt(double value) {
        return (int) Math.round(value);
    }
}