package works.repka.rysquests.client.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import works.repka.ryscore.ui.Notice;
import works.repka.rysquests.block.ModBlocks;
import works.repka.rysquests.block.ReceiverLamp;
import works.repka.rysquests.client.ClientBook;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.network.BlockNoticePayload;

/**
 * "Your block stopped" as one of the family's notices (decision on question 17 of the shop plan): the block's icon, what stopped it,
 * the trade and where it stands. The server sends at most one a minute; several blocks at once come as one notice with "and N more".
 */
public final class BlockStopNotice {
    /** For the in-game autotest: how many notices came, and the last one as "kind status more". */
    private static int shown;
    private static String last = "";

    private BlockStopNotice() {
    }

    public static int shownForTest() {
        return shown;
    }

    public static String lastForTest() {
        return last;
    }

    private static String t(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    public static void show(BlockNoticePayload payload) {
        boolean dispenser = "dispenser".equals(payload.kind());
        ItemStack icon = new ItemStack(dispenser ? ModBlocks.DISPENSER_ITEM.get() : ModBlocks.RECEIVER_ITEM.get());
        String reason = t("rysquests.notice.block.reason." + payload.status().name().toLowerCase(java.util.Locale.ROOT));
        ShopOffer offer = payload.offer().map(id -> ClientBook.current().offers().get(id)).orElse(null);
        BlockPos pos = payload.pos();
        String where = t("rysquests.notice.block.where", pos.getX(), pos.getY(), pos.getZ());
        String title = payload.more() > 0 ? t("rysquests.notice.block.title_more", Plural.full("rysquests.plural.block", payload.more() + 1L))
                : t(dispenser ? "rysquests.notice.block.title.dispenser" : "rysquests.notice.block.title.receiver");
        String longest = offer == null ? reason + " · " + where : reason + " · " + ShopView.name(offer) + " · " + where;
        Notice notice = Notice.card(Notice.QUESTS, "block:" + payload.kind() + ":" + pos.asLong())
                .item(icon)
                .title(title)
                .lines(longest, reason + " · " + where, reason);
        if (payload.status().lamp() == ReceiverLamp.RED) {
            notice.bad();
        }
        notice.show();
        shown++;
        last = payload.kind() + " " + payload.status().name() + " " + payload.more();
    }
}
