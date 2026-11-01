package com.snakesafe.profile;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.snakesafe.auth.AuthPrincipal;
import com.snakesafe.auth.Phones;
import com.snakesafe.auth.User;
import com.snakesafe.auth.UserRepository;
import com.snakesafe.config.Sanitizer;
import com.snakesafe.web.ApiException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private static final int MAX_MEMBERS = 20;
    private static final int MAX_CONTACTS = 10;

    public record ProfileUpdate(@Size(max = 100) String name,
            @Pattern(regexp = "en|kn", message = "must be en or kn") String language) {
    }

    public record ProfileView(long id, String phone, String name, String language, Instant createdAt) {
    }

    public record MemberRequest(@NotBlank @Size(max = 100) String name, @Min(0) @Max(120) Integer age,
            @Size(max = 50) String relation, @Size(max = 500) String allergies, @Size(max = 500) String notes) {
    }

    public record MemberView(long id, String name, Integer age, String relation, String allergies, String notes) {
    }

    public record ContactRequest(@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 20) String phone) {
    }

    public record ContactView(long id, String name, String phone) {
    }

    private final UserRepository users;
    private final FamilyMemberRepository members;
    private final EmergencyContactRepository contacts;

    public ProfileController(UserRepository users, FamilyMemberRepository members, EmergencyContactRepository contacts) {
        this.users = users;
        this.members = members;
        this.contacts = contacts;
    }

    @GetMapping
    public ProfileView profile(@AuthenticationPrincipal AuthPrincipal me) {
        return toView(user(me));
    }

    @PutMapping
    public ProfileView update(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody ProfileUpdate req) {
        User u = user(me);
        if (req.name() != null) {
            u.name = Sanitizer.cleanOrNull(req.name());
        }
        if (req.language() != null) {
            u.language = req.language();
        }
        return toView(users.save(u));
    }

    @GetMapping("/members")
    public List<MemberView> listMembers(@AuthenticationPrincipal AuthPrincipal me) {
        return members.findByUserIdOrderByIdAsc(me.id()).stream().map(ProfileController::toView).toList();
    }

    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberView addMember(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody MemberRequest req) {
        if (members.countByUserId(me.id()) >= MAX_MEMBERS) {
            throw ApiException.conflict("LIMIT_REACHED", "You can save up to " + MAX_MEMBERS + " family members.");
        }
        FamilyMember m = new FamilyMember();
        m.userId = me.id();
        return toView(members.save(fill(m, req)));
    }

    @PutMapping("/members/{id}")
    public MemberView updateMember(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long id,
            @Valid @RequestBody MemberRequest req) {
        FamilyMember m = members.findByIdAndUserId(id, me.id())
                .orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "Family member not found."));
        return toView(members.save(fill(m, req)));
    }

    @DeleteMapping("/members/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMember(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long id) {
        members.delete(members.findByIdAndUserId(id, me.id())
                .orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "Family member not found.")));
    }

    @GetMapping("/contacts")
    public List<ContactView> listContacts(@AuthenticationPrincipal AuthPrincipal me) {
        return contacts.findByUserIdOrderByIdAsc(me.id()).stream().map(ProfileController::toView).toList();
    }

    @PostMapping("/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    public ContactView addContact(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody ContactRequest req) {
        if (contacts.countByUserId(me.id()) >= MAX_CONTACTS) {
            throw ApiException.conflict("LIMIT_REACHED", "You can save up to " + MAX_CONTACTS + " contacts.");
        }
        EmergencyContact c = new EmergencyContact();
        c.userId = me.id();
        return toView(contacts.save(fill(c, req)));
    }

    @PutMapping("/contacts/{id}")
    public ContactView updateContact(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long id,
            @Valid @RequestBody ContactRequest req) {
        EmergencyContact c = contacts.findByIdAndUserId(id, me.id())
                .orElseThrow(() -> ApiException.notFound("CONTACT_NOT_FOUND", "Contact not found."));
        return toView(contacts.save(fill(c, req)));
    }

    @DeleteMapping("/contacts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteContact(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long id) {
        contacts.delete(contacts.findByIdAndUserId(id, me.id())
                .orElseThrow(() -> ApiException.notFound("CONTACT_NOT_FOUND", "Contact not found.")));
    }

    private User user(AuthPrincipal me) {
        return users.findById(me.id()).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Account not found."));
    }

    private static FamilyMember fill(FamilyMember m, MemberRequest r) {
        m.name = Sanitizer.clean(r.name());
        m.age = r.age();
        m.relation = Sanitizer.cleanOrNull(r.relation());
        m.allergies = Sanitizer.cleanOrNull(r.allergies());
        m.notes = Sanitizer.cleanOrNull(r.notes());
        return m;
    }

    private static EmergencyContact fill(EmergencyContact c, ContactRequest r) {
        c.name = Sanitizer.clean(r.name());
        c.phone = Phones.normalize(r.phone());
        return c;
    }

    private static ProfileView toView(User u) {
        return new ProfileView(u.id, u.phone, u.name, u.language, u.createdAt);
    }

    private static MemberView toView(FamilyMember m) {
        return new MemberView(m.id, m.name, m.age, m.relation, m.allergies, m.notes);
    }

    private static ContactView toView(EmergencyContact c) {
        return new ContactView(c.id, c.name, c.phone);
    }
}
