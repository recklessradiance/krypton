package com.krypton.repository;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.krypton.model.ClinicalRecord;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class ClinicalRecordRepository {

    private final Firestore firestore;
    private static final String COLLECTION = "clinical_records";

    public ClinicalRecordRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public void save(ClinicalRecord record) throws ExecutionException, InterruptedException {
        if (record.getId() == null || record.getId().isEmpty()) {
            DocumentReference docRef = firestore.collection(COLLECTION).document();
            record.setId(docRef.getId());
        }
        firestore.collection(COLLECTION).document(record.getId()).set(record).get();
    }

    public Optional<ClinicalRecord> findById(String id) throws ExecutionException, InterruptedException {
        DocumentSnapshot document = firestore.collection(COLLECTION).document(id).get().get();
        if (document.exists()) {
            return Optional.ofNullable(document.toObject(ClinicalRecord.class));
        }
        return Optional.empty();
    }

    public List<ClinicalRecord> findAllOrderByDateDesc() throws ExecutionException, InterruptedException {
        QuerySnapshot querySnapshot = firestore.collection(COLLECTION)
                .orderBy("date", Query.Direction.DESCENDING)
                .get().get();

        return querySnapshot.getDocuments().stream()
                .map(doc -> doc.toObject(ClinicalRecord.class))
                .collect(Collectors.toList());
    }

    public List<ClinicalRecord> findAll() throws ExecutionException, InterruptedException {
        QuerySnapshot querySnapshot = firestore.collection(COLLECTION).get().get();
        return querySnapshot.getDocuments().stream()
                .map(doc -> doc.toObject(ClinicalRecord.class))
                .collect(Collectors.toList());
    }

    public void delete(String id) throws ExecutionException, InterruptedException {
        firestore.collection(COLLECTION).document(id).delete().get();
    }
}