package io.beep.core.app.admin;

import io.beep.core.app.common.Paging;
import io.beep.core.app.port.UserRepository;

/** Lecture seule des utilisateurs pour l'API admin (back office), avec recherche sur l'adresse e-mail. */
public class ListUsersUseCase {
  private final UserRepository users;

  public ListUsersUseCase(UserRepository users) {
    this.users = users;
  }

  public UserRepository.Page execute(int limit, String cursor) {
    return execute(limit, cursor, null);
  }

  public UserRepository.Page execute(int limit, String cursor, String query) {
    return users.page(Paging.limit(limit), cursor, Paging.query(query));
  }
}
