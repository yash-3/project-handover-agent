package com.yash.handover.core;

/** One tool call by an agent. Sizes only, never the content: arguments and results can hold sensitive text. */
public record ToolCallRecord(String agent, String tool, long millis, boolean ok, int argChars, int resultChars) {
}
