package io.beep.core.adapter.in.http;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class ServiceRoleTest {
 @Test void redirectorsCannotExposeAccountOrAdminApis(){for(String path:java.util.List.of("/v1/auth/me","/v1/links","/internal/admin/v1/users"))assertThat(ServiceRoleFilter.allowed("redirector",path)).isFalse();assertThat(ServiceRoleFilter.allowed("redirector","/some-link")).isTrue();assertThat(ServiceRoleFilter.allowed("redirector","/q/code")).isTrue();}
 @Test void apiAndWorkerRejectRedirectTraffic(){assertThat(ServiceRoleFilter.allowed("api","/some-link")).isFalse();assertThat(ServiceRoleFilter.allowed("api","/v1/links")).isTrue();assertThat(ServiceRoleFilter.allowed("worker","/v1/links")).isFalse();assertThat(ServiceRoleFilter.allowed("worker","/some-link")).isFalse();assertThat(ServiceRoleFilter.allowed("worker","/actuator/health/readiness")).isTrue();}
}
