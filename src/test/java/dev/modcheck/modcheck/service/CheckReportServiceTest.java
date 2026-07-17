package dev.modcheck.modcheck.service;

import org.junit.jupiter.api.Test;

import static dev.modcheck.modcheck.service.CheckReportService.isInstallerMetadata;
import static dev.modcheck.modcheck.service.CheckReportService.normalize;
import static dev.modcheck.modcheck.service.CheckReportService.severityOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These three methods are pure functions with no Spring/DB dependency, so
 * they're tested directly - no @SpringBootTest, no Postgres needed. Every
 * case here is a real bug found by running against a live collection
 * (xk05aw rev 311), turned into a regression test rather than left as a
 * one-off manual check.
 */
class CheckReportServiceTest {

    // --- severityOf: found because Nexus returns fileExtension WITH a
    // leading dot (".pex"), not bare ("pex") - every comparison silently
    // failed until this was made tolerant of either format. ---

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

    // --- isInstallerMetadata: found because fomod/ files are installer
    // scaffolding, never actually extracted into the game folder - they
    // aren't real conflicts, just noise every FOMOD-packaged mod shares. ---

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