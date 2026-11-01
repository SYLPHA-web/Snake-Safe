package com.snakesafe.identify;

/** Where training photos live. Chosen by app.photo-storage; callers only see this interface. */
public interface PhotoStorage {

    /** Saves the bytes and returns the storage path (relative to the folder or bucket). */
    String save(String fileName, byte[] data, String contentType);
}
