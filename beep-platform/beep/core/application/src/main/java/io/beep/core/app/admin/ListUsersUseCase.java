package io.beep.core.app.admin;

import io.beep.core.app.port.UserRepository;

/** Lecture seule des utilisateurs pour l'API admin (back office). */
public class ListUsersUseCase {
  private static final int MAX_LIMIT = 100;

  private final UserRepository users;

  public ListUsersUseCase(UserRepository users) {
    this.users = users;
  }

  public UserRepository.Page execute(int limit, String cursor) {
    return users.page(Math.max(1, Math.min(limit, MAX_LIMIT)), cursor);
  }
}
