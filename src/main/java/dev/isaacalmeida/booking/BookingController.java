package dev.isaacalmeida.booking;

import static dev.isaacalmeida.booking.Contracts.*;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import java.util.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class BookingController {
    private final BookingService service;
    BookingController(BookingService service) { this.service=service; }
    @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    @PostMapping("/register") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    Workspace register(@Valid @RequestBody Register input) { return service.register(input); }
    @GetMapping("/me") Map<String,Object> me(Principal user) { return Map.of("email",user.getName(),"workspaces",service.workspaces(user.getName())); }
    @GetMapping("/workspaces/{workspace}/resources") List<Resource> resources(Principal user,@PathVariable UUID workspace) { return service.resources(user.getName(),workspace); }
    @PostMapping("/workspaces/{workspace}/resources") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    Resource createResource(Principal user,@PathVariable UUID workspace,@Valid @RequestBody ResourceInput input) { return service.createResource(user.getName(),workspace,input); }
    @PostMapping("/workspaces/{workspace}/staff") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    void addStaff(Principal user,@PathVariable UUID workspace,@Valid @RequestBody Staff input) { service.addStaff(user.getName(),workspace,input); }
    @GetMapping("/workspaces/{workspace}/bookings") List<Booking> bookings(Principal user,@PathVariable UUID workspace,@RequestParam Instant from,@RequestParam Instant to) { return service.bookings(user.getName(),workspace,from,to); }
    @PostMapping("/workspaces/{workspace}/bookings") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    Booking book(Principal user,@PathVariable UUID workspace,@Valid @RequestBody BookingInput input) { return service.book(user.getName(),workspace,input); }
    @PostMapping("/workspaces/{workspace}/bookings/{booking}/cancel") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void cancel(Principal user,@PathVariable UUID workspace,@PathVariable UUID booking) { service.cancel(user.getName(),workspace,booking); }
}
