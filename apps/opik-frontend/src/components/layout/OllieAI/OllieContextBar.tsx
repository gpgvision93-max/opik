import React from "react";
import { FileText, Table2, SlidersHorizontal } from "lucide-react";
import {
  HoverCard,
  HoverCardContent,
  HoverCardTrigger,
} from "@/components/ui/hover-card";
import { PAGE_ID, PAGE_SHORT_LABELS } from "@/constants/pageIds";
import { OllieTableState } from "@/store/OllieStore";

type OllieContextBarProps = {
  pageId: PAGE_ID;
  pageDescription: string;
  params: Record<string, string>;
  tableState: OllieTableState | null;
};

const PARAM_LABELS: Record<string, string> = {
  projectId: "Project",
  datasetId: "Dataset",
  promptId: "Prompt",
  traceId: "Trace",
  spanId: "Span",
  threadId: "Thread",
  dashboardId: "Dashboard",
  alertId: "Alert",
  annotationQueueId: "Queue",
  optimizationId: "Optimization",
};

function summarizeTableState(tableState: OllieTableState): string {
  const parts: string[] = [];

  if (tableState.filters) {
    try {
      const parsed = JSON.parse(tableState.filters);
      const count = Array.isArray(parsed) ? parsed.length : 1;
      if (count > 0) parts.push(`${count} filter${count > 1 ? "s" : ""}`);
    } catch {
      parts.push("filters");
    }
  }

  if (tableState.search) {
    parts.push(`search: "${tableState.search}"`);
  }

  if (tableState.sorting) {
    try {
      const parsed = JSON.parse(tableState.sorting);
      const col =
        Array.isArray(parsed) && parsed[0]?.field ? parsed[0].field : null;
      if (col) parts.push(`sorted by ${col}`);
    } catch {
      parts.push("sorted");
    }
  }

  return parts.length > 0 ? parts.join(", ") : "active";
}

function hasTableStateContent(tableState: OllieTableState): boolean {
  if (tableState.filters) {
    try {
      const parsed = JSON.parse(tableState.filters);
      if (Array.isArray(parsed) && parsed.length > 0) return true;
      if (!Array.isArray(parsed)) return true;
    } catch {
      return true;
    }
  }
  return !!(tableState.search || tableState.sorting || tableState.groups);
}

const ContextChip: React.FC<{
  icon?: React.ReactNode;
  label: string;
  hoverContent: React.ReactNode;
}> = ({ icon, label, hoverContent }) => (
  <HoverCard openDelay={200} closeDelay={100}>
    <HoverCardTrigger asChild>
      <div className="flex cursor-default items-center gap-1 rounded-full border border-border bg-muted/50 px-2 py-0.5 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground">
        {icon && <span className="shrink-0">{icon}</span>}
        <span className="comet-body-xs max-w-[120px] truncate">{label}</span>
      </div>
    </HoverCardTrigger>
    <HoverCardContent className="w-72" side="top" align="start" sideOffset={8}>
      {hoverContent}
    </HoverCardContent>
  </HoverCard>
);

const OllieContextBar: React.FC<OllieContextBarProps> = ({
  pageId,
  pageDescription,
  params,
  tableState,
}) => {
  const pageLabel = PAGE_SHORT_LABELS[pageId] ?? pageId;
  const entityParams = Object.entries(params).filter(([, v]) => v);
  const showTableState =
    tableState !== null && hasTableStateContent(tableState);

  return (
    <div className="border-t px-4 py-2">
      <div className="flex flex-wrap gap-1.5">
        <ContextChip
          icon={<FileText className="size-3" />}
          label={pageLabel}
          hoverContent={
            <div className="space-y-1.5">
              <p className="comet-body-s-accented text-foreground">
                Current page
              </p>
              <p className="comet-body-xs text-muted-foreground">
                {pageDescription}
              </p>
            </div>
          }
        />

        {entityParams.map(([key, value]) => {
          const humanLabel = PARAM_LABELS[key] ?? key;
          const displayValue =
            value.length > 12 ? `${value.slice(0, 12)}…` : value;
          return (
            <ContextChip
              key={key}
              label={`${humanLabel}: ${displayValue}`}
              hoverContent={
                <div className="space-y-1.5">
                  <p className="comet-body-s-accented text-foreground">
                    {humanLabel} ID
                  </p>
                  <p className="comet-body-xs break-all font-mono text-muted-foreground">
                    {value}
                  </p>
                </div>
              }
            />
          );
        })}

        {showTableState && (
          <ContextChip
            icon={<SlidersHorizontal className="size-3" />}
            label={`Table: ${summarizeTableState(tableState!)}`}
            hoverContent={
              <div className="space-y-2">
                <p className="comet-body-s-accented text-foreground">
                  Table state
                </p>
                <div className="space-y-1">
                  {tableState!.filters &&
                    (() => {
                      try {
                        const parsed = JSON.parse(tableState!.filters!);
                        const count = Array.isArray(parsed) ? parsed.length : 1;
                        return count > 0 ? (
                          <p className="comet-body-xs text-muted-foreground">
                            <span className="font-medium text-foreground">
                              Filters:
                            </span>{" "}
                            {count} active
                          </p>
                        ) : null;
                      } catch {
                        return null;
                      }
                    })()}
                  {tableState!.search && (
                    <p className="comet-body-xs text-muted-foreground">
                      <span className="font-medium text-foreground">
                        Search:
                      </span>{" "}
                      &ldquo;{tableState!.search}&rdquo;
                    </p>
                  )}
                  {tableState!.sorting &&
                    (() => {
                      try {
                        const parsed = JSON.parse(tableState!.sorting!);
                        if (Array.isArray(parsed) && parsed[0]?.field) {
                          return (
                            <p className="comet-body-xs text-muted-foreground">
                              <span className="font-medium text-foreground">
                                Sort:
                              </span>{" "}
                              {parsed[0].field}{" "}
                              {parsed[0].direction === "DESC"
                                ? "(descending)"
                                : "(ascending)"}
                            </p>
                          );
                        }
                      } catch {
                        /* ignore */
                      }
                      return null;
                    })()}
                  {tableState!.page !== undefined && (
                    <p className="comet-body-xs text-muted-foreground">
                      <span className="font-medium text-foreground">Page:</span>{" "}
                      {tableState!.page}
                      {tableState!.size !== undefined
                        ? `, ${tableState!.size} rows`
                        : ""}
                    </p>
                  )}
                </div>
              </div>
            }
          />
        )}

        {pageId === PAGE_ID.unknown && (
          <ContextChip
            icon={<Table2 className="size-3" />}
            label="No page context"
            hoverContent={
              <p className="comet-body-xs text-muted-foreground">
                OllieAI could not determine the current page. Navigate to a
                specific page for more targeted assistance.
              </p>
            }
          />
        )}
      </div>
    </div>
  );
};

export default OllieContextBar;
