package works.repka.rysquests.block;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.data.BookHolder;
import works.repka.rysquests.data.ProgressMode;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.progress.ProgressStore;
import works.repka.rysquests.shop.ShopService;
import works.repka.rysquests.shop.TradeCore;
import works.repka.rysquests.shop.TradeSink;
import works.repka.rysquests.shop.TradeSource;

/**
 * What the intake block asks of the shop: its trades and the trade's core for an owner who may be offline. On a server it is the
 * shop's own ({@link #of}); the tests give one over a bare progress store.
 */
public interface ReceiverMarket {
    @Nullable
    ShopOffer offer(ResourceLocation id);

    /** Sells as many times as the source allows, up to {@code times}. */
    TradeCore.Outcome sell(UUID owner, ShopOffer offer, int times, boolean treasury, TradeSource from);

    /** Whether the trade could be made once, changing nothing. @return the lang key of a refusal, or null */
    @Nullable
    String check(UUID owner, ShopOffer offer, boolean treasury, TradeSource from);

    /** Gives what the source holds of a delivery's rows to the owner's team's count, never more than it lacks. */
    TradeCore.Delivered deliver(UUID owner, ShopOffer offer, TradeSource from, boolean simulate);

    /** The team of a player, the player alone when there is none. */
    UUID team(UUID player);

    /** The balance of a wallet: the owner's own, or the treasury of the owner's team. */
    long balance(UUID owner, boolean treasury, String currency);

    static ReceiverMarket of(MinecraftServer server) {
        return new ReceiverMarket() {
            @Override
            public @Nullable ShopOffer offer(ResourceLocation id) {
                return BookHolder.current().book().offers().get(id);
            }

            @Override
            public TradeCore.Outcome sell(UUID owner, ShopOffer offer, int times, boolean treasury, TradeSource from) {
                return ShopService.tradeFor(server, owner, offer, times, treasury, from, TradeSink.NONE);
            }

            @Override
            public @Nullable String check(UUID owner, ShopOffer offer, boolean treasury, TradeSource from) {
                return ShopService.checkFor(server, owner, offer, treasury, from, TradeSink.NONE);
            }

            @Override
            public TradeCore.Delivered deliver(UUID owner, ShopOffer offer, TradeSource from, boolean simulate) {
                return ShopService.deliverFor(server, owner, offer, from, simulate);
            }

            @Override
            public UUID team(UUID player) {
                return ProgressStore.get(server).teamOf(player, ProgressMode.TEAM);
            }

            @Override
            public long balance(UUID owner, boolean treasury, String currency) {
                ProgressStore store = ProgressStore.get(server);
                UUID team = team(owner);
                return treasury && team.equals(owner) ? 0 : store.balance(treasury ? team : owner, currency);
            }
        };
    }
}
