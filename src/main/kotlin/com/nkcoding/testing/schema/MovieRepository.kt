package com.nkcoding.testing.schema

import com.nkcoding.testing.model.Movie
import org.springframework.data.repository.CrudRepository

interface MovieRepository : CrudRepository<Movie, String> {
}