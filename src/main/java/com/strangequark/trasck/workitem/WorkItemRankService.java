package com.strangequark.trasck.workitem;

import java.math.BigInteger;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkItemRankService {

    private static final int WIDTH = 16;
    private static final BigInteger INITIAL_GAP = BigInteger.valueOf(1_000_000L);
    private static final BigInteger MIN_RANK = BigInteger.ZERO;
    private static final BigInteger MAX_RANK = new BigInteger("9".repeat(WIDTH));

    private final WorkItemRepository workItemRepository;

    public WorkItemRankService(WorkItemRepository workItemRepository) {
        this.workItemRepository = workItemRepository;
    }

    public String appendRank(UUID projectId) {
        return workItemRepository.findTopByProjectIdAndDeletedAtIsNullOrderByRankDesc(projectId)
                .map(WorkItem::getRank)
                .map(this::rankAfter)
                .orElse(format(INITIAL_GAP));
    }

    public String between(String previousRank, String nextRank) {
        BigInteger lower = previousRank == null ? MIN_RANK : parse(previousRank);
        BigInteger upper = nextRank == null ? MAX_RANK : parse(nextRank);
        if (lower.compareTo(upper) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rank bounds are out of order");
        }
        BigInteger candidate = lower.add(upper).divide(BigInteger.TWO);
        if (candidate.equals(lower) || candidate.equals(upper)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No rank space remains between these work items");
        }
        return format(candidate);
    }

    private String rankAfter(String rank) {
        BigInteger next = parse(rank).add(INITIAL_GAP);
        if (next.compareTo(MAX_RANK) >= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No rank space remains at the end of this project");
        }
        return format(next);
    }

    private BigInteger parse(String rank) {
        if (rank == null || rank.isBlank()) {
            return MIN_RANK;
        }
        return new BigInteger(rank);
    }

    private String format(BigInteger rank) {
        String value = rank.toString();
        if (value.length() > WIDTH) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Rank value is too large");
        }
        return "0".repeat(WIDTH - value.length()) + value;
    }
}
