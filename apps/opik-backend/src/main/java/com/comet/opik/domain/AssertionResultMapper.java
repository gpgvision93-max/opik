package com.comet.opik.domain;

import com.comet.opik.api.AssertionResult;
import com.comet.opik.api.ExecutionPolicy;
import com.comet.opik.api.ExperimentItem;
import com.comet.opik.api.ExperimentRunSummary;
import com.comet.opik.api.RunStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@UtilityClass
@Slf4j
class AssertionResultMapper {

    private static final TypeReference<List<AssertionResultRow>> ASSERTION_LIST_TYPE = new TypeReference<>() {
    };

    record AssertionResultRow(String value, int passed, String reason) {
    }

    static ExperimentItem enrichWithAssertions(@NonNull ExperimentItem item, @Nullable String assertionsJson) {
        if (StringUtils.isBlank(assertionsJson)) {
            return item;
        }

        List<AssertionResultRow> rows;
        try {
            rows = com.comet.opik.utils.JsonUtils.getMapper().readValue(assertionsJson, ASSERTION_LIST_TYPE);
        } catch (Exception e) {
            log.warn("Failed to parse assertions_array JSON", e);
            return item;
        }

        if (CollectionUtils.isEmpty(rows)) {
            return item;
        }

        var assertionResults = rows.stream()
                .map(row -> AssertionResult.builder()
                        .value(row.value())
                        .passed(row.passed() >= 1)
                        .reason(row.reason())
                        .build())
                .toList();

        boolean allPassed = assertionResults.stream().allMatch(AssertionResult::passed);

        return item.toBuilder()
                .assertionResults(assertionResults)
                .status(allPassed ? RunStatus.PASSED : RunStatus.FAILED)
                .build();
    }

    /**
     * Legacy overload: partitions assertions from feedback scores by category_name.
     * Used by DatasetItemResultMapper which doesn't yet read from assertion_results table.
     */
    static ExperimentItem enrichWithAssertions(@NonNull ExperimentItem item) {
        var feedbackScores = item.feedbackScores();
        if (CollectionUtils.isEmpty(feedbackScores)) {
            return item;
        }

        var partitioned = feedbackScores.stream()
                .collect(Collectors.partitioningBy(
                        fs -> "suite_assertion".equals(fs.categoryName())));

        var assertions = partitioned.get(true);
        var regularScores = partitioned.get(false);

        if (CollectionUtils.isEmpty(assertions)) {
            return item;
        }

        var assertionResults = assertions.stream()
                .map(fs -> AssertionResult.builder()
                        .value(fs.name())
                        .passed(fs.value().compareTo(java.math.BigDecimal.ONE) >= 0)
                        .reason(fs.reason())
                        .build())
                .toList();

        boolean allPassed = assertionResults.stream().allMatch(AssertionResult::passed);

        return item.toBuilder()
                .feedbackScores(regularScores.isEmpty() ? null : regularScores)
                .assertionResults(assertionResults)
                .status(allPassed ? RunStatus.PASSED : RunStatus.FAILED)
                .build();
    }

    static Map<String, ExperimentRunSummary> computeRunSummaries(List<ExperimentItem> items) {
        if (CollectionUtils.isEmpty(items)) {
            return null;
        }

        var byExperiment = items.stream()
                .collect(Collectors.groupingBy(ExperimentItem::experimentId));

        Map<String, ExperimentRunSummary> summaries = new LinkedHashMap<>();

        for (var entry : byExperiment.entrySet()) {
            var group = entry.getValue();
            boolean hasAssertions = group.stream()
                    .anyMatch(i -> i.assertionResults() != null);

            if (!hasAssertions || group.size() <= 1) {
                continue;
            }

            long passedRuns = group.stream()
                    .filter(i -> RunStatus.PASSED.equals(i.status()))
                    .count();
            int totalRuns = group.size();

            int passThreshold = group.stream()
                    .map(ExperimentItem::executionPolicy)
                    .filter(ep -> ep != null)
                    .findFirst()
                    .map(ExecutionPolicy::passThreshold)
                    .orElse(1);

            RunStatus itemStatus = passedRuns >= passThreshold ? RunStatus.PASSED : RunStatus.FAILED;

            summaries.put(entry.getKey().toString(),
                    ExperimentRunSummary.builder()
                            .passedRuns((int) passedRuns)
                            .totalRuns(totalRuns)
                            .status(itemStatus)
                            .build());
        }

        return summaries.isEmpty() ? null : summaries;
    }
}
