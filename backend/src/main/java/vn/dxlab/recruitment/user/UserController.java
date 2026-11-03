package vn.dxlab.recruitment.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.dxlab.recruitment.user.dto.CreateUserRequest;
import vn.dxlab.recruitment.user.dto.UpdateUserRequest;
import vn.dxlab.recruitment.user.dto.UserDto;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    /** Giảng viên xem được (để biết hội đồng gồm ai); chỉ Người tổ chức sửa. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ORGANIZER', 'LECTURER')")
    public List<UserDto> list() {
        return users.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ORGANIZER')")
    public UserDto create(@Valid @RequestBody CreateUserRequest req) {
        return users.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req) {
        return users.update(id, req);
    }

    @PostMapping("/{id}/invite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ORGANIZER')")
    public void resendInvite(@PathVariable Long id) {
        users.resendInvite(id);
    }
}
