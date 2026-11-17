package works.repka.rysquests.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import works.repka.ryscore.team.TeamsApi;
import works.repka.rysquests.data.ShopOffer;
import works.repka.rysquests.data.ShopOffer.Entry;
import works.repka.rysquests.data.ShopOffer.Kind;

/** The team's treasury is spent only with the team's right to it (ADR-52); putting money in needs none. */
class TreasuryRightTest {
    private static ShopOffer offer(Entry cost, Entry gives) {
        return new ShopOffer(ResourceLocation.parse("t:offer"), Optional.empty(), Optional.empty(), Optional.empty(), List.of(cost), List.of(gives),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Entry coins(int amount) {
        return new Entry(Kind.CURRENCY, "coin", amount);
    }

    private static Entry wheat(int count) {
        return new Entry(Kind.ITEM, "minecraft:wheat", count);
    }

    @Test
    void onlyAPurchasePaidFromTheTreasurySpendsIt() {
        assertTrue(ShopService.spendsTreasury(offer(coins(4), wheat(16)), true));
        assertFalse(ShopService.spendsTreasury(offer(coins(4), wheat(16)), false));
        // A sale into the treasury only adds to it, and a barter of items never touches money.
        assertFalse(ShopService.spendsTreasury(offer(wheat(16), coins(4)), true));
        assertFalse(ShopService.spendsTreasury(offer(wheat(16), wheat(1)), true));
    }

    @Test
    void theRightIsAskedOfTheTeamModByIdAlone() {
        UUID officer = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        List<String> asked = new ArrayList<>();
        TeamsApi before = TeamsApi.get();
        TeamsApi.provide(new TeamsApi() {
            @Override
            public UUID teamOf(UUID player) {
                return player;
            }

            @Override
            public Collection<UUID> members(UUID team) {
                return List.of(team);
            }

            @Override
            public boolean can(UUID player, String permission) {
                asked.add(permission);
                return Set.of(officer).contains(player);
            }

            @Override
            public void post(UUID team, Post post) {
            }
        });
        try {
            // Neither player is online here: a shop block asks for its owner the same way.
            assertTrue(ShopService.maySpendTreasury(officer));
            assertFalse(ShopService.maySpendTreasury(guest));
            assertEquals(List.of("spend_treasury", "spend_treasury"), asked);
        } finally {
            TeamsApi.provide(before);
        }
    }
}
