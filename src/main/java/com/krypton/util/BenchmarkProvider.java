package com.krypton.util;

import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.Period;

@Component
public class BenchmarkProvider {

    private static final LocalDate DOB = LocalDate.of(1997, 1, 27);

    public int getAge() {
        return Period.between(DOB, LocalDate.now()).getYears();
    }

    public int getAgeAt(LocalDate date) {
        return Period.between(DOB, date).getYears();
    }

    public IdealRange getSleepRange() {
        return new IdealRange(7.0, 9.0, "NSF");
    }

    public IdealRange getRestingHeartRateRange() {
        return new IdealRange(60.0, 70.0, "AHA");
    }

    public IdealRange getWeightRange() {
        return new IdealRange(59.0, 79.0, "WHO");
    }

    public IdealRange getFastingBloodGlucoseRange() {
        return new IdealRange(70.0, 100.0, "ADA");
    }

    public IdealRange getPostPrandialBloodGlucoseRange() {
        return new IdealRange(null, 140.0, "ADA");
    }

    public static record IdealRange(Double min, Double max, String source) {}
}\n