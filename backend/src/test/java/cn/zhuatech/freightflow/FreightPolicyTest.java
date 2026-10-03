// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import static org.junit.jupiter.api.Assertions.*;

import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 精确金额、数量、时间及业务导出边界；官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class FreightPolicyTest {
  @Test
  void moneyDoesNotRound() {
    assertEquals(new BigDecimal("0.10"), FreightPolicy.decimal("0.10", 2, true));
    assertThrows(Problem.class, () -> FreightPolicy.decimal("0.001", 2, true));
  }

  @Test
  void quantitiesMustBePositive() {
    assertThrows(Problem.class, () -> FreightPolicy.decimal("0", 3, true));
    assertThrows(Problem.class, () -> FreightPolicy.integer("1.5", 1, 100));
  }

  @Test
  void moneyRejectsNonFiniteNegativeAndExcess() {
    for (var s : List.of("NaN", "Infinity", "-1", "1000000001"))
      assertThrows(Problem.class, () -> FreightPolicy.decimal(s, 2, false));
  }

  @Test
  void intervalUsesHalfOpenBoundaries() {
    var a = Instant.parse("2026-10-02T00:00:00Z");
    assertFalse(FreightPolicy.overlap(a, a.plusSeconds(60), a.plusSeconds(60), a.plusSeconds(120)));
    assertTrue(FreightPolicy.overlap(a, a.plusSeconds(61), a.plusSeconds(60), a.plusSeconds(120)));
  }

  @Test
  void timeRequiresExplicitUtcMinute() {
    assertThrows(Problem.class, () -> FreightPolicy.instant("2026-10-02T09:00"));
    assertThrows(Problem.class, () -> FreightPolicy.instant("2026-10-02T00:00:01Z"));
  }

  @Test
  void localMinuteUsesConfiguredZone() {
    assertEquals(
        Instant.parse("2026-10-02T02:00:00Z"),
        FreightPolicy.localInput("2026-10-02T10:00", ZoneId.of("Asia/Shanghai")));
  }

  @Test
  void localMinuteRejectsDstGapAndAmbiguity() {
    var zone = ZoneId.of("America/New_York");
    assertThrows(Problem.class, () -> FreightPolicy.localInput("2026-03-08T02:30", zone));
    assertThrows(Problem.class, () -> FreightPolicy.localInput("2026-11-01T01:30", zone));
  }

  @Test
  void csvNeutralizesFormulaAndQuotes() {
    assertEquals("\"'=1+2\"", FreightPolicy.csv("=1+2"));
    assertEquals("\"a\"\"b\"", FreightPolicy.csv("a\"b"));
  }
}
