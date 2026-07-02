package com.krypton.repository;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.krypton.model.DailyMetrics;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class DailyMetricsRepository {

    private final Firestore firestore;
    private static final String COLLECTION = "daily_metrics";

    public DailyMetricsRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public void save(DailyMetrics metrics) throws ExecutionException, InterruptedException {
        firestore.collection(COLLECTION).document(metrics.getDate()).set(metrics).get();
    }

    public Optional<DailyMetrics> findByDate(String date) throws ExecutionException, InterruptedException {
        DocumentSnapshot document = firestore.collection(COLLECTION).document(date).get().get();
        if (document.exists()) {
            return Optional.ofNullable(document.toObject(DailyMetrics.class));
        }
        return Optional.empty();
    }

    public List<DailyMetrics> findByDateRange(String fromDate, String toDate) throws ExecutionException, InterruptedException {
        QuerySnapshot querySnapshot = firestore.collection(COLLECTION)
                .whereGreaterThanOrEqualTo("date", fromDate)
                .whereLessThanOrEqualTo("date", toDate)
                .get().get();

        return querySnapshot.getDocuments().stream()
                .map(doc -> doc.toObject(DailyMetrics.class))
                .collect(Collectors.toList());
    }

    public void delete(String date) throws ExecutionException, InterruptedException {
        firestore.collection(COLLECTION).document(date).delete().get();
    }
}\n