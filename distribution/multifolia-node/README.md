# MultiFolia Node

This directory is the **node-side package** produced by the MultiFolia build.

## Current contents

The package contains the Folia API runtime JAR used by plugins and node-side Java development. The distribution intentionally ships the runtime API JAR only; source and Javadoc archives are not required to run the current package.

**Important:** the current MultiFolia build does **not yet contain a standalone executable distributed-node process**. Do not launch the API JAR with `java -jar`; it is a library, not a Minecraft server.

## Java version

Use **JDK 25** for MultiFolia 26.2.

Verify with:

```text
java -version
```

The runtime should report Java 25.

## Node setup

For the current build, this folder is not enough to turn another machine into a live worker for the same world as the main server. Safe multi-worker region ownership, state transfer, fencing, and handoff are still being implemented.

For now:

1. Install JDK 25.
2. Keep the API JAR available if you are developing against the MultiFolia API.
3. Run the actual Minecraft server from `multifolia-server`.
4. Never point two machines at the same live world files.

A future release will add an executable node runtime here once the distributed worker layer is implemented and validated. That runtime will communicate with the server through a dedicated coordination/state-transfer protocol rather than shared concurrent world files.

The CI pipeline validates the packaged server artifact separately; the API JAR in this directory is not itself an executable node.
