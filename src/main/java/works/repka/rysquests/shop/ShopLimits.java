package works.repka.rysquests.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import works.repka.rysquests.data.ShopOffer;

/** Counts trades against limits, which reset at midnight of the server's own clock (ADR-37). */
public final class ShopLimits {
    /** How many times a trade was made in a period. */
    public record Usage(long period, int count) {
        public static final Codec<Usage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("period").forGetter(Usage::period),
                Codec.INT.fieldOf("count").forGetter(Usage::count)
        ).apply(instance, Usage::new));
    }

    private ShopLimits() {
    }

    /** The number of the current period: a day, a week starting on Monday, or one that never ends. */
    public static long period(ShopOffer.Reset reset, LocalDate today) {
        return switch (reset) {
            case NEVER -> 0;
            case DAILY -> today.toEpochDay();
            case WEEKLY -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay();
        };
    }

    /** What counts toward a limit now: an old period counts as nothing. */
    public static int used(ShopOffer.Limit limit, Usage usage, LocalDate today) {
        return usage != null && usage.period() == period(limit.reset(), today) ? usage.count() : 0;
    }

    public static Usage after(ShopOffer.Limit limit, Usage usage, int times, LocalDate today) {
        return new Usage(period(limit.reset(), today), used(limit, usage, today) + times);
    }
}
