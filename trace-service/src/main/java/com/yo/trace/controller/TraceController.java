package com.yo.trace.controller;

import com.yo.trace.domain.dto.*;
import com.yo.trace.domain.query.*;
import com.yo.trace.domain.vo.*;
import com.yo.trace.service.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/traces")
public class TraceController {
  private final TraceService s;

  public TraceController(TraceService s) {
    this.s = s;
  }

  @PostMapping
  public TraceSearchVO append(@Valid @RequestBody AppendTraceDTO x) {
    return s.append(x);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('trace:view')")
  public List<TraceSearchVO> search(TraceQuery q) {
    return s.search(q);
  }

  @GetMapping("/chain/verify")
  @PreAuthorize("hasAuthority('trace:view')")
  public ChainVerificationVO verifyChain() {
    return s.verifyChain();
  }

  @PostMapping("/archive")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public java.util.Map<String, String> archive() {
    return java.util.Map.of("objectKey", s.archiveManifest());
  }
}
