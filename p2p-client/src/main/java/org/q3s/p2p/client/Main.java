package org.q3s.p2p.client;

import java.nio.file.Paths;
import org.q3s.p2p.client.util.I18n;
import org.q3s.p2p.client.util.LookAndFeelManager;
import org.q3s.p2p.client.util.UserPreferences;
import org.q3s.p2p.client.view.Controller;

public class Main {

    public static void main(String args[]) {
        UserPreferences.init(Paths.get(Config.SHARED_DIR));
        LookAndFeelManager.apply(UserPreferences.getLookAndFeel());
        I18n.setLocale(UserPreferences.getLanguage());

        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                Controller controller = new Controller();
                controller.start();
            }
        });
    }
}
