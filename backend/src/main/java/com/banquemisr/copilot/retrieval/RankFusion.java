package com.banquemisr.copilot.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RankFusion {

    public record FusedRank(UUID chunkId, Integer semanticRank, Integer keywordRank, double score) {
    }

    private RankFusion() {
    }

    public static List<FusedRank> fuse(List<UUID> semantic, List<UUID> keyword, int k) {
        Set<UUID> all = new LinkedHashSet<>(semantic);
        all.addAll(keyword);
        List<FusedRank> fused = new ArrayList<>();
        for (UUID id : all) {
            Integer semanticRank = rankOf(semantic, id);
            Integer keywordRank = rankOf(keyword, id);
            double score = contribution(semanticRank, k) + contribution(keywordRank, k);
            fused.add(new FusedRank(id, semanticRank, keywordRank, score));
        }
        fused.sort(Comparator.comparingDouble(FusedRank::score).reversed()
                .thenComparing(rank -> rank.semanticRank() == null ? Integer.MAX_VALUE : rank.semanticRank()));
        return fused;
    }

    private static Integer rankOf(List<UUID> ranking, UUID id) {
        int index = ranking.indexOf(id);
        return index < 0 ? null : index + 1;
    }

    private static double contribution(Integer rank, int k) {
        return rank == null ? 0 : 1.0 / (k + rank);
    }
}
