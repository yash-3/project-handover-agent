package com.yash.handover.evals;

import java.util.List;

/**
 * One question with its answer key.
 *
 * @param mustContain    terms (case-insensitive) the answer must all contain
 * @param mustCite       substrings of file paths; each must appear in at least one VERIFIED citation
 * @param expectNotFound true for questions the sources cannot answer: the agent must say it could not find it
 * @param reference      a model answer, used only by the optional LLM judge
 */
public record EvalCase(String id, String question, List<String> mustContain, List<String> mustCite,
                       Boolean expectNotFound, String reference) {

    public EvalCase {
        mustContain = mustContain == null ? List.of() : List.copyOf(mustContain);
        mustCite = mustCite == null ? List.of() : List.copyOf(mustCite);
        expectNotFound = expectNotFound != null && expectNotFound;
    }

    public boolean unanswerable() {
        return expectNotFound;
    }
}
