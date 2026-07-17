package dev.modcheck.modcheck.service;

import org.junit.jupiter.api.Test;

import static dev.modcheck.modcheck.service.CheckReportService.isInstallerMetadata;
import static dev.modcheck.modcheck.service.CheckReportService.normalize;
import static dev.modcheck.modcheck.service.CheckReportService.severityOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckReportServiceTest {

    @Test
    void dottedHighRiskExtensionIsHigh() {
        assertEquals("high", severityOf(".pex"));
    }

    @Test
    void bareHighRiskExtensionIsAlsoHigh() {
        assertEquals("high", severityOf("pex"));
    }

    @Test
    void allFiveHighRiskExtensionsAreHigh() {
        assertEquals("high", severityOf(".esp"));
        assertEquals("high", severityOf(".esl"));
        assertEquals("high", severityOf(".esm"));
        assertEquals("high", severityOf(".dll"));
        assertEquals("high", severityOf(".pex"));
    }

    @Test
    void severityCheckIsCaseInsensitive() {
        assertEquals("high", severityOf(".PEX"));
        assertEquals("high", severityOf("ESP"));
    }

    @Test
    void ordinaryAssetExtensionIsLow() {
        assertEquals("low", severityOf(".nif"));
        assertEquals("low", severityOf(".xml"));
        assertEquals("low", severityOf(".psc")); // Papyrus SOURCE, not compiled .pex
    }

    @Test
    void nullExtensionIsLow() {
        assertEquals("low", severityOf(null));
    }

    // --- normalize: found because mods are packaged both with and without
    // a "Data/" root folder, and both land in the same place on install -
    // without stripping it, the same real file was seen as two paths. ---

    @Test
    void stripsLeadingDataPrefix() {
        assertEquals("meshes/x.nif", normalize("Data/meshes/x.nif"));
    }

    @Test
    void dataPrefixStripIsCaseInsensitive() {
        assertEquals("meshes/x.nif", normalize("DATA/Meshes/X.NIF"));
        assertEquals("meshes/x.nif", normalize("data/meshes/x.nif"));
    }

    @Test
    void pathWithoutDataPrefixIsJustLowercased() {
        assertEquals("meshes/x.nif", normalize("Meshes/X.NIF"));
    }

    @Test
    void shortPathDoesNotThrow() {
        // regionMatches must not blow up on a string shorter than "Data/".
        assertEquals("abc", normalize("abc"));
    }

    @Test
    void fomodPathIsInstallerMetadata() {
        assertTrue(isInstallerMetadata("fomod/ModuleConfig.xml"));
    }

    @Test
    void fomodDetectionIsCaseInsensitive() {
        assertTrue(isInstallerMetadata("FOMOD/info.xml"));
        assertTrue(isInstallerMetadata("FOMod/info.xml"));
    }

    @Test
    void fomodUnderDataPrefixIsStillDetected() {
        assertTrue(isInstallerMetadata("Data/fomod/ModuleConfig.xml"));
    }

    @Test
    void ordinaryPathIsNotInstallerMetadata() {
        assertFalse(isInstallerMetadata("meshes/x.nif"));
    }

    @Test
    void fomodAsPartOfAFileNameIsNotInstallerMetadata() {
        // Must match "fomod/" as a directory prefix, not just contain the substring.
        assertFalse(isInstallerMetadata("somefolder/fomod-thing.xml"));
    }
}