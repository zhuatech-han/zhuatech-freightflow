// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { money, minutes, localDate, localTime } from "./format.js";
test("store date handles midnight independently of browser timezone", () => {
  assert.equal(
    localDate("2026-12-31T23:30:00Z", "Asia/Shanghai"),
    "2027-01-01",
  );
  assert.equal(
    localDate("2027-01-01T00:30:00Z", "America/New_York"),
    "2026-12-31",
  );
});
test("store time respects daylight saving", () => {
  assert.match(
    localTime("2026-07-01T13:00:00Z", "America/New_York", "en"),
    /09:00/,
  );
  assert.match(
    localTime("2026-01-01T13:00:00Z", "America/New_York", "en"),
    /08:00/,
  );
});
test("format money and schedule endpoints without posting floating point totals", () => {
  assert.match(money("68.10", "USD", "en"), /68\.10/);
  assert.equal(minutes(0), "00:00");
  assert.equal(minutes(1440), "24:00");
});
