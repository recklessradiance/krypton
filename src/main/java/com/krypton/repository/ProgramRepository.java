package com.krypton.repository;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.krypton.model.Program;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class ProgramRepository {

    private final Firestore firestore;
    private static final String COLLECTION = "programs";

    public ProgramRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public void save(Program program) throws ExecutionException, InterruptedException {
        if (program.getId() == null || program.getId().isEmpty()) {
            DocumentReference docRef = firestore.collection(COLLECTION).document();
            program.setId(docRef.getId());
        }
        firestore.collection(COLLECTION).document(program.getId()).set(program).get();
    }

    public Optional<Program> findById(String id) throws ExecutionException, InterruptedException {
        DocumentSnapshot document = firestore.collection(COLLECTION).document(id).get().get();
        if (document.exists()) {
            return Optional.ofNullable(document.toObject(Program.class));
        }
        return Optional.empty();
    }

    public Optional<Program> findActiveProgram() throws ExecutionException, InterruptedException {
        QuerySnapshot querySnapshot = firestore.collection(COLLECTION)
                .whereEqualTo("isActive", true)
                .limit(1)
                .get().get();
        if (!querySnapshot.isEmpty()) {
            DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
            return Optional.ofNullable(doc.toObject(Program.class));
        }
        return Optional.empty();
    }

    public Optional<Program> findByName(String name) throws ExecutionException, InterruptedException {
        QuerySnapshot querySnapshot = firestore.collection(COLLECTION)
                .whereEqualTo("name", name)
                .limit(1)
                .get().get();
        if (!querySnapshot.isEmpty()) {
            DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
            return Optional.ofNullable(doc.toObject(Program.class));
        }
        return Optional.empty();
    }

    public List<Program> findAll() throws ExecutionException, InterruptedException {
        QuerySnapshot querySnapshot = firestore.collection(COLLECTION).get().get();
        return querySnapshot.getDocuments().stream()
                .map(doc -> doc.toObject(Program.class))
                .collect(Collectors.toList());
    }

    public void delete(String id) throws ExecutionException, InterruptedException {
        firestore.collection(COLLECTION).document(id).delete().get();
    }
}