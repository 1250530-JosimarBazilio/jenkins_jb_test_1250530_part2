package pt.psoft.g1.psoftg1.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Value object representing a person's name.
 */
@Embeddable
public class Name {

    private static final int NAME_MAX_LENGTH = 150;

    @Column(name = "NAME", length = NAME_MAX_LENGTH)
    private String name;

    protected Name() {
        // For ORM
    }

    public Name(String name) {
        setName(name);
    }

    private void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or blank");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Name cannot exceed " + NAME_MAX_LENGTH + " characters");
        }
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
