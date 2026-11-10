package works.repka.rysquests.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import works.repka.rysquests.RYSQuests;
import works.repka.rysquests.data.Chapter;
import works.repka.rysquests.data.Quest;

/**
 * What the quest editor saves: the quests that changed, one chapter's.
 * @param chapterChanged whether the chapter's own file has to be written
 * @param texts strings in the author's language
 */
public record QuestEditorSavePayload(Chapter chapter, boolean chapterChanged, List<Quest> quests, String language, Map<String, String> texts) implements CustomPacketPayload {
    public static final Type<QuestEditorSavePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RYSQuests.MODID, "quest_editor_save"));
    private static final int MAX_TEXT = 1 << 20;

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestEditorSavePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(Chapter.CODEC), QuestEditorSavePayload::chapter,
            ByteBufCodecs.BOOL, QuestEditorSavePayload::chapterChanged,
            ByteBufCodecs.fromCodecWithRegistries(Quest.CODEC.listOf()), QuestEditorSavePayload::quests,
            ByteBufCodecs.STRING_UTF8, QuestEditorSavePayload::language,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.stringUtf8(MAX_TEXT), ByteBufCodecs.stringUtf8(MAX_TEXT)), QuestEditorSavePayload::texts,
            QuestEditorSavePayload::new);

    @Override
    public Type<QuestEditorSavePayload> type() {
        return TYPE;
    }
}
