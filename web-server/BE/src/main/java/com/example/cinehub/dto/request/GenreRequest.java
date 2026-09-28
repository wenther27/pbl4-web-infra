package com.example.cinehub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GenreRequest {
    @NotBlank(message = "Tên thể loại không được để trống")
    @Size(max = 50, message = "Tên thể loại tối đa 50 kí tự")
    private String name;

    @NotBlank(message = "Slug không được để trống")
    @Size(max = 50, message = "Slug tối đa 50 kí tự")
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
            message = "Slug chỉ gồm chữ thường, số và dấu gạch ngang (ví dụ: hanh-dong)")
    private String slug;
}
