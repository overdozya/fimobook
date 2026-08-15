package com.fimobook.backend;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DuplicateKeyException;

@RestController
@RequestMapping("/api")
public class ReviewController {

    public record ReviewRequest(int rating, String content) {
    }

    private final ReviewRepository repository;

    public ReviewController(ReviewRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/players/{cid}/reviews")
    public List<ReviewRepository.Review> list(@PathVariable long cid) {
        return repository.findByCid(cid);
    }

    @PostMapping("/players/{cid}/reviews")
    public ReviewRepository.Review create(@PathVariable long cid, @RequestBody ReviewRequest request,
            Authentication authentication) {
        validate(request);
        try {
            return repository.create(userId(authentication), cid, request.rating(), request.content().trim());
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "한 카드에는 평가를 하나만 작성할 수 있습니다.");
        }
    }

    @PutMapping("/reviews/{id}")
    public ReviewRepository.Review update(@PathVariable long id, @RequestBody ReviewRequest request,
            Authentication authentication) {
        validate(request);
        requireOwner(id, authentication);
        return repository.update(id, request.rating(), request.content().trim());
    }

    @DeleteMapping("/reviews/{id}")
    public void delete(@PathVariable long id, Authentication authentication) {
        requireOwner(id, authentication);
        repository.delete(id);
    }

    @PostMapping("/reviews/{id}/{reaction:like|dislike}")
    public ReviewRepository.Review react(@PathVariable long id, @PathVariable String reaction,
            Authentication authentication) {
        repository.findById(id).orElseThrow(() -> notFound("평가를 찾을 수 없습니다."));
        return repository.react(id, userId(authentication), reaction);
    }

    private void requireOwner(long id, Authentication authentication) {
        var review = repository.findById(id).orElseThrow(() -> notFound("평가를 찾을 수 없습니다."));
        if (review.userId() != userId(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인의 평가만 변경할 수 있습니다.");
        }
    }

    private void validate(ReviewRequest request) {
        String content = request.content() == null ? "" : request.content().trim();
        if (request.rating() < 1 || request.rating() > 5 || content.isEmpty() || content.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "별점과 100자 이내 평가를 입력해주세요.");
        }
    }

    private long userId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
