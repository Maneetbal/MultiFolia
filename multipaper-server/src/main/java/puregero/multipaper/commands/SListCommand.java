package puregero.multipaper.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import puregero.multipaper.ExternalPlayer;
import puregero.multipaper.ExternalServer;
import puregero.multipaper.MultiPaper;

public class SListCommand extends Command {
    public SListCommand(String command) {
        super(command);
        setPermission("multipaper.command.slist");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, String @NotNull [] args) {
        if (!testPermission(sender)) return false;

        sender.sendMessage(Component
                .text("There are %d out of %d players online"
                        .formatted(Bukkit.getAllOnlinePlayers().size(), Bukkit.getMaxPlayers()))
                .color(NamedTextColor.WHITE)
        );

        for (ExternalServer server : MultiPaper.getConnection().getServersMap().values()) {
            Component name = Component.text("[%s]".formatted(server.getName())).color(NamedTextColor.GREEN);
            Component playerList = Component.empty().color(NamedTextColor.WHITE);
            String playerSep = "";

            int playerCount = 0;

            for (ServerPlayer player : MinecraftServer.getServer().getPlayerList().getPlayers()) {
                if ((server.isMe() && MultiPaper.isRealPlayer(player))
                        || (player instanceof ExternalPlayer && ((ExternalPlayer) player).externalServerConnection == server.getConnection())) {
                    playerList = playerList.append(Component.text(playerSep + player.getScoreboardName()));
                    playerSep = ", ";
                    playerCount++;
                }
            }

            Component players = Component.text("(%d)".formatted(playerCount)).color(NamedTextColor.YELLOW);

            if (!server.isAlive()) {
                if (playerCount == 0) {
                    continue;
                } else {
                    name = name.color(NamedTextColor.GRAY);
                }
            }

            sender.sendMessage(name.append(players).append(playerList));
        }

        return true;
    }
}
