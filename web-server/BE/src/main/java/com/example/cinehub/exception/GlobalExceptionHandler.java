package com.example.cinehub.exception;

import com.example.cinehub.dto.response.ExceptionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 404 - khong tim thay tai nguyen
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ExceptionResponse> handleResourceNotFound(ResourceNotFoundException ex,
                                                                   WebRequest request) {
        return build(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên", ex.getMessage());
    }

    // 400 - logic nghiep vu sai hoac trung lap du lieu
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ExceptionResponse> handleBadRequest(BadRequestException ex,
                                                              WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ", ex.getMessage());
    }

    // 400 - @Valid tren request body khong qua
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ExceptionResponse> handleValidationException(MethodArgumentNotValidException ex,
                                                                      WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        return build(HttpStatus.BAD_REQUEST, "Dữ liệu đầu vào không hợp lệ", errors);
    }

    // 400 - sai kieu tham so, hay gap nhat la enum sai
    // (vi du ?movieType=phim thay vi movie/series/anime).
    // Khong co handler nay thi loi roi xuong handler Exception va thanh 500.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ExceptionResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                WebRequest request) {
        String detail = "Tham số \"" + ex.getName() + "\" có giá trị không hợp lệ";
        Class<?> requiredType = ex.getRequiredType();
        if (requiredType != null && requiredType.isEnum()) {
            detail += ". Giá trị cho phép: " + String.join(", ",
                    java.util.Arrays.stream(requiredType.getEnumConstants())
                            .map(String::valueOf)
                            .toList());
        }
        return build(HttpStatus.BAD_REQUEST, "Tham số không hợp lệ", detail);
    }

    // 400 - thieu query param bat buoc
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ExceptionResponse> handleMissingParam(MissingServletRequestParameterException ex,
                                                                WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Thiếu tham số",
                "Thiếu tham số bắt buộc \"" + ex.getParameterName() + "\"");
    }

    // 400 - JSON gui len sai cu phap
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ExceptionResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                                  WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Dữ liệu đầu vào không hợp lệ",
                "Body của request không đúng định dạng JSON hoặc sai kiểu dữ liệu");
    }

    // 401 - dang nhap sai
    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<ExceptionResponse> handleBadCredentials(Exception ex, WebRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Đăng nhập thất bại",
                "Tài khoản hoặc mật khẩu không chính xác, vui lòng kiểm tra lại");
    }

    // 403 - da dang nhap nhung khong du quyen (vi du xoa binh luan cua nguoi khac)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ExceptionResponse> handleAccessDenied(AccessDeniedException ex,
                                                                WebRequest request) {
        return build(HttpStatus.FORBIDDEN, "Không có quyền truy cập", ex.getMessage());
    }

    // 409 - vi pham rang buoc DB (UNIQUE, FOREIGN KEY...)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ExceptionResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                                 WebRequest request) {
        log.warn("Vi phạm ràng buộc dữ liệu: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "Dữ liệu bị trùng hoặc không hợp lệ",
                "Dữ liệu vi phạm ràng buộc của cơ sở dữ liệu (trùng lặp hoặc tham chiếu không tồn tại)");
    }

    // 404 - URL khong ton tai
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ExceptionResponse> handleNoHandler(NoHandlerFoundException ex,
                                                             WebRequest request) {
        return build(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên",
                "Không có API nào ứng với đường dẫn " + ex.getRequestURL());
    }

    // 500 - loi khong luong truoc. Bat buoc phai log stacktrace, neu khong
    // thi khi chay trong Docker sau load balancer se khong con cach nao debug.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleGlobalException(Exception ex, WebRequest request) {
        log.error("Lỗi không xử lý được tại {}", request.getDescription(false), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống",
                "Đã có lỗi xảy ra từ phía máy chủ, vui lòng thử lại sau");
    }

    private ResponseEntity<ExceptionResponse> build(HttpStatus status, String error, Object details) {
        ExceptionResponse body = new ExceptionResponse();
        body.setError(error);
        body.setDetails(details);
        return new ResponseEntity<>(body, status);
    }
}
