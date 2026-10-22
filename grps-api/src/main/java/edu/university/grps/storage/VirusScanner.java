package edu.university.grps.storage;

/** Virus scanning (SEC-06). The v1 implementation talks to a ClamAV daemon; local runs use a stub. */
public interface VirusScanner {

    enum Result { CLEAN, INFECTED, UNAVAILABLE }

    Result scan(byte[] content);
}
