package me.herobane;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

public class HerosAnchorOptimizerClient implements ClientModInitializer {

	private static final Set<BlockPos> fakeAnchorPositions = new HashSet<>();
	private static final int PURPLE_COLOR = 0xFF00FF;
	@Override
	public void onInitializeClient() {
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClient() && world.getBlockState(hitResult.getBlockPos()).isOf(Blocks.RESPAWN_ANCHOR)) {

				boolean isSingleplayer = MinecraftClient.getInstance().isInSingleplayer();
				int charge = world.getBlockState(hitResult.getBlockPos()).get(RespawnAnchorBlock.CHARGES);
				boolean holdingGlowstoneMainHand = player.getStackInHand(hand).isOf(Items.GLOWSTONE);
				boolean holdingGlowstoneOffHand = player.getOffHandStack().isOf(Items.GLOWSTONE);
				boolean wouldExplode = !world.getDimension().respawnAnchorWorks();

				if (!player.isSneaking() && !holdingGlowstoneMainHand && !holdingGlowstoneOffHand && !isSingleplayer) {
					if ((charge >= 1 && charge <= 3 && wouldExplode) || (charge == 4 && wouldExplode)) {
						placeClientSideFakeAnchor(world, hitResult);
						return ActionResult.SUCCESS;
					}
				}
				return ActionResult.PASS;
			}
			return ActionResult.PASS;
		});

		WorldRenderEvents.END.register(this::renderFakeAnchors);
	}
	private void placeClientSideFakeAnchor(World world, BlockHitResult hitResult) {
		BlockPos pos = hitResult.getBlockPos();
		world.setBlockState(pos, Blocks.STRUCTURE_VOID.getDefaultState());
		fakeAnchorPositions.add(pos.toImmutable());
		new Thread(() -> {
			try {
				while (true) {
					Thread.sleep(25);

					if (world.getBlockState(pos).isOf(Blocks.STRUCTURE_VOID)) {
					} else {
						fakeAnchorPositions.remove(pos);
						break;
					}
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				fakeAnchorPositions.remove(pos);
			}
		}).start();
	}
	private void renderFakeAnchors(WorldRenderContext context) {
		if (fakeAnchorPositions.isEmpty()) return;

		MatrixStack matrices = context.matrixStack();
		VertexConsumerProvider vertexConsumers = context.consumers();

		for (BlockPos pos : fakeAnchorPositions) {
			drawCross(matrices, vertexConsumers, pos);
		}
	}

	private void drawCross(MatrixStack matrices, VertexConsumerProvider vertexConsumers, BlockPos pos) {
		double centerX = pos.getX() + 0.5;
		double centerY = pos.getY() + 0.5;
		double centerZ = pos.getZ() + 0.5;

		Vec3d cameraPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();

		matrices.push();
		matrices.translate(centerX - cameraPos.x, centerY - cameraPos.y, centerZ - cameraPos.z);

		VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());
		Matrix4f matrix = matrices.peek().getPositionMatrix();

		float r = ((PURPLE_COLOR >> 16) & 0xFF) / 255.0f;
		float g = ((PURPLE_COLOR >> 8) & 0xFF) / 255.0f;
		float b = (PURPLE_COLOR & 0xFF) / 255.0f;
		float a = 1.0f;

		float size = 0.5f;

		vertexConsumer.vertex(matrix, -size, 0, 0).color(r, g, b, a).normal(1, 0, 0);
		vertexConsumer.vertex(matrix, size, 0, 0).color(r, g, b, a).normal(1, 0, 0);

		vertexConsumer.vertex(matrix, 0, -size, 0).color(r, g, b, a).normal(0, 1, 0);
		vertexConsumer.vertex(matrix, 0, size, 0).color(r, g, b, a).normal(0, 1, 0);

		vertexConsumer.vertex(matrix, 0, 0, -size).color(r, g, b, a).normal(0, 0, 1);
		vertexConsumer.vertex(matrix, 0, 0, size).color(r, g, b, a).normal(0, 0, 1);

		matrices.pop();
	}
}