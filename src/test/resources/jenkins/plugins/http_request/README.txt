Test keystores:

* test.p12 was picked from credentials-plugin
  It includes one entry aliased "1" with a private key,
  its cert, and issuing CA cert (as a simple chain).
  The keystore password is "password"

* testTrusted.p12 is a modification of that, adding an
  entry aliased "ca" with that same CA cert imported as
  trusted:
````
# Show certs in BASE64 format, last of these is CA:
:; keytool -list -keystore test.p12 -storepass password -alias 1 -rfc

# Save second block to "ca.pem"

# Re-import:
:; cp test.p12 testTrusted.p12
:; keytool -importcert -trustcacerts -alias ca -file ca.pem -keystore testTrusted.p12 -storepass password
...
Trust this certificate? [no]:  yes
Certificate was added to keystore
````

Verification:
````
:; keytool -list -keystore testTrusted.p12 -storepass password -rfc
Keystore type: PKCS12
Keystore provider: SUN

Your keystore contains 2 entries

Alias name: 1
Creation date: 23.11.2022
Entry type: PrivateKeyEntry
Certificate chain length: 2
Certificate[1]:
-----BEGIN CERTIFICATE-----
MIIDRzCCArCgAwIBAgIBATANBgkqhkiG9w0BAQQFADBmMQswCQYDVQQGEwJLRzEL
MAkGA1UECBMCTkExEDAOBgNVBAcTB0JJU0hLRUsxFTATBgNVBAoTDE9wZW5WUE4t
VEVTVDEhMB8GCSqGSIb3DQEJARYSbWVAbXlob3N0Lm15ZG9tYWluMB4XDTA1MDgw
NDE4MTYyMFoXDTE1MDgwMjE4MTYyMFowfDELMAkGA1UEBhMCVVMxCzAJBgNVBAgT
AkNBMRUwEwYDVQQHEwxTYW5GcmFuY2lzY28xFTATBgNVBAoTDEZvcnQtRnVuc3Rv
bjEPMA0GA1UEAxMGcGtjczEyMSEwHwYJKoZIhvcNAQkBFhJtZUBteWhvc3QubXlk
b21haW4wgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAMOT0PRbWiTEJTUjjiwW
yPC7hR2ruxshzWcgWZUuNg5RARnnsQfGpBK+kKp4QsJSunVCo2fmUFkU/UGYVVXK
nHMEcDtX2JqVY/bAPjxptn5k1bnvMFkKFnaAZl5Mi0K0s+D9U0ivpIaw1QXdQbw+
w3STcv1kpy8rmyerH6KOXL1bAgMBAAGjge4wgeswCQYDVR0TBAIwADAsBglghkgB
hvhCAQ0EHxYdT3BlblNTTCBHZW5lcmF0ZWQgQ2VydGlmaWNhdGUwHQYDVR0OBBYE
FP9dcedV6TFtLIWOWXxIQ5h6JR45MIGQBgNVHSMEgYgwgYWAFImmYOO66j6v/GR/
TL2M0kiN4MxGoWqkaDBmMQswCQYDVQQGEwJLRzELMAkGA1UECBMCTkExEDAOBgNV
BAcTB0JJU0hLRUsxFTATBgNVBAoTDE9wZW5WUE4tVEVTVDEhMB8GCSqGSIb3DQEJ
ARYSbWVAbXlob3N0Lm15ZG9tYWluggEAMA0GCSqGSIb3DQEBBAUAA4GBABP/5mXw
ttXKG6dqQl5kPisFs/c+0j64xytp5/cdB/zMpEWRWTBXtyL3T5T16xs52kJS0VfT
t+jezYbeu/dCdBL8Moz3RTYb1aY2/xymZ433kWjvgtrOzgGlaW3eKXcQpQEyK2v/
J4q7+oDCElBRilZCm0mBcQsySKsZjGm8BMjh
-----END CERTIFICATE-----
Certificate[2]:
-----BEGIN CERTIFICATE-----
MIIDBjCCAm+gAwIBAgIBADANBgkqhkiG9w0BAQQFADBmMQswCQYDVQQGEwJLRzEL
MAkGA1UECBMCTkExEDAOBgNVBAcTB0JJU0hLRUsxFTATBgNVBAoTDE9wZW5WUE4t
VEVTVDEhMB8GCSqGSIb3DQEJARYSbWVAbXlob3N0Lm15ZG9tYWluMB4XDTA0MTEy
NTE0NDA1NVoXDTE0MTEyMzE0NDA1NVowZjELMAkGA1UEBhMCS0cxCzAJBgNVBAgT
Ak5BMRAwDgYDVQQHEwdCSVNIS0VLMRUwEwYDVQQKEwxPcGVuVlBOLVRFU1QxITAf
BgkqhkiG9w0BCQEWEm1lQG15aG9zdC5teWRvbWFpbjCBnzANBgkqhkiG9w0BAQEF
AAOBjQAwgYkCgYEAqPjWJnesPu6bR/iec4FMz3opVaPdBHxg+ORKNmrnVZPh0t8/
ZT34KXkYoI9B82scurp8UlZVXG8JdUsz+yai8ti9+g7vcuyKUtcCIjn0HLgmdPu5
gFX25lB0pXw+XIU031dOfPvtROdG5YZN5yCErgCy7TE7zntLnkEDuRmyU6cCAwEA
AaOBwzCBwDAdBgNVHQ4EFgQUiaZg47rqPq/8ZH9MvYzSSI3gzEYwgZAGA1UdIwSB
iDCBhYAUiaZg47rqPq/8ZH9MvYzSSI3gzEahaqRoMGYxCzAJBgNVBAYTAktHMQsw
CQYDVQQIEwJOQTEQMA4GA1UEBxMHQklTSEtFSzEVMBMGA1UEChMMT3BlblZQTi1U
RVNUMSEwHwYJKoZIhvcNAQkBFhJtZUBteWhvc3QubXlkb21haW6CAQAwDAYDVR0T
BAUwAwEB/zANBgkqhkiG9w0BAQQFAAOBgQBfJoiWYrYdjM0mKPEzUQk0nLYTovBP
I0es/2rfGrin1zbcFY+4dhVBd1E/StebnG+CP8r7QeEIwu7x8gYDdOLLsZn+2vBL
e4jNU1ClI6Q0L7jrzhhunQ5mAaZztVyYwFB15odYcdN2iO0tP7jtEsvrRqxICNy3
8itzViPTf5W4sA==
-----END CERTIFICATE-----


*******************************************
*******************************************


Alias name: ca
Creation date: 23.11.2022
Entry type: trustedCertEntry

-----BEGIN CERTIFICATE-----
MIIDBjCCAm+gAwIBAgIBADANBgkqhkiG9w0BAQQFADBmMQswCQYDVQQGEwJLRzEL
MAkGA1UECBMCTkExEDAOBgNVBAcTB0JJU0hLRUsxFTATBgNVBAoTDE9wZW5WUE4t
VEVTVDEhMB8GCSqGSIb3DQEJARYSbWVAbXlob3N0Lm15ZG9tYWluMB4XDTA0MTEy
NTE0NDA1NVoXDTE0MTEyMzE0NDA1NVowZjELMAkGA1UEBhMCS0cxCzAJBgNVBAgT
Ak5BMRAwDgYDVQQHEwdCSVNIS0VLMRUwEwYDVQQKEwxPcGVuVlBOLVRFU1QxITAf
BgkqhkiG9w0BCQEWEm1lQG15aG9zdC5teWRvbWFpbjCBnzANBgkqhkiG9w0BAQEF
AAOBjQAwgYkCgYEAqPjWJnesPu6bR/iec4FMz3opVaPdBHxg+ORKNmrnVZPh0t8/
ZT34KXkYoI9B82scurp8UlZVXG8JdUsz+yai8ti9+g7vcuyKUtcCIjn0HLgmdPu5
gFX25lB0pXw+XIU031dOfPvtROdG5YZN5yCErgCy7TE7zntLnkEDuRmyU6cCAwEA
AaOBwzCBwDAdBgNVHQ4EFgQUiaZg47rqPq/8ZH9MvYzSSI3gzEYwgZAGA1UdIwSB
iDCBhYAUiaZg47rqPq/8ZH9MvYzSSI3gzEahaqRoMGYxCzAJBgNVBAYTAktHMQsw
CQYDVQQIEwJOQTEQMA4GA1UEBxMHQklTSEtFSzEVMBMGA1UEChMMT3BlblZQTi1U
RVNUMSEwHwYJKoZIhvcNAQkBFhJtZUBteWhvc3QubXlkb21haW6CAQAwDAYDVR0T
BAUwAwEB/zANBgkqhkiG9w0BAQQFAAOBgQBfJoiWYrYdjM0mKPEzUQk0nLYTovBP
I0es/2rfGrin1zbcFY+4dhVBd1E/StebnG+CP8r7QeEIwu7x8gYDdOLLsZn+2vBL
e4jNU1ClI6Q0L7jrzhhunQ5mAaZztVyYwFB15odYcdN2iO0tP7jtEsvrRqxICNy3
8itzViPTf5W4sA==
-----END CERTIFICATE-----


*******************************************
*******************************************



Warning:
<1> uses the MD5withRSA signature algorithm which is considered a security risk and is disabled.
<1> uses a 1024-bit RSA key which is considered a security risk. This key size will be disabled in a future update.
<1> uses a 1024-bit RSA key which is considered a security risk. This key size will be disabled in a future update.
<ca> uses a 1024-bit RSA key which is considered a security risk. This key size will be disabled in a future update.



# Keytool forbids to export private key; openssl can do it:
:; openssl pkcs12 -in testTrusted.p12 -nodes -nocerts -password pass:password
Bag Attributes
    friendlyName: 1
    localKeyID: 2E 39 A5 71 AE FD F1 64 40 83 69 72 3A B6 3D 64 05 18 58 B7
Key Attributes: <No Attributes>
-----BEGIN PRIVATE KEY-----
MIICdwIBADANBgkqhkiG9w0BAQEFAASCAmEwggJdAgEAAoGBAMOT0PRbWiTEJTUj
jiwWyPC7hR2ruxshzWcgWZUuNg5RARnnsQfGpBK+kKp4QsJSunVCo2fmUFkU/UGY
VVXKnHMEcDtX2JqVY/bAPjxptn5k1bnvMFkKFnaAZl5Mi0K0s+D9U0ivpIaw1QXd
Qbw+w3STcv1kpy8rmyerH6KOXL1bAgMBAAECgYBommqsByATggUUgsvLsPQQLXto
/yy3ukCN47OGIo0u4wxfupfovMmMbPga9O9f17d6eAXF0F0xCBTcPImHtTIvMLIt
UY4U4xwtdlEM3G5ToBxNvCHvtkkDiUVW8AorZfLFY9Agnsc3cTarrvEkdtzyYN8k
246tqACTJZEW8b/QoQJBAP/cd5sEPACChyHx7jr44mMBUppfu/5QC3+0N39XhTx+
gXNfpRe/V77Qn0CMcH8RqkQVWTaSzPrpzJcAXgc9cB8CQQDDrvois8c+5ZSGu8MG
1zNCEjxTU9BBjWEkGLgwMwsH+5BlA7QT8B9QGWYiCJ4pJVJQ37AyQrUqt4at19Yb
vdtFAkEAgylFtxXInIpNM72N3nVPuGkpKzIAcTIfcuuzt3fqOUSwn7BcNXxFQvA3
cyOLV9h6bER1Y2CF6+qGkrIBgbyhCQJBAKR14vRXdBWAjhvOolKVexcEjH7b6iOt
1v6nZ+XagGLtIqZDPo2jOi3vqs7fv02FeHFQDp2vQuPr6t0gkWovXqECQFEAAAj3
TKXvRs1jL5gKNifOBJgeEqzZJXpLMkWDGTgImu+VyKcGE6+pie0okh4rmIoJqNx0
EzBIslSYYUz8Q+A=
-----END PRIVATE KEY-----
````

