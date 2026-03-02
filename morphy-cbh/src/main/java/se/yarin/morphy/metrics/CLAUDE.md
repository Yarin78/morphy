# se.yarin.morphy.metrics

Performance and usage instrumentation for database operations. Optional and can be disabled.

## Key Classes

- **MetricsRepository** - Central registry for all metrics. Collects and aggregates metrics from providers.
- **MetricsRef\<T\>** - Type-safe reference to a specific metric for recording values.
- **MetricsKey** - Uniquely identifies a metric.
- **Metrics** - Interface with `merge()`, `clear()`, `isEmpty()`, `formatHeaderRow()`, `formatTableRow()` for metrics implementations.
- **MetricsProvider** - Interface for objects that provide metrics (implemented by ItemMetrics, FileMetrics).
- **ItemMetrics** - Metrics for item storage operations (record counts, bytes read/written).
- **FileMetrics** - Metrics for file-level I/O operations.

## Design

- Registry pattern with type-safe references.
- Formatted output support for console reporting (used by `--iostats` CLI flag).
- Provider interface allows any storage component to contribute metrics.
