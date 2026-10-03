// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import java.math.*;
import java.time.*;
import java.util.*;

/** 货物数量、两位金额和半开资源预约区间约束。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class FreightPolicy {
  private FreightPolicy() {}

  /** 计划表单使用运营时区，重复或缺失本地时间拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static Instant localInput(Object value, ZoneId zone) {
    var t = LocalDateTime.parse(String.valueOf(value));
    var offsets = zone.getRules().getValidOffsets(t);
    if (offsets.size() != 1 || t.getSecond() != 0 || t.getNano() != 0)
      throw new Problem(400, "AMBIGUOUS_LOCAL_TIME");
    return t.toInstant(offsets.getFirst());
  }

  /** 校验精确非负小数，禁止舍入、科学计数导致精度损失和超界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal decimal(Object v, int scale, boolean positive) {
    try {
      var x = new BigDecimal(String.valueOf(v));
      if (x.scale() > scale
          || x.signum() < (positive ? 1 : 0)
          || x.compareTo(new BigDecimal("1000000000")) > 0)
        throw new Problem(400, "INVALID_AMOUNT");
      return x.setScale(scale);
    } catch (NumberFormatException e) {
      throw new Problem(400, "INVALID_AMOUNT");
    }
  }

  /** 有界正整数，不截断小数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static int integer(Object value, int min, int max) {
    try {
      int x = new BigDecimal(String.valueOf(value)).intValueExact();
      if (x < min || x > max) throw new Problem(400, "INVALID_INPUT");
      return x;
    } catch (ArithmeticException | NumberFormatException e) {
      throw new Problem(400, "INVALID_INPUT");
    }
  }

  /** 正标识引用；可选引用须由调用方显式处理空值。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static Long id(Map<String, Object> v, String key) {
    try {
      long x = new BigDecimal(String.valueOf(v.get(key))).longValueExact();
      if (x < 1) throw new Problem(400, "INVALID_INPUT");
      return x;
    } catch (ArithmeticException | NumberFormatException e) {
      throw new Problem(400, "INVALID_INPUT");
    }
  }

  /** 选填文本有界，必填由AdminService.text处理。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String optional(Object v, int max) {
    if (v == null) return "";
    if (!(v instanceof String s) || s.length() > max) throw new Problem(400, "INVALID_INPUT");
    return s.trim();
  }

  /** 明确偏移的UTC时间，整分钟且拒绝未指明时区输入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static Instant instant(Object v) {
    try {
      var x = Instant.parse(String.valueOf(v));
      if (x.getNano() != 0 || x.getEpochSecond() % 60 != 0) throw new Problem(400, "INVALID_TIME");
      return x;
    } catch (DateTimeException e) {
      throw new Problem(400, "INVALID_TIME");
    }
  }

  /** 半开资源窗口允许首尾相接。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static boolean overlap(Instant a, Instant b, Instant c, Instant d) {
    return a.isBefore(d) && c.isBefore(b);
  }

  /** CSV防公式执行，保持原业务文本，不添加联系广告。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String csv(Object v) {
    var s = String.valueOf(v);
    if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }
}
