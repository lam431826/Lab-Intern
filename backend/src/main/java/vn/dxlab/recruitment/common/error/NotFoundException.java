package vn.dxlab.recruitment.common.error;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, Object id) {
        super("Không tìm thấy " + what + " " + id);
    }
}
