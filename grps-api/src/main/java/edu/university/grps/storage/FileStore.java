package edu.university.grps.storage;

import java.io.InputStream;

/** Object storage (PRD section 24). Files live outside the web root and are served only through signed links. */
public interface FileStore {

    void put(String key, byte[] content, String contentType);

    InputStream get(String key);

    boolean exists(String key);

    void delete(String key);
}
