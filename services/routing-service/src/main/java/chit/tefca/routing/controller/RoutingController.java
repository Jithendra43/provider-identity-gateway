package chit.tefca.routing.controller;

import chit.tefca.routing.dto.RouteRequest;
import chit.tefca.routing.dto.RouteResponse;
import chit.tefca.routing.service.RoutingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/routing")
@RequiredArgsConstructor
public class RoutingController {

    private final RoutingService routingService;

    @PostMapping("/route")
    public ResponseEntity<RouteResponse> route(@Valid @RequestBody RouteRequest request) {
        RouteResponse response = routingService.routeTransaction(request);
        return ResponseEntity.ok(response);
    }
}
