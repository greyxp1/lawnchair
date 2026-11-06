package app.lawnchair.root;

interface IRootHelper {
    oneway void goToSleep();
    boolean toggleNotifications(String packageName, int uid);
}
