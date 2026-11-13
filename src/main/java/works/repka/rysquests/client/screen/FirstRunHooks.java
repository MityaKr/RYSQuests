package works.repka.rysquests.client.screen;

import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import works.repka.rysquests.client.ClientImport;
import works.repka.rysquests.data.MapForm;

/** The in-game autotest's handles on the first-run pages (pictures 06, 07, 09a, 10, 50). */
public final class FirstRunHooks {
    private FirstRunHooks() {
    }

    @Nullable
    private static BookView page() {
        return Minecraft.getInstance().screen instanceof BookScreen screen ? screen.page() : null;
    }

    @Nullable
    private static ImportWizardView wizard() {
        return page() instanceof ImportWizardView wizard ? wizard : null;
    }

    /** "none", "running", "error", "report" or "forms". */
    public static String wizardState() {
        ImportWizardView wizard = wizard();
        if (wizard == null) {
            return "none";
        }
        return switch (ClientImport.stage()) {
            case FAILED -> "error";
            case DONE -> ClientImport.report() == null ? "running" : wizard.step() == 2 ? "forms" : "report";
            default -> "running";
        };
    }

    public static void wizardNext() {
        ImportWizardView wizard = wizard();
        if (wizard != null) {
            wizard.next();
        }
    }

    @Nullable
    private static MapFormStep forms() {
        ImportWizardView wizard = wizard();
        return wizard == null ? null : wizard.forms();
    }

    public static boolean previewsDone() {
        MapFormStep forms = forms();
        return forms != null && forms.previewsDone();
    }

    public static boolean advisedDone() {
        MapFormStep forms = forms();
        return forms != null && forms.advisedDone();
    }

    public static String advisedForm() {
        MapFormStep forms = forms();
        return forms == null ? "" : forms.advised().getSerializedName();
    }

    public static void selectForm(String name) {
        MapFormStep forms = forms();
        if (forms != null) {
            for (MapForm form : MapForm.values()) {
                if (form.getSerializedName().equals(name)) {
                    forms.select(form);
                }
            }
        }
    }

    public static void chooseForm() {
        MapFormStep forms = forms();
        if (forms != null) {
            forms.choose();
        }
    }

    /** Opens a first-run page without the empty book's button: "import" or "new". */
    public static void open(String which, boolean startImport) {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            ImportWizardView.startSuppressed = !startImport;
            try {
                screen.openPage("import".equals(which) ? new ImportWizardView(screen) : new NewBookView(screen));
            } finally {
                ImportWizardView.startSuppressed = false;
            }
        }
    }

    /** The class of the page over the tab, or "" without one. */
    public static String pageName() {
        BookView page = page();
        return page == null ? "" : page.getClass().getSimpleName();
    }

    public static void newBookAnswer(String answer) {
        if (page() instanceof NewBookView view) {
            view.answer(FirstRun.Answer.valueOf(answer));
        }
    }

    public static void createBook() {
        if (page() instanceof NewBookView view) {
            view.create();
        }
    }

    public static void askToPublish() {
        if (Minecraft.getInstance().screen instanceof BookScreen screen) {
            PublishQuestion.ask(screen);
        }
    }
}
