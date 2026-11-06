package io.github.rin.passkey.shizuku;

import android.os.Bundle;

interface ICredentialSettingsService {
    Bundle readSettings(int userId) = 0;
    Bundle applySettings(int userId,
        @nullable String expectedEnabled, @nullable String expectedPrimary, @nullable String expectedAutofill,
        @nullable String enabled, @nullable String primary, @nullable String autofill) = 1;
    Bundle dumpService(int userId, String service) = 2;
    void destroy() = 16777114;
}
