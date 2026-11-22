package dev.alaindustrial.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.alaindustrial.Industrialization;
import dev.alaindustrial.recipe.RecipeConflictScan;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * {@code /ala recipes conflicts} (MOD-1018): runs {@link RecipeConflictScan} over the live server's recipes and
 * reports every pair of one vanilla type where one input gives different results and one recipe is ours. A
 * summary goes to chat, the full list to {@code logs/alaindustrial-recipe-conflicts.txt} in the server directory
 * and to the log. Op-only; registered in production too, because its purpose is a run on a real modpack (041).
 * Feedback is plain English, the op/diagnostic convention of the {@code /ala config} tree.
 */
final class RecipeConflictCommand {

	/** The report file, relative to the server directory. */
	static final String REPORT = "logs/alaindustrial-recipe-conflicts.txt";

	/** Lines of the list repeated in chat; the rest is in the file. */
	private static final int CHAT_LINES = 8;

	private RecipeConflictCommand() {
	}

	static LiteralArgumentBuilder<CommandSourceStack> tree() {
		return Commands.literal("recipes")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("conflicts").executes(ctx -> run(ctx.getSource())));
	}

	private static int run(CommandSourceStack source) {
		MinecraftServer server = source.getServer();
		List<RecipeConflictScan.Conflict> conflicts = RecipeConflictScan.scan(server.getRecipeManager().getRecipes());
		Map<RecipeConflictScan.Kind, Integer> counts = new EnumMap<>(RecipeConflictScan.Kind.class);
		List<String> lines = new ArrayList<>();
		lines.add("# Recipe conflicts with Ala Industrial recipes (MOD-1018): one input, different results.");
		lines.add("# stonecutting is informational: the stonecutter shows every result and the player picks.");
		for (RecipeConflictScan.Conflict conflict : conflicts) {
			counts.merge(conflict.kind(), 1, Integer::sum);
			lines.add(conflict.line());
			Industrialization.LOGGER.info("Recipe conflict: {}", conflict.line());
		}
		Path file = server.getServerDirectory().resolve(REPORT);
		String written;
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
			written = file.toAbsolutePath().normalize().toString();
		} catch (IOException error) {
			Industrialization.LOGGER.warn("Could not write the recipe conflict report to {}", file, error);
			written = "not written (" + error.getMessage() + "), see the log";
		}
		StringBuilder summary = new StringBuilder("Recipe conflicts with our recipes: ").append(conflicts.size());
		if (!counts.isEmpty()) {
			summary.append(" (");
			counts.forEach((kind, n) -> summary.append(kind.id()).append(' ').append(n).append(", "));
			summary.setLength(summary.length() - 2);
			summary.append(')');
		}
		String head = summary.toString();
		String where = written;
		source.sendSuccess(() -> Component.literal(head).withStyle(ChatFormatting.GOLD), false);
		for (int i = 0; i < Math.min(CHAT_LINES, conflicts.size()); i++) {
			String line = conflicts.get(i).line();
			source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
		}
		source.sendSuccess(() -> Component.literal("Full list: " + where).withStyle(ChatFormatting.AQUA), false);
		return Math.max(conflicts.size(), Command.SINGLE_SUCCESS);
	}
}
