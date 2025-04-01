package jenkins.plugins.http_request.auth;

import java.io.IOException;
import java.io.PrintStream;
import java.security.KeyStore;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;

import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;

import hudson.Util;

public class CertificateAuthentication implements Authenticator {

    private final StandardCertificateCredentials credentials;

    private final boolean ignoreSslErrors;

    public CertificateAuthentication(StandardCertificateCredentials credentials) {
        this.credentials = credentials;
        this.ignoreSslErrors = false;
    }

    public CertificateAuthentication(StandardCertificateCredentials credentials, boolean ignoreSslErrors) {
        this.credentials = credentials;
        this.ignoreSslErrors = ignoreSslErrors;
    }

    @Override
    public String getKeyName() {
        return credentials.getId();
    }

    @Override
    public CloseableHttpClient authenticate(HttpClientBuilder clientBuilder,
                                            HttpClientContext context,
                                            HttpUriRequestBase requestBase,
                                            PrintStream logger) throws IOException {
        try {
            KeyStore keyStore = credentials.getKeyStore();
            // Note: modeled after CertificateCredentialsImpl.toCharArray()
            // which ignores both null and "" empty passwords, even though
            // technically the byte stream reader there *can* decipher with
            // "" as the password. The null value is explicitly ignored by
            // ultimate sun.security.pkcs12.PKCS12KeyStore::engineLoad(),
            // for more context see comments in its sources.
            String keyStorePass = Util.fixEmpty(credentials.getPassword().getPlainText());
            char[] keyStorePassChars = (keyStorePass == null ? null : keyStorePass.toCharArray());
            SSLContextBuilder contextBuilder = SSLContexts.custom();

            if (keyStorePassChars == null) {
                logger.println("WARNING: Jenkins Certificate Credential '" +
                    credentials.getId() + "' was saved without a password, " +
                    "so any certificates (and chain of trust) in it would " +
                    "be ignored by Java PKCS12 support!");
            }

            try {
                TrustStrategy trustStrategy = null;
                if (ignoreSslErrors) {
                    // e.g. for user certificate issued by test CA so
                    // is not persisted in the system 'cacerts' file.
                    // Hopefully it is at least added/trusted in the
                    // generated keystore...
                    trustStrategy = new TrustAllStrategy();
                    //trustStrategy = new TrustSelfSignedStrategy();
                }

                contextBuilder.loadTrustMaterial(keyStore, trustStrategy);
                logger.println("Added Trust Material from provided KeyStore");
            } catch (Exception e) {
                logger.println("Failed to add Trust Material from provided KeyStore (so Key Material might end up untrusted): " + e.getMessage());
                // Do no re-throw, maybe system trust would suffice?
                // TODO: Can we identify lack of trust material in
                //  key store vs. inability to load what exists?..
                //  And do we really care about the difference?
            }

            contextBuilder.loadKeyMaterial(keyStore, keyStorePassChars);
            logger.println("Added Key Material from provided KeyStore");

            ConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(contextBuilder.build(), new DefaultHostnameVerifier());
            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create()
                            .register("https", sslsf)
                            .register("http", new PlainConnectionSocketFactory())
                            .build();
            PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            clientBuilder.setConnectionManager(connectionManager);
            logger.println("Set SSL context and socket factory for the HTTP client builder");

            return clientBuilder.build();
        } catch (Exception e) {
            logger.println("Failed to set SSL context: " + e.getMessage());
            throw new IOException(e);
        }
    }
}
