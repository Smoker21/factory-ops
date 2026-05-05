/**
 * MongoDB Replica Set 初始化腳本
 * 由 mongo-rs-init 服務一次性執行
 * 冪等設計：已初始化時不報錯
 */
try {
  var status = rs.status();
  print("Replica set already initialized, status: " + status.set);
} catch (e) {
  print("Initializing replica set rs0...");
  var result = rs.initiate({
    _id: "rs0",
    members: [{ _id: 0, host: "mongo:27017" }]
  });
  print("rs.initiate() result: " + JSON.stringify(result));
  if (result.ok !== 1) {
    throw new Error("Failed to initiate replica set: " + JSON.stringify(result));
  }
  print("Replica set rs0 initialized successfully.");
}
