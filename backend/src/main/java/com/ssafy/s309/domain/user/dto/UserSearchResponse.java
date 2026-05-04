package com.ssafy.s309.domain.user.dto;

import com.ssafy.s309.domain.user.entity.User;

public record UserSearchResponse(Integer userId, String name) {

  public static UserSearchResponse from(User user) {
    return new UserSearchResponse(user.getId(), user.getName());
  }
}
