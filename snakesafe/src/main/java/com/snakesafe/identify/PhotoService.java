package com.snakesafe.identify;

import java.time.Clock;

import org.springframework.stereotype.Service;

import com.snakesafe.config.Sanitizer;

/** Saves training photos: the file through PhotoStorage, the details in the snake_photos table. */
@Service
public class PhotoService {

    private final PhotoStorage storage;
    private final SnakePhotoRepository photos;
    private final Clock clock;

    public PhotoService(PhotoStorage storage, SnakePhotoRepository photos, Clock clock) {
        this.storage = storage;
        this.photos = photos;
        this.clock = clock;
    }

    SnakePhoto store(ImageFiles.Checked image, String species, String source, boolean consent) {
        String path = storage.save(IdentifyService.newFileName(image.extension()), image.bytes(), image.mediaType());
        SnakePhoto p = new SnakePhoto();
        p.storagePath = path;
        p.species = Sanitizer.clean(species);
        p.source = Sanitizer.clean(source);
        p.consent = consent;
        p.createdAt = clock.instant();
        return photos.save(p);
    }
}
