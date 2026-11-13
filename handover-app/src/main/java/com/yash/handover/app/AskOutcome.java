package com.yash.handover.app;

import java.math.BigDecimal;

import com.yash.handover.agents.Answer;

/** A Q&A answer with what it cost. */
public record AskOutcome(Answer answer, long tokens, BigDecimal costUsd) {
}
