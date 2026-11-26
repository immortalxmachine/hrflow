package com.hrflow.web;

import com.hrflow.dto.RequestDtos.CreateGenericRequest;
import com.hrflow.dto.RequestDtos.CreateLeaveRequest;
import com.hrflow.dto.RequestDtos.RequestDto;
import com.hrflow.service.RequestService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Endpoints for raising and viewing requests. Any authenticated user may raise one. */
@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final RequestService requests;

    public RequestController(RequestService requests) {
        this.requests = requests;
    }

    @PostMapping("/leave")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestDto createLeave(@Valid @RequestBody CreateLeaveRequest request) {
        return requests.createLeave(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequestDto createGeneric(@Valid @RequestBody CreateGenericRequest request) {
        return requests.createGeneric(request);
    }

    @GetMapping("/mine")
    public Page<RequestDto> myRequests(@PageableDefault(size = 20) Pageable pageable) {
        return requests.myRequests(pageable);
    }

    @GetMapping("/{id}")
    public RequestDto get(@PathVariable Long id) {
        return requests.get(id);
    }
}
