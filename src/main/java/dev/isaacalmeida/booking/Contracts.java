package dev.isaacalmeida.booking;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

final class Contracts {
    record Register(@NotBlank @Size(max=100) String workspaceName,
                    @NotBlank @Email @Size(max=254) String email,
                    @NotBlank @Size(min=10,max=64) String password) {}
    record Staff(@NotBlank @Email @Size(max=254) String email,
                 @NotBlank @Size(min=10,max=64) String password) {}
    record ResourceInput(@NotBlank @Size(max=100) String name) {}
    record BookingInput(@NotNull UUID resourceId, @NotBlank @Size(max=120) String customerName,
                        @NotNull Instant startsAt, @NotNull Instant endsAt) {}
    record Workspace(UUID id, String name, String role) {}
    record Resource(UUID id, String name) {}
    record Booking(UUID id, UUID resourceId, String customerName, Instant startsAt, Instant endsAt, String status) {}
}
