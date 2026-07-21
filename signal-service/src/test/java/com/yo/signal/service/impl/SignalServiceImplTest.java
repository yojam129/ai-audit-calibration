package com.yo.signal.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class SignalServiceImplTest {
  @Test
  void baselineAndFeaturesAreDeterministic() {
    var n = SignalServiceImpl.normalize(List.of(2d, 2d, 4d));
    assertEquals(-2d / 3d, n.getFirst(), 1e-12);
    assertEquals(2d, SignalServiceImpl.features(n).get("amplitude"));
  }

  @Test
  void checksumStable() {
    assertEquals(SignalServiceImpl.checksum(List.of(1d)), SignalServiceImpl.checksum(List.of(1d)));
  }
}
