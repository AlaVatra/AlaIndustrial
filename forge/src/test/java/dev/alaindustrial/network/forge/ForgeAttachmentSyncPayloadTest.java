package dev.alaindustrial.network.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.alaindustrial.registry.ModPlayerAttachments;
import dev.alaindustrial.skill.PlayerSkills;
import dev.alaindustrial.skill.SkillBranch;
import dev.alaindustrial.skill.SkillSlot;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import dev.alaindustrial.stats.PlayerModStats;
import org.junit.jupiter.api.Test;

/** The Forge-only attachment sync payload on the wire (MOD-962, slice 2.5). */
class ForgeAttachmentSyncPayloadTest {

	@Test
	void everyAttachmentRoundTrips() {
		roundTrip(new ForgeAttachmentSyncPayload(ModPlayerAttachments.PLAYER_STATS, new PlayerModStats(10L, 4L, 2,
				Map.of(Identifier.fromNamespaceAndPath("alaindustrial", "solar_panel"), 10L), 7L, 1L)));
		roundTrip(new ForgeAttachmentSyncPayload(ModPlayerAttachments.PLAYER_SKILLS,
				PlayerSkills.EMPTY.with(SkillBranch.ENERGY, SkillSlot.IN)));
		for (ModPlayerAttachments.PlayerAttachmentDef<?> def : ModPlayerAttachments.PLAYER_ATTACHMENTS) {
			roundTrip(new ForgeAttachmentSyncPayload(def, def.empty()));
		}
	}

	@Test
	void anUnknownIdIsADecodeError() {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		buf.writeUtf("no_such_attachment");
		assertThrows(DecoderException.class, () -> ForgeAttachmentSyncPayload.CODEC.decode(buf));
	}

	private static void roundTrip(ForgeAttachmentSyncPayload sent) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		ForgeAttachmentSyncPayload.CODEC.encode(buf, sent);
		ForgeAttachmentSyncPayload received = ForgeAttachmentSyncPayload.CODEC.decode(buf);
		assertSame(sent.def(), received.def());
		assertEquals(sent.value(), received.value(), sent.def().id());
		assertEquals(0, buf.readableBytes(), "the reader must consume exactly what the writer wrote");
	}
}
