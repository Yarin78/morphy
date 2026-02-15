package se.yarin.morphy.queries.operations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Generates a self-contained HTML page visualizing a query execution plan as a left-to-right DAG.
 * Each QueryOperator becomes a box; arrows show data flow between operators. Estimated and actual
 * costs are displayed on hover.
 */
public class QueryPlanVisualizer {

  public static void writeHtmlFile(QueryOperator<?> root, Path outputPath) throws IOException {
    Files.writeString(outputPath, generateHtml(root));
  }

  public static String generateHtml(QueryOperator<?> root) {
    List<Map<String, Object>> nodes = new ArrayList<>();
    List<int[]> edges = new ArrayList<>();
    Map<QueryOperator<?>, Integer> visited = new IdentityHashMap<>();

    collectGraph(root, nodes, edges, visited);

    QueryCost totalCost = root.getQueryCost();

    return HTML_TEMPLATE
        .replace("/*{{NODES}}*/", toNodesJson(nodes))
        .replace("/*{{EDGES}}*/", toEdgesJson(edges))
        .replace("/*{{TOTAL_COST}}*/", toTotalCostJson(totalCost));
  }

  private static void collectGraph(
      QueryOperator<?> op,
      List<Map<String, Object>> nodes,
      List<int[]> edges,
      Map<QueryOperator<?>, Integer> visited) {
    if (visited.containsKey(op)) return;

    int id = nodes.size();
    visited.put(op, id);

    OperatorCost cost = op.getOperatorCost();
    String fullLabel = op.toString();
    String name;
    String params = "";
    int parenIdx = fullLabel.indexOf('(');
    if (parenIdx >= 0 && fullLabel.endsWith(")")) {
      name = fullLabel.substring(0, parenIdx);
      params = fullLabel.substring(parenIdx + 1, fullLabel.length() - 1);
    } else {
      name = fullLabel;
    }

    Map<String, Object> nd = new LinkedHashMap<>();
    nd.put("id", id);
    nd.put("name", name);
    nd.put("params", params);
    nd.put("type", categorize(op));
    nd.put("hasFullData", op.hasFullData());
    nd.put("sorted", !op.sortOrder().isNone());
    nd.put("sortOrder", op.sortOrder().toString());
    nd.put("mayDuplicate", op.mayContainDuplicates());
    nd.put("estRows", cost.estimateRows());
    nd.put("estDeser", cost.estimateDeserializations());
    nd.put("estPageReads", cost.estimatePageReads());
    nd.put("actRows", cost.actualRows());
    nd.put("actDeser", cost.actualDeserializations());
    nd.put("actPhysReads", cost.actualPhysicalPageReads());
    nd.put("actLogReads", cost.actualLogicalPageReads());
    nd.put("actDuplicate", cost.actualIsDuplicate());
    nodes.add(nd);

    // Recurse into sources first, then add edges (source → this)
    for (QueryOperator<?> source : op.sources()) {
      collectGraph(source, nodes, edges, visited);
      edges.add(new int[] {visited.get(source), id});
    }
  }

  private static String categorize(QueryOperator<?> op) {
    String cls = op.getClass().getSimpleName();
    if (cls.contains("Scan") || cls.equals("Manual")) return "source";
    if (cls.contains("Lookup")) return "lookup";
    if (cls.contains("Join") || cls.contains("IdsByEntities") || cls.contains("IdsByGames"))
      return "join";
    return "utility";
  }

  // --- JSON serialization helpers (no external library needed) ---

  private static String toNodesJson(List<Map<String, Object>> nodes) {
    StringBuilder sb = new StringBuilder("[\n");
    for (int i = 0; i < nodes.size(); i++) {
      if (i > 0) sb.append(",\n");
      sb.append("  ").append(mapToJson(nodes.get(i)));
    }
    sb.append("\n]");
    return sb.toString();
  }

  private static String toEdgesJson(List<int[]> edges) {
    StringBuilder sb = new StringBuilder("[\n");
    for (int i = 0; i < edges.size(); i++) {
      if (i > 0) sb.append(",\n");
      sb.append(String.format("  {\"from\":%d,\"to\":%d}", edges.get(i)[0], edges.get(i)[1]));
    }
    sb.append("\n]");
    return sb.toString();
  }

  private static String toTotalCostJson(QueryCost c) {
    return String.format(
        Locale.US,
        "{\"estRows\":%d,\"estPageReads\":%d,\"estDeser\":%d,"
            + "\"estCpuCost\":%.1f,\"estIOCost\":%.1f,\"estTotalCost\":%.1f,"
            + "\"actRows\":%d,\"actPhysReads\":%d,\"actLogReads\":%d,"
            + "\"actDeser\":%d,\"actWallClock\":%d}",
        c.estimatedRows(),
        c.estimatedPageReads(),
        c.estimatedDeserializations(),
        c.estimatedCpuCost(),
        c.estimatedIOCost(),
        c.estimatedTotalCost(),
        c.actualRows(),
        c.actualPhysicalPageReads(),
        c.actualLogicalPageReads(),
        c.actualDeserializations(),
        c.actualWallClockTime());
  }

  private static String mapToJson(Map<String, Object> map) {
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (var entry : map.entrySet()) {
      if (!first) sb.append(",");
      first = false;
      sb.append("\"").append(entry.getKey()).append("\":");
      Object v = entry.getValue();
      if (v instanceof String s) {
        sb.append("\"").append(escapeJson(s)).append("\"");
      } else if (v instanceof Boolean b) {
        sb.append(b);
      } else {
        sb.append(v);
      }
    }
    sb.append("}");
    return sb.toString();
  }

  private static String escapeJson(String s) {
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  // The full HTML template is a self-contained page using dagre (CDN) for layout.
  private static final String HTML_TEMPLATE =
      """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Query Execution Plan</title>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
       background: #f5f6f8; color: #333; }
#header { background: #1a1a2e; color: #fff; padding: 14px 24px;
          display: flex; align-items: center; gap: 16px; }
#header h1 { font-size: 18px; font-weight: 600; }
.legend { display: flex; gap: 14px; align-items: center; margin-left: auto; font-size: 12px; color: #ccc; }
.legend-item { display: flex; align-items: center; gap: 5px; }
.legend-swatch { width: 14px; height: 14px; border-radius: 3px; border: 1.5px solid; }
#summary { background: #fff; border-bottom: 1px solid #e0e0e0; padding: 12px 24px;
           display: flex; gap: 32px; flex-wrap: wrap; font-size: 13px; }
.summary-group { display: flex; flex-direction: column; gap: 2px; }
.summary-label { color: #888; font-size: 11px; text-transform: uppercase; letter-spacing: 0.5px; }
.summary-value { font-weight: 600; font-size: 15px; }
.summary-sep { border-left: 1px solid #ddd; margin: 0 8px; }
#graph-container { overflow: auto; padding: 24px; }
svg text { font-family: inherit; }
.node rect.bg { stroke-width: 1.5; cursor: pointer; transition: filter 0.15s; }
.node:hover rect.bg { filter: brightness(0.94); }
.node rect.header-bg { stroke: none; }
.node-source rect.bg      { fill: #f0f7ff; stroke: #4a90d9; }
.node-source rect.header-bg { fill: #4a90d9; }
.node-lookup rect.bg      { fill: #f0faf0; stroke: #3da33d; }
.node-lookup rect.header-bg { fill: #3da33d; }
.node-join rect.bg        { fill: #fffcf0; stroke: #c9a000; }
.node-join rect.header-bg { fill: #d4a800; }
.node-utility rect.bg     { fill: #f4f4f7; stroke: #8892a0; }
.node-utility rect.header-bg { fill: #8892a0; }
.node-header-text { font-weight: 600; font-size: 12px; fill: #fff; }
.node-params { font-size: 11px; fill: #555; }
.node-metric-label { font-size: 10.5px; fill: #888; }
.node-metric-value { font-size: 10.5px; fill: #333; font-weight: 500; }
.node-badge rect { rx: 3; }
.node-badge text { font-size: 8.5px; font-weight: 700; }
.edge path { fill: none; stroke: #b0b8c4; stroke-linecap: round; stroke-linejoin: round; }
.edge polygon { fill: #b0b8c4; }
.edge-label { font-size: 10px; fill: #999; }
#tooltip { position: fixed; display: none; background: #1a1a2e; color: #ddd;
           padding: 14px 18px; border-radius: 8px; font-size: 12px; line-height: 1.6;
           max-width: 380px; z-index: 1000; pointer-events: none;
           box-shadow: 0 6px 24px rgba(0,0,0,0.35); }
.tt-header { font-weight: 600; font-size: 14px; color: #fff;
             margin-bottom: 6px; border-bottom: 1px solid #333; padding-bottom: 5px; }
.tt-params { color: #aaa; margin-bottom: 8px; word-break: break-all; font-size: 11.5px; }
.tt-section { margin-top: 8px; font-weight: 600; color: #7eb8da; font-size: 10.5px;
              text-transform: uppercase; letter-spacing: 0.4px; }
.tt-row { display: flex; justify-content: space-between; gap: 20px; }
.tt-label { color: #aaa; }
.tt-value { font-weight: 500; color: #fff; }
</style>
</head>
<body>
<div id="header">
  <h1>Query Execution Plan</h1>
  <div class="legend">
    <div class="legend-item"><div class="legend-swatch" style="background:#f0f7ff;border-color:#4a90d9"></div>Source</div>
    <div class="legend-item"><div class="legend-swatch" style="background:#f0faf0;border-color:#3da33d"></div>Lookup</div>
    <div class="legend-item"><div class="legend-swatch" style="background:#fffcf0;border-color:#c9a000"></div>Join</div>
    <div class="legend-item"><div class="legend-swatch" style="background:#f4f4f7;border-color:#8892a0"></div>Utility</div>
  </div>
</div>
<div id="summary"></div>
<div id="graph-container"><svg id="graph"></svg></div>
<div id="tooltip"></div>

<script src="https://cdn.jsdelivr.net/npm/dagre@0.8.5/dist/dagre.min.js"></script>
<script>
const nodes = /*{{NODES}}*/;
const edges = /*{{EDGES}}*/;
const totalCost = /*{{TOTAL_COST}}*/;

// ---- Helpers ----
function fmt(n) {
  if (n >= 1e9) return (n / 1e9).toFixed(1) + 'B';
  if (n >= 1e6) return (n / 1e6).toFixed(1) + 'M';
  if (n >= 1e3) return (n / 1e3).toFixed(1) + 'K';
  return String(n);
}
function svgEl(tag, attrs) {
  const el = document.createElementNS('http://www.w3.org/2000/svg', tag);
  for (const [k, v] of Object.entries(attrs || {})) el.setAttribute(k, v);
  return el;
}

// ---- Summary panel ----
const hasActual = totalCost.actRows > 0;
let sh = '';
if (hasActual) {
  sh += '<div class="summary-group"><span class="summary-label">Wall Clock</span><span class="summary-value">' + totalCost.actWallClock + ' ms</span></div>';
  sh += '<div class="summary-group"><span class="summary-label">Actual Rows</span><span class="summary-value">' + fmt(totalCost.actRows) + '</span></div>';
  sh += '<div class="summary-group"><span class="summary-label">Phys Reads</span><span class="summary-value">' + fmt(totalCost.actPhysReads) + '</span></div>';
  sh += '<div class="summary-group"><span class="summary-label">Log Reads</span><span class="summary-value">' + fmt(totalCost.actLogReads) + '</span></div>';
  sh += '<div class="summary-group"><span class="summary-label">Deserializations</span><span class="summary-value">' + fmt(totalCost.actDeser) + '</span></div>';
  sh += '<div class="summary-sep"></div>';
}
sh += '<div class="summary-group"><span class="summary-label">Est Rows</span><span class="summary-value">' + fmt(totalCost.estRows) + '</span></div>';
sh += '<div class="summary-group"><span class="summary-label">Est Page Reads</span><span class="summary-value">' + fmt(totalCost.estPageReads) + '</span></div>';
sh += '<div class="summary-group"><span class="summary-label">Est Cost</span><span class="summary-value">' + Math.round(totalCost.estTotalCost) + '</span></div>';
document.getElementById('summary').innerHTML = sh;

// ---- Compute layout with dagre ----
const HEADER_H = 24;
const BODY_PAD_X = 12;
const BODY_PAD_Y = 6;
const LINE_H = 15;
const CHAR_W = 6.5;

function nodeLines(n) {
  const lines = [];
  if (n.params) {
    const p = n.params.length > 50 ? n.params.substring(0, 47) + '...' : n.params;
    lines.push({ text: p, cls: 'params' });
  }
  lines.push({ label: 'Est rows', value: fmt(n.estRows) });
  if (n.estPageReads > 0) lines.push({ label: 'Est pages', value: fmt(n.estPageReads) });
  if (hasActual && n.actRows > 0) {
    lines.push({ label: 'Act rows', value: fmt(n.actRows) });
  }
  return lines;
}

function badgeWidth(n) {
  let w = 0;
  const bw = label => label.length * 6 + 8 + 3;
  if (n.hasFullData) w += bw('DATA');
  if (n.sorted) w += bw('SORT');
  if (n.mayDuplicate) w += bw('DUP');
  return w;
}

const nodeSizes = {};
nodes.forEach(n => {
  const lines = nodeLines(n);
  const badges = badgeWidth(n);
  const headerW = n.name.length * CHAR_W + BODY_PAD_X * 2 + badges + 8;
  let bodyW = 0;
  lines.forEach(l => {
    const len = l.text ? l.text.length : (l.label.length + l.value.length + 3);
    bodyW = Math.max(bodyW, len * CHAR_W + BODY_PAD_X * 2 + 20);
  });
  const w = Math.max(130, headerW, bodyW);
  const h = HEADER_H + lines.length * LINE_H + BODY_PAD_Y * 2 + 4;
  nodeSizes[n.id] = { w, h, lines };
});

const g = new dagre.graphlib.Graph();
g.setGraph({ rankdir: 'LR', nodesep: 30, ranksep: 70, marginx: 30, marginy: 30 });
g.setDefaultEdgeLabel(() => ({}));
nodes.forEach(n => g.setNode(String(n.id), { width: nodeSizes[n.id].w, height: nodeSizes[n.id].h }));
edges.forEach(e => g.setEdge(String(e.from), String(e.to)));
dagre.layout(g);

// ---- Render ----
const svg = document.getElementById('graph');
const gd = g.graph();
svg.setAttribute('width', gd.width);
svg.setAttribute('height', gd.height);

// Edge width: absolute logarithmic scale (1 row -> 1.5px, ~1K rows -> 4px, capped at 10px)
const allEdgeRows = edges.map(e => {
  const src = nodes.find(n => n.id === e.from);
  return Math.max(1, hasActual && src.actRows > 0 ? src.actRows : src.estRows);
});
function edgeWidth(rows) {
  return Math.min(10, Math.floor(1.0 + Math.log10(Math.max(1, rows))));
}

// Arrowhead geometry helpers
function arrowLen(w) { return Math.max(10, w * 2); }
function arrowHalfW(w) { return w * 0.7 + 1.5; }

function shortenPath(pts, amount) {
  const last = pts[pts.length - 1], prev = pts[pts.length - 2];
  const dx = last.x - prev.x, dy = last.y - prev.y;
  const len = Math.hypot(dx, dy);
  if (len < amount) return pts;
  const r = (len - amount) / len;
  return [...pts.slice(0, -1), { x: prev.x + dx * r, y: prev.y + dy * r }];
}

function drawArrowhead(grp, tip, prev, w) {
  const dx = tip.x - prev.x, dy = tip.y - prev.y;
  const len = Math.hypot(dx, dy);
  if (len === 0) return;
  const ux = dx / len, uy = dy / len;
  const px = -uy, py = ux;
  const al = arrowLen(w), ah = arrowHalfW(w);
  const bx = tip.x - ux * al, by = tip.y - uy * al;
  grp.appendChild(svgEl('polygon', {
    points: [tip.x,tip.y, bx+px*ah,by+py*ah, bx-px*ah,by-py*ah].join(' '),
    fill: '#b0b8c4'
  }));
}

// Edges
edges.forEach((e, i) => {
  const ed = g.edge(String(e.from), String(e.to));
  const grp = svgEl('g', { class: 'edge' });
  const rows = allEdgeRows[i];
  const w = edgeWidth(rows);

  // Shorten path so the line stops before the target box, leaving room for the arrowhead
  const shortened = shortenPath(ed.points, arrowLen(w));
  let d = 'M' + shortened[0].x.toFixed(1) + ',' + shortened[0].y.toFixed(1);
  for (let j = 1; j < shortened.length; j++) {
    d += ' L' + shortened[j].x.toFixed(1) + ',' + shortened[j].y.toFixed(1);
  }
  grp.appendChild(svgEl('path', { d, 'stroke-width': w.toFixed(2) }));

  // Draw arrowhead polygon at the original (un-shortened) end point
  const tip = ed.points[ed.points.length - 1];
  const prev = ed.points.length >= 2 ? ed.points[ed.points.length - 2] : ed.points[0];
  drawArrowhead(grp, tip, prev, w);

  // Edge label: actual rows (or estimated if no actuals)
  const mid = ed.points[Math.floor(ed.points.length / 2)];
  const lbl = svgEl('text', { x: mid.x, y: mid.y - w / 2 - 4, 'text-anchor': 'middle', class: 'edge-label' });
  lbl.textContent = fmt(rows);
  grp.appendChild(lbl);

  svg.appendChild(grp);
});

// Nodes
nodes.forEach(n => {
  const lay = g.node(String(n.id));
  const sz = nodeSizes[n.id];
  const x = lay.x - sz.w / 2, y = lay.y - sz.h / 2;
  const grp = svgEl('g', { class: 'node node-' + n.type });

  // Background rect
  grp.appendChild(svgEl('rect', { class: 'bg', x, y, width: sz.w, height: sz.h, rx: 6, ry: 6 }));

  // Header bar
  grp.appendChild(svgEl('rect', {
    class: 'header-bg', x, y, width: sz.w, height: HEADER_H,
    rx: 6, ry: 6
  }));
  // Cover bottom rounding of header
  grp.appendChild(svgEl('rect', {
    class: 'header-bg', x, y: y + HEADER_H - 6, width: sz.w, height: 6
  }));

  // Header text
  const ht = svgEl('text', { x: x + BODY_PAD_X, y: y + 16, class: 'node-header-text' });
  ht.textContent = n.name;
  grp.appendChild(ht);

  // Badges in header (right side)
  let bx = x + sz.w - BODY_PAD_X;
  function addBadge(label, color) {
    const tw = label.length * 6 + 8;
    bx -= tw + 3;
    grp.appendChild(svgEl('rect', {
      x: bx, y: y + 5, width: tw, height: 14, rx: 3,
      fill: 'rgba(255,255,255,0.25)', stroke: 'none', class: ''
    }));
    const bt = svgEl('text', {
      x: bx + tw / 2, y: y + 15, 'text-anchor': 'middle',
      fill: '#fff', 'font-size': '8.5', 'font-weight': '600'
    });
    bt.textContent = label;
    grp.appendChild(bt);
  }
  if (n.hasFullData) addBadge('DATA', '#fff');
  if (n.sorted) addBadge('SORT', '#fff');
  if (n.mayDuplicate) addBadge('DUP', '#fff');

  // Body lines
  let ly = y + HEADER_H + BODY_PAD_Y + LINE_H - 2;
  sz.lines.forEach(line => {
    if (line.text) {
      const t = svgEl('text', { x: x + BODY_PAD_X, y: ly, class: 'node-params' });
      t.textContent = line.text;
      grp.appendChild(t);
    } else {
      const tl = svgEl('text', { x: x + BODY_PAD_X, y: ly, class: 'node-metric-label' });
      tl.textContent = line.label;
      grp.appendChild(tl);
      const tv = svgEl('text', { x: x + sz.w - BODY_PAD_X, y: ly, 'text-anchor': 'end', class: 'node-metric-value' });
      tv.textContent = line.value;
      grp.appendChild(tv);
    }
    ly += LINE_H;
  });

  // Tooltip events
  grp.addEventListener('mouseenter', ev => showTooltip(ev, n));
  grp.addEventListener('mousemove', ev => moveTooltip(ev));
  grp.addEventListener('mouseleave', hideTooltip);
  svg.appendChild(grp);
});

// ---- Tooltip ----
const tooltip = document.getElementById('tooltip');
function showTooltip(ev, n) {
  let h = '<div class="tt-header">' + n.name + '</div>';
  if (n.params) h += '<div class="tt-params">' + escHtml(n.params) + '</div>';

  h += '<div class="tt-section">Properties</div>';
  h += ttRow('Full data', n.hasFullData ? 'Yes' : 'No');
  h += ttRow('Sorted', n.sorted ? n.sortOrder || 'Yes' : 'No');
  h += ttRow('May duplicate', n.mayDuplicate ? 'Yes' : 'No');

  h += '<div class="tt-section">Estimates</div>';
  h += ttRow('Rows', n.estRows.toLocaleString());
  h += ttRow('Deserializations', n.estDeser.toLocaleString());
  h += ttRow('Page reads', n.estPageReads.toLocaleString());

  if (n.actRows > 0) {
    h += '<div class="tt-section">Actual' + (n.actDuplicate ? ' (shared metrics)' : '') + '</div>';
    h += ttRow('Rows', n.actRows.toLocaleString());
    h += ttRow('Deserializations', n.actDeser.toLocaleString());
    h += ttRow('Physical reads', n.actPhysReads.toLocaleString());
    h += ttRow('Logical reads', n.actLogReads.toLocaleString());
  }
  tooltip.innerHTML = h;
  tooltip.style.display = 'block';
  moveTooltip(ev);
}
function moveTooltip(ev) {
  const pad = 14;
  let left = ev.clientX + pad, top = ev.clientY + pad;
  const tw = tooltip.offsetWidth, th = tooltip.offsetHeight;
  if (left + tw > window.innerWidth - 8) left = ev.clientX - tw - pad;
  if (top + th > window.innerHeight - 8) top = ev.clientY - th - pad;
  tooltip.style.left = left + 'px';
  tooltip.style.top = top + 'px';
}
function hideTooltip() { tooltip.style.display = 'none'; }
function ttRow(label, value) {
  return '<div class="tt-row"><span class="tt-label">' + label + '</span><span class="tt-value">' + value + '</span></div>';
}
function escHtml(s) { return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
</script>
</body>
</html>
""";
}
