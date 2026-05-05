# ⚠️ DEVELOPMENT-ONLY JWT KEYS

`privateKey.pem` 與 `publicKey.pem` 是**僅供本機開發**的 RSA 金鑰對,目的是讓 Quarkus 的 SmallRye JWT 在 `dev` profile 直接可用,不必每位開發者各自產生。

## 安全須知

- **絕對不可** 用在 staging / production 環境。
- 此處的私鑰已隨原始碼公開(GitHub repo),任何人都能簽 JWT。
- 部署前請執行下方步驟產生新的金鑰對,並由 secrets manager(Vault / Doppler / AWS Secrets Manager…)注入。

## 重新產生(production)

```bash
openssl genpkey -algorithm RSA -out privateKey.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in privateKey.pem -out publicKey.pem
```

並把對應 `application-prod.properties` 的兩個 key 路徑換成 secrets manager 注入的路徑或 env var:

```properties
mp.jwt.verify.publickey.location=${JWT_PUBLIC_KEY_PATH}
smallrye.jwt.sign.key.location=${JWT_PRIVATE_KEY_PATH}
```
