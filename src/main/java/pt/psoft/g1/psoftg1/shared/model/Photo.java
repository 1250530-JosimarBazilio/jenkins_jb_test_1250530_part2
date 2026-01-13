package pt.psoft.g1.psoftg1.shared.model;

import lombok.Getter;

/**
 * Value object representing a photo.
 */
@Getter
public class Photo {

    private final String photoFile;

    public Photo(String photoFile) {
        this.photoFile = photoFile;
    }
}
