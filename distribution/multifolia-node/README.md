# MultiFolia Node

This directory contains the **runnable MultiFolia 26.2 node package** produced by the build.

## Requirements

- Minecraft 26.2
- **JDK 25**
- A clean, dedicated server directory for each node

## Launch

The node package contains the same runnable Paperclip server runtime used by the MultiFolia server distribution. Start it from a terminal with:

```text
java -Xms4G -Xmx8G -jar multifolia-node.jar --nogui
```

On first launch, accept the Minecraft EULA by setting `eula=true` in `eula.txt`, then start the command again.

## Node deployment

Each node must use its **own server directory and world storage**. Do not point multiple running nodes at the same live world files.

The current distributed control plane provides worker registration and lease/fencing primitives, but cross-node world state transfer, routing, and live region handoff are not yet enabled. A runnable node does not imply that one world can safely be shared between independent server processes yet.

## Java version

Use **JDK 25** for MultiFolia 26.2.

Verify with:

```text
java -version
```

The runtime should report Java 25.
