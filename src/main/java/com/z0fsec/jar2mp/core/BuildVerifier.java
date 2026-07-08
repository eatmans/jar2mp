package com.z0fsec.jar2mp.core;

import com.z0fsec.jar2mp.model.VerificationResult;

import java.io.File;
import java.io.IOException;

public interface BuildVerifier {
    VerificationResult verify(File projectDir, String goal);
    void writeReport(File projectDir, VerificationResult result) throws IOException;
}
