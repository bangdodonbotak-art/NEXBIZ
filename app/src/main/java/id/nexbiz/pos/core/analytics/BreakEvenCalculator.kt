package id.nexbiz.pos.core.analytics

data class BreakEvenAnalysisResult(
    val grossRevenueRp: Long,
    val discountTotalRp: Long,
    val netRevenueRp: Long,
    val totalCogsRp: Long,
    val contributionMarginRp: Long,
    val contributionMarginRatioBps: Long, // 10_000 bps = 100.00%
    val fixedOperatingCostsRp: Long,
    val breakEvenRevenueRp: Long,
    val averageSellingPriceRp: Long,
    val averageVariableCostRp: Long,
    val breakEvenUnits: Long,
    val currentUnitsSold: Long,
    val netOperatingProfitRp: Long,
    val marginOfSafetyRp: Long,
    val breakEvenProgressBps: Long, // 10_000 bps = 100.00% of BEP target
    val isBreakEvenReached: Boolean
) {
    /**
     * Formats basis points (e.g., 5833L -> "58.33%") using pure integer math.
     */
    fun formatBpsAsPercent(bps: Long): String {
        val sign = if (bps < 0L) "-" else ""
        val absBps = kotlin.math.abs(bps)
        val whole = absBps / 100L
        val fraction = (absBps % 100L).toString().padStart(2, '0')
        return "$sign$whole.$fraction%"
    }
}

object BreakEvenCalculator {

    private const val BPS_SCALE = 10_000L

    /**
     * Pure Long-only Break-Even Point (BEP) and Contribution Margin calculator.
     * Never uses Double or Float for financial calculations.
     *
     * Formulas (in integer Long / Basis Points):
     * - Net Revenue = Gross Revenue - Discounts
     * - Contribution Margin (Rp) = Net Revenue - Total Variable COGS
     * - Contribution Margin Ratio (bps) = (Contribution Margin * 10,000) / Net Revenue
     * - Break-Even Revenue (Rp) = ceil((Fixed Operating Costs * 10,000) / Contribution Margin Ratio (bps))
     * - Break-Even Units = ceil(Fixed Operating Costs / Unit Contribution Margin)
     */
    fun calculate(
        grossRevenueRp: Long,
        discountTotalRp: Long,
        totalCogsRp: Long,
        fixedOperatingCostsRp: Long,
        currentUnitsSold: Long,
        fallbackAvgPriceRp: Long = 28_000L,
        fallbackAvgCostRp: Long = 11_000L
    ): BreakEvenAnalysisResult {
        val safeGross = grossRevenueRp.coerceAtLeast(0L)
        val safeDiscount = discountTotalRp.coerceIn(0L, safeGross)
        val netRevenueRp = (safeGross - safeDiscount).coerceAtLeast(0L)
        val safeCogs = totalCogsRp.coerceAtLeast(0L)
        val safeFixedCosts = fixedOperatingCostsRp.coerceAtLeast(0L)
        val safeUnits = currentUnitsSold.coerceAtLeast(0L)

        val contributionMarginRp = netRevenueRp - safeCogs

        val avgPriceRp = if (safeUnits > 0L && netRevenueRp > 0L) {
            netRevenueRp / safeUnits
        } else {
            fallbackAvgPriceRp.coerceAtLeast(1L)
        }

        val avgCostRp = if (safeUnits > 0L && safeCogs > 0L) {
            safeCogs / safeUnits
        } else {
            fallbackAvgCostRp.coerceAtLeast(0L)
        }

        // If no sales yet, derive theoretical CM ratio from catalog average price & cost
        val contributionMarginRatioBps: Long = when {
            netRevenueRp > 0L && contributionMarginRp > 0L ->
                (contributionMarginRp * BPS_SCALE) / netRevenueRp
            avgPriceRp > avgCostRp ->
                ((avgPriceRp - avgCostRp) * BPS_SCALE) / avgPriceRp
            else -> 0L
        }

        val breakEvenRevenueRp: Long = if (safeFixedCosts == 0L) {
            0L
        } else if (contributionMarginRatioBps > 0L) {
            // Ceiling integer division: (numerator + denominator - 1) / denominator
            val numerator = safeFixedCosts * BPS_SCALE
            (numerator + contributionMarginRatioBps - 1L) / contributionMarginRatioBps
        } else {
            safeFixedCosts
        }

        val unitContributionRp = (avgPriceRp - avgCostRp).coerceAtLeast(1L)
        val breakEvenUnits: Long = if (safeFixedCosts == 0L) {
            0L
        } else {
            (safeFixedCosts + unitContributionRp - 1L) / unitContributionRp
        }

        val netOperatingProfitRp = contributionMarginRp - safeFixedCosts
        val marginOfSafetyRp = netRevenueRp - breakEvenRevenueRp

        val breakEvenProgressBps: Long = when {
            breakEvenRevenueRp <= 0L -> BPS_SCALE
            netRevenueRp <= 0L -> 0L
            else -> ((netRevenueRp * BPS_SCALE) / breakEvenRevenueRp).coerceAtMost(20_000L)
        }

        val isBreakEvenReached = netRevenueRp >= breakEvenRevenueRp &&
            contributionMarginRp >= safeFixedCosts

        return BreakEvenAnalysisResult(
            grossRevenueRp = safeGross,
            discountTotalRp = safeDiscount,
            netRevenueRp = netRevenueRp,
            totalCogsRp = safeCogs,
            contributionMarginRp = contributionMarginRp,
            contributionMarginRatioBps = contributionMarginRatioBps,
            fixedOperatingCostsRp = safeFixedCosts,
            breakEvenRevenueRp = breakEvenRevenueRp,
            averageSellingPriceRp = avgPriceRp,
            averageVariableCostRp = avgCostRp,
            breakEvenUnits = breakEvenUnits,
            currentUnitsSold = safeUnits,
            netOperatingProfitRp = netOperatingProfitRp,
            marginOfSafetyRp = marginOfSafetyRp,
            breakEvenProgressBps = breakEvenProgressBps,
            isBreakEvenReached = isBreakEvenReached
        )
    }
}
