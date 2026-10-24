package io.beep.core.app.port;

import io.beep.core.domain.user.Email;
import io.beep.core.domain.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
  Optional<User> findById(UUID id);

  Optional<User> findByEmail(Email email);

  /** Insère un nouvel utilisateur. Lève {@code DuplicateEmailException} si l'adresse existe déjà. */
  void insert(User user);

  void update(User user);

  /** Pagination par curseur (plus récents d'abord). */
  Page page(int limit, String cursor);

  record Page(List<User> items, String nextCursor) {}

  class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException() {
      super("duplicate email");
    }
  }
}
