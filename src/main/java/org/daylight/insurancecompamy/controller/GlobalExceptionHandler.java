package org.daylight.insurancecompamy.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.NoSuchElementException;

@Controller
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class
    })
    public String handleBadInput(Exception ex, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        String referer = request.getHeader("Referer");
        redirectAttributes.addFlashAttribute("errorMessage",
                "Ошибка ввода или некорректный параметр: " + ex.getMessage());
        return (referer != null && !referer.isBlank()) ? "redirect:" + referer : "redirect:/";
    }

    @ExceptionHandler(IllegalStateException.class)
    public String handleIllegalState(IllegalStateException ex,
                                     HttpServletRequest request,
                                     RedirectAttributes redirectAttributes) {
        String referer = request.getHeader("Referer");
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return (referer != null && !referer.isBlank()) ? "redirect:" + referer : "redirect:/";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(Model model) {
        model.addAttribute("statusCode", 403);
        model.addAttribute("title", "Доступ запрещён");
        model.addAttribute("message", "У вашей учётной записи недостаточно прав для выполнения этого действия.");
        return "error";
    }

    @ExceptionHandler({NoSuchElementException.class, NoHandlerFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(Exception ex, Model model) {
        model.addAttribute("statusCode", 404);
        model.addAttribute("title", "Ресурс не найден");
        model.addAttribute("message", ex.getMessage() != null ? ex.getMessage() : "Запрошенная страница или запись не найдена.");
        return "error";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneric(Exception ex, Model model) {
        model.addAttribute("statusCode", 500);
        model.addAttribute("title", "Внутренняя ошибка приложения");
        model.addAttribute("message", "Произошла ошибка при обработке запроса. Попробуйте повторить действие или вернуться на главную.");
        return "error";
    }
}
