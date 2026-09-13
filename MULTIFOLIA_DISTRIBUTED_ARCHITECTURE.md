# MultiFolia distributed architecture

Folia remains authoritative for execution and thread ownership.

- One worker owns a region at a time.
- Every lease grant gets a monotonic fencing token.
- Stale owners cannot renew or release.
- Workers never directly write another worker's live region files.
- Handoff requires explicit state transfer and acknowledgement before mutation.

The current phase is only the in-process control plane. Network transport, persistent state transfer, routing, and failure recovery are intentionally disabled until they can be integrated without bypassing Folia ownership semantics.
