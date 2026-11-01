package com.snakesafe.identify;

import java.util.List;

/**
 * Looks at a photo and returns guesses. The controllers only know this interface, so a self-trained
 * ONNX model can be added later as another implementation without touching them.
 */
public interface SnakeIdentifier {

    /** One guess: the scientific name (matched to our snake list when possible) and a 0..1 confidence. */
    record RawGuess(String scientificName, String commonName, double confidence) {
    }

    List<RawGuess> identify(byte[] image, String mediaType);

    /** STUB or AI: shown in the response so the app can say when results are only samples. */
    String mode();
}
