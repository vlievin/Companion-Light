package comvlievin.github.companionlight;

/**
 * Created by valentin on 21/06/16.
 */
public class Constants {
    public static final int THRESHOLD_RELAXING = 60;
    public static final int THRESHOLD_OFF = 78;
    public static final float LUX_DAY = 300;
    public static final int GAIN_NIGHT = 15;
    public static final int GAIN_DAY = 100;
    public static final String WORK_KEY = "work";
    public static final String RELAX__KEY = "relax";
    public static final int LUX_FILTER_LENGTH = 6;
    public static final int RSSI_FILTER_LENGTH = 3;
    public static final int TRAINING_SIZE = 2;


    public interface ACTION {
        public static String MAIN_ACTION = "comvlievin.github.companionlight.action.main";
        public static String WORK_MODE = "comvlievin.github.companionlight.action.prev";
        public static String RELAX_MODE = "comvlievin.github.companionlight.action.relax";
        public static String CONNECT_ORDER_FROM_NOTIF = "comvlievin.github.companionlight.action.play";
        public static String AUTO_MODE = "comvlievin.github.companionlight.action.next";
        public static String STARTFOREGROUND_ACTION = "comvlievin.github.companionlight.action.startforeground";
        public static String STOPFOREGROUND_ACTION = "comvlievin.github.companionlight.action.stopforeground";
        public static final String SCAN_ACTION = "comvlievin.github.companionlight.action.scanOrder";
        public static final String ON_HUE_CHANGE = "comvlievin.github.companionlight.action.changeHue";
        public static final String ON_GAIN_CHANGE = "comvlievin.github.companionlight.action.changeGain";
        public static final String ON_AUTO_CHANGE = "comvlievin.github.companionlight.action.changeAuto";
        public static final String RESET_ORDER = "comvlievin.github.companionlight.action.reset";
    }

    public interface NOTIFICATION_ID {
        public static int FOREGROUND_SERVICE = 101;
    }
}
