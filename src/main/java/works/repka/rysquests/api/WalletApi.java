package works.repka.rysquests.api;

import net.minecraft.server.level.ServerPlayer;
import works.repka.rysquests.shop.ShopService;

/**
 * Coins for other mods and scripts, on the server only. KubeJS server scripts see it as {@code RYSQuestsWallet}.
 * A currency is any name; it is the same name shops and {@code rysquests:currency} rewards use.
 */
public final class WalletApi {
    private WalletApi() {
    }

    public static long balance(ServerPlayer player, String currency) {
        return ShopService.balance(player, false, currency);
    }

    /** @return the team's balance, 0 for a player without a team */
    public static long treasury(ServerPlayer player, String currency) {
        return ShopService.balance(player, true, currency);
    }

    public static void deposit(ServerPlayer player, String currency, long amount) {
        ShopService.change(player, false, currency, ShopService.WalletOp.ADD, positive(amount));
    }

    /** @return false for a player without a team, and then nothing is added */
    public static boolean depositTreasury(ServerPlayer player, String currency, long amount) {
        return ShopService.change(player, true, currency, ShopService.WalletOp.ADD, positive(amount));
    }

    /** @return false when there is not enough, and then nothing is taken */
    public static boolean withdraw(ServerPlayer player, String currency, long amount) {
        return ShopService.change(player, false, currency, ShopService.WalletOp.TAKE, positive(amount));
    }

    /**
     * Turns items from the whole inventory, armor aside, into coins. What does not make up a full batch stays.
     *
     * @param item an item id, or a tag as {@code #c:coins}
     * @param items how many items make one batch
     * @param coins how many coins one batch gives
     * @return the coins added, 0 when there are too few items
     */
    public static long exchange(ServerPlayer player, String item, String currency, int items, long coins) {
        return ShopService.exchange(player, item, currency, items, coins, false);
    }

    /** The same as {@link #exchange}, for what the player holds in the main hand only. */
    public static long exchangeHeld(ServerPlayer player, String item, String currency, int items, long coins) {
        return ShopService.exchange(player, item, currency, items, coins, true);
    }

    private static long positive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("An amount of coins has to be positive, got " + amount);
        }
        return amount;
    }
}
