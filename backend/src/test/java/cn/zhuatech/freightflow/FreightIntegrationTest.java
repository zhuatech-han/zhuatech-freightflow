// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 真实HTTP/数据库运输闭环、账目守恒、幂等、并发与岗位隔离。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FreightIntegrationTest.Config.class)
class FreightIntegrationTest {
  static final String PASSWORD = "Test" + UUID.randomUUID() + "Aa9";
  static final Instant NOW = Instant.parse("2026-10-02T02:00:00Z");

  @TestConfiguration
  static class Config {
    /** 可复现时间边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
    @Bean
    @Primary
    Clock fixed() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:freight;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("freightflow.admin-password", () -> PASSWORD);
  }

  @Autowired MockMvc mvc;
  final JsonMapper json = JsonMapper.builder().build();
  MockHttpSession admin, driver, client, other;
  long carrier, vehicle, driverId, driverAccount, customer, customerAccount, shipment, trip;
  String suffix;

  @BeforeEach
  void setup() throws Exception {
    admin = login("admin", PASSWORD);
    suffix = UUID.randomUUID().toString().substring(0, 8);
    var roles = getAs(admin, "/lists/roles?size=100", 200).get("items");
    long dr = 0, cr = 0;
    for (var r : roles) {
      if (r.get("name").asString().contains("Driver")) dr = r.get("id").asLong();
      if (r.get("name").asString().contains("Customer")) cr = r.get("id").asLong();
    }
    driverAccount = createAccount("driver" + suffix, dr);
    customerAccount = createAccount("client" + suffix, cr);
    other = login("admin", PASSWORD);
    driver = login("driver" + suffix, PASSWORD);
    client = login("client" + suffix, PASSWORD);
    carrier =
        postAs(
                admin,
                "/master/carriers",
                m("code", "C" + suffix, "name", "TEST Carrier", "departmentId", 1, "enabled", true),
                200)
            .get("id")
            .asLong();
    vehicle =
        postAs(
                admin,
                "/master/vehicles",
                m(
                    "code",
                    "V" + suffix,
                    "name",
                    "TEST Vehicle",
                    "departmentId",
                    1,
                    "enabled",
                    true,
                    "carrierId",
                    carrier,
                    "maxWeightKg",
                    "10000",
                    "maxVolumeM3",
                    "50",
                    "maxPieces",
                    100),
                200)
            .get("id")
            .asLong();
    driverId =
        postAs(
                admin,
                "/master/drivers",
                m(
                    "code",
                    "D" + suffix,
                    "name",
                    "TEST Driver",
                    "departmentId",
                    1,
                    "enabled",
                    true,
                    "carrierId",
                    carrier,
                    "accountId",
                    driverAccount,
                    "licenseExpires",
                    "2027-10-01"),
                200)
            .get("id")
            .asLong();
    customer =
        postAs(
                admin,
                "/master/customers",
                m(
                    "code",
                    "P" + suffix,
                    "name",
                    "TEST Customer",
                    "departmentId",
                    1,
                    "enabled",
                    true,
                    "accountId",
                    customerAccount),
                200)
            .get("id")
            .asLong();
    shipment = postAs(admin, "/shipments", shipBody(), 200).get("id").asLong();
    trip = postAs(admin, "/trips", tripBody(), 200).get("id").asLong();
  }

  @Test
  void completeTransportAndBothSettlementLedgers() throws Exception {
    runDelivery();
    postAs(admin, "/trips/" + trip + "/close", tripCmd(), 200);
    long ar = receivable();
    long ap = payable();
    assertEquals(1000, invoice(ar).get("total").asInt());
    assertEquals(600, invoice(ap).get("total").asInt());
    var pay = invoiceCmd(ar);
    pay.putAll(m("amount", "400", "method", "CASH", "reference", "TEST-R1"));
    postAs(admin, "/invoices/" + ar + "/pay", pay, 200);
    postAs(admin, "/invoices/" + ar + "/pay", pay, 200);
    assertEquals(1, getAs(admin, "/invoices/" + ar, 200).get("entries").size());
    var next = invoiceCmd(ar);
    next.putAll(m("amount", "600", "method", "BANK", "reference", "TEST-R2"));
    postAs(admin, "/invoices/" + ar + "/pay", next, 200);
    assertEquals("PAID", invoice(ar).get("status").asString());
    var out = invoiceCmd(ap);
    out.putAll(m("amount", "600", "method", "BANK", "reference", "TEST-P"));
    postAs(admin, "/invoices/" + ap + "/pay", out, 200);
    assertEquals("PAID", invoice(ap).get("status").asString());
  }

  @Test
  void vehicleWeightCapacityCannotBeExceeded() throws Exception {
    var body = shipBody();
    body.put("weightKg", "10001");
    shipment = postAs(admin, "/shipments", body, 200).get("id").asLong();
    quote("1000");
    var v = tripCmd();
    v.put("shipmentId", shipment);
    postAs(admin, "/trips/" + trip + "/add", v, 409);
  }

  @Test
  void normalLoadAndDuplicateAssignmentRejected() throws Exception {
    load();
    long second = postAs(admin, "/trips", alternateTrip(), 200).get("id").asLong();
    var v =
        m(
            "revision",
            getAs(admin, "/trips/" + second, 200).get("trip").get("revision").asInt(),
            "shipmentId",
            shipment,
            "requestKey",
            key());
    postAs(admin, "/trips/" + second + "/add", v, 409);
  }

  @Test
  void vehicleAndDriverCannotOverlap() throws Exception {
    postAs(admin, "/trips", tripBody(), 409);
  }

  @Test
  void adjacentTripBoundaryAllowed() throws Exception {
    postAs(admin, "/trips", alternateTrip(), 200);
  }

  @Test
  void emptyTripDispatchRejected() throws Exception {
    postAs(admin, "/trips/" + trip + "/dispatch", tripCmd(), 409);
  }

  @Test
  void administratorCannotImpersonateDriver() throws Exception {
    load();
    postAs(admin, "/trips/" + trip + "/dispatch", tripCmd(), 200);
    postAs(admin, "/trips/" + trip + "/accept", tripCmd(), 403);
  }

  @Test
  void anotherDriverCannotExecuteAssignedTrip() throws Exception {
    load();
    postAs(admin, "/trips/" + trip + "/dispatch", tripCmd(), 200);
    postAs(client, "/trips/" + trip + "/accept", tripCmd(), 403);
  }

  @Test
  void departRequiresAcceptance() throws Exception {
    load();
    postAs(driver, "/trips/" + trip + "/depart", tripCmd(), 409);
  }

  @Test
  void closeRequiresEveryDelivery() throws Exception {
    depart();
    postAs(admin, "/trips/" + trip + "/close", tripCmd(), 409);
  }

  @Test
  void deliveryRequiresPhoto() throws Exception {
    depart();
    var v = shipmentCmd();
    v.putAll(m("receivedPieces", 2, "receiver", "TEST Receiver"));
    postAs(driver, "/shipments/" + shipment + "/deliver", v, 409);
  }

  @Test
  void partialDeliveryIsNotFakedAsComplete() throws Exception {
    depart();
    upload(driver, 200);
    var v = shipmentCmd();
    v.putAll(m("receivedPieces", 1, "receiver", "TEST Receiver"));
    postAs(driver, "/shipments/" + shipment + "/deliver", v, 409);
  }

  @Test
  void proofMimeAndDuplicateProtection() throws Exception {
    depart();
    var a = upload(driver, 200);
    var b = upload(driver, 200);
    assertEquals(a.get("id").asLong(), b.get("id").asLong());
    mvc.perform(
            multipart("/api/shipments/" + shipment + "/proofs")
                .file(
                    new MockMultipartFile(
                        "file", "../../evil.png", "image/png", "<script>x</script>".getBytes()))
                .session(driver)
                .with(csrf()))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isBadRequest());
  }

  @Test
  void clientCannotUploadButCanReadOwnProof() throws Exception {
    depart();
    upload(client, 403);
    var proof = upload(driver, 200);
    mvc.perform(get("/api/proofs/" + proof.get("id").asLong()).session(client))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
  }

  @Test
  void issueBlocksDeliveryApprovalUntilResolution() throws Exception {
    depart();
    var issue =
        postAs(
            driver,
            "/shipments/" + shipment + "/issue",
            m("requestKey", key(), "note", "TEST damaged carton"),
            200);
    upload(driver, 200);
    var v = shipmentCmd();
    v.putAll(m("receivedPieces", 2, "receiver", "TEST Receiver"));
    postAs(driver, "/shipments/" + shipment + "/deliver", v, 200);
    postAs(admin, "/shipments/" + shipment + "/confirm-delivery", shipmentCmd(), 409);
    postAs(
        admin,
        "/issues/" + issue.get("id").asLong() + "/resolve",
        m("requestKey", key(), "revision", 0, "note", "TEST agreed disposition"),
        200);
    postAs(admin, "/shipments/" + shipment + "/confirm-delivery", shipmentCmd(), 200);
  }

  @Test
  void driverCannotApproveOwnReceipt() throws Exception {
    depart();
    upload(driver, 200);
    var v = shipmentCmd();
    v.putAll(m("receivedPieces", 2, "receiver", "TEST Receiver"));
    postAs(driver, "/shipments/" + shipment + "/deliver", v, 200);
    postAs(driver, "/shipments/" + shipment + "/confirm-delivery", shipmentCmd(), 403);
  }

  @Test
  void expensesRequireIndependentReviewAndBlockClosure() throws Exception {
    runDelivery();
    var e =
        postAs(
            driver,
            "/expenses",
            m(
                "requestKey",
                key(),
                "tripId",
                trip,
                "type",
                "TOLL",
                "amount",
                "20",
                "note",
                "TEST toll"),
            200);
    postAs(admin, "/trips/" + trip + "/close", tripCmd(), 409);
    postAs(
        admin,
        "/expenses/" + e.get("id").asLong() + "/review",
        m("requestKey", key(), "revision", 0, "approved", true, "note", "TEST checked"),
        200);
    postAs(admin, "/trips/" + trip + "/close", tripCmd(), 200);
    assertEquals(620, invoice(payable()).get("total").asInt());
  }

  @Test
  void ownExpenseCannotBeApprovedEvenByAdmin() throws Exception {
    depart();
    var e =
        postAs(
            admin,
            "/expenses",
            m("requestKey", key(), "tripId", trip, "type", "FUEL", "amount", "20", "note", "TEST"),
            200);
    postAs(
        admin,
        "/expenses/" + e.get("id").asLong() + "/review",
        m("requestKey", key(), "revision", 0, "approved", true, "note", "TEST"),
        403);
  }

  @Test
  void invoiceRequiresReviewedDeliveryAndNoDuplicate() throws Exception {
    postAs(admin, "/invoices", arBody(), 409);
    runDelivery();
    receivable();
    postAs(admin, "/invoices", arBody(), 409);
  }

  @Test
  void payableRequiresClosedTrip() throws Exception {
    postAs(admin, "/invoices", apBody(), 409);
  }

  @Test
  void moneyRejectsOverpaymentAndFractionalCent() throws Exception {
    runDelivery();
    long id = receivable();
    var v = invoiceCmd(id);
    v.putAll(m("amount", "1001", "method", "CASH", "reference", "TEST"));
    postAs(admin, "/invoices/" + id + "/pay", v, 409);
    v.put("requestKey", key());
    v.put("amount", "1.001");
    postAs(admin, "/invoices/" + id + "/pay", v, 400);
    assertEquals(0, invoice(id).get("paid").asInt());
  }

  @Test
  void reversalPreservesOriginalAndCannotExceedSource() throws Exception {
    runDelivery();
    long id = receivable();
    var v = invoiceCmd(id);
    v.putAll(m("amount", "100", "method", "CASH", "reference", "TEST"));
    postAs(admin, "/invoices/" + id + "/pay", v, 200);
    long source = getAs(admin, "/invoices/" + id, 200).get("entries").get(0).get("id").asLong();
    var reverse = invoiceCmd(id);
    reverse.putAll(
        m(
            "amount",
            "30",
            "sourceId",
            source,
            "reference",
            "TEST correction",
            "note",
            "TEST duplicate bank reference"));
    postAs(admin, "/invoices/" + id + "/reverse", reverse, 200);
    assertEquals(70, invoice(id).get("paid").asInt());
    reverse = invoiceCmd(id);
    reverse.putAll(m("amount", "71", "sourceId", source, "reference", "TEST", "note", "TEST"));
    postAs(admin, "/invoices/" + id + "/reverse", reverse, 409);
    assertEquals(2, getAs(admin, "/invoices/" + id, 200).get("entries").size());
  }

  @Test
  void twoPaymentSessionsHaveOnlyOneWinner() throws Exception {
    runDelivery();
    long id = receivable();
    var one = invoiceCmd(id);
    one.putAll(m("amount", "600", "method", "CASH", "reference", "TEST-A"));
    var two = new LinkedHashMap<>(one);
    two.put("requestKey", key());
    var a = login("admin", PASSWORD);
    var b = login("admin", PASSWORD);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      var fa =
          pool.submit(
              () -> {
                start.await();
                return rawPost(a, "/invoices/" + id + "/pay", one).getResponse().getStatus();
              });
      var fb =
          pool.submit(
              () -> {
                start.await();
                return rawPost(b, "/invoices/" + id + "/pay", two).getResponse().getStatus();
              });
      start.countDown();
      var results = new ArrayList<>(List.of(fa.get(), fb.get()));
      Collections.sort(results);
      assertEquals(List.of(200, 409), results);
    }
    assertEquals(600, invoice(id).get("paid").asInt());
  }

  @Test
  void staleRevisionAndChangedRetryBodyRejected() throws Exception {
    var body = shipBody();
    var s = postAs(admin, "/shipments", body, 200);
    body.put("goods", "changed");
    postAs(admin, "/shipments", body, 409);
    quote("1000");
    var v = shipmentCmd();
    v.put("revision", 0);
    v.put("note", "TEST");
    postAs(admin, "/shipments/" + shipment + "/cancel", v, 409);
    assertNotNull(s);
  }

  @Test
  void customerIsBoundToOwnConsignments() throws Exception {
    var body = shipBody();
    body.put("customerId", 999999);
    var s = postAs(client, "/shipments", body, 200);
    assertEquals(customer, s.get("customerId").asLong());
    postAs(
        client,
        "/shipments/" + shipment + "/quote",
        m("requestKey", key(), "revision", 0, "freight", "1"),
        403);
    getAs(client, "/trips/" + trip, 403);
  }

  @Test
  void driversNeverReceiveFinancialFields() throws Exception {
    load();
    var s = getAs(driver, "/shipments/" + shipment, 200).get("shipment");
    assertFalse(s.has("freight"));
    assertFalse(s.has("invoiceId"));
    var t = getAs(driver, "/trips/" + trip, 200).get("trip");
    assertFalse(t.has("carrierFee"));
    getAs(driver, "/lists/invoices", 403);
    getAs(driver, "/reports?from=2026-10-02&to=2026-10-02", 403);
  }

  @Test
  void unauthenticatedAndCsrfWritesRejected() throws Exception {
    mvc.perform(get("/api/lists/shipments"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isUnauthorized());
    mvc.perform(
            post("/api/shipments")
                .session(admin)
                .contentType("application/json")
                .content(json.writeValueAsString(shipBody())))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isForbidden());
  }

  @Test
  void importRollbackDoesNotLeaveFirstRow() throws Exception {
    var before = getAs(admin, "/lists/shipments?size=100", 200).get("total").asInt();
    var good = shipBody();
    var bad = shipBody();
    bad.put("pieces", 0);
    postAs(admin, "/shipments/import", m("requestKey", key(), "items", List.of(good, bad)), 400);
    assertEquals(before, getAs(admin, "/lists/shipments?size=100", 200).get("total").asInt());
  }

  @Test
  void cancelledTripReleasesCapacityAndRestoresShipment() throws Exception {
    load();
    var c = tripCmd();
    c.put("note", "TEST cancellation");
    postAs(admin, "/trips/" + trip + "/cancel", c, 200);
    assertEquals("READY", ship().get("status").asString());
    postAs(admin, "/trips", tripBody(), 200);
  }

  @Test
  void activeAssignmentProtectsAccountAndVehicle() throws Exception {
    putAs(
        admin,
        "/master/vehicles/" + vehicle,
        m(
            "code",
            "V" + suffix,
            "name",
            "TEST Vehicle",
            "departmentId",
            1,
            "enabled",
            false,
            "carrierId",
            carrier,
            "maxWeightKg",
            "10000",
            "maxVolumeM3",
            "50",
            "maxPieces",
            100),
        409);
    var roles = getAs(admin, "/lists/roles", 200).get("items");
    long role = 0;
    for (var r : roles)
      if (r.get("name").asString().contains("Driver")) role = r.get("id").asLong();
    var v =
        m(
            "username",
            "driver" + suffix,
            "displayName",
            "TEST Driver",
            "roleId",
            role,
            "departmentId",
            1,
            "enabled",
            false);
    var response =
        mvc.perform(
                put("/api/admin/users/" + driverAccount)
                    .session(admin)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(v)))
            .andReturn();
    assertEquals(409, response.getResponse().getStatus());
  }

  @Test
  void assignedRoleCannotEscalateByEdit() throws Exception {
    var roles = getAs(admin, "/lists/roles", 200).get("items");
    long cr = 0;
    for (var r : roles)
      if (r.get("name").asString().contains("Customer")) cr = r.get("id").asLong();
    var resp =
        mvc.perform(
                put("/api/admin/roles/" + cr)
                    .session(admin)
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            m(
                                "name",
                                "客户 / Customer",
                                "scope",
                                "ALL",
                                "permissions",
                                List.of("admin")))))
            .andReturn();
    assertEquals(409, resp.getResponse().getStatus());
    assertTrue(getAs(client, "/catalog", 200).get("profile").get("permissions").isArray());
  }

  @Test
  void lastAdminCannotBeDisabled() throws Exception {
    var roles = getAs(admin, "/lists/roles", 200).get("items");
    long role = roles.get(0).get("id").asLong();
    var users = getAs(admin, "/lists/users?q=admin&size=100", 200).get("items");
    long id = 0;
    for (var u : users) if (u.get("username").asString().equals("admin")) id = u.get("id").asLong();
    assertEquals(
        409,
        mvc.perform(
                put("/api/admin/users/" + id)
                    .session(admin)
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            m(
                                "username",
                                "admin",
                                "displayName",
                                "Admin",
                                "roleId",
                                role,
                                "departmentId",
                                1,
                                "enabled",
                                false))))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void futureNonOverlappingTripBlockedDuringActualTransit() throws Exception {
    depart();
    postAs(admin, "/trips", alternateTrip(), 409);
  }

  @Test
  void listSortRejectsInjectedExpression() throws Exception {
    getAs(admin, "/lists/shipments?sort=id%20desc", 400);
  }

  @Test
  void foreignCustomerCannotReadShipmentProofOrStatement() throws Exception {
    runDelivery();
    var proof = getAs(admin, "/shipments/" + shipment, 200).get("proofs").get(0).get("id").asLong();
    long role = 0;
    for (var r : getAs(admin, "/lists/roles?size=100", 200).get("items"))
      if (r.get("name").asString().contains("Customer")) role = r.get("id").asLong();
    var name = "foreign" + suffix;
    var account = createAccount(name, role);
    postAs(
        admin,
        "/master/customers",
        m(
            "code",
            "F" + suffix,
            "name",
            "TEST Foreign",
            "accountId",
            account,
            "departmentId",
            1,
            "enabled",
            true),
        200);
    var foreign = login(name, PASSWORD);
    getAs(foreign, "/shipments/" + shipment, 403);
    mvc.perform(get("/api/proofs/" + proof).session(foreign))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isForbidden());
    var invoice = receivable();
    getAs(foreign, "/invoices/" + invoice, 403);
    assertEquals(0, getAs(foreign, "/lists/shipments", 200).get("total").asInt());
  }

  @Test
  void currencyAndTimezoneCannotReinterpretExistingTransactions() throws Exception {
    for (var r : getAs(admin, "/lists/settings", 200).get("items")) {
      var code = r.get("code").asString();
      if (code.equals("currency") || code.equals("timezone"))
        putAs(
            admin,
            "/admin/settings/" + r.get("id").asLong(),
            m("value", code.equals("currency") ? "USD" : "Europe/London"),
            409);
    }
  }

  @Test
  void volumeAndPieceCapacityAreBothEnforced() throws Exception {
    for (var field : List.of("volumeM3", "pieces")) {
      var v = shipBody();
      v.put(field, field.equals("pieces") ? 101 : "51");
      var id = postAs(admin, "/shipments", v, 200).get("id").asLong();
      postAs(
          admin,
          "/shipments/" + id + "/quote",
          m("revision", 0, "requestKey", key(), "freight", "10"),
          200);
      var cmd = tripCmd();
      cmd.put("shipmentId", id);
      postAs(admin, "/trips/" + trip + "/add", cmd, 409);
    }
  }

  private long createAccount(String name, long role) throws Exception {
    return postAs(
            admin,
            "/admin/users",
            m(
                "username",
                name,
                "displayName",
                name,
                "password",
                PASSWORD,
                "roleId",
                role,
                "departmentId",
                1,
                "enabled",
                true),
            200)
        .get("id")
        .asLong();
  }

  private Map<String, Object> shipBody() {
    return m(
        "customerId",
        customer,
        "origin",
        "TEST Shanghai",
        "destination",
        "TEST Suzhou",
        "consignee",
        "TEST Receiver",
        "consigneePhone",
        "",
        "goods",
        "TEST parts",
        "pieces",
        2,
        "weightKg",
        "200",
        "volumeM3",
        "2",
        "requestKey",
        key());
  }

  private Map<String, Object> tripBody() {
    return m(
        "driverId",
        driverId,
        "carrierId",
        carrier,
        "vehicleId",
        vehicle,
        "plannedStart",
        NOW.minusSeconds(3600).toString(),
        "plannedEnd",
        NOW.plusSeconds(28800).toString(),
        "carrierFee",
        "600",
        "requestKey",
        key());
  }

  private Map<String, Object> alternateTrip() {
    var v = tripBody();
    v.put("plannedStart", NOW.plusSeconds(28800).toString());
    v.put("plannedEnd", NOW.plusSeconds(36000).toString());
    return v;
  }

  private JsonNode ship() throws Exception {
    return getAs(admin, "/shipments/" + shipment, 200).get("shipment");
  }

  private JsonNode invoice(long id) throws Exception {
    return getAs(admin, "/invoices/" + id, 200).get("invoice");
  }

  private Map<String, Object> shipmentCmd() throws Exception {
    return m("requestKey", key(), "revision", ship().get("revision").asInt());
  }

  private Map<String, Object> tripCmd() throws Exception {
    return m(
        "requestKey",
        key(),
        "revision",
        getAs(admin, "/trips/" + trip, 200).get("trip").get("revision").asInt());
  }

  private Map<String, Object> invoiceCmd(long id) throws Exception {
    return m("requestKey", key(), "revision", invoice(id).get("revision").asInt());
  }

  private void quote(String price) throws Exception {
    var v = shipmentCmd();
    v.put("freight", price);
    postAs(admin, "/shipments/" + shipment + "/quote", v, 200);
  }

  private void load() throws Exception {
    quote("1000");
    var v = tripCmd();
    v.put("shipmentId", shipment);
    postAs(admin, "/trips/" + trip + "/add", v, 200);
  }

  private void depart() throws Exception {
    load();
    postAs(admin, "/trips/" + trip + "/dispatch", tripCmd(), 200);
    postAs(driver, "/trips/" + trip + "/accept", tripCmd(), 200);
    postAs(driver, "/trips/" + trip + "/depart", tripCmd(), 200);
  }

  private void runDelivery() throws Exception {
    depart();
    upload(driver, 200);
    var v = shipmentCmd();
    v.putAll(m("receiver", "TEST Receiver", "receivedPieces", 2));
    postAs(driver, "/shipments/" + shipment + "/deliver", v, 200);
    postAs(admin, "/shipments/" + shipment + "/confirm-delivery", shipmentCmd(), 200);
  }

  private Map<String, Object> arBody() {
    return m(
        "kind",
        "RECEIVABLE",
        "customerId",
        customer,
        "shipmentIds",
        List.of(shipment),
        "dueDate",
        "2026-10-03",
        "requestKey",
        key());
  }

  private Map<String, Object> apBody() {
    return m("kind", "PAYABLE", "tripId", trip, "dueDate", "2026-10-03", "requestKey", key());
  }

  private long receivable() throws Exception {
    return postAs(admin, "/invoices", arBody(), 200).get("id").asLong();
  }

  private long payable() throws Exception {
    return postAs(admin, "/invoices", apBody(), 200).get("id").asLong();
  }

  private JsonNode upload(MockHttpSession s, int status) throws Exception {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB), "png", bytes);
    var response =
        mvc.perform(
                multipart("/api/shipments/" + shipment + "/proofs")
                    .file(
                        new MockMultipartFile("file", "TEST.png", "image/png", bytes.toByteArray()))
                    .session(s)
                    .with(csrf()))
            .andReturn();
    assertEquals(
        status, response.getResponse().getStatus(), response.getResponse().getContentAsString());
    return json.readTree(response.getResponse().getContentAsString());
  }

  private MockHttpSession login(String user, String pass) throws Exception {
    var r =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(m("username", user, "password", pass))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return (MockHttpSession) r.getRequest().getSession();
  }

  private JsonNode getAs(MockHttpSession s, String path, int status) throws Exception {
    var r = mvc.perform(get("/api" + path).session(s)).andReturn();
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  private org.springframework.test.web.servlet.MvcResult rawPost(
      MockHttpSession s, String path, Object body) throws Exception {
    return mvc.perform(
            post("/api" + path)
                .session(s)
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(body)))
        .andReturn();
  }

  private JsonNode postAs(MockHttpSession s, String path, Object body, int status)
      throws Exception {
    var r = rawPost(s, path, body);
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  private JsonNode putAs(MockHttpSession s, String path, Object body, int status) throws Exception {
    var r =
        mvc.perform(
                put("/api" + path)
                    .session(s)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)))
            .andReturn();
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  private String key() {
    return UUID.randomUUID().toString();
  }

  private static Map<String, Object> m(Object... values) {
    var r = new LinkedHashMap<String, Object>();
    for (int n = 0; n < values.length; n += 2) r.put((String) values[n], values[n + 1]);
    return r;
  }
}
