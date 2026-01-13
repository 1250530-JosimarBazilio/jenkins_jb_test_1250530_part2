package pt.psoft.g1.psoftg1.genremanagement.api;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;

/**
 * MapStruct mapper for Genre views.
 */
@Mapper(componentModel = "spring")
public abstract class GenreViewMapper {

    @Mapping(target = "genre", source = "genre")
    public abstract GenreView toGenreView(Genre genre);
}
