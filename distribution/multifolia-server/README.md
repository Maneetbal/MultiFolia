# MultiFolia Server

This directory contains the **runnable MultiFolia 26.2 Paperclip server JAR**.

## Requirements

- Minecraft 26.2
- **JDK 25**
- A clean, dedicated server directory

The distributed artifact is the Paperclip JAR produced by `createPaperclipJar`. The ordinary `folia-server` JAR produced by the Gradle `jar` task is a classes-only build artifact and is not the file users should launch directly.

## Windows setup

1. Copy `multifolia-server.jar` from this directory into a new server folder, for example:

```text
C:\MultiFoliaServer\
```

2. Open PowerShell in that folder.

3. Confirm Java 25:

```text
java -version
```

4. Start MultiFolia:

```text
java -Xms4G -Xmx8G -jar multifolia-server.jar --nogui
```

Adjust the memory values for your machine.

5. On the first launch, open `eula.txt` and set:

```text
eula=true
```

after accepting the Minecraft EULA.

6. Start the same command again.

## Linux/macOS setup

Copy `multifolia-server.jar` to a clean server directory and run:

```text
java -Xms4G -Xmx8G -jar multifolia-server.jar --nogui
```

Use JDK 25. Start the server from a terminal so startup errors remain visible.

## First launch troubleshooting

Do not double-click the JAR on Windows. Run it from PowerShell or another terminal so any startup error is visible.

MultiFolia 26.2 is compiled for Java 25. A newer JDK may be able to run the bytecode, but **JDK 25 is the supported build/runtime target** and is the recommended choice.

## Important: distributed node support

The current server is a Folia-based Minecraft server. The separate distributed worker/node runtime is **not finished yet**.

Do not run two copies against the same `world` directory. MultiFolia must implement safe region ownership, leases/fencing, state transfer, and handoff before one world can safely span multiple workers.
