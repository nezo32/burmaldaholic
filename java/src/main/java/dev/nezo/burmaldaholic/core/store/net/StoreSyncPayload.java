package dev.nezo.burmaldaholic.core.store.net;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Server → client: the chip store as the player sees it at the current counter (EC-SPEC §5): offers of the counter's
 * dimension (End-only offers hidden elsewhere), with the price, VIP gate, purchases left today and why it is locked.
 */
public record StoreSyncPayload(List<OfferView> offers, long balance, int tier) implements CustomPacketPayload {
	/**
	 * @param stack       one purchase (count included)
	 * @param limitLeft   purchases left (-1 = unlimited)
	 * @param lockReason  "" = buyable, else {@code vip} / {@code limit} / {@code funds}
	 */
	public record OfferView(String id, String category, ItemStack stack, long price, int minTier, long limitLeft, int limitPerDay,
			int limitEveryDays, String lockReason) {}

	public static CustomPacketPayload.Type<StoreSyncPayload> TYPE;

	public static final StreamCodec<RegistryFriendlyByteBuf, StoreSyncPayload> CODEC = StreamCodec.of(StoreSyncPayload::write, StoreSyncPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, StoreSyncPayload p) {
		buf.writeVarInt(p.offers.size());
		for (OfferView o : p.offers) {
			buf.writeUtf(o.id());
			buf.writeUtf(o.category());
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, o.stack());
			buf.writeVarLong(o.price());
			buf.writeVarInt(o.minTier());
			buf.writeLong(o.limitLeft());
			buf.writeVarInt(o.limitPerDay());
			buf.writeVarInt(o.limitEveryDays());
			buf.writeUtf(o.lockReason());
		}
		buf.writeVarLong(p.balance);
		buf.writeVarInt(p.tier);
	}

	private static StoreSyncPayload read(RegistryFriendlyByteBuf buf) {
		int n = Math.min(buf.readVarInt(), 512);
		List<OfferView> offers = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			offers.add(new OfferView(buf.readUtf(), buf.readUtf(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarLong(), buf.readVarInt(),
				buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readUtf()));
		}
		return new StoreSyncPayload(List.copyOf(offers), buf.readVarLong(), buf.readVarInt());
	}

	@Override
	public Type<StoreSyncPayload> type() {
		return TYPE;
	}
}
