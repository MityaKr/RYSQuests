package works.repka.rysquests.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import works.repka.rysquests.data.Achievement;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.BookReport;
import works.repka.rysquests.data.BookReport.Issue;
import works.repka.rysquests.data.BookReport.Severity;
import works.repka.rysquests.data.Quest;
import works.repka.rysquests.data.QuestBook;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.data.TaskDef;
import works.repka.rysquests.dev.DevReports;
import works.repka.rysquests.integration.ftbq.FtbqFiles;
import works.repka.rysquests.integration.ftbq.FtbqImporter;
import works.repka.rysquests.integration.ftbq.FtbqProgress;
import works.repka.rysquests.integration.ftbq.ImportedPack;
import works.repka.rysquests.integration.patchouli.PatchouliImporter;
import works.repka.rysquests.editor.EditorFiles;
import works.repka.rysquests.editor.EditorFolder;
import works.repka.rysquests.migrate.MigrationEngine;
import works.repka.rysquests.migrate.MigrationRunner;
import works.repka.rysquests.progress.AchievementService;
import works.repka.rysquests.progress.ProgressService;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.progress.QuestProgress;
import works.repka.rysquests.reward.CurrencyReward;
import works.repka.rysquests.shop.ShopService;
import works.repka.rysquests.shop.ShopService.WalletOp;
import works.repka.rysquests.task.ItemRef;

/** The {@code /rysquests} command tree. */
public final class QuestCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestCommands.class);
    private static final String REPORT_FILE = "rysquests-validate.txt";
    private static final int ISSUES_IN_CHAT = 10;
    private static final String DEFAULT_IMPORT_NAMESPACE = "ftbq";
    private static final String DEFAULT_PATCHOULI_NAMESPACE = "patchouli";
    private static final java.util.regex.Pattern PLAIN_NAME = java.util.regex.Pattern.compile("[a-z0-9_]+");
    private static final Map<UUID, UUID> INVITES = new HashMap<>();
    private static final SuggestionProvider<CommandSourceStack> OFFERS = (context, builder) -> SharedSuggestionProvider.suggestResource(BookHolder.current().book().offers().keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> ACHIEVEMENTS = (context, builder) -> SharedSuggestionProvider.suggestResource(BookHolder.current().book().achievements().keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> CURRENCIES = (context, builder) -> SharedSuggestionProvider.suggest(currencies(), builder);

    private QuestCommands() {
    }

    /** The currencies the book mentions, for suggestions: a currency has no list of its own. */
    private static Set<String> currencies() {
        QuestBook book = BookHolder.current().book();
        Set<String> names = new TreeSet<>();
        for (ShopOffer offer : book.offers().values()) {
            Stream.concat(offer.cost().stream(), offer.gives().stream()).filter(ShopOffer.Entry::isCurrency).forEach(entry -> names.add(entry.id()));
        }
        for (Quest quest : book.quests().values()) {
            quest.rewards().forEach(reward -> {
                if (reward instanceof CurrencyReward currency) {
                    names.add(currency.id());
                }
            });
        }
        return names;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rysquests")
                .then(Commands.literal("team")
                        .then(Commands.literal("create").executes(QuestCommands::teamCreate))
                        .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(QuestCommands::teamInvite)))
                        .then(Commands.literal("join").executes(QuestCommands::teamJoin))
                        .then(Commands.literal("leave").executes(QuestCommands::teamLeave)))
                .then(admin("migrate")
                        .then(Commands.literal("--dry-run").executes(context -> migrate(context, false)))
                        .then(Commands.literal("--apply").executes(context -> migrate(context, true))))
                .then(admin("dev")
                        .then(Commands.literal("report").executes(QuestCommands::devReport))
                        .then(Commands.literal("registry").executes(QuestCommands::devRegistry)))
                .then(admin("reload").executes(QuestCommands::reload))
                .then(admin("validate").executes(QuestCommands::validate))
                .then(walletBranch(admin("wallet"), false).then(walletBranch(Commands.literal("treasury"), true)))
                .then(admin("shop").then(Commands.literal("reset").then(Commands.argument("players", EntityArgument.players())
                        .executes(context -> shopReset(context, null))
                        .then(Commands.argument("offer", ResourceLocationArgument.id()).suggests(OFFERS)
                                .executes(context -> shopReset(context, ResourceLocationArgument.getId(context, "offer")))))))
                .then(admin("title").then(titleAction("give", true)).then(titleAction("take", false)))
                .then(admin("achievement").then(achievementAction("complete", true)).then(achievementAction("reset", false)))
                .then(admin("import").then(Commands.literal("patchouli").then(Commands.argument("book", StringArgumentType.word())
                                .executes(context -> importPatchouli(context, DEFAULT_PATCHOULI_NAMESPACE))
                                .then(Commands.argument("namespace", StringArgumentType.word())
                                        .executes(context -> importPatchouli(context, StringArgumentType.getString(context, "namespace"))))))
                        .then(Commands.literal("ftbq")
                        .executes(context -> importFtbq(context, DEFAULT_IMPORT_NAMESPACE))
                        .then(Commands.literal("progress").executes(QuestCommands::importFtbqProgress))
                        .then(Commands.argument("namespace", StringArgumentType.word())
                                .executes(context -> importFtbq(context, StringArgumentType.getString(context, "namespace"))))))
                .then(admin("progress").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.literal("reset").then(Commands.argument("quest", ResourceLocationArgument.id())
                                .executes(context -> setProgress(context, false))))
                        .then(Commands.literal("complete").then(Commands.argument("quest", ResourceLocationArgument.id())
                                .executes(context -> setProgress(context, true)))))));
    }

    /** Writes logs/rysquests-report.json and .csv, for tools as much as for people. */
    private static int devReport(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        DevReports.Standing standing = DevReports.standing(server);
        Util.backgroundExecutor().execute(() -> {
            DevReports.Report report = DevReports.build(server, standing);
            DevReports.write(server, report);
            server.execute(() -> source.sendSuccess(() -> Component.translatable("rysquests.dev.report_done", DevReports.REPORT_JSON), false));
        });
        return 1;
    }

    /** Writes every id a book may use, so that a book can be checked offline. */
    private static int devRegistry(CommandContext<CommandSourceStack> context) {
        DevReports.writeRegistry(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.translatable("rysquests.dev.registry_done", DevReports.REGISTRY_JSON), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> walletBranch(LiteralArgumentBuilder<CommandSourceStack> root, boolean treasury) {
        return root
                .then(Commands.literal("get").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("currency", StringArgumentType.word()).suggests(CURRENCIES).executes(context -> walletGet(context, treasury)))))
                .then(walletAction("add", WalletOp.ADD, 1, treasury))
                .then(walletAction("take", WalletOp.TAKE, 1, treasury))
                .then(walletAction("set", WalletOp.SET, 0, treasury));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> walletAction(String name, WalletOp op, long minimum, boolean treasury) {
        return Commands.literal(name).then(Commands.argument("players", EntityArgument.players())
                .then(Commands.argument("currency", StringArgumentType.word()).suggests(CURRENCIES)
                        .then(Commands.argument("amount", LongArgumentType.longArg(minimum)).executes(context -> walletChange(context, op, treasury)))));
    }

    /** @return the balance, so that {@code /execute store} and {@code /execute if} can use it */
    private static int walletGet(CommandContext<CommandSourceStack> context, boolean treasury) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String currency = StringArgumentType.getString(context, "currency");
        if (treasury && !ShopService.hasTreasury(player)) {
            context.getSource().sendFailure(Component.translatable("rysquests.wallet.no_team", player.getDisplayName()));
            return 0;
        }
        long balance = ShopService.balance(player, treasury, currency);
        context.getSource().sendSuccess(() -> Component.translatable(treasury ? "rysquests.wallet.treasury" : "rysquests.wallet.balance", player.getDisplayName(), balance, currency), false);
        return (int) Math.min(balance, Integer.MAX_VALUE);
    }

    /** @return how many players it worked for */
    private static int walletChange(CommandContext<CommandSourceStack> context, WalletOp op, boolean treasury) throws CommandSyntaxException {
        String currency = StringArgumentType.getString(context, "currency");
        long amount = LongArgumentType.getLong(context, "amount");
        int done = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
            if (treasury && !ShopService.hasTreasury(player)) {
                context.getSource().sendFailure(Component.translatable("rysquests.wallet.no_team", player.getDisplayName()));
            } else if (ShopService.change(player, treasury, currency, op, amount)) {
                long balance = ShopService.balance(player, treasury, currency);
                context.getSource().sendSuccess(() -> Component.translatable(treasury ? "rysquests.wallet.treasury" : "rysquests.wallet.balance", player.getDisplayName(), balance, currency), true);
                done++;
            } else {
                context.getSource().sendFailure(Component.translatable("rysquests.wallet.not_enough", player.getDisplayName(), ShopService.balance(player, treasury, currency), currency));
            }
        }
        return done;
    }

    private static int shopReset(CommandContext<CommandSourceStack> context, @Nullable ResourceLocation offer) throws CommandSyntaxException {
        if (offer != null && !BookHolder.current().book().offers().containsKey(offer)) {
            context.getSource().sendFailure(Component.translatable("rysquests.shop.unknown_offer", offer.toString()));
            return 0;
        }
        var players = EntityArgument.getPlayers(context, "players");
        players.forEach(player -> ShopService.resetLimits(player, offer));
        context.getSource().sendSuccess(() -> Component.translatable("rysquests.shop.limits_reset", players.size()), true);
        return players.size();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> titleAction(String name, boolean give) {
        return Commands.literal(name).then(Commands.argument("players", EntityArgument.players())
                .then(Commands.argument("title", StringArgumentType.string()).executes(context -> {
                    String title = StringArgumentType.getString(context, "title");
                    var players = EntityArgument.getPlayers(context, "players");
                    players.forEach(player -> {
                        if (give) {
                            AchievementService.grantTitle(player, title);
                        } else {
                            AchievementService.takeTitle(player, title);
                        }
                    });
                    context.getSource().sendSuccess(() -> Component.translatable(give ? "rysquests.title.given" : "rysquests.title.taken", Component.translatable(title), players.size()), true);
                    return players.size();
                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> achievementAction(String name, boolean completed) {
        return Commands.literal(name).then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("achievement", ResourceLocationArgument.id()).suggests(ACHIEVEMENTS)
                        .executes(context -> achievement(context, completed, null))
                        .then(Commands.argument("tier", StringArgumentType.word()).executes(context -> achievement(context, completed, StringArgumentType.getString(context, "tier"))))));
    }

    /** Completing a tier takes the ones below it along; resetting one takes the ones above. Rewards follow as usual. */
    private static int achievement(CommandContext<CommandSourceStack> context, boolean completed, @Nullable String tierName) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ResourceLocation id = ResourceLocationArgument.getId(context, "achievement");
        QuestBook book = BookHolder.current().book();
        Achievement achievement = book.achievements().get(id);
        if (achievement == null) {
            context.getSource().sendFailure(Component.translatable("rysquests.achievement.unknown", id.toString()));
            return 0;
        }
        List<Achievement.Tier> tiers = achievement.tiers();
        int index = completed ? tiers.size() - 1 : 0;
        if (tierName != null) {
            index = tiers.stream().map(Achievement.Tier::tier).toList().indexOf(tierName);
            if (index < 0) {
                context.getSource().sendFailure(Component.translatable("rysquests.achievement.unknown_tier", tierName));
                return 0;
            }
        }
        int changed = 0;
        for (int i = completed ? 0 : index; i <= (completed ? index : tiers.size() - 1); i++) {
            Quest quest = book.quests().get(achievement.questId(tiers.get(i)));
            if (quest != null) {
                ProgressService.setCompleted(player, quest, completed);
                changed++;
            }
        }
        int tiersChanged = changed;
        context.getSource().sendSuccess(() -> Component.translatable(completed ? "rysquests.achievement.completed" : "rysquests.achievement.reset", id.toString(), tiersChanged), true);
        return changed;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> admin(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS));
    }

    private static int teamCreate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ProgressStore.get(player.server).setTeam(player.getUUID(), player.getUUID());
        refreshTeam(player);
        context.getSource().sendSuccess(() -> Component.translatable("rysquests.team.created"), false);
        return 1;
    }

    private static int teamInvite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        INVITES.put(target.getUUID(), player.getUUID());
        target.sendSystemMessage(Component.translatable("rysquests.team.invite_received", player.getDisplayName()));
        context.getSource().sendSuccess(() -> Component.translatable("rysquests.team.invited", target.getDisplayName()), false);
        return 1;
    }

    private static int teamJoin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UUID team = INVITES.remove(player.getUUID());
        if (team == null) {
            context.getSource().sendFailure(Component.translatable("rysquests.team.no_invite"));
            return 0;
        }
        ProgressStore.get(player.server).setTeam(player.getUUID(), team);
        refreshTeam(player);
        context.getSource().sendSuccess(() -> Component.translatable("rysquests.team.joined"), false);
        return 1;
    }

    private static int teamLeave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ProgressStore.get(player.server).leaveTeam(player.getUUID());
        refreshTeam(player);
        context.getSource().sendSuccess(() -> Component.translatable("rysquests.team.left"), false);
        return 1;
    }

    private static void refreshTeam(ServerPlayer player) {
        ProgressService.invalidateAll();
        player.server.getPlayerList().getPlayers().forEach(ProgressService::sendAll);
    }

    private static int migrate(CommandContext<CommandSourceStack> context, boolean apply) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        MigrationEngine.State snapshot = MigrationRunner.snapshot(server);
        Util.backgroundExecutor().execute(() -> {
            MigrationEngine.Result result = MigrationRunner.run(snapshot);
            server.execute(() -> {
                if (!result.changed()) {
                    source.sendSuccess(() -> Component.translatable("rysquests.migrate.none"), false);
                    return;
                }
                result.report().forEach(line -> source.sendSuccess(() -> Component.translatable("rysquests.migrate.line", line), false));
                if (apply) {
                    MigrationRunner.commit(server, result);
                    server.getPlayerList().getPlayers().forEach(ProgressService::sendAll);
                } else {
                    MigrationRunner.writeReport(server, result, "dry run");
                }
                source.sendSuccess(() -> Component.translatable(apply ? "rysquests.migrate.applied" : "rysquests.migrate.dry_run"), false);
            });
        });
        return 1;
    }

    private static int setProgress(CommandContext<CommandSourceStack> context, boolean completed) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ResourceLocation id = ResourceLocationArgument.getId(context, "quest");
        Quest quest = BookHolder.current().book().quests().get(id);
        if (quest == null) {
            context.getSource().sendFailure(Component.translatable("rysquests.progress.unknown_quest", id.toString()));
            return 0;
        }
        ProgressService.setCompleted(player, quest, completed);
        return 1;
    }

    /** Parses in the background; the store is only touched on the server thread. */
    private static int importFtbqProgress(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        QuestBook book = BookHolder.current().book();
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("ftbquests");
        Path root = ImportedPack.root();
        Util.backgroundExecutor().execute(() -> {
            try {
                Map<UUID, Map<ResourceLocation, QuestProgress>> teams = FtbqProgress.read(dir, FtbqFiles.readIds(root), book);
                server.execute(() -> {
                    ProgressStore store = ProgressStore.get(server);
                    teams.forEach(store::replaceTeam);
                    ProgressService.invalidateAll();
                    server.getPlayerList().getPlayers().forEach(ProgressService::sendAll);
                    int quests = teams.values().stream().mapToInt(Map::size).sum();
                    source.sendSuccess(() -> Component.translatable("rysquests.import.progress", teams.size(), quests), false);
                });
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("FTB Quests progress import failed", e);
                server.execute(() -> source.sendFailure(Component.translatable("rysquests.import.failed", String.valueOf(e.getMessage()))));
            }
        });
        return 1;
    }

    /** Converts in the background, then reloads so that the new pack is picked up. */
    private static int importFtbq(CommandContext<CommandSourceStack> context, String namespace) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Path quests = FMLPaths.CONFIGDIR.get().resolve("ftbquests").resolve("quests");
        Path root = ImportedPack.root();
        Util.backgroundExecutor().execute(() -> {
            try {
                FtbqImporter.Result result = FtbqImporter.convert(FtbqFiles.read(quests), namespace, FtbqFiles.readIds(root));
                FtbqFiles.write(root, result);
                server.execute(() -> {
                    source.sendSuccess(() -> Component.translatable("rysquests.import.done", result.chapters(), result.quests(), result.notes().stream().filter(note -> !note.startsWith("Summary: ")).count()), false);
                    server.getCommands().performPrefixedCommand(source, "reload");
                });
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("FTB Quests import failed", e);
                server.execute(() -> source.sendFailure(Component.translatable("rysquests.import.failed", String.valueOf(e.getMessage()))));
            }
        });
        return 1;
    }

    /** Reads a book from {@code patchouli_books/} into the editor's folder, then reloads so that the guides appear. */
    private static int importPatchouli(CommandContext<CommandSourceStack> context, String namespace) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        String book = StringArgumentType.getString(context, "book");
        if (!PLAIN_NAME.matcher(book).matches() || !PLAIN_NAME.matcher(namespace).matches()) {
            source.sendFailure(Component.translatable("rysquests.import.failed", book));
            return 0;
        }
        Path folder = server.getServerDirectory().resolve("patchouli_books").resolve(book);
        Path root = EditorFolder.root();
        boolean createPack = !EditorFolder.servedByKubeJs();
        Util.backgroundExecutor().execute(() -> {
            try {
                PatchouliImporter.Result result = PatchouliImporter.convert(folder, book, namespace);
                EditorFiles.writeImported(root, namespace, result.files(), result.lang(), createPack);
                result.notes().forEach(note -> LOGGER.debug("Patchouli import: {}", note));
                server.execute(() -> {
                    source.sendSuccess(() -> Component.translatable("rysquests.import.patchouli", result.guides(), result.pages(), result.structures(), result.notes().size()), false);
                    server.getCommands().performPrefixedCommand(source, "reload");
                });
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Patchouli import failed", e);
                server.execute(() -> source.sendFailure(Component.translatable("rysquests.import.failed", String.valueOf(e.getMessage()))));
            }
        });
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.getServer().getCommands().performPrefixedCommand(source, "reload");
        return 1;
    }

    /** Tags are bound only after a reload ends, so they are checked here rather than by the loader. */
    private static int validate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        BookHolder.Loaded loaded = BookHolder.current();
        Path file = server.getServerDirectory().resolve("logs").resolve(REPORT_FILE);
        Util.backgroundExecutor().execute(() -> {
            List<Issue> issues = new ArrayList<>(loaded.report().issues());
            loaded.book().quests().values().forEach(quest -> checkTags(quest, issues));
            BookReport report = new BookReport(List.copyOf(issues));
            write(file, report);
            server.execute(() -> send(source, report, file));
        });
        return 1;
    }

    private static void checkTags(Quest quest, List<Issue> issues) {
        for (TaskDef task : quest.tasks()) {
            for (ItemRef ref : task.itemRefs()) {
                if (ref.tag() && isEmptyTag(ref)) {
                    issues.add(new Issue(Severity.WARNING, "empty_tag", quest.id().toString(), ref.toString()));
                }
            }
        }
    }

    private static boolean isEmptyTag(ItemRef ref) {
        return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, ref.id()))
                .map(tag -> tag.size() == 0)
                .orElse(true);
    }

    private static void write(Path file, BookReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("errors: " + report.errors() + ", warnings: " + report.warnings());
        report.issues().forEach(issue -> lines.add(issue.toString()));
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Could not write {}", file, e);
        }
    }

    private static void send(CommandSourceStack source, BookReport report, Path file) {
        ChatFormatting color = report.errors() > 0 ? ChatFormatting.RED : ChatFormatting.GREEN;
        source.sendSuccess(() -> Component.translatable("rysquests.validate.summary", report.errors(), report.warnings()).withStyle(color), false);
        report.issues().stream().limit(ISSUES_IN_CHAT).forEach(issue -> source.sendSuccess(() -> Component.translatable(
                "rysquests.validate.issue." + issue.code(), issue.subject(), issue.detail())
                .withStyle(issue.severity() == Severity.ERROR ? ChatFormatting.RED : ChatFormatting.YELLOW), false));
        if (report.issues().size() > ISSUES_IN_CHAT) {
            source.sendSuccess(() -> Component.translatable("rysquests.validate.more", report.issues().size() - ISSUES_IN_CHAT, file.getFileName().toString()), false);
        }
    }
}
