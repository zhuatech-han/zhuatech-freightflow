<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import {
  Truck,
  Package,
  LayoutDashboard,
  Users,
  Wallet,
  Settings,
  Menu,
  X,
  Plus,
  Search,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  ArrowUpRight,
  LogOut,
  Printer,
  Upload,
  Check,
  AlertTriangle,
} from "@lucide/vue";
import { api, resetCsrf, downloadReport } from "./api.js";
import { fields, columns, labels, statusLabels } from "./schema.js";
import { money, localDate, localTime, localInput } from "./format.js";
const lang = ref(localStorage.getItem("freightflow.language") || "zh"),
  profile = ref(null),
  catalog = ref({}),
  page = ref("dashboard"),
  rows = ref([]),
  total = ref(0),
  offset = ref(0),
  query = ref(""),
  filter = ref(""),
  sort = ref("id"),
  descending = ref(true),
  loading = ref(false),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  navOpen = ref(false),
  detail = ref(null),
  detailType = ref(""),
  dialog = ref(null),
  form = ref({}),
  options = ref({}),
  overview = ref({ counts: {}, shipments: [], trips: [] }),
  report = ref({ closedTrips: [] }),
  from = ref(""),
  to = ref(""),
  login = ref({ username: "", password: "" });
let querySequence = 0;
const t = (zh, en) => (lang.value === "en" ? en : zh),
  can = (p) => profile.value?.permissions.includes(p),
  internal = computed(() => profile.value?.internal),
  setting = (key) => catalog.value.settings?.find((x) => x.code === key)?.value,
  zone = computed(() => setting("timezone") || "Asia/Shanghai"),
  currency = computed(() => setting("currency") || "CNY"),
  cash = (v) => money(v, currency.value, lang.value),
  when = (v) => localTime(v, zone.value, lang.value),
  statusName = (v) => (statusLabels[v] ? t(...statusLabels[v]) : v),
  fieldName = (k) => (labels[k] ? t(...labels[k]) : k);
const menus = computed(() => profile.value?.menus || []),
  title = computed(
    () =>
      menus.value.find((x) => x.code === page.value)?.[
        lang.value === "en" ? "nameEn" : "name"
      ] || t("运营工作台", "Operations"),
  ),
  rowColumns = computed(() =>
    (columns[page.value] || []).filter(
      (k) =>
        internal.value ||
        !["freight", "carrierFee"].includes(k) ||
        (profile.value?.customerId && k === "freight"),
    ),
  );
const icons = {
  dashboard: LayoutDashboard,
  shipments: Package,
  trips: Truck,
  invoices: Wallet,
  expenses: Wallet,
  customers: Users,
  drivers: Users,
};
const errorLabels = {
  INVALID_INPUT: ["请检查必填字段及长度", "Check required fields and lengths"],
  INVALID_NUMBER: [
    "请填写范围内的有效数字",
    "Enter a valid number within the allowed range",
  ],
  INVALID_TIME: ["请检查起止时间与时区", "Check schedule dates and timezone"],
  INVALID_MONEY: ["金额或精度不正确", "Invalid amount or precision"],
  PARTIAL_DELIVERY_UNSUPPORTED: [
    "实收件数须与运单一致；当前版本不支持部分签收",
    "Received pieces must match; partial delivery is unsupported",
  ],
  CURRENCY_LOCKED: [
    "已有业务记录，不能更改币种",
    "Currency cannot change after business records exist",
  ],
  TIMEZONE_LOCKED: [
    "已有业务记录，不能更改时区",
    "Timezone cannot change after business records exist",
  ],
  PAYMENT_EXISTS: [
    "已有收付款流水，不能作废",
    "A statement with payment history cannot be voided",
  ],
  OUT_OF_SCOPE: [
    "此记录不在你的业务范围内",
    "This record is outside your scope",
  ],
  NOT_ASSIGNED_DRIVER: [
    "只有该车次关联司机可以执行",
    "Only the assigned driver may execute this trip",
  ],
  CAPACITY_EXCEEDED: [
    "超过车辆重量、体积或件数容量",
    "Vehicle capacity exceeded",
  ],
  RESOURCE_BUSY: [
    "司机或车辆已被占用",
    "Driver or vehicle is already reserved",
  ],
  STALE_VERSION: [
    "记录已更新，请刷新后重试",
    "Record changed; refresh and retry",
  ],
  PROOF_REQUIRED: ["请先上传签收照片", "Upload delivery evidence first"],
  UNRESOLVED_ISSUE: [
    "还有未解决的运输异常",
    "Resolve the open transport issue first",
  ],
  DELIVERY_INCOMPLETE: [
    "运单尚未全部确认签收",
    "Delivery has not been approved",
  ],
  EXPENSE_UNREVIEWED: ["费用尚未复核", "Expenses are pending review"],
  INDEPENDENT_REVIEW_REQUIRED: [
    "需由另一名财务账号复核",
    "A different finance user must review",
  ],
  OVERPAYMENT: ["金额超过待结算余额", "Amount exceeds outstanding balance"],
  OVER_REVERSAL: [
    "金额超过原流水可冲正金额",
    "Amount exceeds reversible balance",
  ],
  LINKED_ACCOUNT_ROLE: [
    "关联账号的角色或范围不适用",
    "Role or scope does not match the linked identity",
  ],
  ACTIVE_ASSIGNMENT: [
    "已有未完成派车，暂不可更改",
    "An active assignment prevents this change",
  ],
  WEAK_PASSWORD: [
    "密码需12–72位，含大小写字母和数字",
    "Password: 12–72 characters with upper/lowercase and digits",
  ],
  LOGIN_FAILED: ["用户名或密码不正确", "Invalid username or password"],
  INVALID_STATE: [
    "当前状态不允许此操作",
    "Operation is unavailable in this state",
  ],
  INVALID_IMAGE: [
    "仅接受有效PNG或JPEG图片",
    "Choose a valid PNG or JPEG image",
  ],
  UNAUTHENTICATED: ["登录已失效，请重新登录", "Session expired; sign in again"],
  LINKED_ACCOUNT_IDENTITY: [
    "已有业务关联，身份不能更换",
    "Linked identity cannot be replaced",
  ],
  DEPARTMENT_MISMATCH: [
    "客户、司机、车辆须属于同一运营部门",
    "Records must belong to the same department",
  ],
  FORBIDDEN: ["没有此操作权限", "You do not have permission"],
  TOO_EARLY: ["尚未到允许发车时间", "It is too early to depart"],
  ALREADY_INVOICED: [
    "该运单或车次已生成对账单",
    "This record is already invoiced",
  ],
  EMPTY_TRIP: ["请先添加运单", "Add shipments first"],
  CONFLICT: [
    "记录存在引用或编号重复",
    "A reference or duplicate code prevents this change",
  ],
  LAST_ADMIN: [
    "必须保留一个可用管理员",
    "Keep at least one active administrator",
  ],
};
function fail(e) {
  error.value = errorLabels[e.message]
    ? t(...errorLabels[e.message])
    : t("操作失败：", "Operation failed: ") + e.message;
  if (e.message === "UNAUTHENTICATED") {
    profile.value = null;
    detail.value = null;
  }
}
async function run(fn) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await fn();
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
function language() {
  notice.value = "";
  error.value = "";
  lang.value = lang.value === "zh" ? "en" : "zh";
  localStorage.setItem("freightflow.language", lang.value);
  document.documentElement.lang = lang.value === "en" ? "en" : "zh-CN";
}
async function init() {
  catalog.value = await api("/catalog");
  profile.value = catalog.value.profile;
  if (!menus.value.some((m) => m.code === page.value))
    page.value = menus.value[0]?.code || "dashboard";
  from.value ||= localDate(new Date(), zone.value);
  to.value ||= from.value;
  await load();
}
async function signIn() {
  await run(async () => {
    await api("/auth/login", "POST", login.value);
    resetCsrf();
    login.value.password = "";
    await init();
  });
}
async function signOut() {
  await run(async () => {
    await api("/auth/logout", "POST", {});
    profile.value = null;
    detail.value = null;
    resetCsrf();
  });
}
async function load() {
  const seq = ++querySequence,
    target = page.value;
  loading.value = true;
  error.value = "";
  try {
    let value;
    if (target === "dashboard") value = await api("/dashboard");
    else if (target === "reports")
      value = await api(
        "/reports?" + new URLSearchParams({ from: from.value, to: to.value }),
      );
    else
      value = await api(
        "/lists/" +
          target +
          "?" +
          new URLSearchParams({
            page: offset.value,
            size: 20,
            q: query.value,
            status: filter.value,
            sort: sort.value,
            descending: descending.value,
          }),
      );
    if (seq !== querySequence || target !== page.value) return;
    if (target === "dashboard") overview.value = value;
    else if (target === "reports") report.value = value;
    else {
      rows.value = value.items;
      total.value = value.total;
    }
  } catch (e) {
    if (seq === querySequence) fail(e);
  } finally {
    if (seq === querySequence) loading.value = false;
  }
}
async function navigate(name) {
  page.value = name;
  detail.value = null;
  query.value = "";
  filter.value = "";
  offset.value = 0;
  navOpen.value = false;
  await load();
}
async function refresh() {
  await run(async () => {
    catalog.value = await api("/catalog");
    profile.value = catalog.value.profile;
    if (detail.value)
      await openDetail(
        detailType.value,
        (detail.value.shipment || detail.value.trip || detail.value.invoice).id,
      );
    else await load();
  });
}
async function openDetail(type, id) {
  error.value = "";
  try {
    detail.value = await api("/" + type + "/" + id);
    detailType.value = type;
  } catch (e) {
    fail(e);
  }
}
const current = computed(
  () => detail.value?.shipment || detail.value?.trip || detail.value?.invoice,
);
function display(row, key) {
  const v = row[key];
  if (v === null || v === undefined) return "—";
  if (["freight", "carrierFee", "total", "paid", "amount"].includes(key))
    return cash(v);
  if (["createdAt", "updatedAt", "plannedStart", "plannedEnd"].includes(key))
    return when(v);
  if (key === "enabled")
    return v ? t("启用", "Enabled") : t("停用", "Disabled");
  if (["status", "scope", "kind"].includes(key)) return statusName(v);
  if (key === "roleId")
    return catalog.value.roles?.find((x) => x.id === v)?.name || v;
  if (key === "departmentId")
    return catalog.value.departments?.find((x) => x.id === v)?.name || v;
  if (Array.isArray(v)) return v.join(", ");
  return v;
}
const statuses = computed(() =>
  page.value === "shipments"
    ? [
        "DRAFT",
        "READY",
        "PLANNED",
        "IN_TRANSIT",
        "POD_PENDING",
        "DELIVERED",
        "CANCELLED",
      ]
    : page.value === "trips"
      ? ["DRAFT", "DISPATCHED", "ACCEPTED", "IN_TRANSIT", "CLOSED", "CANCELLED"]
      : page.value === "expenses"
        ? ["SUBMITTED", "APPROVED", "REJECTED"]
        : page.value === "invoices"
          ? ["OPEN", "PARTIAL", "PAID", "VOID"]
          : [],
);
const administrative = [
  "users",
  "roles",
  "departments",
  "permissions",
  "menus",
  "dictionaries",
  "settings",
];
const masterPages = ["customers", "carriers", "drivers", "vehicles"];
const canCreate = computed(() =>
  page.value === "shipments"
    ? can("shipment.write")
    : page.value === "trips"
      ? can("dispatch")
      : page.value === "expenses"
        ? can("expense.write")
        : page.value === "invoices"
          ? can("finance")
          : masterPages.includes(page.value)
            ? can("master.edit")
            : administrative.includes(page.value) &&
              !["permissions", "menus", "settings"].includes(page.value) &&
              can("admin"),
);
function canEdit(row) {
  return masterPages.includes(page.value)
    ? can("master.edit")
    : administrative.includes(page.value)
      ? can("admin")
      : page.value === "shipments"
        ? can("shipment.write") && row.status === "DRAFT"
        : page.value === "trips"
          ? can("dispatch") && row.status === "DRAFT"
          : false;
}
const activeFields = computed(() => {
  const f = dialog.value?.fields || fields[dialog.value?.type] || [];
  return f.filter(
    (x) =>
      !(
        dialog.value?.type === "shipments" &&
        !internal.value &&
        x[0] === "customerId"
      ) &&
      !(
        dialog.value?.type === "invoices" &&
        (form.value.kind === "PAYABLE"
          ? ["customerId", "shipmentIds"].includes(x[0])
          : x[0] === "tripId")
      ),
  );
});
function optionsFor(kind) {
  if (kind === "scope")
    return ["ALL", "DEPARTMENT", "ASSIGNED"].map((v) => ({
      id: v,
      name: statusName(v),
    }));
  if (kind === "invoiceKind")
    return ["RECEIVABLE", "PAYABLE"].map((v) => ({
      id: v,
      name: statusName(v),
    }));
  if (kind === "expenseType")
    return ["FUEL", "TOLL", "PARKING", "OTHER"].map((v) => ({
      id: v,
      name: t(
        ...{
          FUEL: ["燃油", "Fuel"],
          TOLL: ["通行费", "Toll"],
          PARKING: ["停车", "Parking"],
          OTHER: ["其他", "Other"],
        }[v],
      ),
    }));
  if (kind === "permissions" || kind === "permissionCode")
    return (catalog.value.permissions || []).map((v) => ({
      id: v.code,
      name: v.name,
    }));
  if (kind === "methods")
    return (catalog.value.methods || []).map((v) => ({
      id: v.code,
      name: t(v.name, v.nameEn),
    }));
  if (kind === "sources")
    return (detail.value.entries || [])
      .filter((x) => x.kind === "PAYMENT")
      .map((x) => ({
        id: x.id,
        name: `#${x.id} · ${cash(x.amount)} · ${x.reference}`,
      }));
  if (kind === "shipmentsReady")
    return (options.value.shipments || [])
      .filter(
        (s) =>
          s.status === "READY" && s.departmentId === current.value.departmentId,
      )
      .map((s) => ({
        id: s.id,
        name: `${s.code} · ${s.customerName} · ${s.goods}`,
      }));
  if (kind === "deliveredShipments")
    return (options.value.shipments || [])
      .filter(
        (s) =>
          s.status === "DELIVERED" &&
          !s.invoiceId &&
          s.customerId === Number(form.value.customerId),
      )
      .map((s) => ({
        id: s.id,
        name: `${s.code} · ${s.goods} · ${cash(s.freight)}`,
      }));
  if (kind === "closedTrips")
    return (options.value.trips || [])
      .filter((x) => x.status === "CLOSED" && !x.invoiceId)
      .map((x) => ({ id: x.id, name: x.code }));
  if (kind === "activeTrips")
    return (options.value.trips || [])
      .filter((x) => x.status === "IN_TRANSIT")
      .map((x) => ({ id: x.id, name: x.code }));
  let opts = catalog.value[kind] || [];
  if (kind === "departments" && !can("admin"))
    opts = [
      { id: profile.value.departmentId, name: t("本部门", "Own department") },
    ];
  if (
    ["drivers", "vehicles"].includes(kind) &&
    dialog.value?.type === "trips" &&
    form.value.carrierId
  )
    opts = opts.filter((x) => x.carrierId === Number(form.value.carrierId));
  return opts.map((x) => ({
    id: x.id,
    name: x.name || x.displayName || x.code,
  }));
}
const selectKinds = [
  "customers",
  "carriers",
  "drivers",
  "vehicles",
  "users",
  "roles",
  "departments",
  "scope",
  "permissions",
  "permissionCode",
  "methods",
  "sources",
  "expenseType",
  "invoiceKind",
  "closedTrips",
  "activeTrips",
  "deliveredShipments",
  "shipmentsReady",
];
async function allOptions(path) {
  let out = [],
    n = 0;
  while (true) {
    const v = await api(path + "&size=100&page=" + n++);
    out.push(...v.items);
    if (out.length >= v.total) break;
  }
  return out;
}
async function editor(type, row = null) {
  error.value = "";
  await run(async () => {
    if (["expenses", "invoices"].includes(type)) {
      options.value.trips = await allOptions("/lists/trips?");
    }
    if (type === "invoices")
      options.value.shipments = await allOptions(
        "/lists/shipments?status=DELIVERED",
      );
    form.value = {
      enabled: true,
      departmentId: profile.value.departmentId,
      carrierFee: "0",
      kind: "RECEIVABLE",
      shipmentIds: [],
      permissions: [],
      dueDate: to.value,
      plannedStartLocal: localInput(new Date(), zone.value),
      plannedEndLocal: localInput(
        new Date(Date.now() + 8 * 3600000),
        zone.value,
      ),
      ...JSON.parse(JSON.stringify(row || {})),
    };
    if (type === "trips" && row) {
      form.value.plannedStartLocal = localInput(row.plannedStart, zone.value);
      form.value.plannedEndLocal = localInput(row.plannedEnd, zone.value);
    }
    form.value.password = "";
    if (!internal.value) form.value.customerId = profile.value.customerId;
    dialog.value = {
      type,
      id: row?.id,
      title: row
        ? t("编辑", "Edit") + " · " + title.value
        : t("新建", "New") + " · " + title.value,
      key: crypto.randomUUID(),
    };
  });
}
async function save() {
  await run(async () => {
    const d = dialog.value;
    const body = JSON.parse(JSON.stringify(form.value));
    const signature = JSON.stringify(body);
    if (d.signature && d.signature !== signature) d.key = crypto.randomUUID();
    d.signature = signature;
    for (const f of activeFields.value) {
      if (
        selectKinds.includes(f[3]) &&
        ![
          "permissions",
          "permissionCode",
          "methods",
          "scope",
          "invoiceKind",
          "expenseType",
        ].includes(f[3]) &&
        !["shipmentIds"].includes(f[0])
      ) {
        if (body[f[0]] === undefined || body[f[0]] === "") body[f[0]] = null;
        else body[f[0]] = Number(body[f[0]]);
      }
    }
    if (d.mode === "password") {
      await api("/auth/password", "POST", body);
      dialog.value = null;
      profile.value = null;
      resetCsrf();
      return;
    }
    if (d.mode === "delete") {
      await api("/" + d.path + "/" + d.id, "DELETE");
    } else if (d.mode === "action") {
      await api(d.path, "POST", {
        ...body,
        revision: d.revision ?? current.value?.revision,
        requestKey: d.key,
      });
    } else {
      let prefix = administrative.includes(d.type)
        ? "/admin/"
        : masterPages.includes(d.type)
          ? "/master/"
          : "/";
      if (!administrative.includes(d.type) && !masterPages.includes(d.type))
        body.requestKey = d.key;
      await api(
        prefix + d.type + (d.id ? "/" + d.id : ""),
        d.id ? "PUT" : "POST",
        body,
      );
    }
    dialog.value = null;
    notice.value = t("已保存", "Saved");
    catalog.value = await api("/catalog");
    profile.value = catalog.value.profile;
    if (detail.value) await openDetail(detailType.value, current.value.id);
    else await load();
  });
}
async function action(name, label, extra = [], defaults = {}) {
  await run(async () => {
    if (name === "add")
      options.value.shipments = await allOptions(
        "/lists/shipments?status=READY",
      );
    form.value = { ...defaults };
    dialog.value = {
      mode: "action",
      path: "/" + detailType.value + "/" + current.value.id + "/" + name,
      title: label,
      key: crypto.randomUUID(),
      fields: extra,
    };
  });
}
async function immediate(name) {
  await run(async () => {
    await api(
      "/" + detailType.value + "/" + current.value.id + "/" + name,
      "POST",
      { revision: current.value.revision, requestKey: crypto.randomUUID() },
    );
    await openDetail(detailType.value, current.value.id);
  });
}
function deleteRow(row) {
  form.value = {};
  dialog.value = {
    mode: "delete",
    id: row.id,
    path: administrative.includes(page.value)
      ? "admin/" + page.value
      : "master/" + page.value,
    title: t("删除未引用记录", "Delete unreferenced record"),
    fields: [],
  };
}
function password() {
  form.value = {};
  dialog.value = {
    mode: "password",
    title: t("修改密码", "Change password"),
    fields: [
      ["oldPassword", "原密码", "Current password", "password", true],
      ["newPassword", "新密码", "New password", "password", true],
    ],
  };
}
async function upload(event) {
  const file = event.target.files[0];
  event.target.value = "";
  if (!file) return;
  await run(async () => {
    const token = await api("/auth/csrf");
    const body = new FormData();
    body.append("file", file);
    const r = await fetch("/api/shipments/" + current.value.id + "/proofs", {
      method: "POST",
      headers: { [token.header]: token.token },
      body,
    });
    const value = await r.json();
    if (!r.ok) throw new Error(value.code);
    await openDetail("shipments", current.value.id);
  });
}
async function importFile(event) {
  const file = event.target.files[0];
  event.target.value = "";
  if (!file) return;
  await run(async () => {
    if (file.size > 500000) throw new Error("INVALID_IMPORT");
    const items = JSON.parse(await file.text());
    await api("/shipments/import", "POST", {
      requestKey: crypto.randomUUID(),
      items,
    });
    notice.value = t("整批导入成功", "Batch imported");
    await load();
  });
}
async function reviewExpense(row, approved) {
  form.value = { approved, note: "" };
  dialog.value = {
    mode: "action",
    path: "/expenses/" + row.id + "/review",
    revision: row.revision,
    title: approved
      ? t("核准费用", "Approve expense")
      : t("退回费用", "Reject expense"),
    key: crypto.randomUUID(),
    fields: [["note", "复核说明", "Review note", "textarea", true]],
  };
}
async function resolveIssue(row) {
  form.value = {};
  dialog.value = {
    mode: "action",
    path: "/issues/" + row.id + "/resolve",
    revision: row.revision,
    title: t("解决异常", "Resolve issue"),
    key: crypto.randomUUID(),
    fields: [["note", "处理结果", "Resolution", "textarea", true]],
  };
}
const ownsTrip = computed(
  () =>
    profile.value?.driverId &&
    current.value?.driverId === profile.value.driverId,
);
const canDeliver = computed(
  () =>
    can("driver.execute") &&
    current.value?.status === "IN_TRANSIT" &&
    profile.value?.driverId,
);
function back() {
  detail.value = null;
  load();
}
function about() {
  dialog.value = {
    mode: "about",
    title: t("关于与授权", "About & licence"),
    fields: [],
  };
}
const noteField = [["note", "原因 / 说明", "Reason / note", "textarea", true]];
function eventName(k) {
  const names = {
    ADD: ["添加配载", "Load added"],
    REMOVE: ["移出配载", "Load removed"],
    DEPART: ["确认发车", "Departure confirmed"],
    TRIP_CANCELLED: ["关联车次取消", "Trip cancelled"],
    EXPENSE_SUBMITTED: ["费用填报", "Expense submitted"],
    EXPENSE_APPROVED: ["费用核准", "Expense approved"],
    EXPENSE_REJECTED: ["费用退回", "Expense rejected"],
    CREATED: ["建立运单", "Created"],
    EDITED: ["编辑运单", "Edited"],
    QUOTE: ["确认报价", "Quoted"],
    LOADED: ["配载", "Loaded"],
    UNLOADED: ["移出配载", "Unloaded"],
    TRIP_SAVED: ["车次计划", "Trip planned"],
    DISPATCH: ["派车", "Dispatched"],
    ACCEPT: ["司机接单", "Accepted"],
    DEPARTED: ["发车", "Departed"],
    DELIVERY_SUBMITTED: ["提交签收", "Delivery submitted"],
    "CONFIRM-DELIVERY": ["签收复核通过", "Delivery approved"],
    "REJECT-DELIVERY": ["回单退回", "POD rejected"],
    CANCEL: ["取消", "Cancelled"],
    CLOSE: ["运输关单", "Closed"],
    ISSUE: ["运输异常", "Issue"],
    PROOF_UPLOADED: ["回单上传", "Evidence uploaded"],
    ISSUE_RESOLVED: ["异常已解决", "Resolved"],
  };
  return names[k] ? t(...names[k]) : k;
}
function reportName(k) {
  return t(
    ...{
      revenue: ["期间关单运费", "Closed-trip revenue"],
      cost: ["期间关单成本", "Closed-trip costs"],
      profit: ["期间关单利润", "Closed-trip profit"],
      receipts: ["期间净收款", "Net receipts"],
      disbursements: ["期间净付款", "Net disbursements"],
      netCash: ["期间净现金", "Net cash"],
      receivables: ["全部未收余额", "Outstanding receivables"],
      payables: ["全部未付余额", "Outstanding payables"],
    }[k],
  );
}
function print() {
  window.print();
}
async function exportCsv() {
  await run(() => downloadReport(from.value, to.value));
}
onMounted(async () => {
  try {
    await init();
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED") fail(e);
  }
});
</script>
<template>
  <div v-if="!profile" class="login-shell">
    <aside class="login-aside">
      <img src="/brand/logo.jpg" alt="知华科技" /><small
        >ZHUA TECH / FREIGHTFLOW</small
      >
      <h1>{{ t("运输与运费结算", "Transport & freight settlement") }}</h1>
      <div class="route-line">
        <span>{{ t("托运", "Consignment") }}</span
        ><span>{{ t("运输", "Delivery") }}</span
        ><span>{{ t("结算", "Settlement") }}</span>
      </div>
    </aside>
    <section class="login-panel">
      <button class="language" @click="language">
        {{ lang === "zh" ? "English" : "中文" }}
      </button>
      <form class="login-form" @submit.prevent="signIn">
        <small>FREIGHTFLOW</small>
        <h2>{{ t("登录工作台", "Sign in") }}</h2>
        <p>{{ t("使用管理员分配的账号", "Use your assigned account") }}</p>
        <label
          >{{ t("用户名", "Username")
          }}<input
            v-model.trim="login.username"
            autocomplete="username"
            required /></label
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="login.password"
            type="password"
            autocomplete="current-password"
            required
            maxlength="72"
        /></label>
        <p v-if="error" class="alert error" role="alert">{{ error }}</p>
        <button class="primary" :disabled="busy">
          {{ t("登录", "Sign in") }}
        </button>
        <footer>
          <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
            >知华科技</a
          ><span>{{
            t(
              "非商业源码版 · 商用需授权",
              "Non-commercial source · Commercial licence required",
            )
          }}</span>
        </footer>
      </form>
    </section>
  </div>
  <div v-else class="app-shell">
    <div v-if="navOpen" class="nav-shade" @click="navOpen = false"></div>
    <aside class="sidebar" :class="{ open: navOpen }">
      <a class="brand" href="#" @click.prevent="navigate('dashboard')"
        ><img src="/brand/logo.jpg" alt="知华科技" /><span
          >FreightFlow<small>{{
            t("运输与运费结算", "Transport operations")
          }}</small></span
        ></a
      ><button
        class="close-nav"
        aria-label="Close menu"
        @click="navOpen = false"
      >
        <X :size="20" />
      </button>
      <nav>
        <template v-for="(m, i) in menus" :key="m.code"
          ><small
            v-if="
              i === 0 ||
              (administrative.includes(m.code) &&
                !administrative.includes(menus[i - 1]?.code))
            "
            class="nav-section"
            >{{
              administrative.includes(m.code)
                ? t("系统管理", "Administration")
                : t("业务运营", "Operations")
            }}</small
          ><button
            :class="{ active: page === m.code }"
            @click="navigate(m.code)"
          >
            <component :is="icons[m.code] || Settings" :size="18" />{{
              t(m.name, m.nameEn)
            }}
          </button></template
        >
      </nav>
      <footer>
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >知华科技<ArrowUpRight :size="13" /></a
        ><button @click="about">
          {{ t("授权与联系", "Licence & contact") }}
        </button>
      </footer>
    </aside>
    <main class="workspace">
      <header class="topbar">
        <button
          class="mobile-menu icon-button"
          aria-label="Open menu"
          @click="navOpen = true"
        >
          <Menu :size="20" />
        </button>
        <div class="breadcrumb">
          {{ setting("companyName") || "FreightFlow" }} / {{ title }}
        </div>
        <div class="account">
          <button @click="language">
            {{ lang === "zh" ? "English" : "中文" }}</button
          ><span>{{ profile.displayName || profile.username }}</span
          ><button @click="password">{{ t("密码", "Password") }}</button
          ><button
            class="icon-button"
            :aria-label="t('退出', 'Sign out')"
            @click="signOut"
          >
            <LogOut :size="17" />
          </button>
        </div>
      </header>
      <div class="page-content">
        <p v-if="error" class="alert error" role="alert">{{ error }}</p>
        <p v-if="notice" class="alert success" role="status">{{ notice }}</p>
        <div class="page-heading">
          <div>
            <p class="eyebrow">FREIGHT OPERATIONS</p>
            <h1>{{ detail ? current.code : title }}</h1>
          </div>
          <div class="toolbar">
            <button v-if="detail" @click="back">
              <ChevronLeft :size="16" />{{ t("返回", "Back") }}</button
            ><button @click="refresh" :disabled="busy || loading">
              <RefreshCw :size="15" />{{ t("刷新", "Refresh") }}</button
            ><button v-if="detail" @click="print">
              <Printer :size="16" />{{ t("打印", "Print") }}</button
            ><button
              v-if="!detail && canCreate"
              class="primary"
              @click="editor(page)"
            >
              <Plus :size="16" />{{ t("新建", "New") }}
            </button>
          </div>
        </div>
        <section v-if="detail" class="detail-view">
          <h1 class="print-title">{{ current.code }}</h1>
          <div class="record-header">
            <span class="badge" :data-status="current.status">{{
              statusName(current.status)
            }}</span
            ><span>{{ when(current.createdAt) }}</span
            ><span>{{ t("修订", "Revision") }} {{ current.revision }}</span>
          </div>
          <template v-if="detailType === 'shipments'"
            ><section class="panel">
              <div class="panel-title">
                <h2>{{ current.customerName }}</h2>
                <span v-if="current.freight !== undefined"
                  >{{ t("客户运价", "Freight") }}
                  <strong>{{ cash(current.freight) }}</strong></span
                >
              </div>
              <div class="route">
                <div>
                  <small>{{ t("装货地址", "PICKUP") }}</small>
                  <p>{{ current.origin }}</p>
                </div>
                <ArrowUpRight :size="22" />
                <div>
                  <small>{{ t("卸货地址", "DELIVERY") }}</small>
                  <p>{{ current.destination }}</p>
                </div>
              </div>
              <dl class="facts">
                <div>
                  <dt>{{ t("货物", "Goods") }}</dt>
                  <dd>{{ current.goods }}</dd>
                </div>
                <div>
                  <dt>
                    {{ t("件 / 重量 / 体积", "Pieces / Weight / Volume") }}
                  </dt>
                  <dd>
                    {{ current.pieces }} / {{ current.weightKg }} kg /
                    {{ current.volumeM3 }} m³
                  </dd>
                </div>
                <div>
                  <dt>{{ t("收货人", "Consignee") }}</dt>
                  <dd>{{ current.consignee }} {{ current.consigneePhone }}</dd>
                </div>
                <div v-if="current.receiver">
                  <dt>{{ t("实际签收", "Received by") }}</dt>
                  <dd>
                    {{ current.receiver }} · {{ when(current.deliveredAt) }}
                  </dd>
                </div>
              </dl>
            </section>
            <div class="record-actions">
              <button
                v-if="current.status === 'DRAFT' && can('shipment.write')"
                @click="editor('shipments', current)"
              >
                {{ t("编辑运单", "Edit shipment") }}</button
              ><button
                v-if="current.status === 'DRAFT' && can('dispatch')"
                class="primary"
                @click="
                  action(
                    'quote',
                    t('确认报价', 'Confirm quotation'),
                    [
                      [
                        'freight',
                        '客户运价',
                        'Client freight',
                        'decimal',
                        true,
                      ],
                    ],
                    { freight: current.freight },
                  )
                "
              >
                {{ t("报价并待配载", "Quote & release") }}</button
              ><button
                v-if="
                  ['DRAFT', 'READY'].includes(current.status) &&
                  can('shipment.write')
                "
                class="danger"
                @click="
                  action('cancel', t('取消运单', 'Cancel shipment'), noteField)
                "
              >
                {{ t("取消运单", "Cancel shipment") }}</button
              ><label v-if="canDeliver" class="button"
                ><Upload :size="16" />{{ t("上传回单", "Upload evidence")
                }}<input
                  type="file"
                  accept="image/png,image/jpeg"
                  @change="upload"
                  :disabled="busy" /></label
              ><button
                v-if="canDeliver"
                class="primary"
                @click="
                  action(
                    'deliver',
                    t('提交签收', 'Submit delivery'),
                    [
                      ['receiver', '实际签收人', 'Received by', 'text', true],
                      [
                        'receivedPieces',
                        '实收件数',
                        'Received pieces',
                        'number',
                        true,
                      ],
                    ],
                    { receivedPieces: current.pieces },
                  )
                "
              >
                {{ t("提交签收", "Submit delivery") }}</button
              ><button
                v-if="
                  ['IN_TRANSIT', 'POD_PENDING'].includes(current.status) &&
                  (can('driver.execute') || can('dispatch'))
                "
                @click="
                  action('issue', t('登记异常', 'Report issue'), noteField)
                "
              >
                <AlertTriangle :size="16" />{{
                  t("登记异常", "Report issue")
                }}</button
              ><button
                v-if="current.status === 'POD_PENDING' && can('dispatch')"
                class="primary"
                @click="immediate('confirm-delivery')"
              >
                <Check :size="16" />{{
                  t("复核通过签收", "Approve delivery")
                }}</button
              ><button
                v-if="current.status === 'POD_PENDING' && can('dispatch')"
                @click="
                  action(
                    'reject-delivery',
                    t('退回回单', 'Reject delivery'),
                    noteField,
                  )
                "
              >
                {{ t("退回回单", "Reject delivery") }}
              </button>
            </div>
            <section class="panel">
              <div class="panel-title">
                <h2>{{ t("签收回单", "Delivery evidence") }}</h2>
                <span>{{ detail.proofs.length }} / 5</span>
              </div>
              <div class="proof-grid">
                <a
                  v-for="p in detail.proofs"
                  :key="p.id"
                  :href="'/api/proofs/' + p.id"
                  target="_blank"
                  rel="noopener"
                  ><img
                    :src="'/api/proofs/' + p.id"
                    :alt="t('签收照片', 'Delivery photo') + ' #' + p.id"
                  /><span>#{{ p.id }} · {{ when(p.createdAt) }}</span></a
                >
                <p v-if="!detail.proofs.length" class="empty">
                  {{ t("暂无回单", "No evidence uploaded") }}
                </p>
              </div>
            </section></template
          >
          <template v-else-if="detailType === 'trips'"
            ><section class="panel">
              <div class="panel-title">
                <h2>{{ current.vehicleName }} · {{ current.driverName }}</h2>
                <span>{{ current.carrierName }}</span>
              </div>
              <dl class="facts">
                <div>
                  <dt>{{ t("计划发车", "Planned departure") }}</dt>
                  <dd>{{ when(current.plannedStart) }}</dd>
                </div>
                <div>
                  <dt>{{ t("计划结束", "Planned finish") }}</dt>
                  <dd>{{ when(current.plannedEnd) }}</dd>
                </div>
                <div v-if="current.carrierFee !== undefined">
                  <dt>{{ t("约定承运费", "Carrier fee") }}</dt>
                  <dd>{{ cash(current.carrierFee) }}</dd>
                </div>
                <div>
                  <dt>{{ t("已配载", "Loaded") }}</dt>
                  <dd>
                    {{ detail.shipments.reduce((s, x) => s + x.pieces, 0) }}
                    {{ t("件", "pcs") }} /
                    {{
                      detail.shipments
                        .reduce((s, x) => s + Number(x.weightKg), 0)
                        .toFixed(3)
                    }}
                    kg /
                    {{
                      detail.shipments
                        .reduce((s, x) => s + Number(x.volumeM3), 0)
                        .toFixed(3)
                    }}
                    m³
                  </dd>
                </div>
                <div v-if="current.startedAt">
                  <dt>{{ t("实际发车", "Departed") }}</dt>
                  <dd>{{ when(current.startedAt) }}</dd>
                </div>
                <div v-if="current.closedAt">
                  <dt>{{ t("关单时间", "Closed") }}</dt>
                  <dd>{{ when(current.closedAt) }}</dd>
                </div>
              </dl>
              <p v-if="current.note" class="record-note">{{ current.note }}</p>
            </section>
            <div class="record-actions">
              <template v-if="can('dispatch')"
                ><button
                  v-if="current.status === 'DRAFT'"
                  @click="editor('trips', current)"
                >
                  {{ t("编辑车次", "Edit trip") }}</button
                ><button
                  v-if="current.status === 'DRAFT'"
                  @click="
                    action('add', t('添加配载', 'Load shipment'), [
                      [
                        'shipmentId',
                        '待配载运单',
                        'Ready shipment',
                        'shipmentsReady',
                        true,
                      ],
                    ])
                  "
                >
                  <Plus :size="16" />{{
                    t("添加运单", "Load shipment")
                  }}</button
                ><button
                  v-if="current.status === 'DRAFT'"
                  class="primary"
                  @click="immediate('dispatch')"
                >
                  {{ t("派车", "Dispatch") }}</button
                ><button
                  v-if="
                    ['DRAFT', 'DISPATCHED', 'ACCEPTED'].includes(current.status)
                  "
                  class="danger"
                  @click="
                    action('cancel', t('取消车次', 'Cancel trip'), noteField)
                  "
                >
                  {{ t("取消车次", "Cancel trip") }}</button
                ><button
                  v-if="current.status === 'IN_TRANSIT'"
                  class="primary"
                  @click="immediate('close')"
                >
                  {{ t("结束运输并关单", "Close trip") }}
                </button></template
              ><button
                v-if="ownsTrip && current.status === 'DISPATCHED'"
                class="primary"
                @click="immediate('accept')"
              >
                {{ t("司机接单", "Accept assignment") }}</button
              ><button
                v-if="ownsTrip && current.status === 'ACCEPTED'"
                class="primary"
                @click="immediate('depart')"
              >
                {{ t("确认发车", "Confirm departure") }}</button
              ><button
                v-if="current.status === 'IN_TRANSIT' && can('expense.write')"
                @click="editor('expenses')"
              >
                {{ t("填报费用", "Submit expense") }}
              </button>
            </div>
            <section class="panel">
              <div class="panel-title">
                <h2>{{ t("配载清单", "Load manifest") }}</h2>
                <span
                  >{{ detail.shipments.length }}
                  {{ t("票", "shipments") }}</span
                >
              </div>
              <div class="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>{{ fieldName("code") }}</th>
                      <th>{{ fieldName("goods") }}</th>
                      <th>{{ fieldName("destination") }}</th>
                      <th>{{ fieldName("pieces") }}</th>
                      <th>{{ fieldName("status") }}</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="s in detail.shipments" :key="s.id">
                      <td>
                        <button
                          class="text-link"
                          @click="openDetail('shipments', s.id)"
                        >
                          {{ s.code }}
                        </button>
                      </td>
                      <td>{{ s.goods }}</td>
                      <td>{{ s.destination }}</td>
                      <td>{{ s.pieces }}</td>
                      <td>
                        <span class="badge" :data-status="s.status">{{
                          statusName(s.status)
                        }}</span>
                      </td>
                      <td>
                        <button
                          v-if="current.status === 'DRAFT' && can('dispatch')"
                          @click="
                            action(
                              'remove',
                              t('移出配载', 'Unload shipment'),
                              noteField,
                              { shipmentId: s.id },
                            )
                          "
                        >
                          {{ t("移出", "Unload") }}
                        </button>
                      </td>
                    </tr>
                    <tr v-if="!detail.shipments.length">
                      <td colspan="6" class="empty">
                        {{ t("暂无配载", "No shipments loaded") }}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section></template
          >
          <template v-else-if="detailType === 'invoices'"
            ><section class="panel">
              <div class="panel-title">
                <h2>{{ current.partyName }}</h2>
                <span
                  >{{ statusName(current.kind) }} · {{ t("到期", "Due") }}
                  {{ current.dueDate }}</span
                >
              </div>
              <div class="metrics compact">
                <div>
                  <span>{{ t("对账总额", "Total") }}</span
                  ><strong>{{ cash(current.total) }}</strong>
                </div>
                <div>
                  <span>{{ t("已结算", "Settled") }}</span
                  ><strong>{{ cash(current.paid) }}</strong>
                </div>
                <div>
                  <span>{{ t("待结算", "Outstanding") }}</span
                  ><strong>{{
                    cash(Number(current.total) - Number(current.paid))
                  }}</strong>
                </div>
              </div>
              <p v-if="current.voidReason" class="record-note">
                {{ t("作废原因", "Void reason") }}：{{ current.voidReason }}
              </p>
            </section>
            <div
              v-if="can('finance') && current.status !== 'VOID'"
              class="record-actions"
            >
              <button
                v-if="current.status !== 'PAID'"
                class="primary"
                @click="
                  action('pay', t('登记实际收付款', 'Record actual payment'), [
                    ['amount', '金额', 'Amount', 'decimal', true],
                    ['method', '方式', 'Method', 'methods', true],
                    [
                      'reference',
                      '流水 / 凭证号',
                      'Payment reference',
                      'text',
                      true,
                    ],
                    ['note', '说明', 'Note', 'textarea'],
                  ])
                "
              >
                {{
                  current.kind === "RECEIVABLE"
                    ? t("登记收款", "Record receipt")
                    : t("登记付款", "Record disbursement")
                }}</button
              ><button
                v-if="detail.entries.some((e) => e.kind === 'PAYMENT')"
                @click="
                  action('reverse', t('冲正原流水', 'Reverse payment'), [
                    ['sourceId', '原流水', 'Original payment', 'sources', true],
                    ['amount', '冲正金额', 'Amount', 'decimal', true],
                    [
                      'reference',
                      '冲正凭证号',
                      'Reversal reference',
                      'text',
                      true,
                    ],
                    ...noteField,
                  ])
                "
              >
                {{ t("冲正流水", "Reverse payment") }}</button
              ><button
                v-if="!detail.entries.length"
                class="danger"
                @click="
                  action('void', t('作废对账单', 'Void statement'), noteField)
                "
              >
                {{ t("作废对账单", "Void statement") }}
              </button>
            </div>
            <section class="panel">
              <div class="panel-title">
                <h2>{{ t("对账明细", "Statement lines") }}</h2>
              </div>
              <div class="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>{{ t("明细", "Description") }}</th>
                      <th>{{ t("金额", "Amount") }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="r in detail.lines" :key="r.id">
                      <td>{{ r.description }}</td>
                      <td>{{ cash(r.amount) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>
            <section class="panel">
              <div class="panel-title">
                <h2>{{ t("收付款流水", "Payment ledger") }}</h2>
              </div>
              <div class="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>#</th>
                      <th>{{ t("方向", "Direction") }}</th>
                      <th>{{ t("凭证", "Reference") }}</th>
                      <th>{{ t("金额", "Amount") }}</th>
                      <th>{{ t("说明", "Note") }}</th>
                      <th>{{ t("时间", "Time") }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="e in detail.entries" :key="e.id">
                      <td>
                        {{ e.id
                        }}<small v-if="e.sourceId"> → #{{ e.sourceId }}</small>
                      </td>
                      <td>
                        {{
                          e.kind === "PAYMENT"
                            ? t("收付款", "Payment")
                            : t("冲正", "Reversal")
                        }}
                      </td>
                      <td>{{ e.reference }}</td>
                      <td>{{ cash(e.amount) }}</td>
                      <td>{{ e.note }}</td>
                      <td>{{ when(e.createdAt) }}</td>
                    </tr>
                    <tr v-if="!detail.entries.length">
                      <td colspan="6" class="empty">
                        {{ t("尚无收付款记录", "No payments recorded") }}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section></template
          >
          <section v-if="detail.events" class="panel">
            <div class="panel-title">
              <h2>{{ t("运输记录与异常", "Transport history & issues") }}</h2>
            </div>
            <ol class="timeline">
              <li v-for="e in [...detail.events].reverse()" :key="e.id">
                <span
                  class="timeline-dot"
                  :class="{ warning: e.status === 'OPEN' }"
                ></span>
                <div>
                  <strong>{{ eventName(e.kind) }}</strong>
                  <p v-if="e.note">{{ e.note }}</p>
                  <p v-if="e.resolution">
                    {{ t("处理结果", "Resolution") }}：{{ e.resolution }}
                  </p>
                  <small
                    >{{ when(e.createdAt) }} · {{ e.actor }} ·
                    {{ statusName(e.status) }}</small
                  >
                </div>
                <button
                  v-if="e.status === 'OPEN' && can('dispatch')"
                  @click="resolveIssue(e)"
                >
                  {{ t("解决异常", "Resolve") }}
                </button>
              </li>
            </ol>
          </section>
        </section>
        <template v-else-if="page === 'dashboard'"
          ><div class="metrics">
            <div
              v-for="s in ['READY', 'IN_TRANSIT', 'POD_PENDING', 'DELIVERED']"
              :key="s"
            >
              <span>{{ statusName(s) }}</span
              ><strong>{{ overview.counts[s] || 0 }}</strong
              ><small>{{ t("运单", "shipments") }}</small>
            </div>
            <div class="issue-metric">
              <span>{{ t("未解决异常", "Open issues") }}</span
              ><strong>{{ overview.openIssues || 0 }}</strong
              ><small>{{ t("待处理", "to resolve") }}</small>
            </div>
          </div>
          <section class="panel">
            <div class="panel-title">
              <h2>{{ t("最近运单", "Recent shipments") }}</h2>
              <button class="text-link" @click="navigate('shipments')">
                {{ t("全部运单", "All shipments") }} →
              </button>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ fieldName("code") }}</th>
                    <th>{{ fieldName("customerName") }}</th>
                    <th>{{ fieldName("origin") }}</th>
                    <th>{{ fieldName("destination") }}</th>
                    <th>{{ fieldName("status") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="s in overview.shipments" :key="s.id">
                    <td>
                      <button
                        class="text-link"
                        @click="openDetail('shipments', s.id)"
                      >
                        {{ s.code }}
                      </button>
                    </td>
                    <td>{{ s.customerName }}</td>
                    <td>{{ s.origin }}</td>
                    <td>{{ s.destination }}</td>
                    <td>
                      <span class="badge" :data-status="s.status">{{
                        statusName(s.status)
                      }}</span>
                    </td>
                  </tr>
                  <tr v-if="!overview.shipments.length">
                    <td colspan="5" class="empty">
                      {{ t("暂无运单", "No shipments yet") }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
          <section v-if="can('trip.read')" class="panel">
            <div class="panel-title">
              <h2>{{ t("执行中的车次", "Active trips") }}</h2>
              <button class="text-link" @click="navigate('trips')">
                {{ t("车次调度", "Trip dispatch") }} →
              </button>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ fieldName("code") }}</th>
                    <th>{{ fieldName("vehicleName") }}</th>
                    <th>{{ fieldName("driverName") }}</th>
                    <th>{{ fieldName("plannedStart") }}</th>
                    <th>{{ fieldName("status") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in overview.trips" :key="r.id">
                    <td>
                      <button
                        class="text-link"
                        @click="openDetail('trips', r.id)"
                      >
                        {{ r.code }}
                      </button>
                    </td>
                    <td>{{ r.vehicleName }}</td>
                    <td>{{ r.driverName }}</td>
                    <td>{{ when(r.plannedStart) }}</td>
                    <td>
                      <span class="badge" :data-status="r.status">{{
                        statusName(r.status)
                      }}</span>
                    </td>
                  </tr>
                  <tr v-if="!overview.trips.length">
                    <td colspan="5" class="empty">
                      {{ t("暂无执行中的车次", "No active trips") }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section></template
        >
        <template v-else-if="page === 'reports'"
          ><form class="filters" @submit.prevent="load">
            <label
              >{{ t("开始日期", "From")
              }}<input v-model="from" type="date" required /></label
            ><label
              >{{ t("结束日期", "To")
              }}<input v-model="to" type="date" required /></label
            ><button class="primary">{{ t("查询", "Apply") }}</button
            ><button type="button" @click="exportCsv">
              {{ t("导出 CSV", "Export CSV") }}</button
            ><span class="muted">{{ zone }}</span>
          </form>
          <div class="metrics report-metrics">
            <div
              v-for="k in [
                'revenue',
                'cost',
                'profit',
                'receipts',
                'disbursements',
                'netCash',
                'receivables',
                'payables',
              ]"
              :key="k"
            >
              <span>{{ reportName(k) }}</span
              ><strong>{{ cash(report[k] || 0) }}</strong>
            </div>
          </div>
          <section class="panel">
            <div class="panel-title">
              <h2>{{ t("期间关单利润", "Closed-trip profit in period") }}</h2>
              <span
                >{{ t("期间签收", "Deliveries") }}
                {{ report.deliveredCount || 0 }}</span
              >
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("车次", "Trip") }}</th>
                    <th>{{ t("客户运费", "Client freight") }}</th>
                    <th>
                      {{ t("承运及核准费用", "Carrier & approved costs") }}
                    </th>
                    <th>{{ t("车次利润", "Trip profit") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in report.closedTrips" :key="r.id">
                    <td>{{ r.code }}</td>
                    <td>{{ cash(r.revenue) }}</td>
                    <td>{{ cash(r.cost) }}</td>
                    <td>{{ cash(r.profit) }}</td>
                  </tr>
                  <tr v-if="!report.closedTrips?.length">
                    <td colspan="4" class="empty">
                      {{
                        t(
                          "该期间没有关单车次",
                          "No closed trips in this period",
                        )
                      }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section></template
        >
        <template v-else
          ><form
            class="filters"
            @submit.prevent="
              offset = 0;
              load();
            "
          >
            <div class="search-box">
              <Search :size="17" /><input
                v-model="query"
                :placeholder="
                  t('搜索单号、名称或地址', 'Search reference, name or address')
                "
                :aria-label="t('搜索', 'Search')"
                maxlength="120"
              />
            </div>
            <select
              v-if="statuses.length"
              v-model="filter"
              :aria-label="t('状态筛选', 'Filter status')"
            >
              <option value="">{{ t("全部状态", "All statuses") }}</option>
              <option v-for="s in statuses" :key="s" :value="s">
                {{ statusName(s) }}
              </option></select
            ><button>{{ t("查询", "Search") }}</button
            ><label
              v-if="page === 'shipments' && can('shipment.write')"
              class="button"
              ><Upload :size="15" />{{ t("导入 JSON", "Import JSON")
              }}<input type="file" accept=".json" @change="importFile"
            /></label>
          </form>
          <section class="panel">
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th v-for="k in rowColumns" :key="k">
                      <button
                        v-if="
                          [
                            'id',
                            'code',
                            'name',
                            'status',
                            'createdAt',
                            'updatedAt',
                            'total',
                          ].includes(k)
                        "
                        class="sort"
                        @click="
                          sort = k;
                          descending = !descending;
                          load();
                        "
                      >
                        {{ fieldName(k) }}
                        <span v-if="sort === k">{{
                          descending ? "↓" : "↑"
                        }}</span></button
                      ><template v-else>{{ fieldName(k) }}</template>
                    </th>
                    <th>{{ t("操作", "Actions") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in rows" :key="r.id">
                    <td v-for="k in rowColumns" :key="k">
                      <span
                        v-if="k === 'status'"
                        class="badge"
                        :data-status="r.status"
                        >{{ statusName(r.status) }}</span
                      ><template v-else>{{ display(r, k) }}</template>
                    </td>
                    <td class="row-actions">
                      <button
                        v-if="['shipments', 'trips', 'invoices'].includes(page)"
                        class="text-link"
                        @click="openDetail(page, r.id)"
                      >
                        {{ t("查看", "Open") }}</button
                      ><button
                        v-if="canEdit(r)"
                        class="text-link"
                        @click="editor(page, r)"
                      >
                        {{ t("编辑", "Edit") }}</button
                      ><template
                        v-if="
                          page === 'expenses' &&
                          can('finance') &&
                          r.status === 'SUBMITTED' &&
                          r.createdBy !== profile.username
                        "
                        ><button
                          class="text-link"
                          @click="reviewExpense(r, true)"
                        >
                          {{ t("核准", "Approve") }}</button
                        ><button
                          class="text-link danger-text"
                          @click="reviewExpense(r, false)"
                        >
                          {{ t("退回", "Reject") }}
                        </button></template
                      ><button
                        v-if="
                          (masterPages.includes(page) && can('master.edit')) ||
                          (administrative.includes(page) &&
                            can('admin') &&
                            !['permissions', 'menus', 'settings'].includes(
                              page,
                            ))
                        "
                        class="text-link danger-text"
                        @click="deleteRow(r)"
                      >
                        {{ t("删除", "Delete") }}
                      </button>
                    </td>
                  </tr>
                  <tr v-if="!rows.length">
                    <td :colspan="rowColumns.length + 1" class="empty">
                      {{
                        loading
                          ? t("正在加载…", "Loading…")
                          : t("暂无记录", "No records")
                      }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <footer class="pagination">
              <span
                >{{ t("共", "Total") }} {{ total }} · {{ offset + 1 }} /
                {{ Math.max(1, Math.ceil(total / 20)) }}</span
              >
              <div>
                <button
                  :disabled="offset === 0 || loading"
                  :aria-label="t('上一页', 'Previous page')"
                  @click="
                    offset--;
                    load();
                  "
                >
                  <ChevronLeft :size="17" /></button
                ><button
                  :disabled="(offset + 1) * 20 >= total || loading"
                  :aria-label="t('下一页', 'Next page')"
                  @click="
                    offset++;
                    load();
                  "
                >
                  <ChevronRight :size="17" />
                </button>
              </div>
            </footer></section
        ></template>
      </div>
      <footer class="workspace-footer">
        FreightFlow 1.0 ·
        <button @click="about">
          {{ t("非商业源码版", "Non-commercial source edition") }}
        </button>
      </footer>
    </main>
    <div
      v-if="dialog"
      class="modal-backdrop"
      @click.self="!busy && (dialog = null)"
    >
      <section
        class="modal"
        role="dialog"
        aria-modal="true"
        :aria-label="dialog.title"
      >
        <header>
          <h2>{{ dialog.title }}</h2>
          <button
            class="icon-button"
            :disabled="busy"
            aria-label="Close"
            @click="dialog = null"
          >
            <X :size="20" />
          </button>
        </header>
        <div v-if="dialog.mode === 'about'" class="about">
          <img src="/brand/logo.jpg" alt="知华科技" />
          <p>知华科技 · 上海如静知华信息科技有限公司</p>
          <p>
            {{
              t(
                "公开源码学习版 / 非商业源码版。商用、再分发、部署及源码授权请联系知华科技；授权范围以 LICENSE 及商业合同为准。",
                "Public source learning / non-commercial edition. Contact ZhuaTech for commercial use, redistribution, deployment and source licensing. Scope is defined by LICENSE and the commercial agreement.",
              )
            }}
          </p>
          <p>
            <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
              >www.zhuatech.cn</a
            >
          </p>
          <p>
            {{ t("商业咨询微信", "Commercial enquiries on WeChat") }}：zhuatech
            / zhuatech2
          </p>
        </div>
        <form v-else @submit.prevent="save">
          <div class="form-fields">
            <p v-if="dialog.mode === 'delete'" class="delete-note">
              {{
                t(
                  "删除后不可恢复；有业务引用的记录无法删除，可改为停用。",
                  "Deletion cannot be undone. Referenced records cannot be deleted; disable them instead.",
                )
              }}
            </p>
            <label
              v-for="f in activeFields"
              :key="f[0]"
              :class="{
                wide:
                  f[3] === 'textarea' ||
                  ['permissions', 'deliveredShipments'].includes(f[3]),
                checkbox: f[3] === 'checkbox',
              }"
              ><span>{{ t(f[1], f[2]) }}<b v-if="f[4]"> *</b></span
              ><input
                v-if="f[3] === 'checkbox'"
                v-model="form[f[0]]"
                :aria-label="t(f[1], f[2])"
                type="checkbox"
              /><select
                v-else-if="selectKinds.includes(f[3])"
                v-model="form[f[0]]"
                :aria-label="t(f[1], f[2])"
                :required="f[4]"
                :multiple="['permissions', 'deliveredShipments'].includes(f[3])"
                :size="
                  ['permissions', 'deliveredShipments'].includes(f[3])
                    ? 5
                    : undefined
                "
              >
                <option
                  v-if="!['permissions', 'deliveredShipments'].includes(f[3])"
                  :value="null"
                >
                  {{ t("请选择", "Select") }}
                </option>
                <option
                  v-for="opt in optionsFor(f[3])"
                  :key="opt.id"
                  :value="opt.id"
                >
                  {{ opt.name }}
                </option></select
              ><textarea
                v-else-if="f[3] === 'textarea'"
                v-model="form[f[0]]"
                :aria-label="t(f[1], f[2])"
                :required="f[4]"
                maxlength="800"
                rows="3"
              ></textarea
              ><input
                v-else
                v-model="form[f[0]]"
                :aria-label="t(f[1], f[2])"
                :type="
                  f[3] === 'decimal'
                    ? 'number'
                    : f[3] === 'readonly'
                      ? 'text'
                      : f[3]
                "
                :step="
                  f[3] === 'decimal' ? 'any' : f[3] === 'number' ? 1 : undefined
                "
                :min="['decimal', 'number'].includes(f[3]) ? 0 : undefined"
                :readonly="f[3] === 'readonly'"
                :required="f[4] || (f[0] === 'password' && !dialog.id)"
                :maxlength="f[3] === 'password' ? 72 : 400"
                :autocomplete="f[3] === 'password' ? 'new-password' : 'off'"
              /><small v-if="f[0] === 'password' || f[0] === 'newPassword'">{{
                t(
                  "12–72位，含大小写字母和数字；编辑时留空保留原密码",
                  "12–72 characters; upper/lowercase and digits. Leave blank on edit to keep the password.",
                )
              }}</small
              ><small
                v-if="['permissions', 'deliveredShipments'].includes(f[3])"
                >{{
                  t(
                    "按住 Ctrl / ⌘ 选择多项",
                    "Hold Ctrl / ⌘ to select multiple",
                  )
                }}</small
              ></label
            >
          </div>
          <p v-if="error" class="alert error" role="alert">{{ error }}</p>
          <footer>
            <button type="button" :disabled="busy" @click="dialog = null">
              {{ t("取消", "Cancel") }}</button
            ><button class="primary" :disabled="busy">
              {{ busy ? t("提交中…", "Saving…") : t("确认", "Confirm") }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </div>
</template>
