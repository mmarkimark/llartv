package io.github.mmarkimark.llartv;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Base64;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * Lets scripts/config.sh read and replace home.conf over adb, on TVs where the shell cannot write the app's files
 * folder. Only the adb shell can send it: the manifest requires android.permission.DUMP, which apps cannot get.
 *   adb shell am broadcast -f 32 -n io.github.mmarkimark.llartv/.SettingsReceiver [--es conf64 <base64 home.conf>]
 * The result data is the current home.conf, base64-encoded.
 */
public class SettingsReceiver extends BroadcastReceiver {
    static final String EXTRA_CONF = "conf64";

    @Override public void onReceive(Context c, Intent i) {
        File dir = c.getExternalFilesDir(null);
        String conf = i.getStringExtra(EXTRA_CONF);
        try {
            if (conf != null) Config.write(dir, new String(Base64.decode(conf, Base64.DEFAULT), StandardCharsets.UTF_8));
            setResult(Activity.RESULT_OK,
                    Base64.encodeToString(Config.read(dir).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP), null);
        } catch (Exception e) {
            setResult(Activity.RESULT_CANCELED, e.toString(), null);
        }
    }
}
