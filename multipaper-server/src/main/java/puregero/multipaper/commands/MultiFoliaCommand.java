package puregero.multipaper.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * MultiFolia command namespace.
 *
 * Supports the short `/mf` form and the long `/multifolia` form.
 */
public class MultiFoliaCommand extends Command {
    private final ServersCommand serversCommand = new ServersCommand("servers");
    private final SListCommand listCommand = new SListCommand("list");
    private final MPDebugCommand debugCommand = new MPDebugCommand("debug");
    private final MPMapCommand mapCommand = new MPMapCommand("map");

    public MultiFoliaCommand(String command) {
        super(command, "MultiFolia administrative/debug commands", "/" + command + " <servers|list|debug|map>", null);
        setPermission("multipaper.command");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, String @NotNull [] args) {
        if (!testPermission(sender)) return false;

        if (args.length == 0) {
            sender.sendMessage(Component.text("MultiFolia commands: /" + commandLabel + " servers, /" + commandLabel + " list, /" + commandLabel + " debug, /" + commandLabel + " map").color(NamedTextColor.YELLOW));
            return true;
        }

        String subcommand = args[0].toLowerCase(java.util.Locale.ROOT);
        String[] subArgs = java.util.Arrays.copyOfRange(args, 1, args.length);

        return switch (subcommand) {
            case "servers" -> serversCommand.execute(sender, commandLabel + " servers", subArgs);
            case "list" -> listCommand.execute(sender, commandLabel + " list", subArgs);
            case "debug" -> debugCommand.execute(sender, commandLabel + " debug", subArgs);
            case "map" -> mapCommand.execute(sender, commandLabel + " map", subArgs);
            default -> {
                sender.sendMessage(Component.text("Unknown MultiFolia subcommand: " + args[0]).color(NamedTextColor.RED));
                yield false;
            }
        };
    }
}
