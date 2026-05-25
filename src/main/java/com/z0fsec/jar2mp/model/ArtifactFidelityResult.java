package com.z0fsec.jar2mp.model;

import java.util.ArrayList;
import java.util.List;

public class ArtifactFidelityResult {
    private int originalEntryTotal;
    private int rebuiltEntryTotal;
    private int originalClassEntries;
    private int rebuiltClassEntries;
    private int originalResourceEntries;
    private int rebuiltResourceEntries;
    private int originalNestedLibs;
    private int rebuiltNestedLibs;
    private int commonEntries;
    private int sameSha256;
    private int differentSha256;
    private int missingEntries;
    private int extraEntries;
    private int commonClassEntries;
    private int sameClassBytes;
    private int differentClassBytes;
    private int commonNestedLibs;
    private int sameNestedLibs;
    private int differentNestedLibs;
    private int missingNestedLibs;
    private int extraNestedLibs;
    private boolean manifestOriginalPresent;
    private boolean manifestRebuiltPresent;
    private boolean manifestSame;
    private final List<String> sampleMissingEntries = new ArrayList<>();
    private final List<String> sampleExtraEntries = new ArrayList<>();
    private final List<String> sampleDifferentEntries = new ArrayList<>();

    public boolean isExactMatch() {
        return differentSha256 == 0 && missingEntries == 0 && extraEntries == 0;
    }

    public int getOriginalEntryTotal() { return originalEntryTotal; }
    public void setOriginalEntryTotal(int originalEntryTotal) { this.originalEntryTotal = originalEntryTotal; }
    public int getRebuiltEntryTotal() { return rebuiltEntryTotal; }
    public void setRebuiltEntryTotal(int rebuiltEntryTotal) { this.rebuiltEntryTotal = rebuiltEntryTotal; }
    public int getOriginalClassEntries() { return originalClassEntries; }
    public void setOriginalClassEntries(int originalClassEntries) { this.originalClassEntries = originalClassEntries; }
    public int getRebuiltClassEntries() { return rebuiltClassEntries; }
    public void setRebuiltClassEntries(int rebuiltClassEntries) { this.rebuiltClassEntries = rebuiltClassEntries; }
    public int getOriginalResourceEntries() { return originalResourceEntries; }
    public void setOriginalResourceEntries(int originalResourceEntries) { this.originalResourceEntries = originalResourceEntries; }
    public int getRebuiltResourceEntries() { return rebuiltResourceEntries; }
    public void setRebuiltResourceEntries(int rebuiltResourceEntries) { this.rebuiltResourceEntries = rebuiltResourceEntries; }
    public int getOriginalNestedLibs() { return originalNestedLibs; }
    public void setOriginalNestedLibs(int originalNestedLibs) { this.originalNestedLibs = originalNestedLibs; }
    public int getRebuiltNestedLibs() { return rebuiltNestedLibs; }
    public void setRebuiltNestedLibs(int rebuiltNestedLibs) { this.rebuiltNestedLibs = rebuiltNestedLibs; }
    public int getCommonEntries() { return commonEntries; }
    public void setCommonEntries(int commonEntries) { this.commonEntries = commonEntries; }
    public int getSameSha256() { return sameSha256; }
    public void setSameSha256(int sameSha256) { this.sameSha256 = sameSha256; }
    public int getDifferentSha256() { return differentSha256; }
    public void setDifferentSha256(int differentSha256) { this.differentSha256 = differentSha256; }
    public int getMissingEntries() { return missingEntries; }
    public void setMissingEntries(int missingEntries) { this.missingEntries = missingEntries; }
    public int getExtraEntries() { return extraEntries; }
    public void setExtraEntries(int extraEntries) { this.extraEntries = extraEntries; }
    public int getCommonClassEntries() { return commonClassEntries; }
    public void setCommonClassEntries(int commonClassEntries) { this.commonClassEntries = commonClassEntries; }
    public int getSameClassBytes() { return sameClassBytes; }
    public void setSameClassBytes(int sameClassBytes) { this.sameClassBytes = sameClassBytes; }
    public int getDifferentClassBytes() { return differentClassBytes; }
    public void setDifferentClassBytes(int differentClassBytes) { this.differentClassBytes = differentClassBytes; }
    public int getCommonNestedLibs() { return commonNestedLibs; }
    public void setCommonNestedLibs(int commonNestedLibs) { this.commonNestedLibs = commonNestedLibs; }
    public int getSameNestedLibs() { return sameNestedLibs; }
    public void setSameNestedLibs(int sameNestedLibs) { this.sameNestedLibs = sameNestedLibs; }
    public int getDifferentNestedLibs() { return differentNestedLibs; }
    public void setDifferentNestedLibs(int differentNestedLibs) { this.differentNestedLibs = differentNestedLibs; }
    public int getMissingNestedLibs() { return missingNestedLibs; }
    public void setMissingNestedLibs(int missingNestedLibs) { this.missingNestedLibs = missingNestedLibs; }
    public int getExtraNestedLibs() { return extraNestedLibs; }
    public void setExtraNestedLibs(int extraNestedLibs) { this.extraNestedLibs = extraNestedLibs; }
    public boolean isManifestOriginalPresent() { return manifestOriginalPresent; }
    public void setManifestOriginalPresent(boolean manifestOriginalPresent) { this.manifestOriginalPresent = manifestOriginalPresent; }
    public boolean isManifestRebuiltPresent() { return manifestRebuiltPresent; }
    public void setManifestRebuiltPresent(boolean manifestRebuiltPresent) { this.manifestRebuiltPresent = manifestRebuiltPresent; }
    public boolean isManifestSame() { return manifestSame; }
    public void setManifestSame(boolean manifestSame) { this.manifestSame = manifestSame; }
    public List<String> getSampleMissingEntries() { return sampleMissingEntries; }
    public List<String> getSampleExtraEntries() { return sampleExtraEntries; }
    public List<String> getSampleDifferentEntries() { return sampleDifferentEntries; }
}
