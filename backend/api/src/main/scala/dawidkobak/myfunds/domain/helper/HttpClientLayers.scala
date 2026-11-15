package dawidkobak.myfunds.domain.helper

import java.net.http.HttpClient
import java.security.cert.X509Certificate
import javax.net.ssl.{SSLContext, TrustManager, X509TrustManager}

import org.asynchttpclient.DefaultAsyncHttpClientConfig
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.*

case class Http1Backend(client: SttpBackend[Task, ZioStreams & WebSockets]) extends AnyVal
case class Http2Backend(client: SttpBackend[Task, ZioStreams & WebSockets]) extends AnyVal
case class InsecureTlsBackend(client: SttpBackend[Task, ZioStreams & WebSockets]) extends AnyVal
case class PooledAsyncBackend(client: SttpBackend[Task, ZioStreams & WebSockets]) extends AnyVal

object HttpClientLayers {
  type HttpBackend = SttpBackend[Task, ZioStreams & WebSockets]
  private type StreamingCapabilities = ZioStreams & WebSockets
  implicit val backendTag: Tag[HttpBackend] = Tag.materialize[SttpBackend[Task, StreamingCapabilities]]

  // Legacy opt-in configuration: certificate validation is deliberately disabled.
  private val uncheckedCertificates: Array[TrustManager] = Array(new X509TrustManager {
    override def getAcceptedIssuers: Array[X509Certificate] = Array.empty
    override def checkClientTrusted(certificates: Array[X509Certificate], authenticationType: String): Unit = ()
    override def checkServerTrusted(certificates: Array[X509Certificate], authenticationType: String): Unit = ()
  })
  private val permissiveTlsContext: SSLContext = SSLContext.getInstance("TLS")
  permissiveTlsContext.init(null, uncheckedCertificates, new java.security.SecureRandom())

  val permissiveJavaClient: HttpClient = HttpClient.newBuilder().sslContext(permissiveTlsContext).build()
  val insecureJavaLayer: TaskLayer[HttpBackend] = HttpClientZioBackend.layerUsingClient(permissiveJavaClient)

  def javaClientLayer(httpVersion: HttpClient.Version, allowUntrustedCertificates: Boolean = false): TaskLayer[HttpBackend] =
    ZLayer.scoped {
      for {
        blockingExecutor <- ZIO.blockingExecutor
        javaClient <- ZIO.attemptBlocking {
          val builder = HttpClient.newBuilder().executor(blockingExecutor.asJava).version(httpVersion)
          if (allowUntrustedCertificates) builder.sslContext(permissiveTlsContext)
          builder.build()
        }
        backend <- HttpClientZioBackend.scopedUsingClient(javaClient)
      } yield backend
    }

  val http2Layer: TaskLayer[Http2Backend] =
    javaClientLayer(HttpClient.Version.HTTP_2) >>> ZLayer.fromFunction((backend: HttpBackend) => Http2Backend(backend))

  private def pooledConfiguration(allowUntrustedCertificates: Boolean): DefaultAsyncHttpClientConfig = {
    val builder = new DefaultAsyncHttpClientConfig.Builder()
      .setMaxConnections(1000)
      .setMaxConnectionsPerHost(500)
      .setPooledConnectionIdleTimeout(60000)
      .setConnectionTtl(300000)
      .setConnectTimeout(10000)
      .setRequestTimeout(180000)
      .setReadTimeout(180000)
      .setKeepAlive(true)
      .setCompressionEnforced(true)
      .setFollowRedirect(true)
      .setMaxRedirects(5)
      .setIoThreadsCount(java.lang.Runtime.getRuntime.availableProcessors() * 2)
    builder.setUseInsecureTrustManager(allowUntrustedCertificates).build()
  }

  private val verifiedPoolConfiguration = pooledConfiguration(allowUntrustedCertificates = false)
  private val uncheckedPoolConfiguration = pooledConfiguration(allowUntrustedCertificates = true)

  val pooledAsyncLayer: TaskLayer[HttpBackend] =
    ZLayer.scoped(AsyncHttpClientZioBackend.scopedUsingConfig(verifiedPoolConfiguration))

  val asyncLayer: TaskLayer[PooledAsyncBackend] =
    pooledAsyncLayer >>> ZLayer.fromFunction((backend: HttpBackend) => PooledAsyncBackend(backend))

  val insecureAsyncLayer: TaskLayer[InsecureTlsBackend] =
    ZLayer.scoped {
      AsyncHttpClientZioBackend.scopedUsingConfig(uncheckedPoolConfiguration)
        .map(backend => InsecureTlsBackend(backend))
    }
}
