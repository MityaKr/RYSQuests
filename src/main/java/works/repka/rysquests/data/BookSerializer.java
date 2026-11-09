package works.repka.rysquests.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;

/** Converts a book to and from the gzipped NBT sent to clients. */
public final class BookSerializer {
    private BookSerializer() {
    }

    public static byte[] encode(QuestBook book, HolderLookup.Provider registries) {
        CompoundTag tag = (CompoundTag) QuestBook.Wire.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), book.wire())
                .getOrThrow();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            NbtIo.writeCompressed(tag, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    public static QuestBook decode(byte[] data, String hash, HolderLookup.Provider registries) {
        CompoundTag tag;
        try {
            tag = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return QuestBook.fromWire(
                QuestBook.Wire.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow(), hash);
    }
}
