# MultiFolia

MultiFolia is a Folia 26.2 fork focused on running a **single Minecraft world across multiple worker/server processes** while keeping Folia's regionized multithreading and thread-ownership rules authoritative.

![MultiFolia architecture diagram](assets/multifolia-diagram.jpg)

## What MultiFolia is

Folia remains the execution engine. MultiFolia does **not** replace Folia's region scheduler or bypass its thread-safety model. Instead, MultiFolia is intended to add a distributed control layer around Folia so multiple workers can coordinate ownership of regions of one world safely.

The core design goal is to scale a single world across multiple workers without introducing unsafe concurrent world access.

## Distributed foundation

The first phase is deliberately conservative. The distributed layer is responsible for control-plane concerns such as:

- worker registration and identity
- worker health and liveness
- region ownership
- single-writer leases
- fencing so stale owners cannot continue writing after ownership changes
- coordination and handoff metadata

Cross-worker live-region writes and world-file replication are **disabled** until a safe state-transfer mechanism exists. MultiFolia must never rely on multiple workers concurrently mutating the same world files.

## Region ownership

A region is owned by one active worker at a time. The ownership model is designed around leases and fencing rather than assuming that a disconnected or stalled worker has immediately disappeared.

Ownership changes must remain compatible with Folia's scheduler and thread-ownership checks. A distributed command or control-plane operation must never be able to force arbitrary work onto the wrong region thread.

## Management

The management namespace is `/multifolia` with alias `/mf`.

Planned management operations include:

- `servers` — inspect registered workers
- `list` — inspect region/worker state
- `debug` — inspect coordination state and diagnostics
- `map` — inspect region ownership and placement

These operations are observational/control-plane operations. They must not bypass Folia scheduling, region ownership, or thread checks.

## Plugins

MultiFolia targets normal Folia-compatible plugins first. Priority compatibility targets include:

AuthMe, CombatLog, EasyHome, EzRTP, Floodgate, Geyser, GrimAC, LuckPerms, PacketEvents, PASF, PlaceholderAPI, RaycastedAntiESP, SkinsRestorer, SnowballDamage, TAB, ViaBackwards, and ViaVersion.

**Axiom is excluded.**

Plugin compatibility does not change the requirement that plugins respect Folia's threading model.

## Safety principles

MultiFolia is being built around a few hard constraints:

1. **Folia remains authoritative for execution.**
2. **Only one worker may hold write ownership for a region at a time.**
3. **Stale workers must be fenced before ownership is reassigned.**
4. **Live world state must not be concurrently modified by multiple workers.**
5. **Distributed coordination must not bypass Folia's thread-ownership checks.**
6. **Failure recovery must prefer safe suspension/handoff over risking world corruption.**

## Project status

This repository is in active development. The distributed architecture is being introduced incrementally so that each step can be validated without weakening Folia's concurrency guarantees.

The long-term goal is a production-quality MultiFolia implementation capable of safely coordinating multiple workers over a single world, including worker health, region placement, leases, fencing, synchronization, handoff, and failure recovery.
