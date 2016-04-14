package bin.xposed.Unblock163MusicClient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.AbstractHttpClient;
import org.xbill.DNS.TextParseException;

import java.net.URI;
import java.net.URISyntaxException;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

public class Main implements IXposedHookLoadPackage {
    public static String HOOK_UTILS;
    public static String HOOK_CONSTRUCTOR;
    public static final String VERSION_2_9_3 = "2.9.3";
    public static final String VERSION_3_2_1 = "3.2.1";
    public static final String VERSION_3_3_0 = "3.3.0";
    public static final String VERSION_3_3_1 = "3.3.1";
    public static final String VERSION_3_3_2 = "3.3.2";
    public static String VERSION;

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals(BuildConfig.APPLICATION_ID)) {
            // make current module activated
            findAndHookMethod("bin.xposed.Unblock163MusicClient.ui.SettingsActivity", lpparam.classLoader,
                    "getActivatedModuleVersion", XC_MethodReplacement.returnConstant(BuildConfig.VERSION_CODE));
        }

        if (lpparam.packageName.equals("com.netease.cloudmusic")) {
            final Object activityThread = callStaticMethod(findClass("android.app.ActivityThread", null), "currentActivityThread");
            final Context systemContext = (Context) callMethod(activityThread, "getSystemContext");
            VERSION = systemContext.getPackageManager().getPackageInfo(lpparam.packageName, 0).versionName;
            switch (VERSION) {
                case VERSION_3_3_2:
                case VERSION_3_3_1:
                case VERSION_3_3_0:
                    HOOK_UTILS = "com.netease.cloudmusic.utils.w";
                    HOOK_CONSTRUCTOR = "com.netease.cloudmusic.i.f";
                    break;
                case VERSION_3_2_1:
                    HOOK_UTILS = "com.netease.cloudmusic.utils.n";
                    HOOK_CONSTRUCTOR = "com.netease.cloudmusic.i.b";
                    break;
                case VERSION_2_9_3:
                    HOOK_UTILS = "com.netease.cloudmusic.utils.af";
                    HOOK_CONSTRUCTOR = "com.netease.cloudmusic.h.h";
                    break;
                default:
                    HOOK_UTILS = "com.netease.cloudmusic.utils.u";
                    HOOK_CONSTRUCTOR = "com.netease.cloudmusic.i.f";
                    break;
            }

            Utility.init(lpparam.classLoader);
            findAndHookMethod(HOOK_UTILS, lpparam.classLoader, "i", new XC_MethodHook() { //3.1.4
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Exception {
                            String url = (String) Utility.FIELD_utils_c.get(param.thisObject);
                            XposedBridge.log(url);
                            if (url.startsWith("http://music.163.com/eapi/")) {
                                String path = url.replace("http://music.163.com", "");
                                XposedBridge.log("修改前" + param.getResult());
                                if (path.startsWith("/eapi/batch")
                                        || path.startsWith("/eapi/song/enhance/privilege")
                                        || path.startsWith("/eapi/v1/artist")
                                        || path.startsWith("/eapi/v1/album")
                                        || path.startsWith("/eapi/v1/discovery/new/songs")
                                        || path.startsWith("/eapi/v1/play/record")
                                        || path.startsWith("/eapi/v1/search/get")
                                        || path.startsWith("/eapi/v3/playlist/detail")
                                        || path.startsWith("/eapi/v3/song/detail")) {
                                    String modified = Utility.modifyDetailApi((String) param.getResult());
                                    XposedBridge.log("修改后：" + modified);
                                    param.setResult(modified);

                                } else if (path.startsWith("/eapi/song/enhance/player/url")) {
                                    String modified = Utility.modifyPlayerApi(path, (String) param.getResult());
                                    XposedBridge.log("play修改后：" + modified);
                                    param.setResult(modified);
                                } else if (path.startsWith("/eapi/song/enhance/download")) {
                                    String modified = Utility.modifyDownload(path, (String) param.getResult());
                                    XposedBridge.log("downlad修改后:" + modified);
                                    param.setResult(modified);
                                }
                            }
                        }
                    }
            );

            XSharedPreferences xSharedPreferences = new XSharedPreferences(BuildConfig.APPLICATION_ID);
            Utility.OVERSEA_MODE_ENABLED = xSharedPreferences.getBoolean(Settings.OVERSEA_MODE_KEY, Settings.OVERSEA_MODE_DEFAULT);

            if (Utility.OVERSEA_MODE_ENABLED) {
                Utility.setDnsServer(xSharedPreferences.getString(Settings.DNS_SERVER_KEY, Settings.DNS_SERVER_DEFAULT));

                findAndHookMethod("com.netease.cloudmusic.NeteaseMusicApplication", lpparam.classLoader,
                        "onCreate", new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                Context context = (Context) param.thisObject;
                                BroadcastReceiver settingChangedReceiver = new BroadcastReceiver() {
                                    @Override
                                    public void onReceive(Context context, Intent intent) {
                                        Utility.setDnsServer(intent.getStringExtra(Settings.DNS_SERVER_KEY));
                                    }
                                };
                                IntentFilter settingChangedFilter = new IntentFilter(Settings.SETTING_CHANGED);
                                context.registerReceiver(settingChangedReceiver, settingChangedFilter);
                            }
                        });

                findAndHookMethod(AbstractHttpClient.class, "execute", HttpUriRequest.class, new XC_MethodHook() {
                            @SuppressWarnings("deprecation")
                            @Override
                            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws URISyntaxException, TextParseException {
                                if (param.args[0] instanceof HttpGet) {
                                    HttpGet httpGet = (HttpGet) param.args[0];
                                    URI uri = httpGet.getURI();
                                    String path = uri.getPath();

                                    // only process self-generate url
                                    // original music url contains ymusic string, while self-generate url not contain.
                                    if (path.endsWith(".mp3") && !path.contains("/ymusic/")) {
                                        String host = uri.getHost();
                                        String ip = Utility.getIpByHost(host);
                                        if (ip != null) {
                                            httpGet.setURI(new URI("http://" + ip + path));
                                            httpGet.setHeader("Host", host);
                                            param.args[0] = httpGet;
                                        }
                                    }
                                }
                            }
                        }
                );
            }
        }
    }
}
