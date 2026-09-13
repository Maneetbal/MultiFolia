package dev.multifolia;

import dev.multifolia.distributed.MultiFoliaDistributedControlPlane;
import dev.multifolia.distributed.MultiFoliaDistributedControlPlane.Lease;
import dev.multifolia.distributed.MultiFoliaDistributedControlPlane.Worker;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Observational/control-plane management command for MultiFolia.
 *
 * <p>The command intentionally does not perform world or region mutations. The distributed
 * implementation is not yet connected to Folia's live region ownership machinery.</p>
 */
public final class MultiFoliaCommand extends Command {
    private static final String LOCAL_WORKER_ID = "local-server";
    private static final String LOCAL_ENDPOINT = "local://server";
    private static final MultiFoliaDistributedControlPlane CONTROL_PLANE = new MultiFoliaDistributedControlPlane();

    private static volatile boolean initialized;

    public MultiFoliaCommand() {
        super("multifolia", "MultiFolia management and coordination status", "/multifolia [servers|list|debug|map]", List.of("mf"));
        this.setPermission("multifolia.admin");
    }

    /**
     * Initializes the local worker entry. This only records control-plane identity; it does not
     * claim any world regions or start remote communication.
     */
    public static void initializeLocalWorker() {
        if (!initialized) {
            synchronized (MultiFoliaCommand.class) {
                if (!initialized) {
                    CONTROL_PLANE.registerWorker(LOCAL_WORKER_ID, LOCAL_ENDPOINT);
                    initialized = true;
                }
            }
        }
    }

    public static MultiFoliaDistributedControlPlane controlPlane() {
        return CONTROL_PLANE;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        initializeLocalWorker();

        if (!testPermission(sender)) {
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "servers" -> sendServers(sender);
            case "list" -> sendList(sender);
            case "debug" -> sendDebug(sender);
            case "map" -> sendMap(sender);
            default -> {
                sender.sendMessage("Unknown subcommand: " + args[0]);
                sendHelp(sender);
            }
        }
        return true;
    }

    @Override
    public java.util.List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (!testPermissionSilent(sender)) {
            return java.util.List.of();
        }
        if (args.length == 1) {
            return java.util.List.of("servers", "list", "debug", "map", "help").stream()
                    .filter(value -> value.regionMatches(true, 0, args[0], 0, args[0].length()))
                    .toList();
        }
        return java.util.List.of();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6MultiFolia management");
        sender.sendMessage("§e/multifolia servers §7- registered workers");
        sender.sendMessage("§e/multifolia list §7- current distributed state");
        sender.sendMessage("§e/multifolia debug §7- coordination diagnostics");
        sender.sendMessage("§e/multifolia map §7- region ownership map");
        sender.sendMessage("§7Alias: §e/mf");
    }

    private void sendServers(CommandSender sender) {
        List<Worker> workers = CONTROL_PLANE.workers();
        sender.sendMessage("§6MultiFolia workers §7(" + workers.size() + ")");
        for (Worker worker : workers) {
            sender.sendMessage("§e- " + worker.id() + " §7state=" + worker.state()
                    + " endpoint=" + (worker.endpoint() == null ? "<none>" : worker.endpoint())
                    + " lastHeartbeat=" + worker.lastHeartbeat());
        }
    }

    private void sendList(CommandSender sender) {
        List<Worker> workers = CONTROL_PLANE.workers();
        sender.sendMessage("§6MultiFolia distributed state");
        sender.sendMessage("§7Workers: §f" + workers.size());
        sender.sendMessage("§7Active region leases are not exposed as a local world map yet.");
        sender.sendMessage("§cNo live cross-worker region ownership is enabled in this build.");
    }

    private void sendDebug(CommandSender sender) {
        List<Worker> workers = CONTROL_PLANE.workers();
        sender.sendMessage("§6MultiFolia debug");
        sender.sendMessage("§7Local worker: §f" + LOCAL_WORKER_ID);
        sender.sendMessage("§7Control-plane workers: §f" + workers.size());
        sender.sendMessage("§7Distributed transport: §cNOT IMPLEMENTED");
        sender.sendMessage("§7Live region handoff: §cNOT IMPLEMENTED");
        sender.sendMessage("§7World state transfer: §cNOT IMPLEMENTED");
        sender.sendMessage("§7Folia scheduler/ownership checks remain authoritative.");
    }

    private void sendMap(CommandSender sender) {
        sender.sendMessage("§6MultiFolia region map");
        sender.sendMessage("§7No distributed region placements are currently assigned.");
        sender.sendMessage("§7The current standalone server continues to use Folia's native region scheduler.");
    }
}
