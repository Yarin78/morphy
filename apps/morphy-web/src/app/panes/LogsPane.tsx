import { Fragment, useEffect, useMemo, useState, useSyncExternalStore } from 'react';
import {
  type ApiCall,
  clearLogs,
  fetchServerLogs,
  getSnapshot,
  type LogLevel,
  markSeen,
  type ServerLogEntry,
  SESSION_ID,
  subscribe,
} from '../../logs/logStore';
import { useDocumentActive } from '../documentsStore';

// How often the service's events are fetched while the logs are shown
const POLL_MS = 3000;

const LEVEL_RANK: Record<LogLevel, number> = { TRACE: 0, DEBUG: 1, INFO: 2, WARN: 3, ERROR: 4 };

type LevelFilter = 'all' | 'warnings' | 'errors';
const FILTER_MIN_RANK: Record<LevelFilter, number> = { all: 0, warnings: 3, errors: 4 };

/** A row: an API call with the service's events about it, or an event of no call of the app's */
type Row =
  | { kind: 'call'; key: string; time: string; level: LogLevel; call: ApiCall; events: ServerLogEntry[] }
  | { kind: 'server'; key: string; time: string; level: LogLevel; entry: ServerLogEntry };

function maxLevel(levels: LogLevel[]): LogLevel {
  return levels.reduce((a, b) => (LEVEL_RANK[b] > LEVEL_RANK[a] ? b : a), 'INFO' as LogLevel);
}

function buildRows(calls: readonly ApiCall[], serverEntries: readonly ServerLogEntry[]): Row[] {
  const eventsByRequest = new Map<string, ServerLogEntry[]>();
  for (const e of serverEntries) {
    if (!e.requestId) continue;
    const list = eventsByRequest.get(e.requestId) ?? [];
    list.push(e);
    eventsByRequest.set(e.requestId, list);
  }
  const callIds = new Set(calls.map((c) => c.requestId));
  const rows: Row[] = [
    ...calls.map((call): Row => {
      const events = eventsByRequest.get(call.requestId) ?? [];
      return {
        kind: 'call',
        key: call.requestId,
        time: call.time,
        level: maxLevel([call.level, ...events.map((e) => e.level)]),
        call,
        events,
      };
    }),
    ...serverEntries
      .filter((e) => !e.requestId || !callIds.has(e.requestId))
      .map((entry): Row => ({ kind: 'server', key: `s${entry.seq}`, time: entry.time, level: entry.level, entry })),
  ];
  return rows.sort((a, b) => b.time.localeCompare(a.time));
}

function rowText(row: Row): string {
  if (row.kind === 'server') return `${row.entry.logger} ${row.entry.message} ${row.entry.stackTrace ?? ''}`;
  const { call, events } = row;
  return [call.method, call.url, call.what, call.error ?? '', ...events.map((e) => `${e.message} ${e.stackTrace ?? ''}`)].join(
    ' '
  );
}

function clockTime(iso: string): string {
  const d = new Date(iso);
  return `${d.toLocaleTimeString([], { hour12: false })}.${String(d.getMilliseconds()).padStart(3, '0')}`;
}

function shortLogger(logger: string): string {
  return logger.split('.').pop() ?? logger;
}

/** The rows as text, to paste into a feedback report */
function asText(rows: Row[]): string {
  const lines = [`Morphy logs — session ${SESSION_ID}, copied ${new Date().toISOString()}`, ''];
  for (const row of [...rows].reverse()) {
    if (row.kind === 'call') {
      const { call } = row;
      const outcome = call.status === undefined ? 'pending' : call.status === 0 ? 'no answer' : call.status;
      const took = call.durationMs !== undefined ? ` ${Math.round(call.durationMs)}ms` : '';
      lines.push(`${call.time} ${call.level} ${call.method} ${call.url} (${call.what}) ${outcome}${took} [${call.requestId}]`);
      if (call.error) lines.push(`    answer: ${call.error}`);
      for (const e of row.events) {
        lines.push(`    ${e.time} ${e.level} ${e.logger}: ${e.message}`);
        if (e.stackTrace) lines.push(...e.stackTrace.trimEnd().split('\n').map((l) => `      ${l}`));
      }
    } else {
      const e = row.entry;
      lines.push(`${e.time} ${e.level} [service] ${e.logger}: ${e.message}`);
      if (e.stackTrace) lines.push(...e.stackTrace.trimEnd().split('\n').map((l) => `    ${l}`));
    }
  }
  return lines.join('\n');
}

function LevelBadge({ level }: { level: LogLevel }) {
  return <span className={`log-level log-level-${level.toLowerCase()}`}>{level}</span>;
}

function ServerEvent({ entry }: { entry: ServerLogEntry }) {
  return (
    <div className="log-event">
      <div className="log-event-line">
        <span className="log-time">{clockTime(entry.time)}</span>
        <LevelBadge level={entry.level} />
        <span className="log-logger" title={entry.logger}>
          {shortLogger(entry.logger)}
        </span>
        <span className="log-message">{entry.message}</span>
      </div>
      {entry.stackTrace && <pre className="log-stack">{entry.stackTrace}</pre>}
    </div>
  );
}

/**
 * Everything that happened between the app and morphy-service in this session: each API call,
 * with the service's log events while handling it, and the service's own events.
 */
export function LogsPane() {
  const { calls, serverEntries, focusedRequestId, unseenErrors } = useSyncExternalStore(subscribe, getSnapshot);
  const active = useDocumentActive();
  const [levelFilter, setLevelFilter] = useState<LevelFilter>('all');
  const [showServer, setShowServer] = useState(true);
  const [query, setQuery] = useState('');
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(new Set());
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  // The service's events are fetched while the logs are shown
  useEffect(() => {
    if (!active) return;
    const fetchLogs = () =>
      fetchServerLogs().then(
        () => setFetchError(null),
        (err: unknown) => setFetchError(err instanceof Error ? err.message : String(err))
      );
    void fetchLogs();
    const timer = setInterval(fetchLogs, POLL_MS);
    return () => clearInterval(timer);
  }, [active]);

  // Errors shown are seen
  useEffect(() => {
    if (active && unseenErrors > 0) markSeen();
  }, [active, unseenErrors]);

  // A request asked for is scrolled to
  useEffect(() => {
    if (!active || !focusedRequestId) return;
    document.querySelector(`[data-request-id="${focusedRequestId}"]`)?.scrollIntoView({ block: 'center' });
  }, [active, focusedRequestId, calls]);

  const rows = useMemo(() => {
    const words = query.toLowerCase().split(/\s+/).filter(Boolean);
    return buildRows(calls, serverEntries).filter(
      (row) =>
        LEVEL_RANK[row.level] >= FILTER_MIN_RANK[levelFilter] &&
        (showServer || row.kind === 'call') &&
        words.every((w) => rowText(row).toLowerCase().includes(w))
    );
  }, [calls, serverEntries, levelFilter, showServer, query]);

  const isExpanded = (key: string) => expanded.has(key) || key === focusedRequestId;
  const toggle = (key: string) => {
    const next = new Set(expanded);
    if (isExpanded(key)) next.delete(key);
    else next.add(key);
    setExpanded(next);
  };

  const copy = async () => {
    await navigator.clipboard.writeText(asText(rows));
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div className="logs-pane">
      <div className="logs-toolbar">
        <select value={levelFilter} onChange={(e) => setLevelFilter(e.target.value as LevelFilter)} aria-label="Level">
          <option value="all">All levels</option>
          <option value="warnings">Warnings and errors</option>
          <option value="errors">Errors</option>
        </select>
        <label className="logs-check">
          <input type="checkbox" checked={showServer} onChange={(e) => setShowServer(e.target.checked)} />
          Service events
        </label>
        <input
          className="logs-search"
          type="search"
          placeholder="Filter…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <span className="logs-count">
          {rows.length} {rows.length === 1 ? 'entry' : 'entries'}
        </span>
        {fetchError && <span className="logs-fetch-error">{fetchError}</span>}
        <button onClick={copy} title="Copy the entries shown, to paste into a feedback report">
          {copied ? 'Copied' : 'Copy'}
        </button>
        <button onClick={clearLogs} title="Forget the entries so far">
          Clear
        </button>
      </div>
      <div className="logs-list">
        {rows.length === 0 && <p className="logs-empty">Nothing logged yet.</p>}
        {rows.map((row) => {
          const open = isExpanded(row.key);
          if (row.kind === 'server') {
            const e = row.entry;
            return (
              <div key={row.key} className={`log-row log-row-${row.level.toLowerCase()}`}>
                <div className="log-line" onClick={() => e.stackTrace && toggle(row.key)}>
                  <span className="log-toggle">{e.stackTrace ? (open ? '▾' : '▸') : ''}</span>
                  <span className="log-time">{clockTime(e.time)}</span>
                  <LevelBadge level={e.level} />
                  <span className="log-source">service</span>
                  <span className="log-logger" title={e.logger}>
                    {shortLogger(e.logger)}
                  </span>
                  <span className="log-message">{e.message}</span>
                </div>
                {open && e.stackTrace && <pre className="log-stack">{e.stackTrace}</pre>}
              </div>
            );
          }
          const { call, events } = row;
          const hasDetails = !!call.error || events.length > 0;
          return (
            <div
              key={row.key}
              data-request-id={call.requestId}
              className={`log-row log-row-${row.level.toLowerCase()}${call.requestId === focusedRequestId ? ' focused' : ''}`}
            >
              <div className="log-line" onClick={() => hasDetails && toggle(row.key)}>
                <span className="log-toggle">{hasDetails ? (open ? '▾' : '▸') : ''}</span>
                <span className="log-time">{clockTime(call.time)}</span>
                <LevelBadge level={row.level} />
                <span className="log-source">app</span>
                <span className="log-what">{call.what}</span>
                <span className="log-message">
                  {call.method} {decodeURIComponent(call.url)}
                </span>
                <span className="log-status">
                  {call.status === undefined ? '…' : call.status === 0 ? 'no answer' : call.status}
                  {call.durationMs !== undefined && ` · ${Math.round(call.durationMs)} ms`}
                </span>
              </div>
              {open && (
                <div className="log-details">
                  <div className="log-detail-line">
                    Request id <code>{call.requestId}</code>
                  </div>
                  {call.error && (
                    <Fragment>
                      <div className="log-detail-label">The service answered</div>
                      <pre className="log-stack">{call.error}</pre>
                    </Fragment>
                  )}
                  {events.length > 0 && <div className="log-detail-label">Logged by the service</div>}
                  {events.map((e) => (
                    <ServerEvent key={e.seq} entry={e} />
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
