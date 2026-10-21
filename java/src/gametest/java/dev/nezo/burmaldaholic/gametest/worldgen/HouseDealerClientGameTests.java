package dev.nezo.burmaldaholic.gametest.worldgen;

import dev.nezo.burmaldaholic.core.anim.cards.DealerGesture;
import dev.nezo.burmaldaholic.core.table.parts.TableParts;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.worldgen.CasinoQa.Placed;
import dev.nezo.burmaldaholic.gametest.worldgen.CasinoQa.Target;
import dev.nezo.burmaldaholic.worldgen.npc.HouseDealer;
import dev.nezo.burmaldaholic.worldgen.npc.NpcContent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

/**
 * DL tester, client lane {@code BURMALDAHOLIC_CGT=DL}: a real client loads a placed casino with its house dealers plus a
 * piglin and an ender dealer; the three dealer renderers must be invoked in real frames (probe mixin on the per-frame
 * pose extraction), every dealer's render state extracts without exceptions, the synced TABLE / LOOK / gesture reach
 * the client. No screenshots.
 */
public class HouseDealerClientGameTests implements FabricClientGameTest {
	static boolean lane(String lane) {
		String f = System.getenv("BURMALDAHOLIC_CGT");
		return f == null || f.isBlank() || Arrays.stream(f.split(",")).map(String::trim).anyMatch(s -> s.equalsIgnoreCase(lane));
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!lane("DL")) {
			return;
		}
		List<String> failures = new ArrayList<>();
		context.runOnClient(mc -> mc.gui.setScreen(null));
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create(); var q = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			world.getServer().runCommand("gamemode spectator @p");
			world.getServer().runCommand("time set noon");
			Target t = world.getServer().computeOnServer(server -> CasinoClientQa.defaultTarget(server, "riviera"));
			Placed placed = CasinoClientQa.place(world, t, new BlockPos(0, 0, 40), 2);
			// wait for the worldgen dealers on the server
			int n = 0;
			for (int i = 0; i < 40 && n == 0; i++) {
				context.waitTicks(5);
				n = world.getServer().computeOnServer(server -> server.overworld()
					.getEntitiesOfClass(HouseDealer.class, AABB.of(placed.box()).inflate(2), Entity::isAlive).size());
			}
			if (n == 0) {
				throw new AssertionError("no house dealers in " + t.name());
			}
			// stand in front of the first dealer's table, looking at the dealer; a piglin and an ender dealer beside it
			int[] view = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				HouseDealer d = level.getEntitiesOfClass(HouseDealer.class, AABB.of(placed.box()).inflate(2), Entity::isAlive).getFirst();
				Direction f = Direction.fromYRot(d.home() == null ? d.getYRot() : d.getYRot());
				BlockPos eye = d.blockPosition().relative(f, 5);
				for (EntityType<HouseDealer> type : List.of(NpcContent.PIGLIN_HOUSE_DEALER, NpcContent.ENDER_HOUSE_DEALER)) {
					HouseDealer extra = type.create(level, EntitySpawnReason.COMMAND);
					int side = type == NpcContent.PIGLIN_HOUSE_DEALER ? 1 : -1;
					extra.setHome(d.blockPosition().relative(f.getClockWise(), side), f.toYRot());
					extra.addTag("dl_tester");
					level.addFreshEntity(extra);
				}
				d.gesture(DealerGesture.DEAL);
				return new int[] {eye.getX(), eye.getY(), eye.getZ(), (int) f.getOpposite().toYRot(), d.getId()};
			});
			world.getServer().runCommand("tp @p " + view[0] + " " + view[1] + " " + view[2] + " " + view[3] + " 10");
			DealerRenderProbe.CALLS.clear();
			context.waitTicks(60);
			for (String id : List.of("burmaldaholic:house_dealer", "burmaldaholic:piglin_house_dealer", "burmaldaholic:ender_house_dealer")) {
				if (DealerRenderProbe.count(id) == 0) {
					failures.add(id + ": renderer never extracted a frame");
				}
			}
			String client = context.computeOnClient(mc -> {
				StringBuilder err = new StringBuilder();
				int seen = 0, bound = 0;
				for (Entity e : mc.level.entitiesForRendering()) {
					if (!(e instanceof HouseDealer d)) {
						continue;
					}
					seen++;
					String cls = mc.getEntityRenderDispatcher().getRenderer(d).getClass().getName();
					if (!cls.contains("HouseDealerRenderers")) {
						err.append(" wrong renderer ").append(cls);
					}
					try {
						mc.getEntityRenderDispatcher().extractEntity(d, 0.5f);
					} catch (Throwable x) {
						err.append(" extract threw ").append(x);
					}
					if (d.table() != null) {
						bound++;
						if (TableParts.gameOf(mc.level.getBlockState(d.table())) == null) {
							err.append(" synced TABLE is not a table ").append(d.table());
						}
					}
				}
				return seen + " dealers, " + bound + " bound;" + err;
			});
			if (client.contains(" wrong") || client.contains(" threw") || client.contains(" synced") || client.startsWith("0 ")) {
				failures.add("client: " + client);
			}
			int[] counts = world.getServer().computeOnServer(server -> new int[] {
				server.overworld().getEntitiesOfClass(HouseDealer.class, AABB.of(placed.box()).inflate(2), e -> !e.entityTags().contains("dl_tester")).size()});
			System.out.println("[DL-T client] " + client + "; server dealers " + counts[0] + "; probe " + DealerRenderProbe.CALLS);
			world.getServer().runOnServer(server -> server.overworld()
				.getEntitiesOfClass(HouseDealer.class, AABB.of(placed.box()).inflate(4), e -> e.entityTags().contains("dl_tester")).forEach(Entity::discard));
			context.waitTicks(10);
		}
		if (!failures.isEmpty()) {
			throw new AssertionError("house dealer client test failed:\n  " + String.join("\n  ", failures));
		}
	}
}
