package com.orda.backend.domain.trail.service;

import com.orda.backend.domain.trail.dto.response.TrailDifficultyResponse;
import com.orda.backend.domain.trail.entity.TrailEdge;
import com.orda.backend.domain.trail.repository.TrailEdgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrailDifficultyService {

    private final TrailEdgeRepository trailEdgeRepository;

    public TrailDifficultyResponse getDifficultyByEdgeId(String edgeId) {
        TrailEdge edge = findEdgeById(edgeId);
        return TrailDifficultyResponse.from(edge);
    }

    public List<TrailDifficultyResponse> getDifficultyBySummitId(String summitId) {
        return trailEdgeRepository.findByNearestSummitId(summitId)
                .stream()
                .map(TrailDifficultyResponse::from)
                .collect(Collectors.toList());
    }

    public List<TrailDifficultyResponse> getAllDifficulties() {
        return trailEdgeRepository.findAll()
                .stream()
                .map(TrailDifficultyResponse::from)
                .collect(Collectors.toList());
    }

    private TrailEdge findEdgeById(String edgeId) {
        return trailEdgeRepository.findById(edgeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 구간입니다: " + edgeId));
    }
}