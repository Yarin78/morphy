import { useCallback, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import dagre from 'dagre';
import type {
  QueryPlanDebugInfo,
  QueryPlanDto,
  QueryOperatorNodeDto,
} from './api/types';
import './QueryPlanVisualiser.css';

const HEADER_H = 24;
const BODY_PAD_X = 12;
const BODY_PAD_Y = 6;
const LINE_H = 15;
const CHAR_W = 6.5;

function fmt(n: number): string {
  if (n >= 1e9) return (n / 1e9).toFixed(1) + 'B';
  if (n >= 1e6) return (n / 1e6).toFixed(1) + 'M';
  if (n >= 1e3) return (n / 1e3).toFixed(1) + 'K';
  return String(n);
}

interface NodeSize {
  w: number;
  h: number;
  lines: Array<{ text?: string; label?: string; value?: string }>;
}

function nodeLines(n: QueryOperatorNodeDto, hasAct: boolean): NodeSize['lines'] {
  const lines: NodeSize['lines'] = [];
  if (n.params) {
    const p = n.params.length > 50 ? n.params.substring(0, 47) + '...' : n.params;
    lines.push({ text: p });
  }
  const c = n.cost;
  lines.push({ label: 'Est rows', value: fmt(c.estimatedRows) });
  if (c.estimatedPageReads > 0) {
    lines.push({ label: 'Est pages', value: fmt(c.estimatedPageReads) });
  }
  if (hasAct && (c.actualRows ?? 0) > 0) {
    lines.push({ label: 'Act rows', value: fmt(c.actualRows!) });
  }
  return lines;
}

function badgeWidth(n: QueryOperatorNodeDto): number {
  const bw = (label: string) => label.length * 6 + 8 + 3;
  let w = 0;
  if (n.hasFullData) w += bw('DATA');
  if (n.sorted) w += bw('SORT');
  if (n.mayContainDuplicates) w += bw('DUP');
  return w;
}

function computeNodeSizes(
  nodes: QueryOperatorNodeDto[],
  hasAct: boolean
): Map<number, NodeSize> {
  const sizes = new Map<number, NodeSize>();
  for (const n of nodes) {
    const lines = nodeLines(n, hasAct);
    const badges = badgeWidth(n);
    const headerW = n.name.length * CHAR_W + BODY_PAD_X * 2 + badges + 8;
    let bodyW = 0;
    for (const l of lines) {
      const len = l.text ? l.text.length : (l.label?.length ?? 0) + (l.value?.length ?? 0) + 3;
      bodyW = Math.max(bodyW, len * CHAR_W + BODY_PAD_X * 2 + 20);
    }
    sizes.set(n.id, {
      w: Math.max(130, headerW, bodyW),
      h: HEADER_H + lines.length * LINE_H + BODY_PAD_Y * 2 + 4,
      lines,
    });
  }
  return sizes;
}

function formatSummary(tc: QueryPlanDto['totalCost']): string {
  const hasAct = (tc.actualRows ?? 0) > 0;
  let h = '';
  if (hasAct) {
    h += `<div class="qp-summary-group"><span class="qp-summary-label">Wall Clock</span><span class="qp-summary-value">${tc.actualWallClockTimeMs ?? 0} ms</span></div>`;
    h += `<div class="qp-summary-group"><span class="qp-summary-label">Actual Rows</span><span class="qp-summary-value">${fmt(tc.actualRows ?? 0)}</span></div>`;
    h += `<div class="qp-summary-group"><span class="qp-summary-label">Phys Reads</span><span class="qp-summary-value">${fmt(tc.actualPhysicalPageReads ?? 0)}</span></div>`;
    h += `<div class="qp-summary-group"><span class="qp-summary-label">Log Reads</span><span class="qp-summary-value">${fmt(tc.actualLogicalPageReads ?? 0)}</span></div>`;
    h += `<div class="qp-summary-group"><span class="qp-summary-label">Deserializations</span><span class="qp-summary-value">${fmt(tc.actualDeserializations ?? 0)}</span></div>`;
    h += '<div class="qp-summary-sep"></div>';
  }
  h += `<div class="qp-summary-group"><span class="qp-summary-label">Est Rows</span><span class="qp-summary-value">${fmt(tc.estimatedRows)}</span></div>`;
  h += `<div class="qp-summary-group"><span class="qp-summary-label">Est Page Reads</span><span class="qp-summary-value">${fmt(tc.estimatedPageReads)}</span></div>`;
  h += `<div class="qp-summary-group"><span class="qp-summary-label">Est Cost</span><span class="qp-summary-value">${Math.round(tc.estimatedTotalCost)}</span></div>`;
  return h;
}

function edgeWidth(rows: number): number {
  return Math.min(10, Math.floor(1.0 + Math.log10(Math.max(1, rows))));
}

function arrowLen(w: number): number {
  return Math.max(10, w * 2);
}

function arrowHalfW(w: number): number {
  return w * 0.7 + 1.5;
}

interface PlanGraphProps {
  plan: QueryPlanDto;
}

function escHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function NodeTooltip({
  node,
  x,
  y,
  tooltipRef,
}: {
  node: QueryOperatorNodeDto;
  x: number;
  y: number;
  tooltipRef: React.RefObject<HTMLDivElement | null>;
}) {
  const c = node.cost;
  const hasAct = (c.actualRows ?? 0) > 0;

  return (
    <div
      ref={tooltipRef}
      className="qp-tooltip"
      style={{
        left: x,
        top: y,
      }}
    >
      <div className="qp-tooltip-header">{escHtml(node.name)}</div>
      {node.params && (
        <div className="qp-tooltip-params">{escHtml(node.params)}</div>
      )}
      <div className="qp-tooltip-section">Properties</div>
      <div className="qp-tooltip-row">
        <span className="qp-tooltip-label">Full data</span>
        <span className="qp-tooltip-value">{node.hasFullData ? 'Yes' : 'No'}</span>
      </div>
      <div className="qp-tooltip-row">
        <span className="qp-tooltip-label">Sorted</span>
        <span className="qp-tooltip-value">
          {node.sorted ? (node.sortOrder || 'Yes') : 'No'}
        </span>
      </div>
      <div className="qp-tooltip-row">
        <span className="qp-tooltip-label">May duplicate</span>
        <span className="qp-tooltip-value">
          {node.mayContainDuplicates ? 'Yes' : 'No'}
        </span>
      </div>
      <div className="qp-tooltip-section">Estimates</div>
      <div className="qp-tooltip-row">
        <span className="qp-tooltip-label">Rows</span>
        <span className="qp-tooltip-value">
          {c.estimatedRows.toLocaleString()}
        </span>
      </div>
      <div className="qp-tooltip-row">
        <span className="qp-tooltip-label">Deserializations</span>
        <span className="qp-tooltip-value">
          {c.estimatedDeserializations.toLocaleString()}
        </span>
      </div>
      <div className="qp-tooltip-row">
        <span className="qp-tooltip-label">Page reads</span>
        <span className="qp-tooltip-value">
          {c.estimatedPageReads.toLocaleString()}
        </span>
      </div>
      {hasAct && (
        <>
          <div className="qp-tooltip-section">
            Actual{c.actualIsDuplicate ? ' (shared metrics)' : ''}
          </div>
          <div className="qp-tooltip-row">
            <span className="qp-tooltip-label">Rows</span>
            <span className="qp-tooltip-value">
              {(c.actualRows ?? 0).toLocaleString()}
            </span>
          </div>
          <div className="qp-tooltip-row">
            <span className="qp-tooltip-label">Deserializations</span>
            <span className="qp-tooltip-value">
              {(c.actualDeserializations ?? 0).toLocaleString()}
            </span>
          </div>
          <div className="qp-tooltip-row">
            <span className="qp-tooltip-label">Physical reads</span>
            <span className="qp-tooltip-value">
              {(c.actualPhysicalPageReads ?? 0).toLocaleString()}
            </span>
          </div>
          <div className="qp-tooltip-row">
            <span className="qp-tooltip-label">Logical reads</span>
            <span className="qp-tooltip-value">
              {(c.actualLogicalPageReads ?? 0).toLocaleString()}
            </span>
          </div>
        </>
      )}
    </div>
  );
}

function PlanGraph({ plan }: PlanGraphProps) {
  const { nodes, edges, totalCost } = plan;
  const hasAct = (totalCost.actualRows ?? 0) > 0;
  const [tooltip, setTooltip] = useState<{
    node: QueryOperatorNodeDto;
    x: number;
    y: number;
  } | null>(null);
  const tooltipRef = useRef<HTMLDivElement | null>(null);

  const handleNodeMouseEnter = useCallback(
    (node: QueryOperatorNodeDto, ev: React.MouseEvent) => {
      const pad = 14;
      let x = ev.clientX + pad;
      let y = ev.clientY + pad;
      setTooltip({ node, x, y });
    },
    []
  );

  const handleNodeMouseMove = useCallback((ev: React.MouseEvent) => {
    if (!tooltipRef.current) return;
    const pad = 14;
    let left = ev.clientX + pad;
    let top = ev.clientY + pad;
    const tw = tooltipRef.current.offsetWidth;
    const th = tooltipRef.current.offsetHeight;
    if (left + tw > window.innerWidth - 8) left = ev.clientX - tw - pad;
    if (top + th > window.innerHeight - 8) top = ev.clientY - th - pad;
    tooltipRef.current.style.left = left + 'px';
    tooltipRef.current.style.top = top + 'px';
  }, []);

  const handleNodeMouseLeave = useCallback(() => {
    setTooltip(null);
  }, []);

  const { graph, nodeSizes, allEdgeRows } = useMemo(() => {
    const nodeSizes = computeNodeSizes(nodes, hasAct);
    const g = new dagre.graphlib.Graph();
    g.setGraph({
      rankdir: 'LR',
      nodesep: 30,
      ranksep: 70,
      marginx: 30,
      marginy: 30,
    });
    g.setDefaultEdgeLabel(() => ({}));
    for (const n of nodes) {
      const sz = nodeSizes.get(n.id)!;
      g.setNode(String(n.id), { width: sz.w, height: sz.h });
    }
    for (const e of edges) {
      g.setEdge(String(e.from), String(e.to));
    }
    dagre.layout(g);

    const allEdgeRows = edges.map((e) => {
      const src = nodes.find((n) => n.id === e.from)!;
      const c = src.cost;
      return Math.max(
        1,
        hasAct && (c.actualRows ?? 0) > 0 ? (c.actualRows ?? 0) : c.estimatedRows
      );
    });

    return { graph: g, nodeSizes, allEdgeRows };
  }, [nodes, edges, totalCost, hasAct]);

  const gd = graph.graph();
  const width = gd.width ?? 400;
  const height = gd.height ?? 200;

  function shortenPath(
    pts: Array<{ x: number; y: number }>,
    amount: number
  ): Array<{ x: number; y: number }> {
    const last = pts[pts.length - 1];
    const prev = pts[pts.length - 2];
    const dx = last.x - prev.x;
    const dy = last.y - prev.y;
    const len = Math.hypot(dx, dy);
    if (len < amount) return pts;
    const r = (len - amount) / len;
    return [...pts.slice(0, -1), { x: prev.x + dx * r, y: prev.y + dy * r }];
  }

  return (
    <div className="qp-plan-graph">
      <div
        className="qp-plan-summary"
        dangerouslySetInnerHTML={{ __html: formatSummary(totalCost) }}
      />
      <div className="qp-graph-container">
        <svg width={width} height={height} className="qp-svg">
          {/* Edges */}
          {edges.map((e, i) => {
            const ed = graph.edge(String(e.from), String(e.to));
            if (!ed?.points?.length) return null;
            const rows = allEdgeRows[i];
            const w = edgeWidth(rows);
            const shortened = shortenPath(ed.points, arrowLen(w));
            let d =
              'M' +
              shortened[0].x.toFixed(1) +
              ',' +
              shortened[0].y.toFixed(1);
            for (let j = 1; j < shortened.length; j++) {
              d +=
                ' L' +
                shortened[j].x.toFixed(1) +
                ',' +
                shortened[j].y.toFixed(1);
            }
            const tip = ed.points[ed.points.length - 1];
            const prev =
              ed.points.length >= 2 ? ed.points[ed.points.length - 2] : ed.points[0];
            const dx = tip.x - prev.x;
            const dy = tip.y - prev.y;
            const len = Math.hypot(dx, dy);
            const ux = len === 0 ? 0 : dx / len;
            const uy = len === 0 ? 0 : dy / len;
            const px = -uy;
            const py = ux;
            const al = arrowLen(w);
            const ah = arrowHalfW(w);
            const bx = tip.x - ux * al;
            const by = tip.y - uy * al;
            const arrowPoints = [
              tip.x,
              tip.y,
              bx + px * ah,
              by + py * ah,
              bx - px * ah,
              by - py * ah,
            ].join(' ');
            const mid = ed.points[Math.floor(ed.points.length / 2)];

            return (
              <g key={`edge-${e.from}-${e.to}`} className="qp-edge">
                <path d={d} strokeWidth={w} />
                <polygon points={arrowPoints} fill="#b0b8c4" />
                <text
                  x={mid.x}
                  y={mid.y - w / 2 - 4}
                  textAnchor="middle"
                  className="qp-edge-label"
                >
                  {fmt(rows)}
                </text>
              </g>
            );
          })}
          {/* Nodes */}
          {nodes.map((n) => {
            const lay = graph.node(String(n.id));
            const sz = nodeSizes.get(n.id)!;
            const x = lay.x - sz.w / 2;
            const y = lay.y - sz.h / 2;

            return (
              <g
                key={n.id}
                className={`qp-node qp-node-${n.type}`}
                transform={`translate(${x},${y})`}
                onMouseEnter={(ev) => handleNodeMouseEnter(n, ev)}
                onMouseMove={handleNodeMouseMove}
                onMouseLeave={handleNodeMouseLeave}
              >
                <rect
                  className="qp-node-bg"
                  width={sz.w}
                  height={sz.h}
                  rx={6}
                  ry={6}
                />
                <rect
                  className="qp-node-header-bg"
                  width={sz.w}
                  height={HEADER_H}
                  rx={6}
                  ry={6}
                />
                <rect
                  className="qp-node-header-bg"
                  y={HEADER_H - 6}
                  width={sz.w}
                  height={6}
                />
                <text x={BODY_PAD_X} y={16} className="qp-node-header-text">
                  {n.name}
                </text>
                {(() => {
                  let bx = sz.w - BODY_PAD_X;
                  const badges: string[] = [];
                  if (n.hasFullData) badges.push('DATA');
                  if (n.sorted) badges.push('SORT');
                  if (n.mayContainDuplicates) badges.push('DUP');
                  return (
                    <>
                      {badges.map((label) => {
                        const tw = label.length * 6 + 8;
                        bx -= tw + 3;
                        return (
                          <g key={label}>
                            <rect
                              x={bx}
                              y={5}
                              width={tw}
                              height={14}
                              rx={3}
                              fill="rgba(255,255,255,0.25)"
                            />
                            <text
                              x={bx + tw / 2}
                              y={15}
                              textAnchor="middle"
                              fill="#fff"
                              fontSize="8.5"
                              fontWeight="600"
                            >
                              {label}
                            </text>
                          </g>
                        );
                      })}
                    </>
                  );
                })()}
                {sz.lines.map((line, idx) => {
                  const ly =
                    HEADER_H + BODY_PAD_Y + LINE_H - 2 + idx * LINE_H;
                  if (line.text) {
                    return (
                      <text
                        key={idx}
                        x={BODY_PAD_X}
                        y={ly}
                        className="qp-node-params"
                      >
                        {line.text}
                      </text>
                    );
                  }
                  return (
                    <g key={idx}>
                      <text
                        x={BODY_PAD_X}
                        y={ly}
                        className="qp-node-metric-label"
                      >
                        {line.label}
                      </text>
                      <text
                        x={sz.w - BODY_PAD_X}
                        y={ly}
                        textAnchor="end"
                        className="qp-node-metric-value"
                      >
                        {line.value}
                      </text>
                    </g>
                  );
                })}
              </g>
            );
          })}
        </svg>
      </div>
      {tooltip &&
        createPortal(
          <NodeTooltip
            node={tooltip.node}
            x={tooltip.x}
            y={tooltip.y}
            tooltipRef={tooltipRef}
          />,
          document.body
        )}
    </div>
  );
}

interface QueryPlanVisualiserProps {
  debugInfo: QueryPlanDebugInfo;
}

export function QueryPlanVisualiser({ debugInfo }: QueryPlanVisualiserProps) {
  const [activePlanIdx, setActivePlanIdx] = useState(0);
  const { queryDescription, plans, allPlansAgree } = debugInfo;

  return (
    <div className="qp-visualiser">
      {queryDescription && (
        <div className="qp-query-section">
          <div className="qp-section-title">Logical Query</div>
          <pre className="qp-query-pre">{queryDescription}</pre>
        </div>
      )}
      {plans.length > 1 && (
        <div className="qp-tab-bar">
          {plans.map((plan, idx) => {
            const costStr =
              (plan.totalCost.actualWallClockTimeMs ?? 0) > 0
                ? `${plan.totalCost.actualWallClockTimeMs} ms`
                : `est ${Math.round(plan.totalCost.estimatedTotalCost)}`;
            const differs = plan.resultsDifferFromSelected;
            return (
              <button
                key={idx}
                type="button"
                className={`qp-tab ${activePlanIdx === idx ? 'active' : ''}`}
                onClick={() => setActivePlanIdx(idx)}
              >
                {plan.label}
                <span className="qp-tab-cost">{costStr}</span>
                {plan.executed && <span className="qp-tab-badge">executed</span>}
                {differs && (
                  <span className="qp-tab-badge qp-tab-badge-warn">
                    differs
                  </span>
                )}
              </button>
            );
          })}
        </div>
      )}
      {allPlansAgree === false && (
        <div className="qp-plans-disagree">
          ⚠ Plans produced different result sets
        </div>
      )}
      <div className="qp-plans-container">
        {plans.map((plan, idx) => (
          <div
            key={idx}
            className={`qp-plan-content ${activePlanIdx === idx ? 'active' : ''}`}
          >
            <PlanGraph plan={plan} />
          </div>
        ))}
      </div>
    </div>
  );
}
