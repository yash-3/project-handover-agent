package com.yash.handover.evals;

import com.yash.handover.agents.Answer;

/** Optional second opinion on an answer, for questions that carry a reference answer. */
public interface Judge {

    CaseResult.Check judge(EvalCase c, Answer a);
}
