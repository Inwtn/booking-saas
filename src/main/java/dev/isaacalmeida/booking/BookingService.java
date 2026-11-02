package dev.isaacalmeida.booking;

import static dev.isaacalmeida.booking.Contracts.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class BookingService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    public BookingService(JdbcTemplate jdbc, PasswordEncoder passwords) { this.jdbc=jdbc; this.passwords=passwords; }

    public Workspace register(Register input) {
        UUID user = createUser(input.email(),input.password());
        UUID workspace = UUID.randomUUID();
        jdbc.update("INSERT INTO workspaces VALUES (?,?)",workspace,input.workspaceName().trim());
        jdbc.update("INSERT INTO memberships VALUES (?,?,'OWNER')",workspace,user);
        return new Workspace(workspace,input.workspaceName().trim(),"OWNER");
    }

    private UUID createUser(String email, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password must fit within 72 UTF-8 bytes");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users VALUES (?,?,?)",id,email.trim().toLowerCase(Locale.ROOT),passwords.encode(password));
        return id;
    }

    public List<Workspace> workspaces(String email) {
        return jdbc.query("SELECT w.id,w.name,m.role FROM workspaces w JOIN memberships m ON w.id=m.workspace_id JOIN app_users u ON u.id=m.user_id WHERE u.email=? ORDER BY w.name",
                (rs,n) -> new Workspace(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("role")),email);
    }

    private UUID member(String email, UUID workspace, boolean owner) {
        var members = jdbc.query("SELECT u.id,m.role FROM app_users u JOIN memberships m ON u.id=m.user_id WHERE u.email=? AND m.workspace_id=?",
                (rs,n) -> Map.entry(rs.getObject("id",UUID.class),rs.getString("role")),email,workspace);
        if (members.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Workspace not found");
        if (owner && !members.get(0).getValue().equals("OWNER"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Owner access required");
        return members.get(0).getKey();
    }

    public void addStaff(String email, UUID workspace, Staff input) {
        member(email,workspace,true);
        UUID user = createUser(input.email(),input.password());
        jdbc.update("INSERT INTO memberships VALUES (?,?,'STAFF')",workspace,user);
    }

    public List<Resource> resources(String email, UUID workspace) {
        member(email,workspace,false);
        return jdbc.query("SELECT id,name FROM resources WHERE workspace_id=? ORDER BY name",
                (rs,n) -> new Resource(rs.getObject("id",UUID.class),rs.getString("name")),workspace);
    }

    public Resource createResource(String email, UUID workspace, ResourceInput input) {
        member(email,workspace,true);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO resources VALUES (?,?,?)",id,workspace,input.name().trim());
        return new Resource(id,input.name().trim());
    }

    public Booking book(String email, UUID workspace, BookingInput input) {
        UUID user = member(email,workspace,false);
        if (!input.startsAt().isAfter(Instant.now()) || !input.endsAt().isAfter(input.startsAt())
                || Duration.between(input.startsAt(),input.endsAt()).compareTo(Duration.ofHours(8))>0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a future interval of at most eight hours");
        if (jdbc.queryForObject("SELECT count(*) FROM resources WHERE workspace_id=? AND id=?",Integer.class,workspace,input.resourceId()) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Resource not found");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO bookings (id,workspace_id,resource_id,customer_name,starts_at,ends_at,created_by) VALUES (?,?,?,?,?,?,?)",
                id,workspace,input.resourceId(),input.customerName().trim(),Timestamp.from(input.startsAt()),Timestamp.from(input.endsAt()),user);
        return new Booking(id,input.resourceId(),input.customerName().trim(),input.startsAt(),input.endsAt(),"CONFIRMED");
    }

    public List<Booking> bookings(String email, UUID workspace, Instant from, Instant to) {
        member(email,workspace,false);
        if (!to.isAfter(from) || Duration.between(from,to).compareTo(Duration.ofDays(31))>0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a range of at most 31 days");
        return jdbc.query("SELECT * FROM bookings WHERE workspace_id=? AND starts_at<? AND ends_at>? ORDER BY starts_at LIMIT 1000",
                (rs,n) -> new Booking(rs.getObject("id",UUID.class),rs.getObject("resource_id",UUID.class),rs.getString("customer_name"),rs.getTimestamp("starts_at").toInstant(),rs.getTimestamp("ends_at").toInstant(),rs.getString("status")),
                workspace,Timestamp.from(to),Timestamp.from(from));
    }

    public void cancel(String email, UUID workspace, UUID booking) {
        member(email,workspace,false);
        if (jdbc.update("UPDATE bookings SET status='CANCELLED' WHERE id=? AND workspace_id=?",booking,workspace)==0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Booking not found");
    }
}
