# MultiFolia

MultiFolia is a genuine Folia 26.2 fork. Folia remains the execution engine and its regionized multithreading, schedulers, and thread-ownership checks remain authoritative.

## Distributed foundation

The first phase is a conservative control plane for worker registration, health, and single-writer region leases with fencing. Cross-worker live-region writes and world-file replication are disabled until safe state transfer exists.

## Management

The management namespace is `/multifolia` with alias `/mf`; planned operations are `servers`, `list`, `debug`, and `map`. They remain observational/control-plane only and never bypass Folia scheduling or ownership.

## Plugins

Priority Folia-compatible targets: AuthMe, CombatLog, EasyHome, EzRTP, Floodgate, Geyser, GrimAC, LuckPerms, PacketEvents, PASF, PlaceholderAPI, RaycastedAntiESP, SkinsRestorer, SnowballDamage, TAB, ViaBackwards, ViaVersion. Axiom is excluded.
