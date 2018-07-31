package me.devsaki.hentoid.activities;

import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.updater.UpdateCheck;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

/**
 * Created by DevSaki on 20/05/2015.
 * Set up and present preferences.
 * <p>
 * Maintained by wightwulf1944 22/02/2018
 * updated class for new AppCompatActivity and cleanup
 */
public class PrefsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, new MyPreferenceFragment())
                .commit();
    }

    public static class MyPreferenceFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            findPreference(Preferences.Key.PREF_HIDE_RECENT)
                    .setOnPreferenceChangeListener((preference, newValue) -> onPrefRequiringRestartChanged());

            findPreference(Preferences.Key.PREF_ANALYTICS_TRACKING)
                    .setOnPreferenceChangeListener((preference, newValue) -> onPrefRequiringRestartChanged());

            findPreference(Preferences.Key.PREF_USE_SFW)
                    .setOnPreferenceChangeListener((preference, newValue) -> onPrefRequiringRestartChanged());

            findPreference(Preferences.Key.PREF_DL_THREADS_QUANTITY_LISTS)
                    .setOnPreferenceChangeListener((preference, newValue) -> onPrefRequiringRestartChanged());

            findPreference(Preferences.Key.PREF_APP_LOCK)
                    .setOnPreferenceChangeListener((preference, newValue) -> onAppLockPinChanged(newValue));
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            switch (preference.getKey()) {
                case Preferences.Key.PREF_ADD_NO_MEDIA_FILE:
                    return FileHelper.createNoMedia();
                case Preferences.Key.PREF_CHECK_UPDATE_MANUAL:
                    return onCheckUpdatePrefClick();
                default:
                    return super.onPreferenceTreeClick(preference);
            }
        }

        private boolean onCheckUpdatePrefClick() {
            Helper.toast("Checking for updates...");
            new UpdateCheck().checkForUpdate(HentoidApp.getAppContext(), false, true,
                    new UpdateCheck.UpdateCheckCallback() {
                        @Override
                        public void noUpdateAvailable() {
                            Timber.d("Update Check: No update.");
                        }

                        @Override
                        public void onUpdateAvailable() {
                            Timber.d("Update Check: Update!");
                        }
                    });

            return true;
        }

        private boolean onPrefRequiringRestartChanged() {
            Helper.toast(R.string.restart_needed);
            return true;
        }

        private boolean onAppLockPinChanged(Object newValue) {
            String pin = (String) newValue;
            if (pin.isEmpty()) {
                Helper.toast(getActivity(), R.string.app_lock_disabled);
            } else {
                Helper.toast(getActivity(), R.string.app_lock_enable);
            }
            return true;
        }
    }
}
