package com.tom.cpm.common;

import java.util.Collections;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.EntityTrackingListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage.EntityTracker;
import net.minecraft.util.Identifier;

import com.tom.cpm.MinecraftServerObject;
import com.tom.cpm.shared.network.NetH;
import com.tom.cpm.shared.network.NetHandler;

import io.netty.buffer.Unpooled;

public class ServerHandler {
	public static NetHandler<Identifier, ServerPlayerEntity, ServerPlayNetworkHandler> netHandler;

	static {
		netHandler = new NetHandler<>(Identifier::new);
		netHandler.setGetPlayerUUID(ServerPlayerEntity::getUuid);
		netHandler.setSendPacketServer(d -> new PacketByteBuf(Unpooled.wrappedBuffer(d)), (c, rl, pb) -> c.sendPacket(new CustomPayloadS2CPacket(rl, pb)), ent -> {
			EntityTracker tr = ((ServerWorld)ent.world).getChunkManager().threadedAnvilChunkStorage.entityTrackers.get(ent.getId());
			if(tr != null) {
				return tr.listeners;
			}
			return Collections.emptyList();
		}, EntityTrackingListener::getPlayer);
		netHandler.setFindTracking((p, f) -> {
			for(EntityTracker tr : ((ServerWorld)p.world).getChunkManager().threadedAnvilChunkStorage.entityTrackers.values()) {
				if(tr.entity instanceof PlayerEntity && tr.listeners.contains(p.networkHandler)) {
					f.accept((ServerPlayerEntity) tr.entity);
				}
			}
		});
		netHandler.setSendChat((p, m) -> p.sendMessage(m.remap(), false));
		netHandler.setExecutor(n -> ((IServerNetHandler)n).cpm$getServer());
		if(FabricLoader.getInstance().isModLoaded("pehkui")) {
			netHandler.setScaler(new PehkuiInterface());
		}
		netHandler.setGetNet(spe -> spe.networkHandler);
		netHandler.setGetPlayer(net -> net.player);
		netHandler.setGetPlayerId(ServerPlayerEntity::getId);
		netHandler.setGetOnlinePlayers(() -> MinecraftServerObject.getServer().getPlayerManager().getPlayerList());
		netHandler.setKickPlayer((p, m) -> p.networkHandler.disconnect(m.remap()));
		netHandler.setGetPlayerAnimGetters(p -> p.fallDistance, p -> p.getAbilities().flying);
	}

	public static void onPlayerJoin(ServerPlayerEntity spe) {
		netHandler.onJoin(spe);
	}

	public static void onTrackingStart(Entity target, ServerPlayerEntity spe) {
		ServerPlayNetworkHandler handler = spe.networkHandler;
		NetH h = (NetH) handler;
		if(h.cpm$hasMod()) {
			if(target instanceof PlayerEntity) {
				netHandler.sendPlayerData((ServerPlayerEntity) target, spe);
			}
		}
	}

	public static void jump(Object player) {
		if(player instanceof ServerPlayerEntity) {
			netHandler.onJump((ServerPlayerEntity) player);
		}
	}
}
