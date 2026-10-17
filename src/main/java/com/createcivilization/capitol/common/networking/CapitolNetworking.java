package com.createcivilization.capitol.common.networking;

import com.createcivilization.capitol.client.networking.ClientPayloadHandler;
import com.createcivilization.capitol.common.networking.packets.C2SClaimChunk;
import com.createcivilization.capitol.common.networking.packets.C2SUnclaimChunk;
import com.createcivilization.capitol.common.networking.packets.S2CChunkData;
import com.createcivilization.capitol.common.networking.packets.C2SChunkRequest;
import com.createcivilization.capitol.common.networking.packets.S2CChunkRemove;
import com.createcivilization.capitol.common.networking.packets.C2SDamageWand;
import com.createcivilization.capitol.common.networking.packets.C2SInvitePlayer;
import com.createcivilization.capitol.common.networking.packets.S2COpenCapitolScreen;
import com.createcivilization.capitol.common.networking.packets.S2COpenCapitolNamingScreen;
import com.createcivilization.capitol.common.networking.packets.C2SNameCapitolBlock;
import com.createcivilization.capitol.common.networking.packets.C2SCancelCapitolBlockNaming;
import com.createcivilization.capitol.common.networking.packets.C2SSetCapitolBlockMayor;
import com.createcivilization.capitol.common.networking.packets.C2STeamChat;
import com.createcivilization.capitol.common.networking.packets.S2CSubClaimData;
import com.createcivilization.capitol.common.networking.packets.S2CSubClaimRemove;
import com.createcivilization.capitol.common.networking.packets.C2SCreateSubClaim;
import com.createcivilization.capitol.common.networking.packets.C2SUpgradeCapitolBlock;
import com.createcivilization.capitol.common.networking.packets.C2SDeclareWar;
import com.createcivilization.capitol.common.networking.packets.C2SEndWar;
import com.createcivilization.capitol.common.networking.packets.S2CSyncWars;
import com.createcivilization.capitol.common.networking.packets.C2SCreateRole;
import com.createcivilization.capitol.common.networking.packets.C2SEditRole;
import com.createcivilization.capitol.common.networking.packets.C2SSetRolePermissions;
import com.createcivilization.capitol.common.networking.packets.C2SDeleteRole;
import com.createcivilization.capitol.common.networking.packets.C2SMoveRole;
import com.createcivilization.capitol.common.networking.packets.C2SAssignRole;
import com.createcivilization.capitol.common.networking.packets.C2SKickMember;
import com.createcivilization.capitol.common.networking.packets.S2CActionResult;
import com.createcivilization.capitol.common.networking.packets.S2CBookInvalidate;
import com.createcivilization.capitol.common.networking.packets.C2STransferLeadership;
import com.createcivilization.capitol.common.networking.packets.C2SLeaveTeam;
import com.createcivilization.capitol.common.networking.packets.C2SRequestDisband;
import com.createcivilization.capitol.common.networking.packets.C2SRespondDisband;
import com.createcivilization.capitol.common.networking.packets.C2SCancelDisband;
import com.createcivilization.capitol.server.networking.ServerPayloadHandler;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.MainThreadPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class CapitolNetworking {

	public static void init(IEventBus bus) {
		bus.addListener(CapitolNetworking::register);
	}

	@SubscribeEvent
	public static void register(final RegisterPayloadHandlersEvent event) {
		final PayloadRegistrar registrar = event.registrar("4");

		registrar.playToClient(
			S2CChunkData.TYPE,
			S2CChunkData.STREAM_CODEC,
			new MainThreadPayloadHandler<>(
				ClientPayloadHandler::chunkDataHandler
			)
		);

		registrar.playToClient(
			S2CChunkRemove.TYPE,
			S2CChunkRemove.STREAM_CODEC,
			new MainThreadPayloadHandler<>(
				ClientPayloadHandler::chunkRemoveHandler
			)
		);

		registrar.playToClient(
			S2CSubClaimData.TYPE,
			S2CSubClaimData.STREAM_CODEC,
			new MainThreadPayloadHandler<>(
				ClientPayloadHandler::subClaimDataHandler
			)
		);

		registrar.playToClient(
			S2COpenCapitolScreen.TYPE,
			S2COpenCapitolScreen.STREAM_CODEC,
			new MainThreadPayloadHandler<>(
				ClientPayloadHandler::openCapitolScreenHandler
			)
		);

		registrar.playToClient(
			S2CSubClaimRemove.TYPE,
			S2CSubClaimRemove.STREAM_CODEC,
			new MainThreadPayloadHandler<>(
				ClientPayloadHandler::subClaimRemoveHandler
			)
		);

		registrar.playToClient(
			S2COpenCapitolNamingScreen.TYPE,
			S2COpenCapitolNamingScreen.STREAM_CODEC,
			new MainThreadPayloadHandler<>(
				ClientPayloadHandler::openCapitolNamingScreenHandler
			)
		);

		registrar.playToServer(
			C2SChunkRequest.TYPE,
			C2SChunkRequest.STREAM_CODEC,
			new MainThreadPayloadHandler<>(
				ServerPayloadHandler::handleChunkRequest
			)
		);

		registrar.playToServer(
			C2SClaimChunk.TYPE,
			C2SClaimChunk.STREAM_CODEC,
			new MainThreadPayloadHandler<>(
				ServerPayloadHandler::handleClaimChunk
			)
		);

		registrar.playToServer(
			C2SUnclaimChunk.TYPE,
			C2SUnclaimChunk.STREAM_CODEC,
			new MainThreadPayloadHandler<>(
				ServerPayloadHandler::handleUnclaimChunk
			)
		);

		registrar.playToServer(
			C2SCreateSubClaim.TYPE,
			C2SCreateSubClaim.STREAM_CODEC,
			new MainThreadPayloadHandler<>(
				ServerPayloadHandler::handleCreateSubClaim
			)
		);

		registrar.playToServer(
			C2SDamageWand.TYPE,
			C2SDamageWand.STREAM_CODEC,
			new MainThreadPayloadHandler<>(
				ServerPayloadHandler::handleDamageWand
			)
		);

		registrar.playToServer(
			C2SInvitePlayer.TYPE,
			C2SInvitePlayer.STREAM_CODEC,
			new MainThreadPayloadHandler<>(
				ServerPayloadHandler::handleInvitePlayer
			)
		);

		registrar.playToServer(
			C2STeamChat.TYPE,
			C2STeamChat.STREAM_CODEC,
			new MainThreadPayloadHandler<>(
				ServerPayloadHandler::handleTeamChat
			)
		);

		registrar.playToServer(
			C2SUpgradeCapitolBlock.TYPE,
			C2SUpgradeCapitolBlock.STREAM_CODEC,
			new MainThreadPayloadHandler<>(
				ServerPayloadHandler::handleUpgradeCapitolBlock
			)
		);

		registrar.playToServer(
			C2SNameCapitolBlock.TYPE,
			C2SNameCapitolBlock.STREAM_CODEC,
			new MainThreadPayloadHandler<>(
				ServerPayloadHandler::handleNameCapitolBlock
			)
		);

		registrar.playToServer(
			C2SCancelCapitolBlockNaming.TYPE,
			C2SCancelCapitolBlockNaming.STREAM_CODEC,
			new MainThreadPayloadHandler<>(
				ServerPayloadHandler::handleCancelCapitolBlockNaming
			)
		);

		registrar.playToServer(
			C2SSetCapitolBlockMayor.TYPE,
			C2SSetCapitolBlockMayor.STREAM_CODEC,
			new MainThreadPayloadHandler<>(
				ServerPayloadHandler::handleSetCapitolBlockMayor
			)
		);

		registrar.playToClient(
			S2CSyncWars.TYPE,
			S2CSyncWars.STREAM_CODEC,
			new MainThreadPayloadHandler<>(
				ClientPayloadHandler::syncWarsHandler
			)
		);

		registrar.playToServer(
			C2SDeclareWar.TYPE,
			C2SDeclareWar.STREAM_CODEC,
			new MainThreadPayloadHandler<>(
				ServerPayloadHandler::handleDeclareWar
			)
		);

		registrar.playToServer(
			C2SEndWar.TYPE,
			C2SEndWar.STREAM_CODEC,
			new MainThreadPayloadHandler<>(
				ServerPayloadHandler::handleEndWar
			)
		);

		registrar.playToServer(
			C2SCreateRole.TYPE,
			C2SCreateRole.STREAM_CODEC,
			new MainThreadPayloadHandler<>(ServerPayloadHandler::handleCreateRole)
		);

		registrar.playToServer(
			C2SEditRole.TYPE,
			C2SEditRole.STREAM_CODEC,
			new MainThreadPayloadHandler<>(ServerPayloadHandler::handleEditRole)
		);

		registrar.playToServer(
			C2SSetRolePermissions.TYPE,
			C2SSetRolePermissions.STREAM_CODEC,
			new MainThreadPayloadHandler<>(ServerPayloadHandler::handleSetRolePermissions)
		);

		registrar.playToServer(
			C2SDeleteRole.TYPE,
			C2SDeleteRole.STREAM_CODEC,
			new MainThreadPayloadHandler<>(ServerPayloadHandler::handleDeleteRole)
		);

		registrar.playToServer(
			C2SMoveRole.TYPE,
			C2SMoveRole.STREAM_CODEC,
			new MainThreadPayloadHandler<>(ServerPayloadHandler::handleMoveRole)
		);

		registrar.playToServer(
			C2SAssignRole.TYPE,
			C2SAssignRole.STREAM_CODEC,
			new MainThreadPayloadHandler<>(ServerPayloadHandler::handleAssignRole)
		);

		registrar.playToServer(
			C2SKickMember.TYPE,
			C2SKickMember.STREAM_CODEC,
			new MainThreadPayloadHandler<>(ServerPayloadHandler::handleKickMember)
		);

		registrar.playToClient(
			S2CActionResult.TYPE,
			S2CActionResult.STREAM_CODEC,
			new MainThreadPayloadHandler<>(ClientPayloadHandler::actionResultHandler)
		);

		registrar.playToClient(
			S2CBookInvalidate.TYPE,
			S2CBookInvalidate.STREAM_CODEC,
			new MainThreadPayloadHandler<>(ClientPayloadHandler::bookInvalidateHandler)
		);

		registrar.playToServer(
			C2STransferLeadership.TYPE,
			C2STransferLeadership.STREAM_CODEC,
			new MainThreadPayloadHandler<>(ServerPayloadHandler::handleTransferLeadership)
		);

		registrar.playToServer(
			C2SLeaveTeam.TYPE,
			C2SLeaveTeam.STREAM_CODEC,
			new MainThreadPayloadHandler<>(ServerPayloadHandler::handleLeaveTeam)
		);

		registrar.playToServer(
			C2SRequestDisband.TYPE,
			C2SRequestDisband.STREAM_CODEC,
			new MainThreadPayloadHandler<>(ServerPayloadHandler::handleRequestDisband)
		);

		registrar.playToServer(
			C2SRespondDisband.TYPE,
			C2SRespondDisband.STREAM_CODEC,
			new MainThreadPayloadHandler<>(ServerPayloadHandler::handleRespondDisband)
		);

		registrar.playToServer(
			C2SCancelDisband.TYPE,
			C2SCancelDisband.STREAM_CODEC,
			new MainThreadPayloadHandler<>(ServerPayloadHandler::handleCancelDisband)
		);
	}

}