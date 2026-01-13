package pt.psoft.g1.psoftg1.shared.model;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * Base entity class for entities that can have a photo.
 */
@MappedSuperclass
public abstract class EntityWithPhoto {

    @Getter
    private String photoURI;

    protected void setPhotoInternal(String photoURI) {
        this.photoURI = photoURI;
    }

    public Photo getPhoto() {
        if (photoURI == null) {
            return null;
        }
        return new Photo(photoURI);
    }
}
