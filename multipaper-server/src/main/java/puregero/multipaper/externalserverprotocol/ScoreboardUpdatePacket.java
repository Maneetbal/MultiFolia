package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.util.PacketCodecHelper;

import javax.annotation.Nullable;

public class ScoreboardUpdatePacket extends ExternalServerPacket {
    private static final Logger LOGGER = LogUtils.getClassLogger();
    public static boolean updating = false;

    @Nullable
    private final String scoreboard;
    @Nullable
    private final String criteria;
    private final Packet<? super ClientGamePacketListener> packet;

    public ScoreboardUpdatePacket(@Nullable String scoreboard, Packet<? super ClientGamePacketListener> packet) {
        this(scoreboard, null, packet);
    }

    public ScoreboardUpdatePacket(@Nullable String scoreboard, @Nullable ObjectiveCriteria criteria, Packet<? super ClientGamePacketListener> packet) {
        this.scoreboard = scoreboard;
        this.criteria = criteria == null ? null : criteria.getName();
        this.packet = packet;
    }

    public ScoreboardUpdatePacket(RegistryFriendlyByteBuf in) {
        this.scoreboard = in.readNullable(FriendlyByteBuf::readUtf);
        this.criteria = in.readNullable(FriendlyByteBuf::readUtf);
        this.packet = PacketCodecHelper.decodeClientbound(in.readByteArray());
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeNullable(this.scoreboard, FriendlyByteBuf::writeUtf);
        out.writeNullable(this.criteria, FriendlyByteBuf::writeUtf);
        out.writeByteArray(PacketCodecHelper.encodeClientbound(this.packet));
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        if (this.scoreboard != null) {
            throw new UnsupportedOperationException("Modifying scoreboard " + this.scoreboard + " is not supported!");
        }

        MultiPaper.runSync(() -> {
            updating = true;

            ServerScoreboard scoreboard = MinecraftServer.getServer().getScoreboard();

            if (this.packet instanceof ClientboundSetPlayerTeamPacket setPlayerTeamPacket) {
                handle(scoreboard, setPlayerTeamPacket);
            } else if (this.packet instanceof ClientboundSetScorePacket setScorePacket) {
                handle(scoreboard, setScorePacket);
            } else if (this.packet instanceof ClientboundResetScorePacket resetScorePacket) {
                handle(scoreboard, resetScorePacket);
            } else if (this.packet instanceof ClientboundSetObjectivePacket setObjectivePacket) {
                handle(scoreboard, this.criteria, setObjectivePacket);
            } else if (this.packet instanceof ClientboundSetDisplayObjectivePacket setDisplayObjectivePacket) {
                handle(scoreboard, setDisplayObjectivePacket);
            } else {
                LOGGER.warn("Unhandled scoreboard update packet of type {}", this.packet.getClass().getSimpleName());
            }

            updating = false;
        });
    }

    private void handle(ServerScoreboard scoreboard, ClientboundSetPlayerTeamPacket setPlayerTeamPacket) {
        PlayerTeam team = scoreboard.getPlayerTeam(setPlayerTeamPacket.getName());

        if (setPlayerTeamPacket.getTeamAction() == ClientboundSetPlayerTeamPacket.Action.ADD && team == null) {
            team = scoreboard.addPlayerTeam(setPlayerTeamPacket.getName());
        } else if (setPlayerTeamPacket.getTeamAction() == ClientboundSetPlayerTeamPacket.Action.REMOVE) {
            scoreboard.removePlayerTeam(team);
        }

        if (setPlayerTeamPacket.getPlayerAction() == ClientboundSetPlayerTeamPacket.Action.ADD) {
            scoreboard.addPlayersToTeam(setPlayerTeamPacket.getPlayers(), team);
        } else if (setPlayerTeamPacket.getPlayerAction() == ClientboundSetPlayerTeamPacket.Action.REMOVE) {
            scoreboard.removePlayersFromTeam(setPlayerTeamPacket.getPlayers(), team);
        }

        if (setPlayerTeamPacket.getParameters().isPresent()) {
            ClientboundSetPlayerTeamPacket.Parameters parameters = setPlayerTeamPacket.getParameters().get();
            team.setDisplayName(parameters.displayName());
            team.setPlayerPrefix(parameters.playerPrefix());
            team.setPlayerSuffix(parameters.playerSuffix());
            team.setNameTagVisibility(parameters.nameTagVisibility());
            team.setCollisionRule(parameters.collisionRule());
            team.setColor(parameters.color());
            team.unpackOptions(parameters.options());
        }
    }

    private void handle(ServerScoreboard scoreboard, ClientboundSetScorePacket setScorePacket) {
        Objective objective = scoreboard.getObjective(setScorePacket.objectiveName());
        ScoreHolder holder = ScoreHolder.forNameOnly(setScorePacket.owner());

        ScoreAccess scoreAccess = scoreboard.getOrCreatePlayerScore(holder, objective, true);
        scoreAccess.set(setScorePacket.score());
        scoreAccess.display(setScorePacket.display().orElse(null));
        scoreAccess.numberFormatOverride(setScorePacket.numberFormat().orElse(null));
    }

    private void handle(ServerScoreboard scoreboard, ClientboundResetScorePacket resetScorePacket) {
        ScoreHolder holder = ScoreHolder.forNameOnly(resetScorePacket.owner());

        if (resetScorePacket.objectiveName() != null) {
            Objective objective = scoreboard.getObjective(resetScorePacket.objectiveName());
            if (objective != null) {
                scoreboard.resetSinglePlayerScore(holder, objective);
            }
        } else {
            scoreboard.resetAllPlayerScores(holder);
        }
    }

    private void handle(ServerScoreboard scoreboard, String criteria, ClientboundSetObjectivePacket setObjectivePacket) {
        Objective objective = scoreboard.getObjective(setObjectivePacket.getObjectiveName());

        if (setObjectivePacket.getMethod() == ClientboundSetObjectivePacket.METHOD_REMOVE) {
            scoreboard.removeObjective(objective);
            return;
        }

        if (setObjectivePacket.getMethod() == ClientboundSetObjectivePacket.METHOD_ADD && objective == null) {
            ObjectiveCriteria objectiveCriteria = criteria == null ? null : ObjectiveCriteria.byName(criteria).orElseThrow();
            objective = scoreboard.addObjective(setObjectivePacket.getObjectiveName(), objectiveCriteria, setObjectivePacket.getDisplayName(), setObjectivePacket.getRenderType(), false, setObjectivePacket.getNumberFormat().orElse(null));
        }

        if (setObjectivePacket.getMethod() == ClientboundSetObjectivePacket.METHOD_CHANGE || setObjectivePacket.getMethod() == ClientboundSetObjectivePacket.METHOD_ADD) {
            objective.setDisplayName(setObjectivePacket.getDisplayName());
            objective.setRenderType(setObjectivePacket.getRenderType());
            objective.setNumberFormat(setObjectivePacket.getNumberFormat().orElse(null));
        }
    }

    private void handle(ServerScoreboard scoreboard, ClientboundSetDisplayObjectivePacket setDisplayObjectivePacket) {
        Objective objective = scoreboard.getObjective(setDisplayObjectivePacket.getObjectiveName());

        scoreboard.setDisplayObjective(setDisplayObjectivePacket.getSlot(), objective);
    }
}
