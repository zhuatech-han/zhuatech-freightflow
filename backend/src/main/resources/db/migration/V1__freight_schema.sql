-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2

CREATE TABLE department (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE
);

CREATE TABLE access_role (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE,
  scope varchar(20) NOT NULL
);

CREATE TABLE permission (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL
);

CREATE TABLE role_permission (role_id bigint NOT NULL, permission_code varchar(60) NOT NULL, PRIMARY KEY(role_id, permission_code), FOREIGN KEY(role_id) REFERENCES access_role(id), FOREIGN KEY(permission_code) REFERENCES permission(code));

CREATE TABLE nav_menu (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  permission_code varchar(60) NOT NULL,
  position int NOT NULL,
  enabled boolean NOT NULL
);

CREATE TABLE account (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  username varchar(60) NOT NULL UNIQUE,
  display_name varchar(120) NOT NULL,
  password_hash varchar(100) NOT NULL,
  role_id bigint NOT NULL,
  department_id bigint NOT NULL,
  enabled boolean NOT NULL,
  FOREIGN KEY (role_id) REFERENCES access_role(id),
  FOREIGN KEY (department_id) REFERENCES department(id)
);

CREATE TABLE audit_event (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  actor varchar(60) NOT NULL,
  action varchar(120) NOT NULL,
  object_id varchar(80) NOT NULL,
  department_id bigint NOT NULL,
  created_at timestamp(6) NOT NULL
);

CREATE TABLE system_setting (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  parameter_value varchar(200) NOT NULL
);

CREATE TABLE dictionary_entry (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  type varchar(60) NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
enabled boolean NOT NULL DEFAULT TRUE,
  UNIQUE (type, code)
);


CREATE TABLE customer (
id bigint AUTO_INCREMENT PRIMARY KEY,
code varchar(60) NOT NULL,
name varchar(120) NOT NULL,
account_id bigint,
contact varchar(120) NOT NULL,
phone varchar(60) NOT NULL,
address varchar(400) NOT NULL,
department_id bigint NOT NULL,
enabled boolean NOT NULL,
UNIQUE(code),
UNIQUE(account_id),
FOREIGN KEY(account_id) REFERENCES account(id),
FOREIGN KEY(department_id) REFERENCES department(id)
);

CREATE TABLE carrier (
id bigint AUTO_INCREMENT PRIMARY KEY,
code varchar(60) NOT NULL,
name varchar(120) NOT NULL,
contact varchar(120) NOT NULL,
phone varchar(60) NOT NULL,
department_id bigint NOT NULL,
enabled boolean NOT NULL,
UNIQUE(code),
FOREIGN KEY(department_id) REFERENCES department(id)
);

CREATE TABLE driver (
id bigint AUTO_INCREMENT PRIMARY KEY,
code varchar(60) NOT NULL,
name varchar(120) NOT NULL,
account_id bigint NOT NULL,
carrier_id bigint NOT NULL,
phone varchar(60) NOT NULL,
license_number varchar(80) NOT NULL,
license_expires date,
department_id bigint NOT NULL,
enabled boolean NOT NULL,
UNIQUE(code),
UNIQUE(account_id),
FOREIGN KEY(account_id) REFERENCES account(id),
FOREIGN KEY(carrier_id) REFERENCES carrier(id),
FOREIGN KEY(department_id) REFERENCES department(id)
);

CREATE TABLE vehicle (
id bigint AUTO_INCREMENT PRIMARY KEY,
code varchar(60) NOT NULL,
name varchar(120) NOT NULL,
carrier_id bigint NOT NULL,
max_weight_kg decimal(18,3) NOT NULL,
max_volume_m3 decimal(18,3) NOT NULL,
max_pieces int NOT NULL,
department_id bigint NOT NULL,
enabled boolean NOT NULL,
UNIQUE(code),
FOREIGN KEY(carrier_id) REFERENCES carrier(id),
FOREIGN KEY(department_id) REFERENCES department(id),
CHECK(max_pieces>0 AND max_weight_kg>0 AND max_volume_m3>0)
);

CREATE TABLE shipment (
id bigint AUTO_INCREMENT PRIMARY KEY,
code varchar(60) NOT NULL,
customer_id bigint NOT NULL,
customer_name varchar(120) NOT NULL,
origin varchar(400) NOT NULL,
destination varchar(400) NOT NULL,
consignee varchar(120) NOT NULL,
consignee_phone varchar(60) NOT NULL,
goods varchar(400) NOT NULL,
pieces int NOT NULL,
weight_kg decimal(18,3) NOT NULL,
volume_m3 decimal(18,3) NOT NULL,
freight decimal(18,2) NOT NULL,
status varchar(30) NOT NULL,
trip_id bigint,
invoice_id bigint,
receiver varchar(120) NOT NULL,
delivered_at timestamp(6),
department_id bigint NOT NULL,
revision int NOT NULL,
created_by varchar(60) NOT NULL,
created_at timestamp(6) NOT NULL,
updated_at timestamp(6) NOT NULL,
UNIQUE(code),
FOREIGN KEY(customer_id) REFERENCES customer(id),
FOREIGN KEY(department_id) REFERENCES department(id),
CHECK(pieces>0 AND weight_kg>0 AND volume_m3>0 AND freight>=0),
INDEX ix_ship_dept_status(department_id,status),
INDEX ix_ship_trip(trip_id)
);

CREATE TABLE trip (
id bigint AUTO_INCREMENT PRIMARY KEY,
code varchar(60) NOT NULL,
carrier_id bigint NOT NULL,
carrier_name varchar(120) NOT NULL,
driver_id bigint NOT NULL,
driver_name varchar(120) NOT NULL,
vehicle_id bigint NOT NULL,
vehicle_name varchar(120) NOT NULL,
planned_start timestamp(6) NOT NULL,
planned_end timestamp(6) NOT NULL,
started_at timestamp(6),
closed_at timestamp(6),
carrier_fee decimal(18,2) NOT NULL,
invoice_id bigint,
status varchar(30) NOT NULL,
note varchar(800) NOT NULL,
department_id bigint NOT NULL,
revision int NOT NULL,
created_by varchar(60) NOT NULL,
created_at timestamp(6) NOT NULL,
updated_at timestamp(6) NOT NULL,
UNIQUE(code),
FOREIGN KEY(carrier_id) REFERENCES carrier(id),
FOREIGN KEY(driver_id) REFERENCES driver(id),
FOREIGN KEY(vehicle_id) REFERENCES vehicle(id),
FOREIGN KEY(department_id) REFERENCES department(id),
CHECK(planned_end>planned_start AND carrier_fee>=0),
INDEX ix_trip_resources(driver_id,vehicle_id,status)
);

CREATE TABLE expense (
id bigint AUTO_INCREMENT PRIMARY KEY,
trip_id bigint NOT NULL,
type varchar(30) NOT NULL,
amount decimal(18,2) NOT NULL,
reference varchar(120) NOT NULL,
note varchar(800) NOT NULL,
status varchar(30) NOT NULL,
created_by varchar(60) NOT NULL,
reviewed_by varchar(60) NOT NULL,
review_note varchar(800) NOT NULL,
created_at timestamp(6) NOT NULL,
department_id bigint NOT NULL,
revision int NOT NULL,
FOREIGN KEY(trip_id) REFERENCES trip(id),
FOREIGN KEY(department_id) REFERENCES department(id),
CHECK(amount>0)
);

CREATE TABLE invoice (
 void_reason VARCHAR(800) NOT NULL,
id bigint AUTO_INCREMENT PRIMARY KEY,
code varchar(60) NOT NULL,
kind varchar(30) NOT NULL,
customer_id bigint,
carrier_id bigint,
trip_id bigint,
party_name varchar(120) NOT NULL,
total decimal(18,2) NOT NULL,
paid decimal(18,2) NOT NULL,
due_date date NOT NULL,
status varchar(30) NOT NULL,
department_id bigint NOT NULL,
revision int NOT NULL,
created_at timestamp(6) NOT NULL,
created_by varchar(60) NOT NULL,
UNIQUE(code),
FOREIGN KEY(customer_id) REFERENCES customer(id),
FOREIGN KEY(carrier_id) REFERENCES carrier(id),
FOREIGN KEY(trip_id) REFERENCES trip(id),
FOREIGN KEY(department_id) REFERENCES department(id),
CHECK(total>=0 AND paid>=0 AND paid<=total)
);

CREATE TABLE invoice_line (
id bigint AUTO_INCREMENT PRIMARY KEY,
invoice_id bigint NOT NULL,
shipment_id bigint,
description varchar(400) NOT NULL,
amount decimal(18,2) NOT NULL,
FOREIGN KEY(invoice_id) REFERENCES invoice(id),
FOREIGN KEY(shipment_id) REFERENCES shipment(id),
CHECK(amount>=0)
);

CREATE TABLE settlement_entry (
id bigint AUTO_INCREMENT PRIMARY KEY,
invoice_id bigint NOT NULL,
source_id bigint,
kind varchar(30) NOT NULL,
amount decimal(18,2) NOT NULL,
method varchar(30) NOT NULL,
reference varchar(120) NOT NULL,
note varchar(800) NOT NULL,
actor varchar(60) NOT NULL,
created_at timestamp(6) NOT NULL,
department_id bigint NOT NULL,
FOREIGN KEY(invoice_id) REFERENCES invoice(id),
FOREIGN KEY(source_id) REFERENCES settlement_entry(id),
FOREIGN KEY(department_id) REFERENCES department(id),
CHECK(amount>0)
);

CREATE TABLE transport_event (
id bigint AUTO_INCREMENT PRIMARY KEY,
shipment_id bigint,
trip_id bigint,
kind varchar(60) NOT NULL,
note varchar(800) NOT NULL,
status varchar(30) NOT NULL,
resolved_by varchar(60) NOT NULL,
resolution varchar(800) NOT NULL,
actor varchar(60) NOT NULL,
created_at timestamp(6) NOT NULL,
department_id bigint NOT NULL,
revision int NOT NULL,
FOREIGN KEY(shipment_id) REFERENCES shipment(id),
FOREIGN KEY(trip_id) REFERENCES trip(id),
FOREIGN KEY(department_id) REFERENCES department(id)
);

CREATE TABLE delivery_proof (
id bigint AUTO_INCREMENT PRIMARY KEY,
shipment_id bigint NOT NULL,
sha256 varchar(64) NOT NULL,
mime varchar(30) NOT NULL,
size int NOT NULL,
payload longblob NOT NULL,
actor varchar(60) NOT NULL,
created_at timestamp(6) NOT NULL,
department_id bigint NOT NULL,
FOREIGN KEY(shipment_id) REFERENCES shipment(id),
FOREIGN KEY(department_id) REFERENCES department(id)
);

ALTER TABLE shipment ADD FOREIGN KEY(trip_id) REFERENCES trip(id);
ALTER TABLE shipment ADD FOREIGN KEY(invoice_id) REFERENCES invoice(id);
ALTER TABLE trip ADD FOREIGN KEY(invoice_id) REFERENCES invoice(id);
CREATE TABLE mutation_stamp(id bigint AUTO_INCREMENT PRIMARY KEY,request_key varchar(80) NOT NULL UNIQUE,fingerprint varchar(64) NOT NULL,result_id bigint NULL);
