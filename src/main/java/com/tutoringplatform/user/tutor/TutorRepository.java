package com.tutoringplatform.user.tutor;

import com.tutoringplatform.subject.Subject;

import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class TutorRepository implements ITutorRepository {
    private Map<String, Tutor> tutors = new HashMap<>();

    @Override
    public Tutor findById(String id) {
        return tutors.get(id);
    }

    @Override
    public Tutor findByEmail(String email) {
        return tutors.values().stream()
                .filter(t -> t.getEmail().equals(email))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Tutor> findAll() {
        return new ArrayList<>(tutors.values());
    }

    @Override
    public void save(Tutor tutor) {
        tutors.put(tutor.getId(), tutor);
    }

    @Override
    public void update(Tutor tutor) {
        tutors.put(tutor.getId(), tutor);
    }

    @Override
    public void delete(String id) {
        tutors.remove(id);
    }

    @Override
    public List<Tutor> findBySubject(Subject subject) {
        return tutors.values().stream()
                .filter(t -> t.getSubjects().contains(subject))
                .collect(Collectors.toList());
    }

    @Override
    public boolean emailExists(String email) {
        return findByEmail(email) != null;
    }
}