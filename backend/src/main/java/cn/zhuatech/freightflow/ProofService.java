// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.freightflow;

import java.io.*;
import java.security.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 签收图片在数据库持久化，按运单鉴权；不使用上传路径和客户端文件名。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class ProofService {
  final Store db;
  final FreightAccess access;
  final AccessService auth;
  final TransitService transit;

  public ProofService(Store db, FreightAccess access, AccessService auth, TransitService transit) {
    this.db = db;
    this.access = access;
    this.auth = auth;
    this.transit = transit;
  }

  /**
   * 实际司机提交2MiB内PNG/JPEG，限制像素与五张附件，SHA防相同图片重复入库。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
   */
  public Map<String, Object> upload(Long shipment, MultipartFile file) {
    db.lock(Department.class, 1L);
    var s = db.get(Shipment.class, shipment);
    access.shipmentRead(s);
    if (s.tripId == null) throw new Problem(409, "NOT_ON_TRIP");
    var t = db.get(Trip.class, s.tripId);
    access.execute(t);
    if (!t.status.equals("IN_TRANSIT") || !s.status.equals("IN_TRANSIT"))
      throw new Problem(409, "INVALID_STATE");
    if (file.isEmpty() || file.getSize() > 2097152) throw new Problem(413, "PROOF_TOO_LARGE");
    try {
      byte[] bytes = file.getBytes();
      String format;
      try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
        var readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) throw new Problem(400, "INVALID_IMAGE");
        var reader = readers.next();
        try {
          reader.setInput(input);
          format = reader.getFormatName().toLowerCase(Locale.ROOT);
          if (!Set.of("png", "jpeg").contains(format)
              || (long) reader.getWidth(0) * reader.getHeight(0) > 20000000
              || reader.read(0) == null) throw new Problem(400, "INVALID_IMAGE");
        } finally {
          reader.dispose();
        }
      }
      String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      var existing =
          db.query(DeliveryProof.class, "from DeliveryProof where shipmentId=?1", shipment);
      for (var p : existing) if (p.sha256.equals(hash)) return metadata(p);
      if (existing.size() >= 5) throw new Problem(409, "PROOF_LIMIT");
      var p = new DeliveryProof();
      p.shipmentId = shipment;
      p.sha256 = hash;
      p.mime = format.equals("png") ? "image/png" : "image/jpeg";
      p.size = bytes.length;
      p.payload = bytes;
      p.actor = auth.current().username;
      p.createdAt = transit.now();
      p.departmentId = s.departmentId;
      db.save(p);
      transit.event(s.id, s.tripId, "PROOF_UPLOADED", "", s.departmentId);
      return metadata(p);
    } catch (IOException e) {
      throw new Problem(400, "INVALID_IMAGE");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** 每次下载按当前运单权限校验，返回原始图片字节。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public DeliveryProof get(Long id) {
    var p = db.get(DeliveryProof.class, id);
    access.shipmentRead(db.get(Shipment.class, p.shipmentId));
    return p;
  }

  /** 附件元数据无内容，不泄露物理存储路径。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> metadata(DeliveryProof p) {
    return Map.of(
        "id", p.id, "mime", p.mime, "size", p.size, "sha256", p.sha256, "createdAt", p.createdAt);
  }
}
