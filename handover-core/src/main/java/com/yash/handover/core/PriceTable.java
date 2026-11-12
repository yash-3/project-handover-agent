package com.yash.handover.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * USD price per million tokens by model id. Defaults are Anthropic list prices as of 2026-11-03
 * (Haiku 5.5 is the price for prompts up to 100K tokens). Prices change, so check the official
 * pricing page and override with {@link #with}. An unknown model gives no estimate (never a guess).
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class PriceTable {

    public record Price(BigDecimal inputPerMTok, BigDecimal outputPerMTok) {
    }

    private final Map<String, Price> prices;

    public static PriceTable defaults() {
        Map<String, Price> m = new HashMap<>();
        m.put("claude-haiku-5-5", price("0.10", "0.50"));
        m.put("claude-sonnet-5-5", price("2.00", "10.00"));
        m.put("claude-opus-5-5", price("4.00", "20.00"));
        m.put("claude-fable-5-2", price("8.00", "40.00"));
        return new PriceTable(m);
    }

    public PriceTable with(String model, String inputPerMTok, String outputPerMTok) {
        Map<String, Price> m = new HashMap<>(prices);
        m.put(model, price(inputPerMTok, outputPerMTok));
        return new PriceTable(m);
    }

    public Optional<BigDecimal> estimate(String model, long inputTokens, long outputTokens) {
        Price p = prices.get(model);
        if (p == null) {
            return Optional.empty();
        }
        BigDecimal million = BigDecimal.valueOf(1_000_000);
        BigDecimal cost = p.inputPerMTok().multiply(BigDecimal.valueOf(inputTokens))
                .add(p.outputPerMTok().multiply(BigDecimal.valueOf(outputTokens)))
                .divide(million, 6, RoundingMode.HALF_UP);
        return Optional.of(cost);
    }

    private static Price price(String in, String out) {
        return new Price(new BigDecimal(in), new BigDecimal(out));
    }
}
