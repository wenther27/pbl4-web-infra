package com.example.cinehub.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {
    @Size(max = 500, message = "Link ảnh đại diện tối đa 500 kí tự")
    private String avatarUrl;
}
