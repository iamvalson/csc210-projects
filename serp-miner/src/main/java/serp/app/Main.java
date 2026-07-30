package serp.app;

import javafx.application.Application;
import serp.gui.MinerApp;

/** Launches the JavaFX GUI when run with no args (or --gui), otherwise runs the CLI. */
public final class Main {

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || hasFlag(args, "--gui")) {
            Application.launch(MinerApp.class, args);
        } else {
            Cli.run(args);
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equals(flag)) {
                return true;
            }
        }
        return false;
    }
}
