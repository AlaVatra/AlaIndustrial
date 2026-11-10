package dev.alaindustrial.gametest.forge;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.AbstractPackMetadataResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The in-memory datapack that carries the Forge lane's test instances and environments (MOD-962 stage 3, ADR-053).
 *
 * <p>Forge 66 has no event that registers a test instance from code: {@code TEST_INSTANCE} is a datapack
 * registry and its loader posts nothing a mod can hook. So the instances enter as data, through the same loader
 * and codec as any datapack test: when the game-test server builds its pack repository, {@link AddPackFindersEvent}
 * adds this pack, whose JSON is produced at that moment from {@code ScenarioRoster.ALL} and the hand-wired Forge
 * list. A {@link PackSource#DEFAULT} pack adds itself to the world the game-test server creates, the way Forge's
 * own mod packs do ({@code ResourcePackLoader.findPacks}).
 *
 * <p>Only on the game-test server ({@link ForgeGameTestHooks#isGametestServer()}): Forge's dev client and server
 * runs set {@code forge.enableGameTest} too, and a dev world must not get two thousand test instances.
 */
final class RosterPack {

	private static final Logger LOGGER = LoggerFactory.getLogger("alaindustrial/scenario-roster");

	/** The pack id; the gametest mod's own pack is {@code mod:alaindustrial_gametest}. */
	static final String ID = "alaindustrial_gametest:roster";

	private static @Nullable Pack pack;

	private RosterPack() {}

	static void install() {
		if (!ForgeGameTestHooks.isGametestServer()) {
			return;
		}
		AddPackFindersEvent.BUS.addListener(event -> {
			if (event.getPackType() == PackType.SERVER_DATA) {
				event.addRepositorySource(consumer -> consumer.accept(pack()));
			}
		});
	}

	private static synchronized Pack pack() {
		if (pack != null) {
			return pack;
		}
		RosterPackWriter writer = new RosterPackWriter();
		ScenarioRosterForge.registerInstances(writer);
		ForgeGameTests.registerInstances(writer);
		writer.writeEnvironments();
		PackFormat format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
		String version = "[" + format.major() + "," + format.minor() + "]";
		byte[] meta = ("{\"pack\":{\"description\":\"Ala Industrial gametest roster\",\"min_format\":" + version
				+ ",\"max_format\":" + version + "}}").getBytes(StandardCharsets.UTF_8);
		PackLocationInfo info = new PackLocationInfo(ID, Component.literal("Ala Industrial roster tests"),
				PackSource.DEFAULT, Optional.empty());
		InMemoryPackResources resources = new InMemoryPackResources(info, meta, writer.files());
		Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
			@Override
			public PackMetadataResources openMetadata(PackLocationInfo location) {
				return resources;
			}

			@Override
			public Stream<PackResources> openResources(PackLocationInfo location, Pack.Metadata metadata) {
				return Stream.of(resources);
			}
		};
		Pack built = Pack.readMetaAndCreate(info, supplier, PackType.SERVER_DATA,
				new PackSelectionConfig(false, Pack.Position.TOP, false));
		if (built == null) {
			throw new IllegalStateException("The Forge roster pack " + ID + " has unreadable metadata (format "
					+ version + ")");
		}
		LOGGER.info("[scenario roster] forge: {} test instances, {} environments in pack {}", writer.instances(),
				writer.environments(), ID);
		pack = built;
		return built;
	}

	/** A data pack held in memory: {@code pack.mcmeta} plus a map from resource location to bytes. */
	private static final class InMemoryPackResources extends AbstractPackMetadataResources implements PackResources {

		private final byte[] meta;
		private final Map<Identifier, byte[]> files;
		private final Set<String> namespaces = new HashSet<>();

		InMemoryPackResources(PackLocationInfo location, byte[] meta, Map<Identifier, byte[]> files) {
			super(location);
			this.meta = meta;
			this.files = Map.copyOf(files);
			for (Identifier id : files.keySet()) {
				namespaces.add(id.getNamespace());
			}
		}

		@Override
		public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
			return String.join("/", path).equals(PACK_META) ? supplier(meta) : null;
		}

		@Override
		public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
			byte[] bytes = type == PackType.SERVER_DATA ? files.get(location) : null;
			return bytes == null ? null : supplier(bytes);
		}

		@Override
		public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
			if (type != PackType.SERVER_DATA) {
				return;
			}
			String prefix = directory + "/";
			for (Map.Entry<Identifier, byte[]> file : files.entrySet()) {
				Identifier id = file.getKey();
				if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix)) {
					output.accept(id, supplier(file.getValue()));
				}
			}
		}

		@Override
		public Set<String> getNamespaces(PackType type) {
			return type == PackType.SERVER_DATA ? Set.copyOf(namespaces) : Set.of();
		}

		@Override
		public void close() {
		}

		private static IoSupplier<InputStream> supplier(byte[] bytes) {
			return () -> new ByteArrayInputStream(bytes);
		}
	}
}
