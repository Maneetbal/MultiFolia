package puregero.multipaper.permissions;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.util.permissions.DefaultPermissions;
import org.jetbrains.annotations.NotNull;

public class MultiPaperCommandPermissions {
    private static final String ROOT = "multipaper.command";
    private static final String PREFIX = ROOT + ".";

    public static void registerPermissions(@NotNull Permission parent) {
        Permission commands = DefaultPermissions.registerPermission(ROOT, "Gives the user the ability to use all MultiPaper commands", parent);

        DefaultPermissions.registerPermission(PREFIX + "mpmap", "MPMap Command", PermissionDefault.OP, commands);
        DefaultPermissions.registerPermission(PREFIX + "slist", "SList Command", PermissionDefault.OP, commands);
        DefaultPermissions.registerPermission(PREFIX + "servers", "Servers Command", PermissionDefault.OP, commands);
        DefaultPermissions.registerPermission(PREFIX + "mpdebug", "MPDebug Command", PermissionDefault.OP, commands);
        DefaultPermissions.registerPermission(PREFIX + "entitiesmap", "EntitiesMap Command", PermissionDefault.OP, commands);

        commands.recalculatePermissibles();
    }
}
