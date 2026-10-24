package io.beep.bo.app.port;

import io.beep.bo.domain.staff.Email;
import io.beep.bo.domain.staff.StaffUser;
import java.util.Optional;
import java.util.UUID;

public interface StaffRepository {
  Optional<StaffUser> findById(UUID id);

  Optional<StaffUser> findByEmail(Email email);

  void insert(StaffUser user);
}
