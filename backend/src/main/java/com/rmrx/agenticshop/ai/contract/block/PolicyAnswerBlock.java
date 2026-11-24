package com.rmrx.agenticshop.ai.contract.block;

import java.util.List;

/**
 * A policy answer as short points. citationIndexes are 0-based positions in the envelope's citations list, so a
 * citation is sent once even when several blocks use it.
 */
public record PolicyAnswerBlock(String id, String question, List<String> points, List<Integer> citationIndexes)
        implements Block {

    public PolicyAnswerBlock {
        points = points == null ? List.of() : List.copyOf(points);
        citationIndexes = citationIndexes == null ? List.of() : List.copyOf(citationIndexes);
    }
}
