// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 金额只作显示，服务端使用十进制账目。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function money(value, currency = "CNY", lang = "zh") {
  return new Intl.NumberFormat(lang === "en" ? "en-GB" : "zh-CN", {
    style: "currency",
    currency,
  }).format(Number(value || 0));
}
/** 分钟数量转换为时间字符串；24:00仅作边界显示。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function minutes(value) {
  const m = Number(value);
  return `${String(Math.floor(m / 60)).padStart(2, "0")}:${String(m % 60).padStart(2, "0")}`;
}
/** 时区日期由Intl解析，不借用浏览器本地日期；跨年及DST遵循企业时区。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function localDate(value, zone) {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: zone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date(value));
  const get = (t) => parts.find((x) => x.type === t).value;
  return `${get("year")}-${get("month")}-${get("day")}`;
}
/** 按企业时区展示运输时间，服务端时间戳使用UTC。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function localTime(value, zone, lang = "zh") {
  if (!value) return "—";
  return new Intl.DateTimeFormat(lang === "en" ? "en-GB" : "zh-CN", {
    timeZone: zone,
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(value));
}

/** 车次计划表单显示企业本地时间，UTC转换由后端完成。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function localInput(value, zone) {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: zone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date(value));
  const get = (t) => parts.find((x) => x.type === t).value;
  return `${get("year")}-${get("month")}-${get("day")}T${get("hour")}:${get("minute")}`;
}
