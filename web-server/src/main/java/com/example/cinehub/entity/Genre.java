package com.example.cinehub.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Entity
@Table(name = "genres")
@Getter
@Setter
public class Genre {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String slug;

    @ManyToMany(mappedBy = "genres") //"genres" chính là tên của biến 'private Set<Genre> genres;' bên file Movie.java
    private Set<Movie> movies;
}
