package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {
    @Test fun stableReleaseSupersedesReleaseCandidate() {
        assertTrue(UpdateChecker.isNewerVersion("v1.5.0", "1.5.0-rc1-beta"))
        assertFalse(UpdateChecker.isNewerVersion("v1.5.0-rc1", "1.5.0"))
    }

    @Test fun releaseCandidatesCompareTheirSuffixNumbers() {
        assertTrue(UpdateChecker.isNewerVersion("v1.5.0-rc2", "1.5.0-rc1-beta"))
        assertFalse(UpdateChecker.isNewerVersion("v1.5.0-rc1", "1.5.0-rc2"))
    }

    @Test fun historicCommaVersionStillComparesByCoreNumbers() {
        assertTrue(UpdateChecker.isNewerVersion("V1.4,1_Beta", "1.4.0-beta"))
        assertFalse(UpdateChecker.isNewerVersion("V1.4,1_Beta", "1.4.2-beta"))
    }
}
