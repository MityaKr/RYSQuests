package works.repka.rysquests.client.layout;

/**
 * What the galaxy form makes of a book, for the words of the form preview (picture 07): "4 основные главы и 63 главы по отдельным
 * модам ... 10 групп расходятся рукавами". Asks the form itself, so that the words never drift from the drawing.
 *
 * @param chapters chapters on the map
 * @param core chapters in the galaxy's core
 * @param arms arms around it
 */
public record FormFacts(int chapters, int core, int arms) {
    public static FormFacts of(MapBook book) {
        if (book.chapters().isEmpty()) {
            return new FormFacts(0, 0, 0);
        }
        var core = Forms.core(book);
        return new FormFacts(book.chapters().size(), core.size(), Forms.arms(book, core).size());
    }
}
