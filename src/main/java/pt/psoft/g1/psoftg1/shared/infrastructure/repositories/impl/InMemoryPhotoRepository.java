package pt.psoft.g1.psoftg1.shared.infrastructure.repositories.impl;

import org.springframework.stereotype.Repository;
import pt.psoft.g1.psoftg1.shared.model.Photo;
import pt.psoft.g1.psoftg1.shared.repositories.PhotoRepository;
import pt.psoft.g1.psoftg1.shared.services.FileStorageService;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of PhotoRepository.
 * Since Photo is a value object (not a JPA entity), we use an in-memory store.
 * In a real application, photo metadata might be stored in the database
 * while the actual files are stored in a file system or cloud storage.
 */
@Repository
public class InMemoryPhotoRepository implements PhotoRepository {

    private final ConcurrentHashMap<Long, Photo> photoStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> photoFileIndex = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Optional<Photo> findById(long id) {
        return Optional.ofNullable(photoStore.get(id));
    }

    @Override
    public Photo save(Photo photo) {
        if (photo == null || photo.getPhotoFile() == null) {
            return photo;
        }

        // Check if this photo file already exists
        Long existingId = photoFileIndex.get(photo.getPhotoFile());
        if (existingId != null) {
            photoStore.put(existingId, photo);
            return photo;
        }

        // Create new entry
        long newId = idGenerator.getAndIncrement();
        photoStore.put(newId, photo);
        photoFileIndex.put(photo.getPhotoFile(), newId);
        return photo;
    }

    @Override
    public void deleteByPhotoFile(String photoFile) {
        if (photoFile == null) {
            return;
        }

        Long id = photoFileIndex.remove(photoFile);
        if (id != null) {
            photoStore.remove(id);
        }
    }
}
