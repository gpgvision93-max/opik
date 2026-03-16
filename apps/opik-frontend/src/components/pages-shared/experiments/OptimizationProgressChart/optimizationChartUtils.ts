/**
 * Utility functions for optimization chart data processing
 */

import { AggregatedCandidate } from "@/types/optimizations";
import { TagProps } from "@/components/ui/tag";

export type FeedbackScore = {
  name: string;
  value: number;
};

export type TrialStatus =
  | "baseline"
  | "passed"
  | "evaluating"
  | "pruned"
  | "running";

export const STATUS_VARIANT_MAP: Record<TrialStatus, TagProps["variant"]> = {
  baseline: "gray",
  passed: "blue",
  evaluating: "orange",
  pruned: "pink",
  running: "yellow",
};

export const TRIAL_STATUS_COLORS: Record<TrialStatus, string> = {
  baseline: "var(--color-gray)",
  passed: "var(--color-blue)",
  evaluating: "var(--color-orange)",
  pruned: "var(--color-pink)",
  running: "var(--color-yellow)",
};

export const TRIAL_STATUS_LABELS: Record<TrialStatus, string> = {
  baseline: "Baseline",
  passed: "Passed",
  evaluating: "Evaluating",
  pruned: "Pruned",
  running: "Running",
};

export const TRIAL_STATUS_ORDER: readonly TrialStatus[] = [
  "baseline",
  "passed",
  "evaluating",
  "pruned",
  "running",
] as const;

export type CandidateDataPoint = {
  candidateId: string;
  stepIndex: number;
  parentCandidateIds: string[];
  value: number | null;
  status: TrialStatus;
  name: string;
};

export type ParentChildEdge = {
  parentCandidateId: string;
  childCandidateId: string;
};

export type InProgressInfo = {
  candidateId: string;
  stepIndex: number;
  parentCandidateIds: string[];
};

/**
 * Compute status for each candidate.
 *
 * During optimization (isInProgress=true):
 *   baseline (step 0), running (no score), passed (has children),
 *   pruned (score < best AND sibling from same step has children),
 *   evaluating (scored but no children yet — awaiting optimizer decision)
 *
 * After completion (isInProgress=false):
 *   baseline (step 0), passed (has descendants OR is best),
 *   pruned (everything else)
 *
 * When !isEvaluationSuite: all scored non-baseline = "passed" (no pruning)
 */
export const computeCandidateStatuses = (
  candidates: AggregatedCandidate[],
  isEvaluationSuite = true,
  isInProgress = false,
  inProgressInfo?: InProgressInfo,
): Map<string, TrialStatus> => {
  const statusMap = new Map<string, TrialStatus>();
  if (!candidates.length) return statusMap;

  // Build lookup structures
  const hasChildren = new Set<string>();
  const stepSiblings = new Map<number, string[]>();
  let bestScore: number | undefined;

  for (const c of candidates) {
    for (const pid of c.parentCandidateIds) {
      hasChildren.add(pid);
    }
    const siblings = stepSiblings.get(c.stepIndex) ?? [];
    siblings.push(c.candidateId);
    stepSiblings.set(c.stepIndex, siblings);

    if (c.score != null) {
      if (bestScore == null || c.score > bestScore) {
        bestScore = c.score;
      }
    }
  }

  // Ghost candidate's parents also count as having children
  if (inProgressInfo) {
    for (const pid of inProgressInfo.parentCandidateIds) {
      hasChildren.add(pid);
    }
  }

  // Find the best candidate (highest score, earliest creation for ties)
  const bestCandidate = candidates.reduce<AggregatedCandidate | undefined>(
    (best, c) => {
      if (c.score == null) return best;
      if (!best || best.score == null) return c;
      if (c.score > best.score) return c;
      if (c.score === best.score && c.created_at < best.created_at) return c;
      return best;
    },
    undefined,
  );

  // For "after completion": build set of candidates with descendants (transitive)
  let hasDescendants: Set<string> | undefined;
  if (!isInProgress) {
    hasDescendants = new Set<string>();
    // Walk backwards: if a candidate has children, all its ancestors have descendants
    const parentOf = new Map<string, string[]>();
    for (const c of candidates) {
      for (const pid of c.parentCandidateIds) {
        const existing = parentOf.get(c.candidateId) ?? [];
        existing.push(pid);
        parentOf.set(c.candidateId, existing);
      }
    }
    // BFS from candidates that have direct children
    const queue = [...hasChildren];
    for (const id of queue) {
      if (hasDescendants.has(id)) continue;
      hasDescendants.add(id);
      const parents = parentOf.get(id);
      if (parents) queue.push(...parents);
    }
  }

  for (const c of candidates) {
    if (c.stepIndex === 0) {
      statusMap.set(c.candidateId, "baseline");
      continue;
    }

    if (!isEvaluationSuite) {
      statusMap.set(c.candidateId, c.score == null ? "running" : "passed");
      continue;
    }

    if (c.score == null) {
      statusMap.set(c.candidateId, "running");
      continue;
    }

    const isBest = bestCandidate?.candidateId === c.candidateId;

    if (isInProgress) {
      if (isBest || hasChildren.has(c.candidateId)) {
        statusMap.set(c.candidateId, "passed");
      } else if (bestScore != null && c.score < bestScore) {
        statusMap.set(c.candidateId, "pruned");
      } else {
        const siblings = stepSiblings.get(c.stepIndex) ?? [];
        const siblingHasChildren = siblings.some(
          (sid) => sid !== c.candidateId && hasChildren.has(sid),
        );
        statusMap.set(
          c.candidateId,
          siblingHasChildren ? "pruned" : "evaluating",
        );
      }
    } else {
      // After completion
      const isDescendant = hasDescendants?.has(c.candidateId) ?? false;
      const isBest = bestCandidate?.candidateId === c.candidateId;
      statusMap.set(
        c.candidateId,
        isDescendant || isBest ? "passed" : "pruned",
      );
    }
  }

  return statusMap;
};

/**
 * Build scatter data points from aggregated candidates.
 * Each candidate becomes one dot on the chart.
 */
export const buildCandidateChartData = (
  candidates: AggregatedCandidate[],
  isEvaluationSuite = true,
  isInProgress = false,
  inProgressInfo?: InProgressInfo,
): CandidateDataPoint[] => {
  const statusMap = computeCandidateStatuses(
    candidates,
    isEvaluationSuite,
    isInProgress,
    inProgressInfo,
  );

  return candidates
    .slice()
    .sort(
      (a, b) =>
        a.stepIndex - b.stepIndex || a.created_at.localeCompare(b.created_at),
    )
    .map((c) => ({
      candidateId: c.candidateId,
      stepIndex: c.stepIndex,
      parentCandidateIds: c.parentCandidateIds,
      value: c.score ?? null,
      status: statusMap.get(c.candidateId) ?? "pruned",
      name: c.name,
    }));
};

/**
 * Build parent-child edges from chart data.
 */
export const buildParentChildEdges = (
  data: CandidateDataPoint[],
): ParentChildEdge[] => {
  const candidateIds = new Set(data.map((d) => d.candidateId));
  const edges: ParentChildEdge[] = [];

  for (const point of data) {
    for (const parentId of point.parentCandidateIds) {
      if (candidateIds.has(parentId)) {
        edges.push({
          parentCandidateId: parentId,
          childCandidateId: point.candidateId,
        });
      }
    }
  }

  return edges;
};

/**
 * Get unique step indices from candidates, sorted.
 */
export const getUniqueSteps = (candidates: AggregatedCandidate[]): number[] => {
  const steps = new Set(candidates.map((c) => c.stepIndex));
  return Array.from(steps).sort((a, b) => a - b);
};

const MAIN_OBJECTIVE_COLOR = "var(--color-blue)";

const SECONDARY_SCORE_COLORS = [
  "var(--color-orange)",
  "var(--color-green)",
  "var(--color-purple)",
  "var(--color-pink)",
  "var(--color-turquoise)",
  "var(--color-yellow)",
  "var(--color-burgundy)",
];

export const generateDistinctColorMap = (
  mainObjective: string,
  secondaryScores: string[],
): Record<string, string> => {
  const colorMap: Record<string, string> = {};
  colorMap[mainObjective] = MAIN_OBJECTIVE_COLOR;

  const sortedSecondaryScores = [...secondaryScores].sort((a, b) =>
    a.localeCompare(b, undefined, { sensitivity: "base" }),
  );

  sortedSecondaryScores.forEach((scoreName, index) => {
    colorMap[scoreName] =
      SECONDARY_SCORE_COLORS[index % SECONDARY_SCORE_COLORS.length];
  });

  return colorMap;
};
