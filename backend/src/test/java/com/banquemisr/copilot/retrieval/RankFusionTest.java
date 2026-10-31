package com.banquemisr.copilot.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RankFusionTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID d = UUID.randomUUID();

    @Test
    void passagesFoundByBothRetrieversRankFirst() {
        List<RankFusion.FusedRank> fused = RankFusion.fuse(List.of(a, b, c), List.of(d, c), 60);

        assertThat(fused).extracting(RankFusion.FusedRank::chunkId).containsExactly(c, a, d, b);
        RankFusion.FusedRank top = fused.getFirst();
        assertThat(top.semanticRank()).isEqualTo(3);
        assertThat(top.keywordRank()).isEqualTo(2);
        assertThat(top.score()).isEqualTo(1.0 / 63 + 1.0 / 62);
    }

    @Test
    void equalScoresPreferTheBetterSemanticRank() {
        List<RankFusion.FusedRank> fused = RankFusion.fuse(List.of(a), List.of(b), 60);

        assertThat(fused).extracting(RankFusion.FusedRank::chunkId).containsExactly(a, b);
        assertThat(fused.get(1).semanticRank()).isNull();
    }

    @Test
    void emptyInputsGiveNoResults() {
        assertThat(RankFusion.fuse(List.of(), List.of(), 60)).isEmpty();
    }
}
