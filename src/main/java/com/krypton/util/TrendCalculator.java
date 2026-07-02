package com.krypton.util;

import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import com.krypton.model.DailyMetrics;

@Component
public class TrendCalculator {

    private static final double DECAY_CONSTANT = Math.log(2.0) / 30.0; // 30-day half-life

    public static record DataPoint(double x, double y) {}

    public static class RegressionResult {
        private final double slope;
        private final double intercept;
        private final double meanX;
        private final double sumW;
        private final double sumWxx;
        private final double residualVariance;

        public RegressionResult(double slope, double intercept, double meanX, double sumW, double sumWxx, double residualVariance) {
            this.slope = slope;
            this.intercept = intercept;
            this.meanX = meanX;
            this.sumW = sumW;
            this.sumWxx = sumWxx;
            this.residualVariance = residualVariance;
        }

        public double getSlope() { return slope; }
        public double getIntercept() { return intercept; }
        public double predict(double x) {
            return intercept + slope * x;
        }

        public double calculateStandardError(double x) {
            return Math.sqrt(residualVariance * (1.0 + (1.0 / sumW) + (sumWxx > 1e-9 ? Math.pow(x - meanX, 2) / sumWxx : 0.0)));
        }
    }

    public RegressionResult fitLinearRegression(List<DataPoint> points) {
        if (points == null || points.size() < 2) {
            // Fallback for insufficient data
            double avgY = 0.0;
            if (points != null && !points.isEmpty()) {
                avgY = points.stream().mapToDouble(DataPoint::y).average().orElse(0.0);
            }
            return new RegressionResult(0.0, avgY, 0.0, 1.0, 0.0, 0.01 * Math.abs(avgY));
        }

        // xLatest is the maximum x in the dataset
        double xLatest = points.stream().mapToDouble(DataPoint::x).max().orElse(0.0);

        double sumW = 0.0;
        double sumWx = 0.0;
        double sumWy = 0.0;

        for (DataPoint p : points) {
            double w = Math.exp(-DECAY_CONSTANT * (xLatest - p.x()));
            sumW += w;
            sumWx += w * p.x();
            sumWy += w * p.y();
        }

        double meanX = sumWx / sumW;
        double meanY = sumWy / sumW;

        double sumWxx = 0.0;
        double sumWxy = 0.0;

        for (DataPoint p : points) {
            double w = Math.exp(-DECAY_CONSTANT * (xLatest - p.x()));
            double dx = p.x() - meanX;
            double dy = p.y() - meanY;
            sumWxx += w * dx * dx;
            sumWxy += w * dx * dy;
        }

        double slope = sumWxx > 1e-9 ? sumWxy / sumWxx : 0.0;
        double intercept = meanY - slope * meanX;

        double rss = 0.0;
        double sumW2 = 0.0;
        for (DataPoint p : points) {
            double w = Math.exp(-DECAY_CONSTANT * (xLatest - p.x()));
            sumW2 += w * w;
            double pred = intercept + slope * p.x();
            double residual = p.y() - pred;
            rss += w * residual * residual;
        }

        double denominator = sumW - (sumW2 / sumW);
        double residualVariance;
        if (denominator > 1e-6) {
            residualVariance = rss / denominator;
        } else {
            residualVariance = rss / sumW;
        }

        // Apply a small floor to residual variance to prevent zero confidence intervals
        double minVariance = Math.pow(0.01 * Math.abs(meanY), 2);
        if (residualVariance < minVariance) {
            residualVariance = minVariance;
        }
        if (residualVariance < 1e-4) {
            residualVariance = 1e-4; // absolute floor
        }

        return new RegressionResult(slope, intercept, meanX, sumW, sumWxx, residualVariance);
    }

    public List<DataPoint> calculateRollingConsistency(List<DailyMetrics> metrics, String habitName) {
        List<DataPoint> consistencyPoints = new ArrayList<>();
        if (metrics == null || metrics.isEmpty()) {
            return consistencyPoints;
        }

        LocalDate firstDate = LocalDate.parse(metrics.get(0).getDate());

        for (int i = 0; i < metrics.size(); i++) {
            DailyMetrics m = metrics.get(i);
            LocalDate date = LocalDate.parse(m.getDate());
            LocalDate startWindow = date.minusDays(29);

            int completedCount = 0;
            for (int j = 0; j <= i; j++) {
                DailyMetrics prev = metrics.get(j);
                LocalDate prevDate = LocalDate.parse(prev.getDate());
                if (!prevDate.isBefore(startWindow)) {
                    if (prev.getChecklist() != null && isHabitTrue(prev.getChecklist(), habitName)) {
                        completedCount++;
                    }
                }
            }

            long daysSinceStart = ChronoUnit.DAYS.between(firstDate, date) + 1;
            long windowDays = Math.min(30, daysSinceStart);

            double consistency = (completedCount * 100.0) / windowDays;
            double x = ChronoUnit.DAYS.between(firstDate, date);
            consistencyPoints.add(new DataPoint(x, consistency));
        }

        return consistencyPoints;
    }

    private boolean isHabitTrue(com.krypton.model.Checklist checklist, String habitName) {
        if (checklist == null) return false;
        switch (habitName) {
            case "workout": return Boolean.TRUE.equals(checklist.getWorkout());
            case "eggs": return Boolean.TRUE.equals(checklist.getEggs());
            case "quinoa": return Boolean.TRUE.equals(checklist.getQuinoa());
            case "vegCurry": return Boolean.TRUE.equals(checklist.getVegCurry());
            case "soya": return Boolean.TRUE.equals(checklist.getSoya());
            case "water": return Boolean.TRUE.equals(checklist.getWater());
            default: return false;
        }
    }
}