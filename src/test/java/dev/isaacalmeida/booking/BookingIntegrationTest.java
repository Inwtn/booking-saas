package dev.isaacalmeida.booking;

import static dev.isaacalmeida.booking.Contracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BookingIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired BookingService service;
    @Autowired MockMvc mvc;
    String email;
    Workspace workspace;
    Resource resource;
    Instant start;

    @BeforeEach void setup() {
        email=UUID.randomUUID()+"@example.test";
        workspace=service.register(new Register("Demo studio",email,"A-valid-password-123"));
        resource=service.createResource(email,workspace.id(),new ResourceInput("Room A"));
        start=Instant.now().plus(1,ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    }

    @Test void authenticationAndCsrfAreRequired() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/register").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(formLogin("/api/login").user(email).password("A-valid-password-123"))
                .andExpect(status().isNoContent());
    }

    @Test void otherWorkspaceCannotBeReadOrChanged() throws Exception {
        String outsider=UUID.randomUUID()+"@example.test";
        service.register(new Register("Other business",outsider,"A-valid-password-123"));
        mvc.perform(get("/api/workspaces/"+workspace.id()+"/resources").with(user(outsider)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/workspaces/"+workspace.id()+"/resources").with(user(outsider)).with(csrf())
                .contentType("application/json").content("{\"name\":\"Injected resource\"}"))
                .andExpect(status().isNotFound());
    }

    @Test void staffCanBookButCannotManageResources() throws Exception {
        String staff=UUID.randomUUID()+"@example.test";
        service.addStaff(email,workspace.id(),new Staff(staff,"A-valid-password-123"));
        mvc.perform(post("/api/workspaces/"+workspace.id()+"/resources").with(user(staff)).with(csrf())
                .contentType("application/json").content("{\"name\":\"Not allowed\"}"))
                .andExpect(status().isForbidden());
        assertThat(service.book(staff,workspace.id(),slot(start)).status()).isEqualTo("CONFIRMED");
    }

    @Test void overlappingBookingFailsAndCancellationReleasesSlot() {
        var booking=service.book(email,workspace.id(),slot(start));
        assertThatThrownBy(()->service.book(email,workspace.id(),slot(start.plusSeconds(300))))
                .isInstanceOf(DataIntegrityViolationException.class);
        service.book(email,workspace.id(),slot(start.plusSeconds(3600)));
        service.cancel(email,workspace.id(),booking.id());
        assertThat(service.book(email,workspace.id(),slot(start)).status()).isEqualTo("CONFIRMED");
    }

    @Test void resourceCannotBeUsedAcrossWorkspaces() {
        String otherEmail=UUID.randomUUID()+"@example.test";
        Workspace other=service.register(new Register("Other",otherEmail,"A-valid-password-123"));
        Resource otherResource=service.createResource(otherEmail,other.id(),new ResourceInput("Other room"));
        assertThatThrownBy(()->service.book(email,workspace.id(),new BookingInput(
                otherResource.id(),"Customer",start,start.plusSeconds(3600))))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test void concurrentBookingsHaveExactlyOneWinner() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        Callable<Boolean> attempt=()->{ ready.countDown(); go.await();
            try { service.book(email,workspace.id(),slot(start)); return true; }
            catch(DataIntegrityViolationException conflict) { return false; }
        };
        try {
            Future<Boolean> a=pool.submit(attempt),b=pool.submit(attempt);
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); go.countDown();
            assertThat((a.get(15,TimeUnit.SECONDS)?1:0)+(b.get(15,TimeUnit.SECONDS)?1:0)).isEqualTo(1);
        } finally { go.countDown(); pool.shutdownNow(); }
    }

    private BookingInput slot(Instant at) { return new BookingInput(resource.id(),"Demo customer",at,at.plusSeconds(3600)); }
}
