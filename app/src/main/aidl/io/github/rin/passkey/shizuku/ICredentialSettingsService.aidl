package io.github.rin.passkey.shizuku;

import android.os.Bundle;

interface ICredentialSettingsService {
    Bundle readSettings(int userId) = 0;
    Bundle applySettings(int userId, @nullable String expectedEnabled, @nullable String expectedPrimary,
        @nullable String enabled, @nullable String primary) = 1;
    void destroy() = 16777114;
}
