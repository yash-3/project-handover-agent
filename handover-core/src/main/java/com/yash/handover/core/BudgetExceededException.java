package com.yash.handover.core;

public class BudgetExceededException extends RuntimeException {

    public BudgetExceededException(long used, long max) {
        super("Token budget exceeded: used " + used + " of " + max + " tokens for this run");
    }
}
